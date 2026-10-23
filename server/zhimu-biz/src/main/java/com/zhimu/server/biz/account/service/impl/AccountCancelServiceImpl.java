package com.zhimu.server.biz.account.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.convert.AccountCancelConvert;
import com.zhimu.server.biz.account.convert.UserConvert;
import com.zhimu.server.biz.account.entity.AccountCancel;
import com.zhimu.server.biz.account.entity.AccountRecovery;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.CleanupStatus;
import com.zhimu.server.biz.account.enums.RecoveryCodeRevokeReason;
import com.zhimu.server.biz.account.enums.SmsScene;
import com.zhimu.server.biz.account.enums.TokenRevokeReason;
import com.zhimu.server.biz.account.event.AccountCancelledEvent;
import com.zhimu.server.biz.account.mapper.AccountCancelMapper;
import com.zhimu.server.biz.account.mapper.AccountRecoveryMapper;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.request.AccountCancelRequest;
import com.zhimu.server.biz.account.service.AccountCancelService;
import com.zhimu.server.biz.account.service.SmsCodeService;
import com.zhimu.server.biz.account.support.AccountStateService;
import com.zhimu.server.biz.account.support.AccountValidation;
import com.zhimu.server.biz.account.support.CancellationRules;
import com.zhimu.server.biz.account.support.Errors;
import com.zhimu.server.biz.account.support.Secrets;
import com.zhimu.server.biz.account.vo.CancelOwnedFarmVO;
import com.zhimu.server.biz.account.vo.CancellationPreviewVO;
import com.zhimu.server.biz.common.api.ChangeRequestService;
import com.zhimu.server.biz.farm.api.CancelOwnedFarm;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;
import com.zhimu.server.framework.jackson.CacheJson;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.token.UserTokenService;
import com.zhimu.server.framework.tx.AfterCommit;
import com.zhimu.server.integration.ipregion.IpRegionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API-A19、A20：注销清单口径见 CancellationRules；注销即时生效、不能撤销。 */
@Service
@RequiredArgsConstructor
public class AccountCancelServiceImpl implements AccountCancelService {

    private final UserMapper userMapper;
    private final AccountCancelMapper accountCancelMapper;
    private final AccountRecoveryMapper accountRecoveryMapper;
    private final AccountStateService accountState;
    private final CancellationRules cancellationRules;
    private final SmsCodeService smsCodeService;
    private final FarmAccountService farmAccountService;
    private final ChangeRequestService changeRequestService;
    private final OperationLogRecorder operationLogRecorder;
    private final UserTokenService userTokenService;
    private final AuthStateCache authStateCache;
    private final IpRegionResolver ipRegionResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountProperties properties;

    @Override
    public CancellationPreviewVO preview() {
        User user = accountState.requireUser(UserContext.requireUserId());
        CancellationRules.CancelCheck check = cancellationRules.evaluate(user);
        List<CancelOwnedFarmVO> owned = new ArrayList<>();
        int transferable = 0;
        for (CancelOwnedFarm farm : check.ownedFarms()) {
            CancelOwnedFarmVO vo = AccountCancelConvert.toOwnedVO(farm);
            owned.add(vo);
            if (Boolean.TRUE.equals(vo.getTransferable())) {
                transferable++;
            }
        }
        return CancellationPreviewVO.builder()
                .phoneMasked(Masks.phone(user.getPhone()))
                .personalDataDesc(CancellationRules.PERSONAL_DATA_DESC)
                .phoneReleaseDate(BeijingTime.format(BeijingTime.today().plusDays(properties.getPhoneRelease().toDays())))
                .checklistVersion(check.checklistVersion())
                .canCancel(check.canCancel())
                .blockReasons(check.blockReasonCodes())
                .cancelAvailableDate(cancellationRules.cancelAvailableDateText(check))
                .farmCleanupDate(check.farmCleanupDate() == null ? null : BeijingTime.format(check.farmCleanupDate()))
                .ownedFarms(owned)
                .transferableFarmCount(transferable)
                .exitFarms(check.exitFarms().stream().map(AccountCancelConvert::toExitVO).toList())
                .build();
    }

