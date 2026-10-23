package com.zhimu.server.biz.account.service.impl;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.RecoveryCode;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.NextStep;
import com.zhimu.server.biz.account.enums.RecoveryCodeScene;
import com.zhimu.server.biz.account.enums.RecoveryCodeStatus;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.request.AfterRecoveryPasswordRequest;
import com.zhimu.server.biz.account.request.PasswordChangeRequest;
import com.zhimu.server.biz.account.request.PasswordLoginRequest;
import com.zhimu.server.biz.account.request.PasswordResetRequest;
import com.zhimu.server.biz.account.request.RegisterRequest;
import com.zhimu.server.biz.account.service.LoginLogService;
import com.zhimu.server.biz.account.service.SmsCodeService;
import com.zhimu.server.biz.account.support.AccountStateService;
import com.zhimu.server.biz.account.support.LoginAttemptStore;
import com.zhimu.server.biz.account.test.FixedTime;
import com.zhimu.server.biz.account.test.TestStores;
import com.zhimu.server.biz.account.vo.AuthTokenVO;
import com.zhimu.server.biz.account.vo.RecoveryCodeVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.framework.security.jwt.UserJwtService;
import com.zhimu.server.framework.security.token.TokenPair;
import com.zhimu.server.framework.security.token.UserTokenService;
import com.zhimu.server.integration.ipregion.IpRegionResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccountStateService accountState = mock(AccountStateService.class);
    private final SmsCodeService smsCodeService = mock(SmsCodeService.class);
    private final LoginLogService loginLogService = mock(LoginLogService.class);
    private final TestStores.InMemoryLoginAttemptStore loginAttempts = new TestStores.InMemoryLoginAttemptStore();
    private final UserTokenService userTokenService = mock(UserTokenService.class);
    private final UserJwtService userJwtService = mock(UserJwtService.class);
    private final IpRegionResolver ipRegionResolver = mock(IpRegionResolver.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AccountProperties properties = new AccountProperties();
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        FixedTime.set();
        service = new AuthServiceImpl(userMapper, passwordEncoder, accountState, smsCodeService, loginLogService,
                loginAttempts, userTokenService, userJwtService, ipRegionResolver, eventPublisher, properties);
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));
    }

    @AfterEach
    void tearDown() {
        FixedTime.reset();
        UserContext.clear();
    }

    private static User user() {
        User user = new User();
        user.setId(10001L);
        user.setPhone("13800006620");
        user.setName("王建国");
        user.setStatus("NORMAL");
        user.setTokenVersion(0);
        user.setProfileVersion(3);
        user.setMustChangePassword(false);
        user.setPasswordHash("hash");
        return user;
    }

    private static TokenPair pair() {
        return new TokenPair("access", "Bearer", 7200, "refresh", 2592000);
    }

    private void mockTokenAssembly() {
        when(accountState.issue(any(User.class), any(ClientType.class), any())).thenReturn(pair());
        when(accountState.toTokenVO(any(User.class), any(TokenPair.class), any(ClientType.class), any()))
                .thenReturn(AuthTokenVO.builder().accessToken("access").nextStep("NONE").build());
    }

    @Test
    void 注册新账号创建默认姓名与恢复码并返回令牌() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(null);
        when(passwordEncoder.encode("Zmt12345")).thenReturn("bcrypt");
        when(accountState.generateRecoveryCode(any(User.class), eq(RecoveryCodeScene.REGISTER), eq(false),
                eq(ClientType.APP_ANDROID), any())).thenReturn(RecoveryCodeVO.builder().code("CODE").build());
        mockTokenAssembly();

        AuthTokenVO token = service.register(new RegisterRequest("13800006620", "123456", "Zmt12345", true,
                "2026-10-01", "device-1", "Pixel", "14"), ClientType.APP_ANDROID);

        assertThat(token.getAccessToken()).isEqualTo("access");
        verify(smsCodeService).verifyAndConsume(com.zhimu.server.biz.account.enums.SmsScene.REGISTER,
                "13800006620", "123456", "sms_code");
        verify(userMapper).insert(org.mockito.ArgumentMatchers.argThat((User u) ->
                "用户6620".equals(u.getName()) && "NORMAL".equals(u.getStatus())
                        && "APP_ANDROID".equals(u.getRegisterClient()) && "bcrypt".equals(u.getPasswordHash())));
        verify(accountState).generateRecoveryCode(any(User.class), eq(RecoveryCodeScene.REGISTER), eq(false),
                eq(ClientType.APP_ANDROID), any());
    }

    @Test
    void 注册字段错误一次返回多个field_errors() {
        BizException e = (BizException) catchException(() -> service.register(
                new RegisterRequest("1380000", "12", "abc", false, "2020-01-01", null, null, null),
                ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40001);
        String data = e.getData().toString();
        assertThat(data).contains("phone", "sms_code", "password", "agree_terms", "agreement_version");
        // device_id 不是输入框字段：只给 message，不进 field_errors（约定 7.1 第 6 条）
        assertThat(data).doesNotContain("device_id", "device_model", "os_version");
        assertThat(e.getMessage()).contains("手机号格式不正确");
        verify(smsCodeService, never()).verifyAndConsume(any(), anyString(), anyString(), anyString());
    }

    @Test
    void 注册幂等重放_密码一致时不重复插库且不带恢复码明文() {
        User existing = user();
        when(userMapper.selectByPhone("13800006620")).thenReturn(existing);
        when(passwordEncoder.matches("Zmt12345", "hash")).thenReturn(true);
        RecoveryCode pending = new RecoveryCode();
        pending.setStatus(RecoveryCodeStatus.PENDING_CONFIRM.name());
        pending.setScene(RecoveryCodeScene.REGISTER.name());
        when(accountState.liveCode(10001L)).thenReturn(pending);
        mockTokenAssembly();

        AuthTokenVO token = service.register(new RegisterRequest("13800006620", "123456", "Zmt12345", true,
                "2026-10-01", "device-1", null, null), ClientType.APP_ANDROID);

        assertThat(token.getAccessToken()).isEqualTo("access");
        verify(userMapper, never()).insert(any(User.class));
        verify(smsCodeService, never()).verifyAndConsume(any(), anyString(), anyString(), anyString());
        verify(accountState).toTokenVO(any(User.class), any(TokenPair.class), eq(ClientType.APP_ANDROID), eq(null));
    }

    @Test
    void 注册已存在账号密码不一致返回40904() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(user());
        when(passwordEncoder.matches("Zmt12345", "hash")).thenReturn(false);
        BizException e = (BizException) catchException(() -> service.register(
                new RegisterRequest("13800006620", "123456", "Zmt12345", true, "2026-10-01", "d", null, null),
                ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40904);
        assertThat(e.getMessage()).contains("已经注册");
    }

    @Test
    void 密码登录第1次错误返回40102并计数() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(user());
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        BizException e = (BizException) catchException(() -> service.loginByPassword(
                new PasswordLoginRequest("13800006620", "wrong", "d", null, null), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40102);
        assertThat(loginAttempts.failures("13800006620")).isEqualTo(1);
        assertThat(e.getData().toString()).contains("password");
    }

    @Test
    void 密码登录第5次错误返回42902与unlock_at() {
        loginAttempts.setFailures("13800006620", 4);
        when(userMapper.selectByPhone("13800006620")).thenReturn(user());
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        BizException e = (BizException) catchException(() -> service.loginByPassword(
                new PasswordLoginRequest("13800006620", "wrong", "d", null, null), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(42902);
        assertThat(e.getData().toString()).contains("unlock_at");
        assertThat(e.getMessage()).contains("密码连续错误 5 次").doesNotContain("（补充）");
    }

    @Test
    void 密码锁定期间不比对密码直接42902() {
        loginAttempts.setFailures("13800006620", 5);
        BizException e = (BizException) catchException(() -> service.loginByPassword(
                new PasswordLoginRequest("13800006620", "whatever", "d", null, null), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(42902);
        verify(userMapper, never()).selectByPhone(anyString());
    }

    @Test
    void 临时密码过期返回40107与指定文案() {
        User user = user();
        user.setMustChangePassword(true);
        user.setTempPasswordExpireAt(LocalDateTime.of(2026, 10, 18, 9, 0));
        when(userMapper.selectByPhone("13800006620")).thenReturn(user);
        when(passwordEncoder.matches("temp", "hash")).thenReturn(true);
        BizException e = (BizException) catchException(() -> service.loginByPassword(
                new PasswordLoginRequest("13800006620", "temp", "d", null, null), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40107);
        assertThat(e.getMessage()).isEqualTo("临时密码已过期，请改用验证码登录");
    }

    @Test
    void 密码登录成功清除错误计数并签发令牌() {
        User user = user();
        loginAttempts.setFailures("13800006620", 2);
        when(userMapper.selectByPhone("13800006620")).thenReturn(user);
        when(passwordEncoder.matches("Zmt12345", "hash")).thenReturn(true);
        mockTokenAssembly();

        AuthTokenVO token = service.loginByPassword(new PasswordLoginRequest("13800006620", "Zmt12345",
                "device-1", "Pixel", "14"), ClientType.APP_ANDROID);

        assertThat(token.getAccessToken()).isEqualTo("access");
        assertThat(loginAttempts.failures("13800006620")).isZero();
        verify(userMapper).update(eq(null), any());
    }

    @Test
    void 未注册手机号登录返回40001与reason() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(null);
        BizException e = (BizException) catchException(() -> service.loginByPassword(
                new PasswordLoginRequest("13800006620", "x", "d", null, null), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40001);
        assertThat(e.getData().toString()).contains("PHONE_NOT_REGISTERED");
    }

    @Test
    void 短信重置密码清空临时密码并失效令牌() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(user());
        when(passwordEncoder.encode("NewPass123")).thenReturn("newhash");

        service.resetPassword(new PasswordResetRequest("13800006620", "123456", "NewPass123"));

        verify(smsCodeService).verifyAndConsume(com.zhimu.server.biz.account.enums.SmsScene.RESET_PASSWORD,
                "13800006620", "123456", "sms_code");
        verify(accountState).invalidateTokens(any(User.class), eq(com.zhimu.server.biz.account.enums.TokenRevokeReason.PASSWORD_CHANGED));
    }

    @Test
    void 找回后改密当前步骤不符返回40309() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        when(accountState.resolveNextStep(any(User.class), any(ClientType.class))).thenReturn(NextStep.SAVE_RECOVERY_CODE);
        BizException e = (BizException) catchException(() -> service.changePasswordAfterRecovery(
                new AfterRecoveryPasswordRequest("NewPass123", "NewPass123"), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40309);
        assertThat(e.getData().toString()).contains("next_step");
    }

    @Test
    void 找回后改密不能使用临时密码() {
        User user = user();
        when(accountState.requireUser(10001L)).thenReturn(user);
        when(accountState.resolveNextStep(any(User.class), any(ClientType.class)))
                .thenReturn(NextStep.RECOVERY_CHANGE_PASSWORD);
        when(passwordEncoder.matches("Temp12345", "hash")).thenReturn(true);
        BizException e = (BizException) catchException(() -> service.changePasswordAfterRecovery(
                new AfterRecoveryPasswordRequest("Temp12345", "Temp12345"), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40001);
        assertThat(e.getMessage()).isEqualTo("不能使用短信中的临时密码");
    }

    @Test
    void 找回后改密成功生成新恢复码并换发令牌() {
        User user = user();
        when(accountState.requireUser(10001L)).thenReturn(user);
        when(accountState.resolveNextStep(any(User.class), any(ClientType.class)))
                .thenReturn(NextStep.RECOVERY_CHANGE_PASSWORD);
        when(passwordEncoder.matches("NewPass123", "hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("newhash");
        when(accountState.generateRecoveryCode(any(User.class), eq(RecoveryCodeScene.RECOVERY), eq(false),
                eq(ClientType.APP_ANDROID), any())).thenReturn(RecoveryCodeVO.builder().code("NEWCODE").build());
        mockTokenAssembly();

        AuthTokenVO token = service.changePasswordAfterRecovery(
                new AfterRecoveryPasswordRequest("NewPass123", "NewPass123"), ClientType.APP_ANDROID);

        assertThat(token).isNotNull();
        verify(accountState).invalidateTokens(any(User.class),
                eq(com.zhimu.server.biz.account.enums.TokenRevokeReason.PASSWORD_CHANGED));
    }

    @Test
    void 修改密码当前密码错误返回40001_current_password() {
        User user = user();
        when(accountState.requireUser(10001L)).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        BizException e = (BizException) catchException(() -> service.changePassword(
                new PasswordChangeRequest("wrong", "NewPass123", "NewPass123"), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40001);
        assertThat(e.getMessage()).isEqualTo("当前密码不正确");
        assertThat(e.getData().toString()).contains("current_password");
    }

    @Test
    void 修改密码新旧相同返回40001_new_password() {
        User user = user();
        when(accountState.requireUser(10001L)).thenReturn(user);
        when(passwordEncoder.matches("Zmt12345", "hash")).thenReturn(true);
        BizException e = (BizException) catchException(() -> service.changePassword(
                new PasswordChangeRequest("Zmt12345", "Zmt12345", "Zmt12345"), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40001);
        assertThat(e.getMessage()).isEqualTo("新密码不能和当前密码相同");
    }

    @Test
    void 刷新令牌时账号停用返回40104_ACCOUNT_DISABLED() {
        when(userTokenService.refresh("refresh")).thenReturn(pair());
        when(userJwtService.parse("access")).thenReturn(new UserJwtService.UserClaims(10001L, 0,
                ClientType.APP_ANDROID, "jti"));
        User user = user();
        user.setStatus("DISABLED");
        when(userMapper.selectById(10001L)).thenReturn(user);
        BizException e = (BizException) catchException(() -> service.refresh("refresh", ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40104);
        assertThat(e.getData().toString()).contains("ACCOUNT_DISABLED");
    }

    private static Throwable catchException(org.junit.jupiter.api.function.Executable executable) {
        try {
            executable.execute();
        } catch (Throwable e) {
            return e;
        }
        throw new AssertionError("expected exception");
    }
}

