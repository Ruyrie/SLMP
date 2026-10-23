package com.zhimu.server.biz.account.service.impl;

import com.zhimu.server.biz.account.entity.RecoveryCode;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.NextStep;
import com.zhimu.server.biz.account.enums.RecoveryCodeRevokeReason;
import com.zhimu.server.biz.account.enums.RecoveryCodeScene;
import com.zhimu.server.biz.account.enums.RecoveryCodeStatus;
import com.zhimu.server.biz.account.mapper.RecoveryCodeMapper;
import com.zhimu.server.biz.account.request.RecoveryCodeRegenerateRequest;
import com.zhimu.server.biz.account.service.SmsCodeService;
import com.zhimu.server.biz.account.support.AccountStateService;
import com.zhimu.server.biz.account.test.FixedTime;
import com.zhimu.server.biz.account.vo.RecoveryCodeConfirmVO;
import com.zhimu.server.biz.account.vo.RecoveryCodeVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecoveryCodeServiceImplTest {

    private final AccountStateService accountState = mock(AccountStateService.class);
    private final RecoveryCodeMapper recoveryCodeMapper = mock(RecoveryCodeMapper.class);
    private final SmsCodeService smsCodeService = mock(SmsCodeService.class);
    private final RecoveryCodeServiceImpl service = new RecoveryCodeServiceImpl(accountState, recoveryCodeMapper,
            smsCodeService);

    @BeforeEach
    void setUp() {
        FixedTime.set();
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
        user.setStatus("NORMAL");
        return user;
    }

    private static RecoveryCode live(String status, String scene, long id) {
        RecoveryCode code = new RecoveryCode();
        code.setId(id);
        code.setUserId(10001L);
        code.setStatus(status);
        code.setScene(scene);
        code.setLiveFlag(1);
        code.setClientType("APP_ANDROID");
        return code;
    }

    @Test
    void A12当前步骤不是保存恢复码返回40309() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        when(accountState.resolveNextStep(any(User.class), eq(ClientType.APP_ANDROID))).thenReturn(NextStep.NONE);
        BizException e = (BizException) catchException(() -> service.regeneratePending(ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40309);
        assertThat(e.getData().toString()).contains("next_step=NONE");
    }

    @Test
    void A12有待确认码时沿用场景并标记重新生成() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        when(accountState.resolveNextStep(any(User.class), eq(ClientType.APP_ANDROID)))
                .thenReturn(NextStep.SAVE_RECOVERY_CODE);
        when(accountState.liveCode(10001L)).thenReturn(live("PENDING_CONFIRM", "REGISTER", 501L));
        when(accountState.generateRecoveryCode(any(User.class), eq(RecoveryCodeScene.REGISTER), eq(true),
                eq(ClientType.APP_ANDROID), eq(RecoveryCodeRevokeReason.UNCONFIRMED)))
                .thenReturn(RecoveryCodeVO.builder().code("CODE").isRegenerated(true).build());

        RecoveryCodeVO vo = service.regeneratePending(ClientType.APP_ANDROID);

        assertThat(vo.getCode()).isEqualTo("CODE");
        verify(accountState).generateRecoveryCode(any(User.class), eq(RecoveryCodeScene.REGISTER), eq(true),
                eq(ClientType.APP_ANDROID), eq(RecoveryCodeRevokeReason.UNCONFIRMED));
    }

    @Test
    void A12没有待确认码时最近一次找回作废取找回场景() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        when(accountState.resolveNextStep(any(User.class), eq(ClientType.APP_ANDROID)))
                .thenReturn(NextStep.SAVE_RECOVERY_CODE);
        when(accountState.liveCode(10001L)).thenReturn(null);
        RecoveryCode revoked = live("REVOKED", "RECOVERY", 500L);
        revoked.setRevokeReason(RecoveryCodeRevokeReason.ACCOUNT_RECOVERED.name());
        when(recoveryCodeMapper.selectOne(any())).thenReturn(revoked);
        when(accountState.generateRecoveryCode(any(User.class), eq(RecoveryCodeScene.RECOVERY), eq(true), any(),
                any())).thenReturn(RecoveryCodeVO.builder().code("CODE").build());

        service.regeneratePending(ClientType.APP_ANDROID);

        verify(accountState).generateRecoveryCode(any(User.class), eq(RecoveryCodeScene.RECOVERY), eq(true), any(),
                any());
    }

    @Test
    void A13验证码错误返回40001_成功后作废并重新生成() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        when(accountState.generateRecoveryCode(any(User.class), eq(RecoveryCodeScene.REGENERATE), eq(false),
                eq(ClientType.APP_ANDROID), eq(RecoveryCodeRevokeReason.REGENERATED)))
                .thenReturn(RecoveryCodeVO.builder().code("CODE").build());

        RecoveryCodeVO vo = service.regenerateBySms(new RecoveryCodeRegenerateRequest("123456"),
                ClientType.APP_ANDROID);

        assertThat(vo.getCode()).isEqualTo("CODE");
        verify(smsCodeService).verifyAndConsume(com.zhimu.server.biz.account.enums.SmsScene.REGENERATE_RECOVERY_CODE,
                "13800006620", "123456", "sms_code");
        verify(accountState).revokeLiveCode(10001L, RecoveryCodeRevokeReason.REGENERATED);
    }

    @Test
    void A13验证码格式错误40001() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        BizException e = (BizException) catchException(() -> service.regenerateBySms(
                new RecoveryCodeRegenerateRequest("12"), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40001);
        verify(smsCodeService, never()).verifyAndConsume(any(), any(), any(), any());
    }

    @Test
    void A14他人恢复码返回40401() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        RecoveryCode other = live("PENDING_CONFIRM", "REGISTER", 601L);
        other.setUserId(10002L);
        when(recoveryCodeMapper.selectById(601L)).thenReturn(other);

        BizException e = (BizException) catchException(() -> service.confirm(601L));
        assertThat(e.getCode()).isEqualTo(40401);
    }

    @Test
    void A14已是ACTIVE同一id直接成功() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        RecoveryCode active = live("ACTIVE", "REGISTER", 501L);
        active.setConfirmedAt(java.time.LocalDateTime.of(2026, 10, 19, 9, 32));
        when(recoveryCodeMapper.selectById(501L)).thenReturn(active);
        when(accountState.resolveNextStep(any(User.class), eq(ClientType.APP_ANDROID))).thenReturn(NextStep.NONE);

        RecoveryCodeConfirmVO vo = service.confirm(501L);

        assertThat(vo.getStatus()).isEqualTo("ACTIVE");
        assertThat(vo.getNextStep()).isEqualTo("NONE");
        verify(recoveryCodeMapper, never()).updateById(any(RecoveryCode.class));
    }

    @Test
    void A14确认当前待确认码置为ACTIVE() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        RecoveryCode pending = live("PENDING_CONFIRM", "REGISTER", 501L);
        when(recoveryCodeMapper.selectById(501L)).thenReturn(pending);
        when(accountState.liveCode(10001L)).thenReturn(pending);
        when(accountState.resolveNextStep(any(User.class), eq(ClientType.APP_ANDROID))).thenReturn(NextStep.NONE);

        RecoveryCodeConfirmVO vo = service.confirm(501L);

        assertThat(pending.getStatus()).isEqualTo("ACTIVE");
        assertThat(vo.getStatus()).isEqualTo("ACTIVE");
        assertThat(vo.getConfirmedAt()).isEqualTo("2026-10-19 09:30:00");
        verify(recoveryCodeMapper).updateById(pending);
    }

    @Test
    void A14已被替换的码返回40903() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        RecoveryCode stale = live("PENDING_CONFIRM", "REGISTER", 501L);
        RecoveryCode current = live("PENDING_CONFIRM", "REGISTER", 502L);
        when(recoveryCodeMapper.selectById(501L)).thenReturn(stale);
        when(accountState.liveCode(10001L)).thenReturn(current);

        BizException e = (BizException) catchException(() -> service.confirm(501L));
        assertThat(e.getCode()).isEqualTo(40903);
        assertThat(e.getMessage()).isEqualTo("恢复码已更新，请保存最新的恢复码");
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