    @Override
    @Transactional
    public void cancel(AccountCancelRequest request, ClientType client) {
        Errors errors = new Errors();
        String smsCode = AccountValidation.trim(request == null ? null : request.smsCode());
        if (!AccountValidation.isValidSmsCode(smsCode)) {
            errors.add("sms_code", AccountValidation.MSG_CODE_FORMAT);
        }
        if (request == null || !Boolean.TRUE.equals(request.confirmed())) {
            errors.add("confirmed", "请先勾选“我已了解注销后果”");
        }
        errors.throwIfAny();
        if (request.checklistVersion() == null || request.checklistVersion().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "注销清单已更新，请刷新后再试");
        }
        Long userId = UserContext.requireUserId();
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason", "EXPIRED");
        }
        CancellationRules.CancelCheck check = cancellationRules.evaluate(user);
        if (!check.canCancel()) {
            throw cancellationRules.blockedException(check);
        }
        if (!request.checklistVersion().equals(check.checklistVersion())) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        smsCodeService.verifyAndConsume(SmsScene.CANCEL_ACCOUNT, user.getPhone(), smsCode, "sms_code");

        LocalDateTime now = BeijingTime.now();
        String ip = RequestContext.ip();
        int withdrawnChangeRequests = changeRequestService.cancelPendingBySubmitter(userId, "提交人注销");

        AccountCancel record = new AccountCancel();
        record.setUserId(userId);
        record.setPhoneMasked(Masks.phone(user.getPhone()));
        record.setPhoneHash(Secrets.sha256Hex("zhimu-account-cancel:" + user.getPhone()));
        record.setRetainedFarmsJson(CacheJson.write(snapshot(check.ownedFarms(), true)));
        record.setDeletedFarmsJson(CacheJson.write(snapshot(check.ownedFarms(), false)));
        record.setExitedFarmsJson(CacheJson.write(exitSnapshot(check)));
        record.setWithdrawnJson(CacheJson.write(Map.of("change_request", withdrawnChangeRequests)));
        record.setClientType(client == null ? null : client.name());
        record.setIp(ip);
        record.setIpRegion(resolveRegion(ip));
        record.setPhoneReleaseAt(now.plusDays(properties.getPhoneRelease().toDays()));
        record.setFarmCleanupAt(check.farmCleanupDate() == null ? null : check.farmCleanupDate().atStartOfDay());
        record.setCleanupStatus(CleanupStatus.PENDING.name());
        record.setRetryCount(0);
        record.setNextRunAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        accountCancelMapper.insert(record);

        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getStatus, com.zhimu.server.biz.account.enums.UserStatus.CANCELLED.name())
                .set(User::getPasswordHash, null)
                .set(User::getName, "已注销用户")
                .set(User::getCancelledAt, now)
                .set(User::getTokenRevokeReason, TokenRevokeReason.ACCOUNT_CANCELLED.name())
                .set(User::getTokenRevokedAt, now)
                .set(User::getTempPasswordExpireAt, null)
                .setSql("token_version = token_version + 1")
                .setSql("version = version + 1")
                .set(User::getUpdatedAt, now));
        accountState.revokeAllCodes(userId, RecoveryCodeRevokeReason.ACCOUNT_CANCELLED);
        autoRejectPendingRecoveries(userId, now, "账号已注销");
        farmAccountService.handleAccountCancelled(userId, user.getPhone());
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(userId)
                .operatorName(user.getName())
                .opType("USER_STATUS")
                .opAction("CANCEL")
                .bizType("USER")
                .bizId(userId)
                .targetName(Masks.phone(user.getPhone()))
                .extraText("账号注销")
                .build());
        String phone = user.getPhone();
        AfterCommit.run(() -> {
            userTokenService.revokeAll(userId);
            authStateCache.evict(userId);
            eventPublisher.publishEvent(new AccountCancelledEvent(userId, phone));
        });
    }

    /** 系统自动驳回待审核找回申请（账号注销、停用） */
    @Transactional
    public int autoRejectPendingRecoveries(Long userId, LocalDateTime now, String note) {
        return accountRecoveryMapper.update(null, Wrappers.<AccountRecovery>lambdaUpdate()
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

    private static List<Map<String, Object>> snapshot(List<CancelOwnedFarm> farms, boolean retained) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (CancelOwnedFarm farm : farms) {
            boolean keep = retained
                    ? farm.disposal() == com.zhimu.server.biz.farm.api.CancelFarmDisposal.RETAIN_180_DAYS
                    : farm.disposal() == com.zhimu.server.biz.farm.api.CancelFarmDisposal.DELETE_NOW;
            if (!keep) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("farm_id", farm.farmId());
            item.put("name", farm.name());
            if (retained) {
                item.put("member_count", farm.memberCount());
                item.put("current_stock", farm.currentStock());
            }
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> exitSnapshot(CancellationRules.CancelCheck check) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (var farm : check.exitFarms()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("farm_id", farm.farmId());
            item.put("name", farm.name());
            item.put("role", farm.role());
            item.put("role_text", farm.roleText());
            list.add(item);
        }
        return list;
    }

    private String resolveRegion(String ip) {
        if (ip == null) {
            return null;
        }
        try {
            return ipRegionResolver.resolve(ip);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
