package com.zhimu.server.biz.account.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.LoginFailReason;
import com.zhimu.server.biz.account.enums.LoginType;
import com.zhimu.server.biz.account.enums.NextStep;
import com.zhimu.server.biz.account.enums.RecoveryCodeRevokeReason;
import com.zhimu.server.biz.account.enums.RecoveryCodeScene;
import com.zhimu.server.biz.account.enums.RecoveryCodeStatus;
import com.zhimu.server.biz.account.enums.SmsScene;
import com.zhimu.server.biz.account.enums.TokenRevokeReason;
import com.zhimu.server.biz.account.enums.UserStatus;
import com.zhimu.server.biz.account.event.UserRegisteredEvent;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.request.AfterRecoveryPasswordRequest;
import com.zhimu.server.biz.account.request.PasswordChangeRequest;
import com.zhimu.server.biz.account.request.PasswordLoginRequest;
import com.zhimu.server.biz.account.request.PasswordResetRequest;
import com.zhimu.server.biz.account.request.RegisterRequest;
import com.zhimu.server.biz.account.request.SmsLoginRequest;
import com.zhimu.server.biz.account.service.AuthService;
import com.zhimu.server.biz.account.service.LoginLogService;
import com.zhimu.server.biz.account.service.SmsCodeService;
import com.zhimu.server.biz.account.support.AccountStateService;
import com.zhimu.server.biz.account.support.AccountValidation;
import com.zhimu.server.biz.account.support.DateTimeText;
import com.zhimu.server.biz.account.support.Errors;
import com.zhimu.server.biz.account.support.LoginAttemptStore;
import com.zhimu.server.biz.account.vo.AuthTokenVO;
import com.zhimu.server.biz.account.vo.RecoveryCodeVO;
import com.zhimu.server.biz.account.vo.SessionVO;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;
import com.zhimu.server.framework.security.jwt.UserJwtService;
import com.zhimu.server.framework.security.token.TokenPair;
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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 注册、两种登录、刷新、退出、会话、短信重置与修改密码。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String MSG_PASSWORD_WRONG = "手机号或密码不正确";
    private static final String MSG_PHONE_NOT_REGISTERED = "这个手机号还没有注册";
    private static final String MSG_ACCOUNT_DISABLED = "这个账号已被平台停用。如有疑问，请拨打客服电话 400-123-4567。";
    private static final String MSG_TEMP_EXPIRED = "临时密码已过期，请改用验证码登录";
    private static final String MSG_CURRENT_PASSWORD_WRONG = "当前密码不正确";
    private static final String MSG_LOCKED_CHANGE = "当前密码错误次数过多，请 30 分钟后再试，或退出后用“忘记密码”重置";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccountStateService accountState;
    private final SmsCodeService smsCodeService;
    private final LoginLogService loginLogService;
    private final LoginAttemptStore loginAttemptStore;
    private final UserTokenService userTokenService;
    private final UserJwtService userJwtService;
    private final IpRegionResolver ipRegionResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountProperties properties;

    @Override
    @Transactional
    public AuthTokenVO register(RegisterRequest request, ClientType client) {
        Errors errors = new Errors();
        String phone = AccountValidation.phone(request == null ? null : request.phone());
        String smsCode = AccountValidation.trim(request == null ? null : request.smsCode());
        String password = request == null || request.password() == null ? "" : request.password();
        if (!AccountValidation.isValidPhone(phone)) {
            errors.add("phone", phone.isEmpty() ? AccountValidation.MSG_PHONE_EMPTY : AccountValidation.MSG_PHONE_FORMAT);
        }
        if (!AccountValidation.isValidSmsCode(smsCode)) {
            errors.add("sms_code", AccountValidation.MSG_CODE_FORMAT);
        }
        String passwordProblem = AccountValidation.passwordProblem(password);
        if (passwordProblem != null) {
            errors.add("password", passwordProblem);
        }
        if (request == null || !Boolean.TRUE.equals(request.agreeTerms())) {
            errors.add("agree_terms", AccountValidation.MSG_AGREE_TERMS);
        }
        if (request == null || request.agreementVersion() == null
                || !request.agreementVersion().trim().equals(properties.getAgreementVersion())) {
            errors.add("agreement_version", AccountValidation.MSG_AGREEMENT_UPDATED);
        }
        if (request == null || request.deviceId() == null || request.deviceId().isBlank()) {
            errors.messageOnly("缺少设备标识");
        } else if (request.deviceId().length() > 64) {
            errors.messageOnly("设备标识过长");
        }
        if (request != null && request.deviceModel() != null && request.deviceModel().length() > 50) {
            errors.messageOnly("设备型号过长");
        }
        if (request != null && request.osVersion() != null && request.osVersion().length() > 30) {
            errors.messageOnly("系统版本过长");
        }
        errors.throwIfAny();

        User existing = userMapper.selectByPhone(phone);
        if (existing != null) {
            if (UserStatus.NORMAL.name().equals(existing.getStatus())
                    && existing.getPasswordHash() != null
                    && passwordEncoder.matches(password, existing.getPasswordHash())) {
                return replayRegister(existing, client, safeDeviceId(request));
            }
            if (UserStatus.CANCELLED.name().equals(existing.getStatus())) {
                LocalDateTime cancelledAt = existing.getCancelledAt();
                LocalDateTime releaseAt = cancelledAt == null ? null
                        : cancelledAt.plusDays(properties.getPhoneRelease().toDays());
                if (releaseAt != null && releaseAt.isAfter(BeijingTime.now())) {
                    String message = "这个手机号注销未满 30 天，"
                            + DateTimeText.monthDayCn(releaseAt.toLocalDate()) + "起可以重新注册";
                    throw new BizException(ErrorCode.PHONE_REGISTERED, message).with("reason", "PHONE_CANCEL_COOLING");
                }
                // 已过 30 天但清理任务尚未置空手机号：先释放旧号码再创建新账号
                userMapper.update(null, Wrappers.<User>lambdaUpdate()
                        .eq(User::getId, existing.getId())
                        .set(User::getPhone, null)
                        .set(User::getUpdatedAt, BeijingTime.now()));
            } else {
                throw new BizException(ErrorCode.PHONE_REGISTERED, AccountValidation.MSG_PHONE_REGISTERED)
                        .with("reason", "PHONE_REGISTERED");
            }
        }

        smsCodeService.verifyAndConsume(SmsScene.REGISTER, phone, smsCode, "sms_code");

        LocalDateTime now = BeijingTime.now();
        User user = new User();
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName(defaultName(phone));
        user.setStatus(UserStatus.NORMAL.name());
        user.setTokenVersion(0);
        user.setMustChangePassword(false);
        user.setRecoveryFailCount(0);
        user.setRegisterClient(client.name());
        user.setAgreementVersion(properties.getAgreementVersion());
        user.setAgreedAt(now);
        user.setLastLoginAt(now);
        user.setLastLoginClient(client.name());
        user.setProfileVersion(0);
        user.setVersion(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PHONE_REGISTERED, AccountValidation.MSG_PHONE_REGISTERED)
                    .with("reason", "PHONE_REGISTERED");
        }
        RecoveryCodeVO code = accountState.generateRecoveryCode(user, RecoveryCodeScene.REGISTER, false, client,
                RecoveryCodeRevokeReason.UNCONFIRMED);
        TokenPair pair = accountState.issue(user, client, safeDeviceId(request));
        loginLogService.recordSuccess(user.getId(), phone, LoginType.REGISTER, client, safeDeviceId(request),
                request.deviceModel(), request.osVersion(), RequestContext.userAgent(), ip(), ipRegion());
        Long userId = user.getId();
        AfterCommit.run(() -> eventPublisher.publishEvent(new UserRegisteredEvent(userId, phone)));
        return accountState.toTokenVO(user, pair, client, code);
    }

    /** A03 幂等重放：密码与账号一致时重新签发令牌，并保证下一步仍是保存恢复码（不返回恢复码明文） */
    private AuthTokenVO replayRegister(User user, ClientType client, String deviceId) {
        var live = accountState.liveCode(user.getId());
        if (live == null || !RecoveryCodeStatus.PENDING_CONFIRM.name().equals(live.getStatus())) {
            RecoveryCodeScene scene = live != null && RecoveryCodeScene.RECOVERY.name().equals(live.getScene())
                    ? RecoveryCodeScene.RECOVERY : RecoveryCodeScene.REGISTER;
            accountState.generateRecoveryCode(user, scene, false, client, RecoveryCodeRevokeReason.UNCONFIRMED);
        }
        TokenPair pair = accountState.issue(user, client, deviceId);
        return accountState.toTokenVO(user, pair, client, null);
    }

    @Override
    public AuthTokenVO loginByPassword(PasswordLoginRequest request, ClientType client) {
        Errors errors = new Errors();
        String phone = AccountValidation.phone(request == null ? null : request.phone());
        String password = request == null || request.password() == null ? "" : request.password();
        if (!AccountValidation.isValidPhone(phone)) {
            errors.add("phone", phone.isEmpty() ? AccountValidation.MSG_PHONE_EMPTY : AccountValidation.MSG_PHONE_FORMAT);
        }
        if (password.isEmpty()) {
            errors.add("password", "请输入密码");
        }
        errors.throwIfAny();

        int failures = loginAttemptStore.failures(phone);
        if (failures >= properties.getPasswordMaxFailures()) {
            loginLogService.recordFailure(null, phone, LoginType.PASSWORD, client, LoginFailReason.LOGIN_LOCKED, ip(), ipRegion());
            throw lockedLogin(loginAttemptStore.remainingSeconds(phone));
        }

        User user = userMapper.selectByPhone(phone);
        if (user == null || UserStatus.CANCELLED.name().equals(user.getStatus())) {
            loginLogService.recordFailure(null, phone, LoginType.PASSWORD, client, LoginFailReason.PHONE_NOT_REGISTERED, ip(), ipRegion());
            throw new Errors().add("phone", MSG_PHONE_NOT_REGISTERED).reason("PHONE_NOT_REGISTERED").toException();
        }
        if (UserStatus.DISABLED.name().equals(user.getStatus())) {
            loginLogService.recordFailure(user.getId(), phone, LoginType.PASSWORD, client, LoginFailReason.ACCOUNT_DISABLED, ip(), ipRegion());
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, MSG_ACCOUNT_DISABLED);
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            int count = loginAttemptStore.recordFailure(phone, properties.getPasswordLockTtl());
            loginLogService.recordFailure(user.getId(), phone, LoginType.PASSWORD, client, LoginFailReason.PASSWORD_WRONG, ip(), ipRegion());
            if (count >= properties.getPasswordMaxFailures()) {
                throw lockedLogin(properties.getPasswordLockTtl().toSeconds());
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("field_errors", List.of(new com.zhimu.server.common.error.FieldErrorVO("password", MSG_PASSWORD_WRONG)));
            throw new BizException(ErrorCode.PASSWORD_WRONG, MSG_PASSWORD_WRONG, data);
        }
        boolean temp = Boolean.TRUE.equals(user.getMustChangePassword());
        if (temp && user.getTempPasswordExpireAt() != null
                && BeijingTime.now().isAfter(user.getTempPasswordExpireAt())) {
            loginLogService.recordFailure(user.getId(), phone, LoginType.TEMP_PASSWORD, client,
                    LoginFailReason.TEMP_PASSWORD_EXPIRED, ip(), ipRegion());
            throw new BizException(ErrorCode.TEMP_PASSWORD_EXPIRED, MSG_TEMP_EXPIRED);
        }
        loginAttemptStore.clear(phone);
        updateLastLogin(user, client);
        loginLogService.recordSuccess(user.getId(), phone, temp ? LoginType.TEMP_PASSWORD : LoginType.PASSWORD, client,
                safeDeviceId(request), request.deviceModel(), request.osVersion(), RequestContext.userAgent(), ip(), ipRegion());
        TokenPair pair = accountState.issue(user, client, safeDeviceId(request));
        return accountState.toTokenVO(user, pair, client, null);
    }

    @Override
    public AuthTokenVO loginBySms(SmsLoginRequest request, ClientType client) {
        Errors errors = new Errors();
        String phone = AccountValidation.phone(request == null ? null : request.phone());
        String smsCode = AccountValidation.trim(request == null ? null : request.smsCode());
        if (!AccountValidation.isValidPhone(phone)) {
            errors.add("phone", phone.isEmpty() ? AccountValidation.MSG_PHONE_EMPTY : AccountValidation.MSG_PHONE_FORMAT);
        }
        if (!AccountValidation.isValidSmsCode(smsCode)) {
            errors.add("sms_code", AccountValidation.MSG_CODE_FORMAT);
        }
        errors.throwIfAny();
        User user = userMapper.selectByPhone(phone);
        if (user == null || UserStatus.CANCELLED.name().equals(user.getStatus())) {
            throw new Errors().add("phone", MSG_PHONE_NOT_REGISTERED).reason("PHONE_NOT_REGISTERED").toException();
        }
        if (UserStatus.DISABLED.name().equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, MSG_ACCOUNT_DISABLED);
        }
        smsCodeService.verifyAndConsume(SmsScene.LOGIN, phone, smsCode, "sms_code");
        loginAttemptStore.clear(phone);
        updateLastLogin(user, client);
        loginLogService.recordSuccess(user.getId(), phone, LoginType.SMS, client, safeDeviceId(request),
                request.deviceModel(), request.osVersion(), RequestContext.userAgent(), ip(), ipRegion());
        TokenPair pair = accountState.issue(user, client, safeDeviceId(request));
        return accountState.toTokenVO(user, pair, client, null);
    }

    @Override
    public AuthTokenVO refresh(String refreshToken, ClientType client) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason", "EXPIRED");
        }
        TokenPair pair = userTokenService.refresh(refreshToken);
        UserJwtService.UserClaims claims = userJwtService.parse(pair.accessToken());
        User user = userMapper.selectById(claims.userId());
        if (user == null) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason", "EXPIRED");
        }
        if (UserStatus.DISABLED.name().equals(user.getStatus())) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason", "ACCOUNT_DISABLED");
        }
        if (UserStatus.CANCELLED.name().equals(user.getStatus())) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason", "ACCOUNT_CANCELLED");
        }
        return accountState.toTokenVO(user, pair, client, null);
    }

    @Override
    public void logout(ClientType client) {
        Long userId = UserContext.requireUserId();
        userTokenService.revokeClient(userId, client);
    }

    @Override
    public SessionVO session(ClientType client) {
        User user = accountState.requireUser(UserContext.requireUserId());
        return accountState.toSessionVO(user, client);
    }

    @Override
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        Errors errors = new Errors();
        String phone = AccountValidation.phone(request == null ? null : request.phone());
        String smsCode = AccountValidation.trim(request == null ? null : request.smsCode());
        String password = request == null || request.newPassword() == null ? "" : request.newPassword();
        if (!AccountValidation.isValidPhone(phone)) {
            errors.add("phone", phone.isEmpty() ? AccountValidation.MSG_PHONE_EMPTY : AccountValidation.MSG_PHONE_FORMAT);
        }
        if (!AccountValidation.isValidSmsCode(smsCode)) {
            errors.add("sms_code", AccountValidation.MSG_CODE_FORMAT);
        }
        String problem = AccountValidation.passwordProblem(password);
        if (problem != null) {
            errors.add("new_password", problem);
        }
        errors.throwIfAny();
        User user = userMapper.selectByPhone(phone);
        if (user == null || UserStatus.CANCELLED.name().equals(user.getStatus())) {
            throw new Errors().add("phone", MSG_PHONE_NOT_REGISTERED).reason("PHONE_NOT_REGISTERED").toException();
        }
        if (UserStatus.DISABLED.name().equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, MSG_ACCOUNT_DISABLED);
        }
        smsCodeService.verifyAndConsume(SmsScene.RESET_PASSWORD, phone, smsCode, "sms_code");
        LocalDateTime now = BeijingTime.now();
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getPasswordHash, passwordEncoder.encode(password))
                .set(User::getPasswordUpdatedAt, now)
                .set(User::getMustChangePassword, false)
                .set(User::getTempPasswordExpireAt, null)
                .set(User::getUpdatedAt, now));
        loginAttemptStore.clear(phone);
        accountState.invalidateTokens(user, TokenRevokeReason.PASSWORD_CHANGED);
    }

    @Override
    @Transactional
    public AuthTokenVO changePasswordAfterRecovery(AfterRecoveryPasswordRequest request, ClientType client) {
        User user = accountState.requireUser(UserContext.requireUserId());
        NextStep step = accountState.resolveNextStep(user, client);
        if (step != NextStep.RECOVERY_CHANGE_PASSWORD) {
            throw new BizException(ErrorCode.FORCED_STEP_REQUIRED).with("next_step", step.name());
        }
        Errors errors = new Errors();
        String password = request == null || request.newPassword() == null ? "" : request.newPassword();
        String confirm = request == null || request.confirmPassword() == null ? "" : request.confirmPassword();
        String problem = AccountValidation.passwordProblem(password);
        if (problem != null) {
            errors.add("new_password", problem);
        }
        if (!password.equals(confirm)) {
            errors.add("confirm_password", AccountValidation.MSG_PASSWORD_MISMATCH);
        }
        errors.throwIfAny();
        if (user.getPasswordHash() != null && passwordEncoder.matches(password, user.getPasswordHash())) {
            throw Errors.fail("new_password", AccountValidation.MSG_TEMP_PASSWORD_SAME);
        }
        LocalDateTime now = BeijingTime.now();
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getPasswordHash, passwordEncoder.encode(password))
                .set(User::getPasswordUpdatedAt, now)
                .set(User::getMustChangePassword, false)
                .set(User::getTempPasswordExpireAt, null)
                .set(User::getUpdatedAt, now));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(false);
        user.setTempPasswordExpireAt(null);
        user.setPasswordUpdatedAt(now);
        accountState.invalidateTokens(user, TokenRevokeReason.PASSWORD_CHANGED);
        RecoveryCodeVO code = accountState.generateRecoveryCode(user, RecoveryCodeScene.RECOVERY, false, client,
                RecoveryCodeRevokeReason.ACCOUNT_RECOVERED);
        TokenPair pair = accountState.issue(user, client, deviceId());
        return accountState.toTokenVO(user, pair, client, code);
    }

    @Override
    @Transactional
    public AuthTokenVO changePassword(PasswordChangeRequest request, ClientType client) {
        User user = accountState.requireUser(UserContext.requireUserId());
        Errors errors = new Errors();
        String current = request == null || request.currentPassword() == null ? "" : request.currentPassword();
        String password = request == null || request.newPassword() == null ? "" : request.newPassword();
        String confirm = request == null || request.confirmPassword() == null ? "" : request.confirmPassword();
        if (current.isEmpty()) {
            errors.add("current_password", "请输入当前密码");
        }
        String problem = AccountValidation.passwordProblem(password);
        if (problem != null) {
            errors.add("new_password", problem);
        }
        if (!password.equals(confirm)) {
            errors.add("confirm_password", AccountValidation.MSG_PASSWORD_MISMATCH);
        }
        errors.throwIfAny();
        if (loginAttemptStore.failures(user.getPhone()) >= properties.getPasswordMaxFailures()) {
            throw lockedPasswordChange(loginAttemptStore.remainingSeconds(user.getPhone()));
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(current, user.getPasswordHash())) {
            int count = loginAttemptStore.recordFailure(user.getPhone(), properties.getPasswordLockTtl());
            if (count >= properties.getPasswordMaxFailures()) {
                throw lockedPasswordChange(properties.getPasswordLockTtl().toSeconds());
            }
            throw Errors.fail("current_password", MSG_CURRENT_PASSWORD_WRONG);
        }
        if (passwordEncoder.matches(password, user.getPasswordHash())) {
            throw Errors.fail("new_password", AccountValidation.MSG_PASSWORD_SAME);
        }
        LocalDateTime now = BeijingTime.now();
        String hash = passwordEncoder.encode(password);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getPasswordHash, hash)
                .set(User::getPasswordUpdatedAt, now)
                .set(User::getMustChangePassword, false)
                .set(User::getTempPasswordExpireAt, null)
                .set(User::getUpdatedAt, now));
        user.setPasswordHash(hash);
        user.setPasswordUpdatedAt(now);
        user.setMustChangePassword(false);
        loginAttemptStore.clear(user.getPhone());
        accountState.invalidateTokens(user, TokenRevokeReason.PASSWORD_CHANGED);
        TokenPair pair = accountState.issue(user, client, deviceId());
        return accountState.toTokenVO(user, pair, client, null);
    }

    private BizException lockedLogin(long seconds) {
        long safe = Math.max(1, seconds);
        LocalDateTime unlockAt = BeijingTime.now().plusSeconds(safe);
        String message = "密码连续错误 5 次，" + DateTimeText.monthDayTime(unlockAt) + " 后可以再试，也可以用验证码登录";
        return new BizException(ErrorCode.LOGIN_LOCKED, message)
                .with("unlock_at", BeijingTime.format(unlockAt));
    }

    private BizException lockedPasswordChange(long seconds) {
        long safe = Math.max(1, seconds);
        LocalDateTime unlockAt = BeijingTime.now().plusSeconds(safe);
        return new BizException(ErrorCode.LOGIN_LOCKED, MSG_LOCKED_CHANGE)
                .with("unlock_at", BeijingTime.format(unlockAt));
    }

    private void updateLastLogin(User user, ClientType client) {
        LocalDateTime now = BeijingTime.now();
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getLastLoginAt, now)
                .set(User::getLastLoginClient, client.name())
                .set(User::getUpdatedAt, now));
        user.setLastLoginAt(now);
        user.setLastLoginClient(client.name());
    }

    private String ip() {
        return RequestContext.ip();
    }

    private String ipRegion() {
        String ip = RequestContext.ip();
        if (ip == null) {
            return null;
        }
        try {
            return ipRegionResolver.resolve(ip);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String safeDeviceId(RegisterRequest request) {
        return request == null ? null : request.deviceId();
    }

    private static String safeDeviceId(PasswordLoginRequest request) {
        return request == null ? null : request.deviceId();
    }

    private static String safeDeviceId(SmsLoginRequest request) {
        return request == null ? null : request.deviceId();
    }

    private static String deviceId() {
        var info = UserContext.get();
        return info == null ? null : null;
    }

    static String defaultName(String phone) {
        String suffix = phone.length() >= 4 ? phone.substring(phone.length() - 4) : phone;
        return "用户" + suffix;
    }
}
