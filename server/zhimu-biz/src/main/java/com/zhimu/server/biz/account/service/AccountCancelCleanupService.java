package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.AccountCancel;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.mapper.AccountCancelMapper;
import com.zhimu.server.biz.account.mapper.AccountPersonalCleanupMapper;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.common.api.AccountFileCleanupService;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.biz.account.support.Secrets;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.integration.push.PushClient;
import com.zhimu.server.integration.push.PushResult;
import com.zhimu.server.integration.push.PushStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 单个注销账号的分阶段清理。每阶段独立事务；锁顺序始终是 user → cancel → 个人资源。
 * 外部解绑成功后本地失败可以安全重试，其他阶段失败不会撤销已经完成的个人清理。
 */
@Slf4j
@Service
public class AccountCancelCleanupService {

    private static final int[] RETRY_MINUTES = {1, 5, 15, 60};
    private final AccountCancelMapper cancelMapper;
    private final UserMapper userMapper;
    private final AccountPersonalCleanupMapper personalMapper;
    private final BizMediaService media;
    private final AccountFileCleanupService files;
    private final FarmAccountService farms;
    private final PushClient push;
    private final AccountProperties properties;
    private final TransactionTemplate transaction;

    public AccountCancelCleanupService(AccountCancelMapper cancelMapper, UserMapper userMapper,
                                       AccountPersonalCleanupMapper personalMapper, BizMediaService media,
                                       AccountFileCleanupService files, FarmAccountService farms,
                                       PushClient push, AccountProperties properties,
                                       PlatformTransactionManager transactionManager) {
        this.cancelMapper = cancelMapper;
        this.userMapper = userMapper;
        this.personalMapper = personalMapper;
        this.media = media;
        this.files = files;
        this.farms = farms;
        this.push = push;
        this.properties = properties;
        transaction = new TransactionTemplate(transactionManager);
        // AccountCancelledEvent 在原事务 afterCommit 发布；绝不能复用那个已提交事务的连接。
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void cleanup(Long userId) {
        if (userId == null) {
            return;
        }
        String stage = "PERSONAL";
        try {
            if (!phase(userId, stage)) {
                return;
            }
            stage = "FILES";
            files.purgeQueued(userId);
            stage = "PHONE";
            phase(userId, stage);
            stage = "FARM";
            phase(userId, stage);
            stage = "FILES";
            files.purgeQueued(userId);
            stage = "FINALIZE";
            phase(userId, stage);
        } catch (RuntimeException error) {
            // 不记录 SDK 原文、手机号或个人内容；在回滚后用独立事务保存可重试的失败状态。
            String errorType = error.getClass().getSimpleName();
            log.warn("注销清理失败 userId={} stage={} errorType={}", userId, stage, errorType);
            try {
                String failedStage = stage;
                transaction.executeWithoutResult(status -> recordFailure(userId, failedStage, errorType));
            } catch (RuntimeException failure) {
                log.warn("注销清理失败状态保存失败 userId={} errorType={}", userId,
                        failure.getClass().getSimpleName());
            }
        }
    }

    private boolean phase(Long userId, String stage) {
        return Boolean.TRUE.equals(transaction.execute(status -> TenantSkip.call(() -> {
            User user = userMapper.selectByIdForUpdate(userId);
            AccountCancel row = cancelMapper.selectByUserForUpdate(userId);
            if (row == null || "DONE".equals(row.getCleanupStatus())) {
                return false;
            }
            LocalDateTime now = BeijingTime.now();
            // 初次事件必须立即处理；DATETIME 秒精度可能将刚写入的 next_run_at 舍入到未来。
            // 已失败的重试或等待保留期的记录仍严格遵守持久化调度时间。
            boolean firstPending = "PENDING".equals(row.getCleanupStatus()) && row.getPersonalCleanedAt() == null;
            if ("PERSONAL".equals(stage) && !firstPending
                    && row.getNextRunAt() != null && row.getNextRunAt().isAfter(now)) {
                return false;
            }
            requireSameCancelledAccount(user, row);
            row.setCleanupStatus("RUNNING");
            switch (stage) {
                case "PERSONAL" -> {
                    if (row.getPersonalCleanedAt() == null) {
                        cleanPersonal(userId, now);
                        row.setPersonalCleanedAt(now);
                    }
                }
                case "PHONE" -> releasePhone(user, row, now);
                case "FARM" -> {
                    if (row.getFarmCleanedAt() == null) {
                        LocalDateTime next = farms.cleanupCancelledOwnerFarms(userId, now);
                        row.setFarmCleanupAt(next);
                        if (next == null) {
                            row.setFarmCleanedAt(now);
                        }
                    }
                }
                case "FINALIZE" -> { /* 对象删除在事务外完成，在此刷新持久化完成状态。 */ }
                default -> throw new IllegalArgumentException("Unknown cleanup stage");
            }
            row.setLastError(null);
            if ("FINALIZE".equals(stage)) {
                row.setRetryCount(0);
            }
            schedule(row, user, now);
            save(row, now);
            return true;
        })));
    }

    private void cleanPersonal(Long userId, LocalDateTime now) {
        // 与设备换账号登记共用行锁；只解绑当前仍属于该注销用户的设备。
        for (var device : personalMapper.lockDevices(userId)) {
            if (!Objects.equals(userId, device.getUserId())) {
                throw new IllegalStateException("Device owner changed");
            }
            boolean bound = Boolean.TRUE.equals(device.getAliasBound());
            if (push.isEnabled()) {
                PushResult result = push.unbindAlias(device.getRegistrationId());
                if (result == null || !(result.isSent()
                        || result.status() == PushStatus.SKIPPED && Objects.equals(result.errorCode(), 1011))) {
                    throw new IllegalStateException("Push unbind pending");
                }
            } else if (bound) {
                // 以前成功绑定过的设备不能因当前缺配置就冒称解绑完成。
                throw new IllegalStateException("Push unbind unavailable");
            }
            if (personalMapper.unbindDevice(userId, device.getId(), now) != 1) {
                throw new IllegalStateException("Device owner changed");
            }
        }
        personalMapper.deletePushRecords(userId);
        personalMapper.deleteMessages(userId);
        personalMapper.deleteReminderSettings(userId);
        personalMapper.deleteCoursePlayRecords(userId);
        personalMapper.deleteCourseProgress(userId);
        personalMapper.redactRecoveryCodes(userId, now);
        personalMapper.clearPersonalProfileLinks(userId, now);
        for (Long messageId : personalMapper.selectAiMessageIds(userId)) {
            // 解除绑定并重算 bound；共享文件和农场业务附件均不删除。
            media.removeAll(BizType.AI_MESSAGE, messageId);
        }
        personalMapper.deleteAiMediaLinks(userId);
        personalMapper.deleteAiCitations(userId);
        personalMapper.deleteAiCorrections(userId);
        personalMapper.redactAiReviewSnapshots(userId);
        personalMapper.detachKnowledgeTaskSources(userId);
        personalMapper.deleteAiMessages(userId);
        personalMapper.deleteAiSessions(userId);
        files.queuePersonalFiles(userId);
    }

    private void releasePhone(User user, AccountCancel row, LocalDateTime now) {
        if (row.getPhoneReleasedAt() != null) {
            return;
        }
        LocalDateTime due = phoneDue(user, row);
        row.setPhoneReleaseAt(due);
        if (now.isBefore(due)) {
            return;
        }
        if (user.getPhone() != null) {
            String expected = Secrets.sha256Hex("zhimu-account-cancel:" + user.getPhone());
            if (!Objects.equals(expected, row.getPhoneHash())) {
                throw new IllegalStateException("Cancellation phone mismatch");
            }
            if (personalMapper.releasePhone(user.getId(), user.getCancelledAt(), user.getPhone(), now) != 1) {
                throw new IllegalStateException("Cancellation account changed");
            }
        }
        row.setPhoneReleasedAt(now);
    }

    private static void requireSameCancelledAccount(User user, AccountCancel row) {
        if (user == null || !Objects.equals(user.getId(), row.getUserId())
                || !"CANCELLED".equals(user.getStatus()) || user.getCancelledAt() == null
                || row.getCreatedAt() == null
                || !user.getCancelledAt().truncatedTo(ChronoUnit.SECONDS)
                .equals(row.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))) {
            throw new IllegalStateException("Cancellation account mismatch");
        }
    }

