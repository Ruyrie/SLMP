package com.zhimu.server.biz.account.service.impl;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.SmsScene;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.request.AccountSmsCodeRequest;
import com.zhimu.server.biz.account.request.SmsCodeSendRequest;
import com.zhimu.server.biz.account.support.AccountStateService;
import com.zhimu.server.biz.account.support.CancellationRules;
import com.zhimu.server.biz.account.test.FixedTime;
import com.zhimu.server.biz.account.test.TestStores;
import com.zhimu.server.biz.account.vo.SmsCodeSendVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.integration.sms.SmsClient;
import com.zhimu.server.integration.sms.SmsSendResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmsCodeServiceImplTest {

    private final SmsClient smsClient = mock(SmsClient.class);
    private final TestStores.InMemorySmsCodeStore store = new TestStores.InMemorySmsCodeStore();
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AccountStateService accountState = mock(AccountStateService.class);
    private final CancellationRules cancellationRules = mock(CancellationRules.class);
    private final AccountProperties properties = new AccountProperties();
    private SmsCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        FixedTime.set();
        service = new SmsCodeServiceImpl(smsClient, store, userMapper, accountState, cancellationRules, properties);
    }

    @AfterEach
    void tearDown() {
        FixedTime.reset();
        UserContext.clear();
    }

    private static User user(String phone, String status) {
        User user = new User();
        user.setId(10001L);
        user.setPhone(phone);
        user.setStatus(status);
        return user;
    }

    private static int codeOf(BizException e) {
        return e.getCode();
    }

    @Test
    void A01登录场景未注册返回40001与reason() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(null);
        BizException e = (BizException) catchException(() ->
                service.send(new SmsCodeSendRequest("13800006620", "LOGIN", null)));
        assertThat(codeOf(e)).isEqualTo(40001);
        assertThat(e.getData().toString()).contains("reason=PHONE_NOT_REGISTERED", "phone");
    }

    @Test
    void A01注册场景已注册返回40904且文案含已经注册() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(user("13800006620", "NORMAL"));
        BizException e = (BizException) catchException(() ->
                service.send(new SmsCodeSendRequest("13800006620", "REGISTER", null)));
        assertThat(codeOf(e)).isEqualTo(40904);
        assertThat(e.getMessage()).contains("已经注册");
        assertThat(e.getData().toString()).contains("PHONE_REGISTERED");
    }

    @Test
    void A01注册场景注销未满30天返回40904与可注册日期() {
        User cancelled = user("13800006620", "CANCELLED");
        cancelled.setCancelledAt(LocalDateTime.of(2026, 10, 19, 8, 0));
        when(userMapper.selectByPhone("13800006620")).thenReturn(cancelled);
        BizException e = (BizException) catchException(() ->
                service.send(new SmsCodeSendRequest("13800006620", "REGISTER", null)));
        assertThat(codeOf(e)).isEqualTo(40904);
        assertThat(e.getMessage()).contains("11月18日").doesNotContain("（补充）");
        assertThat(e.getData().toString()).contains("PHONE_CANCEL_COOLING");
    }

    @Test
    void A01新手机号与原手机号相同返回40001() {
        BizException e = (BizException) catchException(() -> service.send(
                new SmsCodeSendRequest("15147602288", "RECOVERY_NEW_PHONE", "15147602288")));
        assertThat(codeOf(e)).isEqualTo(40001);
        assertThat(e.getData().toString()).contains("new_phone", "不能和原手机号相同");
    }

    @Test
    void A01恢复新手机号被占用返回40904_PHONE_TAKEN() {
        when(userMapper.selectByPhone("15147602288")).thenReturn(user("15147602288", "NORMAL"));
        BizException e = (BizException) catchException(() -> service.send(
                new SmsCodeSendRequest("15147602288", "RECOVERY_NEW_PHONE", "13800006620")));
        assertThat(codeOf(e)).isEqualTo(40904);
        assertThat(e.getData().toString()).contains("PHONE_TAKEN");
        assertThat(e.getMessage()).doesNotContain("已经注册");
    }

    @Test
    void A01冷却中返回42901与剩余秒数() {
        store.startCooldown(SmsScene.RESET_PASSWORD, "13800006620", 60);
        when(userMapper.selectByPhone("13800006620")).thenReturn(user("13800006620", "NORMAL"));
        BizException e = (BizException) catchException(() ->
                service.send(new SmsCodeSendRequest("13800006620", "RESET_PASSWORD", null)));
        assertThat(codeOf(e)).isEqualTo(42901);
        assertThat(e.getData().toString()).contains("cooldown_seconds");
    }

    @Test
    void A01超过每天10条返回42901() {
        store.setDailyCount("13800006620", 10);
        when(userMapper.selectByPhone("13800006620")).thenReturn(user("13800006620", "NORMAL"));
        BizException e = (BizException) catchException(() ->
                service.send(new SmsCodeSendRequest("13800006620", "LOGIN", null)));
        assertThat(codeOf(e)).isEqualTo(42901);
        assertThat(e.getData().toString()).contains("cooldown_seconds");
    }

    @Test
    void A01发送成功返回打码手机号与冷却及有效期() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(user("13800006620", "NORMAL"));
        when(smsClient.send(eq("13800006620"), any())).thenReturn(new SmsSendResult("b", "r", 60, 300));

        SmsCodeSendVO vo = service.send(new SmsCodeSendRequest("13800006620", "LOGIN", null));

        assertThat(vo.getPhoneMasked()).isEqualTo("138****6620");
        assertThat(vo.getCooldownSeconds()).isEqualTo(60);
        assertThat(vo.getExpireSeconds()).isEqualTo(300);
        assertThat(store.isSent(SmsScene.LOGIN, "13800006620")).isTrue();
        assertThat(store.dailyCount("13800006620")).isEqualTo(1);
    }

    @Test
    void 验证码错误第一次返回40103_第5次作废并提示过期() {
        store.markSent(SmsScene.LOGIN, "13800006620", 300);
        when(smsClient.verify("13800006620", "000000")).thenReturn(false);

        BizException first = (BizException) catchException(() ->
                service.verifyAndConsume(SmsScene.LOGIN, "13800006620", "000000", "sms_code"));
        assertThat(first.getCode()).isEqualTo(40103);
        assertThat(first.getMessage()).isEqualTo("验证码不正确，请重新输入");

        for (int i = 0; i < 4; i++) {
            catchException(() -> service.verifyAndConsume(SmsScene.LOGIN, "13800006620", "000000", "sms_code"));
        }
        BizException fifth = (BizException) catchException(() ->
                service.verifyAndConsume(SmsScene.LOGIN, "13800006620", "000000", "sms_code"));
        assertThat(fifth.getMessage()).contains("过期");
        assertThat(store.isSent(SmsScene.LOGIN, "13800006620")).isFalse();
    }

    @Test
    void 未获取验证码提示过期_校验成功即删除() {
        BizException expired = (BizException) catchException(() ->
                service.verifyAndConsume(SmsScene.LOGIN, "13800006620", "123456", "sms_code"));
        assertThat(expired.getMessage()).contains("过期");

        store.markSent(SmsScene.LOGIN, "13800006620", 300);
        when(smsClient.verify("13800006620", "123456")).thenReturn(true);
        service.verifyAndConsume(SmsScene.LOGIN, "13800006620", "123456", "sms_code");
        assertThat(store.isSent(SmsScene.LOGIN, "13800006620")).isFalse();
    }

    @Test
    void A02注销场景被阻止返回40310与阻止原因() {
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));
        when(accountState.requireUser(10001L)).thenReturn(user("13800006620", "NORMAL"));
        CancellationRules.CancelCheck check = new CancellationRules.CancelCheck(
                java.util.List.of(), java.util.List.of(),
                java.util.List.of(com.zhimu.server.biz.account.enums.CancelBlockReason.OWNED_FARM_ACTIVE),
                null, null, "v", "东山猪场", null);
        when(cancellationRules.evaluate(any(User.class))).thenReturn(check);
        when(cancellationRules.blockedException(check)).thenReturn(
                new BizException(com.zhimu.server.common.error.ErrorCode.CANCEL_NOT_ALLOWED,
                        "东山猪场仍在使用，请先转让给场长或停用后再注销")
                        .with("block_reasons", java.util.List.of("OWNED_FARM_ACTIVE")));

        BizException e = (BizException) catchException(() ->
                service.sendToSelf(new AccountSmsCodeRequest("CANCEL_ACCOUNT")));
        assertThat(e.getCode()).isEqualTo(40310);
        assertThat(e.getMessage()).contains("东山猪场");
        assertThat(e.getData().toString()).contains("block_reasons");
    }

    @Test
    void A02只能发本人手机号且场景受限() {
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));
        when(accountState.requireUser(10001L)).thenReturn(user("13800006620", "NORMAL"));
        BizException e = (BizException) catchException(() ->
                service.sendToSelf(new AccountSmsCodeRequest("LOGIN")));
        assertThat(e.getCode()).isEqualTo(40001);
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
