package com.zhimu.server.biz.account.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.api.AccountAdminService;
import com.zhimu.server.biz.account.entity.AccountRecovery;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.UserStatus;
import com.zhimu.server.biz.account.mapper.AccountRecoveryMapper;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.support.AccountValidation;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.token.UserTokenService;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/** 管理端停用/启用账号；缓存失效与刷新令牌作废在事务提交后执行。 */
@Service
@RequiredArgsConstructor
public class AccountAdminServiceImpl implements AccountAdminService {

    private final UserMapper userMapper;
    private final AccountRecoveryMapper accountRecoveryMapper;
    private final OperationLogRecorder operationLogRecorder;
    private final UserTokenService userTokenService;
    private final AuthStateCache authStateCache;

    @Override
    @Transactional
    public void disable(Long userId, Long adminId, String reason, boolean notifySms, int version) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写停用原因");
        }
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (user.getVersion() == null || user.getVersion() != version) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        if (UserStatus.CANCELLED.name().equals(user.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "账号已注销，不能停用");
        }
        if (UserStatus.DISABLED.name().equals(user.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "账号已是停用状态");
        }
        LocalDateTime now = BeijingTime.now();
        int updated = userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .eq(User::getVersion, version)
                .set(User::getStatus, UserStatus.DISABLED.name())
                .set(User::getDisabledAt, now)
                .set(User::getDisabledBy, adminId)
                .set(User::getDisabledReason, reason)
                .set(User::getTokenRevokeReason, "ACCOUNT_DISABLED")
                .set(User::getTokenRevokedAt, now)
                .setSql("token_version = token_version + 1")
                .setSql("version = version + 1")
                .set(User::getUpdatedAt, now));
        if (updated == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        autoRejectPendingRecoveries(user.getId(), now, "账号已被平台停用");
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("USER_STATUS")
                .opAction("DISABLE")
                .bizType("USER")
                .bizId(user.getId())
                .targetName(Masks.phone(user.getPhone()))
                .reason(reason)
                .before(Map.of("status", UserStatus.NORMAL.name()))
                .after(Map.of("status", UserStatus.DISABLED.name()))
                .build());
        AfterCommit.run(() -> {
            userTokenService.revokeAll(user.getId());
            authStateCache.evict(user.getId());
        });
    }

    @Override
    @Transactional
    public void enable(Long userId, Long adminId, int version) {
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (user.getVersion() == null || user.getVersion() != version) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        if (!UserStatus.DISABLED.name().equals(user.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "账号不是停用状态，不能启用");
        }
        LocalDateTime now = BeijingTime.now();
        int updated = userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .eq(User::getVersion, version)
                .set(User::getStatus, UserStatus.NORMAL.name())
                .set(User::getDisabledAt, null)
                .set(User::getDisabledBy, null)
                .set(User::getDisabledReason, null)
                .set(User::getTokenRevokeReason, null)
                .set(User::getTokenRevokedAt, null)
                .setSql("token_version = token_version + 1")
                .setSql("version = version + 1")
                .set(User::getUpdatedAt, now));
        if (updated == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("USER_STATUS")
                .opAction("ENABLE")
                .bizType("USER")
                .bizId(user.getId())
                .targetName(Masks.phone(user.getPhone()))
                .before(Map.of("status", UserStatus.DISABLED.name()))
                .after(Map.of("status", UserStatus.NORMAL.name()))
                .build());
        AfterCommit.run(() -> {
            userTokenService.revokeAll(user.getId());
            authStateCache.evict(user.getId());
        });
    }

    @Override
    public boolean existsPhoneIncludingCooling(String phone) {
        String normalized = AccountValidation.phone(phone);
        return AccountValidation.isValidPhone(normalized) && userMapper.selectByPhone(normalized) != null;
    }

    private void autoRejectPendingRecoveries(Long userId, LocalDateTime now, String note) {
        accountRecoveryMapper.update(null, Wrappers.<AccountRecovery>lambdaUpdate()
                .eq(AccountRecovery::getUserId, userId)
                .eq(AccountRecovery::getStatus, "PENDING")
                .set(AccountRecovery::getStatus, "REJECTED")
                .set(AccountRecovery::getPendingFlag, null)
                .set(AccountRecovery::getHandleNote, note)
                .set(AccountRecovery::getHandledByType, "SYSTEM")
                .set(AccountRecovery::getHandledAt, now)
                .set(AccountRecovery::getResultSmsStatus, "PENDING")
                .set(AccountRecovery::getUpdatedAt, now));
    }
}