    private LocalDateTime phoneDue(User user, AccountCancel row) {
        LocalDateTime minimum = user.getCancelledAt().plus(properties.getPhoneRelease());
        return row.getPhoneReleaseAt() != null && row.getPhoneReleaseAt().isAfter(minimum)
                ? row.getPhoneReleaseAt() : minimum;
    }

    private void schedule(AccountCancel row, User user, LocalDateTime now) {
        boolean pendingFiles = files.hasPending(row.getUserId());
        if (!pendingFiles && row.getPersonalCleanedAt() != null && row.getPhoneReleasedAt() != null
                && row.getFarmCleanedAt() != null) {
            row.setCleanupStatus("DONE");
            row.setRetryCount(0);
            row.setNextRunAt(null);
            return;
        }
        LocalDateTime next = row.getPersonalCleanedAt() == null || pendingFiles ? now : null;
        if (row.getPhoneReleasedAt() == null) {
            next = earlier(next, phoneDue(user, row));
        }
        if (row.getFarmCleanedAt() == null) {
            next = earlier(next, row.getFarmCleanupAt() == null ? now : row.getFarmCleanupAt());
        }
        row.setCleanupStatus("WAITING");
        row.setNextRunAt(next);
    }

    private static LocalDateTime earlier(LocalDateTime left, LocalDateTime right) {
        return left == null || right.isBefore(left) ? right : left;
    }

    private void recordFailure(Long userId, String stage, String errorType) {
        userMapper.selectByIdForUpdate(userId);
        AccountCancel row = cancelMapper.selectByUserForUpdate(userId);
        if (row == null || "DONE".equals(row.getCleanupStatus())) {
            return;
        }
        int failures = Math.min(1000000, row.getRetryCount() == null ? 1 : row.getRetryCount() + 1);
        LocalDateTime now = BeijingTime.now();
        row.setCleanupStatus("FAILED");
        row.setRetryCount(failures);
        row.setLastError(stage + ":" + errorType);
        row.setNextRunAt(now.plusMinutes(RETRY_MINUTES[Math.min(failures - 1, RETRY_MINUTES.length - 1)]));
        save(row, now);
    }

    private void save(AccountCancel row, LocalDateTime now) {
        if (cancelMapper.updateCleanupProgress(row, now) != 1) {
            throw new IllegalStateException("Cleanup progress changed");
        }
    }
}
