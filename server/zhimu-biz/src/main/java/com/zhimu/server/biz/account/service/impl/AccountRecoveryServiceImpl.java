package com.zhimu.server.biz.account.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.api.AccountRecoveryAdminService;
import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.convert.AccountRecoveryConvert;
import com.zhimu.server.biz.account.entity.AccountRecovery;
import com.zhimu.server.biz.account.entity.RecoveryCode;
import com.zhimu.server.biz.account.entity.RecoveryVerifyLog;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.RecoveryCodeRevokeReason;
import com.zhimu.server.biz.account.enums.RecoveryVerifyResult;
import com.zhimu.server.biz.account.enums.SmsScene;
import com.zhimu.server.biz.account.enums.TokenRevokeReason;
import com.zhimu.server.biz.account.enums.UserStatus;
import com.zhimu.server.biz.account.event.UserPhoneChangedEvent;
import com.zhimu.server.biz.account.mapper.AccountRecoveryMapper;
import com.zhimu.server.biz.account.mapper.RecoveryVerifyLogMapper;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.request.AccountRecoveryRequest;
import com.zhimu.server.biz.account.service.AccountRecoveryService;
import com.zhimu.server.biz.account.service.SmsCodeService;
import com.zhimu.server.biz.account.support.AccountStateService;
import com.zhimu.server.biz.account.support.AccountValidation;
import com.zhimu.server.biz.account.support.DateTimeText;
import com.zhimu.server.biz.account.support.Errors;
import com.zhimu.server.biz.account.support.LoginAttemptStore;
import com.zhimu.server.biz.account.support.RecoveryAttemptRecorder;
import com.zhimu.server.biz.account.support.Secrets;
import com.zhimu.server.biz.account.vo.AccountRecoverySubmitVO;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.token.UserTokenService;
import com.zhimu.server.framework.tx.AfterCommit;
import com.zhimu.server.integration.ipregion.IpRegionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API-A10 找回申请，以及管理端通过/驳回/待审核计数。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountRecoveryServiceImpl implements AccountRecoveryService, AccountRecoveryAdminService {

    private static final String MSG_PENDING = "这个账号已有一条找回申请正在处理，请等待短信通知";
    private static final String MSG_SMS_FAILED = "验证码发送失败，请稍后重试";

    private final UserMapper userMapper;
    private final AccountRecoveryMapper accountRecoveryMapper;
    private final RecoveryVerifyLogMapper verifyLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccountStateService accountState;
    private final SmsCodeService smsCodeService;
    private final RecoveryAttemptRecorder attemptRecorder;
    private final LoginAttemptStore loginAttemptStore;
    private final UserTokenService userTokenService;
    private final AuthStateCache authStateCache;
    private final OperationLogRecorder operationLogRecorder;
    private final IpRegionResolver ipRegionResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountProperties properties;

    @Override
    @Transactional
    public AccountRecoverySubmitVO submit(AccountRecoveryRequest request, ClientType client) {
        Errors errors = new Errors();
        String oldPhone = AccountValidation.phone(request == null ? null : request.oldPhone());
        String recoveryCode = AccountValidation.recoveryCode(request == null ? null : request.recoveryCode());
        String newPhone = AccountValidation.phone(request == null ? null : request.newPhone());
        String smsCode = AccountValidation.trim(request == null ? null : request.newPhoneSmsCode());
        String description = AccountValidation.trim(request == null ? null : request.description());
        if (!AccountValidation.isValidPhone(oldPhone)) {
            errors.add("old_phone", oldPhone.isEmpty() ? AccountValidation.MSG_PHONE_EMPTY : AccountValidation.MSG_PHONE_FORMAT);
        }
        if (!AccountValidation.isValidRecoveryCode(recoveryCode)) {
            errors.add("recovery_code", AccountValidation.MSG_RECOVERY_CODE);
        }
        if (!AccountValidation.isValidPhone(newPhone)) {
            errors.add("new_phone", newPhone.isEmpty() ? AccountValidation.MSG_PHONE_EMPTY : AccountValidation.MSG_PHONE_FORMAT);
        } else if (AccountValidation.isValidPhone(oldPhone) && newPhone.equals(oldPhone)) {
            errors.add("new_phone", AccountValidation.MSG_SAME_PHONE);
        }
        if (!AccountValidation.isValidSmsCode(smsCode)) {
            errors.add("new_phone_sms_code", AccountValidation.MSG_CODE_FORMAT);
        }
        String descriptionProblem = AccountValidation.descriptionProblem(description);
        if (descriptionProblem != null) {
            errors.add("description", descriptionProblem);
        }
        errors.throwIfAny();

        User user = userMapper.selectByPhone(oldPhone);
        if (user == null || UserStatus.CANCELLED.name().equals(user.getStatus())) {
            attemptRecorder.recordFailure(null, oldPhone, RecoveryVerifyResult.PHONE_NOT_REGISTERED, 0, null, null, null, client);
            throw new Errors().add("old_phone", "这个手机号没有注册过账号").toException();
        }
        if (UserStatus.DISABLED.name().equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED,
                    "这个账号已被平台停用。如有疑问，请拨打客服电话 400-123-4567。");
        }
        LocalDateTime now = BeijingTime.now();
        if (user.getRecoveryLockUntil() != null && user.getRecoveryLockUntil().isAfter(now)) {
            attemptRecorder.recordFailure(user, oldPhone, RecoveryVerifyResult.LOCKED, currentFailCount(user),
                    user.getRecoveryFailFirstAt(), user.getRecoveryLockUntil(), 0, client);
            throw recoveryLocked(user.getRecoveryLockUntil());
        }
        long pending = accountRecoveryMapper.selectCount(Wrappers.<AccountRecovery>lambdaQuery()
                .eq(AccountRecovery::getUserId, user.getId())
                .eq(AccountRecovery::getStatus, "PENDING"));
        if (pending > 0) {
            throw new BizException(ErrorCode.PENDING_EXISTS, MSG_PENDING);
        }
        User newPhoneUser = userMapper.selectByPhone(newPhone);
        if (newPhoneUser != null) {
            throw new BizException(ErrorCode.PHONE_REGISTERED, AccountValidation.MSG_PHONE_TAKEN)
                    .with("reason", "PHONE_TAKEN");
        }
        smsCodeService.verify(SmsScene.RECOVERY_NEW_PHONE, newPhone, smsCode, "new_phone_sms_code");

        RecoveryCode live = accountState.liveCode(user.getId());
        if (live == null || !passwordEncoder.matches(recoveryCode, live.getCodeHash())) {
            int failCount = currentFailCount(user) + 1;
            boolean newWindow = user.getRecoveryFailFirstAt() == null
                    || user.getRecoveryFailFirstAt().isBefore(now.minus(properties.getRecoveryWindow()));
            if (newWindow) {
                failCount = 1;
            }
            LocalDateTime firstAt = newWindow ? now : user.getRecoveryFailFirstAt();
            if (failCount >= properties.getRecoveryMaxFailures()) {
                LocalDateTime lockUntil = now.plus(properties.getRecoveryLockTtl());
                attemptRecorder.recordFailure(user, oldPhone, RecoveryVerifyResult.LOCKED, failCount, firstAt, lockUntil, 0, client);
                throw recoveryLocked(lockUntil);
            }
            int remaining = properties.getRecoveryMaxFailures() - failCount;
            attemptRecorder.recordFailure(user, oldPhone, RecoveryVerifyResult.CODE_WRONG, failCount, firstAt, null, remaining, client);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("remaining_attempts", remaining);
            data.put("field_errors", List.of(new FieldErrorVO("recovery_code",
                    "恢复码不正确，还可以再试 " + remaining + " 次")));
            throw new BizException(ErrorCode.RECOVERY_CODE_WRONG,
                    "恢复码不正确，还可以再试 " + remaining + " 次", data);
        }

        smsCodeService.verifyAndConsume(SmsScene.RECOVERY_NEW_PHONE, newPhone, smsCode, "new_phone_sms_code");
        int failCount = currentFailCount(user);
        if (user.getRecoveryFailFirstAt() == null
                || user.getRecoveryFailFirstAt().isBefore(now.minus(properties.getRecoveryWindow()))) {
            failCount = 0;
        }
        AccountRecovery recovery = new AccountRecovery();
        recovery.setUserId(user.getId());
        recovery.setOldPhone(oldPhone);
        recovery.setNewPhone(newPhone);
        recovery.setNewPhoneVerifiedAt(now);
        recovery.setDescription(description);
        recovery.setCodeAttemptNo(failCount + 1);
        recovery.setCodeFailCount(failCount);
        recovery.setSubmitChannel(client == null ? ClientType.APP_ANDROID.name() : client.name());
        recovery.setSubmitIp(RequestContext.ip());
        recovery.setSubmitIpRegion(resolveRegion(RequestContext.ip()));
        recovery.setSubmitDevice(deviceText(client));
        recovery.setStatus("PENDING");
        recovery.setPendingFlag(1);
        recovery.setResultSmsStatus("PENDING");
        recovery.setResultSmsRetryCount(0);
        recovery.setVersion(0);
        recovery.setCreatedAt(now);
        recovery.setUpdatedAt(now);
        try {
            accountRecoveryMapper.insert(recovery);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PENDING_EXISTS, MSG_PENDING);
        }
        userMapper.clearRecoveryFailure(user.getId());
        RecoveryVerifyLog verifyLog = new RecoveryVerifyLog();
        verifyLog.setUserId(user.getId());
        verifyLog.setOldPhone(oldPhone);
        verifyLog.setResult(RecoveryVerifyResult.CODE_CORRECT.name());
        verifyLog.setFailCountAfter(0);
        verifyLog.setRemainingAttempts(null);
        verifyLog.setRecoveryId(recovery.getId());
        verifyLog.setSubmitChannel(recovery.getSubmitChannel());
        verifyLog.setIp(RequestContext.ip());
        verifyLog.setIpRegion(recovery.getSubmitIpRegion());
        verifyLogMapper.insert(verifyLog);
        return AccountRecoveryConvert.toSubmitVO(recovery);
    }

    @Override
    @Transactional
    public void approve(Long recoveryId, Long adminId, String verifyMethod, String handleNote, int version) {
        AccountRecovery recovery = requirePending(recoveryId, version);
        User user = recovery.getUserId() == null ? null : userMapper.selectByIdForUpdate(recovery.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        User occupied = userMapper.selectByPhone(recovery.getNewPhone());
        if (occupied != null && !occupied.getId().equals(user.getId())) {
            throw new BizException(ErrorCode.PHONE_REGISTERED, "新手机号已被其他账号注册，不能通过")
                    .with("reason", "PHONE_TAKEN");
        }
        LocalDateTime now = BeijingTime.now();
        String tempPassword = Secrets.tempPassword();
        String oldPhone = user.getPhone();
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getPhone, recovery.getNewPhone())
                .set(User::getPasswordHash, passwordEncoder.encode(tempPassword))
                .set(User::getMustChangePassword, true)
                .set(User::getTempPasswordExpireAt, now.plus(properties.getTempPasswordTtl()))
                .set(User::getRecoveredAt, now)
                .set(User::getRecoveryFailCount, 0)
                .set(User::getRecoveryFailFirstAt, null)
                .set(User::getRecoveryLockUntil, null)
                .set(User::getUpdatedAt, now));
        user.setPhone(recovery.getNewPhone());
        user.setMustChangePassword(true);
        user.setTempPasswordExpireAt(now.plus(properties.getTempPasswordTtl()));
        user.setRecoveredAt(now);
        accountState.invalidateTokens(user, TokenRevokeReason.ACCOUNT_RECOVERED);
        accountState.revokeAllCodes(user.getId(), RecoveryCodeRevokeReason.ACCOUNT_RECOVERED);

        recovery.setStatus("APPROVED");
        recovery.setPendingFlag(null);
        recovery.setVerifyMethod(verifyMethod);
        recovery.setHandleNote(handleNote);
        recovery.setHandledByType("ADMIN");
        recovery.setHandledBy(adminId);
        recovery.setHandledAt(now);
        recovery.setResultSmsStatus("PENDING");
        recovery.setResultSmsRetryCount(0);
        recovery.setResultSmsNextAt(null);
        recovery.setUpdatedAt(now);
        if (accountRecoveryMapper.updateById(recovery) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("ACCOUNT_RECOVERY")
                .opAction("APPROVE")
                .bizType("ACCOUNT_RECOVERY")
                .bizId(recovery.getId())
                .targetName(Masks.phone(recovery.getNewPhone()))
                .reason(handleNote)
                .before(Map.of("status", "PENDING", "phone", Masks.phone(oldPhone)))
                .after(Map.of("status", "APPROVED", "phone", Masks.phone(recovery.getNewPhone())))
                .build());
        Long userId = user.getId();
        AfterCommit.run(() -> {
            loginAttemptStore.clear(oldPhone);
            loginAttemptStore.clear(recovery.getNewPhone());
            eventPublisher.publishEvent(new UserPhoneChangedEvent(userId, oldPhone, recovery.getNewPhone()));
        });
    }

    @Override
    @Transactional
    public void reject(Long recoveryId, Long adminId, String verifyMethod, String handleNote, int version) {
        if (handleNote == null || handleNote.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写驳回原因");
        }
        AccountRecovery recovery = requirePending(recoveryId, version);
        LocalDateTime now = BeijingTime.now();
        recovery.setStatus("REJECTED");
        recovery.setPendingFlag(null);
        recovery.setVerifyMethod(verifyMethod);
        recovery.setHandleNote(handleNote);
        recovery.setHandledByType("ADMIN");
        recovery.setHandledBy(adminId);
        recovery.setHandledAt(now);
        recovery.setResultSmsStatus("PENDING");
        recovery.setResultSmsRetryCount(0);
        recovery.setResultSmsNextAt(null);
        recovery.setUpdatedAt(now);
        if (accountRecoveryMapper.updateById(recovery) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("ACCOUNT_RECOVERY")
                .opAction("REJECT")
                .bizType("ACCOUNT_RECOVERY")
                .bizId(recovery.getId())
                .targetName(Masks.phone(recovery.getNewPhone()))
                .reason(handleNote)
                .before(Map.of("status", "PENDING"))
                .after(Map.of("status", "REJECTED"))
                .build());
    }

    @Override
    public long countPending() {
        return accountRecoveryMapper.selectCount(Wrappers.<AccountRecovery>lambdaQuery()
                .eq(AccountRecovery::getStatus, "PENDING"));
    }

    @Override
    @Transactional
    public AccountRecoveryAdminService.SmsResendResult resendResultSms(Long recoveryId, Long adminId, String reason) {
        AccountRecovery recovery = recoveryId == null ? null : accountRecoveryMapper.selectById(recoveryId);
        if (recovery == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "这条找回申请不存在，列表已刷新");
        }
        boolean approved = "APPROVED".equals(recovery.getStatus());
        if (!approved && !"REJECTED".equals(recovery.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这条申请还未处理，不能重发短信");
        }
        LocalDateTime now = BeijingTime.now();
        String oldSmsStatus = recovery.getResultSmsStatus();
        String note;
        if (approved) {
            User user = recovery.getUserId() == null ? null : userMapper.selectByIdForUpdate(recovery.getUserId());
            if (user == null) {
                throw new BizException(ErrorCode.NOT_FOUND);
            }
            String tempPassword = Secrets.tempPassword();
            LocalDateTime expireAt = now.plus(properties.getTempPasswordTtl());
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, user.getId())
                    .set(User::getPasswordHash, passwordEncoder.encode(tempPassword))
                    .set(User::getMustChangePassword, true)
                    .set(User::getTempPasswordExpireAt, expireAt)
                    .set(User::getUpdatedAt, now));
            user.setMustChangePassword(true);
            user.setTempPasswordExpireAt(expireAt);
            // 重新生成临时密码后旧会话立即失效；明文只交给后续短信任务，不返回、不写日志
            accountState.invalidateTokens(user, TokenRevokeReason.ACCOUNT_RECOVERED);
            note = "已重新生成临时密码，等待短信任务发送";
        } else {
            note = "已重新排队发送驳回结果短信";
        }
        recovery.setResultSmsStatus("PENDING");
        recovery.setResultSmsRetryCount(0);
        recovery.setResultSmsNextAt(null);
        int rows = accountRecoveryMapper.update(null, Wrappers.<AccountRecovery>lambdaUpdate()
                .eq(AccountRecovery::getId, recovery.getId())
                .eq(AccountRecovery::getVersion, recovery.getVersion())
                .set(AccountRecovery::getResultSmsStatus, "PENDING")
                .set(AccountRecovery::getResultSmsRetryCount, 0)
                .set(AccountRecovery::getResultSmsNextAt, null)
                .set(AccountRecovery::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        recovery.setVersion(recovery.getVersion() == null ? 1 : recovery.getVersion() + 1);
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("ACCOUNT_RECOVERY")
                .opAction("RESEND_SMS")
                .bizType("ACCOUNT_RECOVERY")
                .bizId(recovery.getId())
                .targetName(Masks.phone(recovery.getNewPhone()))
                .reason(reason == null || reason.isBlank() ? "短信发送失败后手动重发" : reason.trim())
                .before(Map.of("result_sms_status", oldSmsStatus == null ? "NONE" : oldSmsStatus))
                .after(Map.of("result_sms_status", "PENDING"))
                .build());
        return new AccountRecoveryAdminService.SmsResendResult(recovery.getId(),
                Masks.phone(recovery.getNewPhone()), "PENDING", 0, now, note);
    }

    @Override
    @Transactional
    public AccountRecoveryAdminService.ProxyCreateResult proxyCreate(Long adminId, String phone, String newPhoneRaw,
                                                                     String recoveryCode, String description) {
        String oldPhone = AccountValidation.phone(phone);
        if (!AccountValidation.isValidPhone(oldPhone)) {
            throw fieldError("phone", oldPhone.isEmpty()
                    ? AccountValidation.MSG_PHONE_EMPTY : AccountValidation.MSG_PHONE_FORMAT);
        }
        String newPhone = newPhoneRaw == null || newPhoneRaw.isBlank()
                ? oldPhone : AccountValidation.phone(newPhoneRaw);
        if (!AccountValidation.isValidPhone(newPhone)) {
            throw fieldError("new_phone", AccountValidation.MSG_PHONE_FORMAT);
        }
        String code = AccountValidation.recoveryCode(recoveryCode);
        if (!AccountValidation.isValidRecoveryCode(code)) {
            throw fieldError("recovery_code", AccountValidation.MSG_RECOVERY_CODE);
        }
        String note = AccountValidation.trim(description);
        if (note.isEmpty()) {
            throw fieldError("description", AccountValidation.MSG_DESCRIPTION);
        }
        if (AccountValidation.length(note) > AccountValidation.DESCRIPTION_MAX) {
            throw fieldError("description", "情况说明不能超过 200 字");
        }
        User user = userMapper.selectByPhone(oldPhone);
        if (user == null || UserStatus.CANCELLED.name().equals(user.getStatus())) {
            throw fieldError("phone", "这个手机号没有注册过账号");
        }
        if (UserStatus.DISABLED.name().equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED,
                    "这个账号已被平台停用。如有疑问，请拨打客服电话 400-123-4567。");
        }
        long pending = accountRecoveryMapper.selectCount(Wrappers.<AccountRecovery>lambdaQuery()
                .eq(AccountRecovery::getUserId, user.getId())
                .eq(AccountRecovery::getStatus, "PENDING"));
        if (pending > 0) {
            throw new BizException(ErrorCode.PENDING_EXISTS, "这个账号已有待审核的找回申请");
        }
        if (!newPhone.equals(oldPhone)) {
            User occupied = userMapper.selectByPhone(newPhone);
            if (occupied != null && !occupied.getId().equals(user.getId())) {
                throw new BizException(ErrorCode.PHONE_REGISTERED, "这个手机号已注册其他账号");
            }
        }
        RecoveryCode live = accountState.liveCode(user.getId());
        if (live == null || !passwordEncoder.matches(code, live.getCodeHash())) {
            throw new BizException(ErrorCode.RECOVERY_CODE_WRONG, "恢复码不正确");
        }
        LocalDateTime now = BeijingTime.now();
        AccountRecovery recovery = new AccountRecovery();
        recovery.setUserId(user.getId());
        recovery.setOldPhone(oldPhone);
        recovery.setNewPhone(newPhone);
        recovery.setNewPhoneVerifiedAt(now);
        recovery.setDescription(note);
        recovery.setCodeAttemptNo(1);
        recovery.setCodeFailCount(0);
        recovery.setSubmitChannel("CS_PROXY");
        recovery.setProxyAdminId(adminId);
        recovery.setSubmitIp(RequestContext.ip());
        recovery.setSubmitIpRegion(resolveRegion(RequestContext.ip()));
        recovery.setSubmitDevice("管理端（客服代登记）");
        recovery.setStatus("PENDING");
        recovery.setPendingFlag(1);
        recovery.setResultSmsStatus("PENDING");
        recovery.setResultSmsRetryCount(0);
        recovery.setVersion(0);
        recovery.setCreatedAt(now);
        recovery.setUpdatedAt(now);
        try {
            accountRecoveryMapper.insert(recovery);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PENDING_EXISTS, "这个账号已有待审核的找回申请");
        }
        RecoveryVerifyLog verifyLog = new RecoveryVerifyLog();
        verifyLog.setUserId(user.getId());
        verifyLog.setOldPhone(oldPhone);
        verifyLog.setResult(RecoveryVerifyResult.CODE_CORRECT.name());
        verifyLog.setFailCountAfter(0);
        verifyLog.setRemainingAttempts(null);
        verifyLog.setRecoveryId(recovery.getId());
        verifyLog.setSubmitChannel("CS_PROXY");
        verifyLog.setProxyAdminId(adminId);
        verifyLog.setIp(RequestContext.ip());
        verifyLog.setIpRegion(recovery.getSubmitIpRegion());
        verifyLogMapper.insert(verifyLog);
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("ACCOUNT_RECOVERY")
                .opAction("PROXY_SUBMIT")
                .bizType("ACCOUNT_RECOVERY")
                .bizId(recovery.getId())
                .targetName(Masks.phone(newPhone))
                .reason(note)
                .before(Map.of("status", "NONE"))
                .after(Map.of("status", "PENDING", "submit_channel", "CS_PROXY"))
                .build());
        return new AccountRecoveryAdminService.ProxyCreateResult(recovery.getId(), user.getId(), user.getName(),
                Masks.phone(oldPhone), Masks.phone(newPhone), recovery.getStatus(), now);
    }

    private static BizException fieldError(String field, String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message,
                Map.of("field_errors", List.of(new FieldErrorVO(field, message))));
    }

    private AccountRecovery requirePending(Long recoveryId, int version) {
        AccountRecovery recovery = recoveryId == null ? null : accountRecoveryMapper.selectById(recoveryId);
        if (recovery == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (recovery.getVersion() == null || recovery.getVersion() != version) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        if (!"PENDING".equals(recovery.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "这条申请已处理，列表已刷新");
        }
        return recovery;
    }

    private BizException recoveryLocked(LocalDateTime unlockAt) {
        String message = "恢复码已连续填错 5 次。" + DateTimeText.monthDayTime(unlockAt) + " 后才能再次提交找回申请。";
        return new BizException(ErrorCode.RECOVERY_LOCKED, message)
                .with("unlock_at", BeijingTime.format(unlockAt));
    }

    private static int currentFailCount(User user) {
        return user.getRecoveryFailCount() == null ? 0 : user.getRecoveryFailCount();
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

    private static String deviceText(ClientType client) {
        return client == ClientType.WEB ? "Web" : "App（Android）";
    }

    static Duration recoveryWindow(AccountProperties properties) {
        return properties.getRecoveryWindow();
    }
}
