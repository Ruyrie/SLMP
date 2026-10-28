package com.zhimu.server.biz.account.service.impl;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.AccountRecovery;
import com.zhimu.server.biz.account.entity.RecoveryCode;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.mapper.AccountRecoveryMapper;
import com.zhimu.server.biz.account.mapper.RecoveryVerifyLogMapper;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.request.AccountRecoveryRequest;
import com.zhimu.server.biz.account.service.SmsCodeService;
import com.zhimu.server.biz.account.support.AccountStateService;
import com.zhimu.server.biz.account.support.LoginAttemptStore;
import com.zhimu.server.biz.account.support.RecoveryAttemptRecorder;
import com.zhimu.server.biz.account.test.FixedTime;
import com.zhimu.server.biz.account.test.TestStores;
import com.zhimu.server.biz.account.vo.AccountRecoverySubmitVO;
import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.token.UserTokenService;
import com.zhimu.server.integration.ipregion.IpRegionResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountRecoveryServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AccountRecoveryMapper recoveryMapper = mock(AccountRecoveryMapper.class);
    private final RecoveryVerifyLogMapper verifyLogMapper = mock(RecoveryVerifyLogMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccountStateService accountState = mock(AccountStateService.class);
    private final SmsCodeService smsCodeService = mock(SmsCodeService.class);
    private final RecoveryAttemptRecorder attemptRecorder = mock(RecoveryAttemptRecorder.class);
    private final TestStores.InMemoryLoginAttemptStore loginAttempts = new TestStores.InMemoryLoginAttemptStore();
    private final UserTokenService userTokenService = mock(UserTokenService.class);
    private final AuthStateCache authStateCache = mock(AuthStateCache.class);
    private final OperationLogRecorder operationLogRecorder = mock(OperationLogRecorder.class);
    private final IpRegionResolver ipRegionResolver = mock(IpRegionResolver.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AccountProperties properties = new AccountProperties();
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MessageService> messageServiceProvider = mock(ObjectProvider.class);
    private final MessageService messageService = mock(MessageService.class);
    private AccountRecoveryServiceImpl service;

    @BeforeEach
    void setUp() {
        FixedTime.set();
        service = new AccountRecoveryServiceImpl(userMapper, recoveryMapper, verifyLogMapper, passwordEncoder,
                accountState, smsCodeService, attemptRecorder, loginAttempts, userTokenService, authStateCache,
                operationLogRecorder, ipRegionResolver, eventPublisher, properties, messageServiceProvider);
        when(messageServiceProvider.getIfAvailable()).thenReturn(messageService);
    }

    @AfterEach
    void tearDown() {
        FixedTime.reset();
    }

    private static User user() {
        User user = new User();
        user.setId(10001L);
        user.setPhone("13800006620");
        user.setName("王建国");
        user.setStatus("NORMAL");
        user.setTokenVersion(0);
        user.setRecoveryFailCount(0);
        return user;
    }

    private static AccountRecovery pending() {
        AccountRecovery recovery = new AccountRecovery();
        recovery.setId(3021L);
        recovery.setUserId(10001L);
        recovery.setOldPhone("13800006620");
        recovery.setNewPhone("15147602288");
        recovery.setStatus("PENDING");
        recovery.setPendingFlag(1);
        recovery.setVersion(0);
        recovery.setResultSmsStatus("PENDING");
        return recovery;
    }

    private static AccountRecoveryRequest request() {
        return new AccountRecoveryRequest("13800006620", "8KQ4T2MW7HXN3PDR", "15147602288", "123456",
                "原手机卡丢失，已经停机，现在换了新号码。");
    }

    @Test
    void A10原手机号未注册写校验记录并返回40001() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(null);
        BizException e = (BizException) catchException(() -> service.submit(request(), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40001);
        assertThat(e.getMessage()).isEqualTo("这个手机号没有注册过账号");
        verify(attemptRecorder).recordFailure(eq(null), eq("13800006620"),
                eq(com.zhimu.server.biz.account.enums.RecoveryVerifyResult.PHONE_NOT_REGISTERED),
                eq(0), eq(null), eq(null), eq(null), eq(ClientType.APP_ANDROID));
    }

    @Test
    void A10已有待审核申请返回40906() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(user());
        when(recoveryMapper.selectCount(any())).thenReturn(1L);
        BizException e = (BizException) catchException(() -> service.submit(request(), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40906);
    }

    @Test
    void A10新手机号被占用返回40904_PHONE_TAKEN() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(user());
        when(recoveryMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectByPhone("15147602288")).thenReturn(user());
        BizException e = (BizException) catchException(() -> service.submit(request(), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40904);
        assertThat(e.getData().toString()).contains("PHONE_TAKEN");
    }

    @Test
    void A10恢复码错误返回40105与剩余次数() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(user());
        when(recoveryMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectByPhone("15147602288")).thenReturn(null);
        RecoveryCode live = new RecoveryCode();
        live.setStatus("ACTIVE");
        live.setCodeHash("hash");
        when(accountState.liveCode(10001L)).thenReturn(live);
        when(passwordEncoder.matches("8KQ4T2MW7HXN3PDR", "hash")).thenReturn(false);

        BizException e = (BizException) catchException(() -> service.submit(request(), ClientType.APP_ANDROID));

        assertThat(e.getCode()).isEqualTo(40105);
        assertThat(e.getMessage()).isEqualTo("恢复码不正确，还可以再试 4 次");
        assertThat(e.getData().toString()).contains("remaining_attempts=4", "recovery_code");
        verify(attemptRecorder).recordFailure(any(User.class), eq("13800006620"),
                eq(com.zhimu.server.biz.account.enums.RecoveryVerifyResult.CODE_WRONG),
                eq(1), any(LocalDateTime.class), eq(null), eq(4), eq(ClientType.APP_ANDROID));
    }

    @Test
    void A10第5次错误返回42903并锁定24小时() {
        User user = user();
        user.setRecoveryFailCount(4);
        user.setRecoveryFailFirstAt(LocalDateTime.of(2026, 10, 19, 8, 0));
        when(userMapper.selectByPhone("13800006620")).thenReturn(user);
        when(recoveryMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectByPhone("15147602288")).thenReturn(null);
        RecoveryCode live = new RecoveryCode();
        live.setStatus("ACTIVE");
        live.setCodeHash("hash");
        when(accountState.liveCode(10001L)).thenReturn(live);
        when(passwordEncoder.matches(anyString(), eq("hash"))).thenReturn(false);

        BizException e = (BizException) catchException(() -> service.submit(request(), ClientType.APP_ANDROID));

        assertThat(e.getCode()).isEqualTo(42903);
        assertThat(e.getMessage()).contains("恢复码已连续填错 5 次", "10月20日 09:30");
        assertThat(e.getData().toString()).contains("unlock_at=2026-10-20 09:30:00");
    }

    @Test
    void A10锁定期内直接42903() {
        User user = user();
        user.setRecoveryLockUntil(LocalDateTime.of(2026, 10, 20, 9, 30));
        when(userMapper.selectByPhone("13800006620")).thenReturn(user);
        BizException e = (BizException) catchException(() -> service.submit(request(), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(42903);
        verify(attemptRecorder).recordFailure(any(User.class), eq("13800006620"),
                eq(com.zhimu.server.biz.account.enums.RecoveryVerifyResult.LOCKED), anyInt(), any(), any(), eq(0),
                eq(ClientType.APP_ANDROID));
    }

    @Test
    void A10提交成功写入待审核申请并消费验证码() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(user());
        when(recoveryMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectByPhone("15147602288")).thenReturn(null);
        RecoveryCode live = new RecoveryCode();
        live.setStatus("ACTIVE");
        live.setCodeHash("hash");
        when(accountState.liveCode(10001L)).thenReturn(live);
        when(passwordEncoder.matches("8KQ4T2MW7HXN3PDR", "hash")).thenReturn(true);
        doAnswer(invocation -> {
            AccountRecovery recovery = invocation.getArgument(0);
            recovery.setId(3021L);
            return 1;
        }).when(recoveryMapper).insert(any(AccountRecovery.class));

        AccountRecoverySubmitVO vo = service.submit(request(), ClientType.APP_ANDROID);

        assertThat(vo.getRecoveryId()).isEqualTo(3021L);
        assertThat(vo.getNewPhoneMasked()).isEqualTo("151****2288");
        assertThat(vo.getStatus()).isEqualTo("PENDING");
        assertThat(vo.getStatusText()).isEqualTo("待审核");
        verify(smsCodeService).verifyAndConsume(com.zhimu.server.biz.account.enums.SmsScene.RECOVERY_NEW_PHONE,
                "15147602288", "123456", "new_phone_sms_code");
        verify(userMapper).clearRecoveryFailure(10001L);
        verify(verifyLogMapper).insert(any(com.zhimu.server.biz.account.entity.RecoveryVerifyLog.class));
    }

    @Test
    void 管理端通过_换绑手机号并返回一次性临时密码_结果状态为线下告知() {
        AccountRecovery recovery = pending();
        when(recoveryMapper.selectById(3021L)).thenReturn(recovery);
        when(userMapper.selectByIdForUpdate(10001L)).thenReturn(user());
        when(userMapper.selectByPhone("15147602288")).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("temp-hash");
        when(recoveryMapper.updateById(any(AccountRecovery.class))).thenReturn(1);

        com.zhimu.server.biz.account.api.AccountRecoveryAdminService.ApproveResult result =
                service.approve(3021L, 9001L, "CALLBACK_NEW_PHONE", "已电话核实", 0);

        assertThat(result.tempPassword()).isNotBlank();
        assertThat(result.tempPasswordExpireAt()).isNotNull();
        verify(passwordEncoder).encode(result.tempPassword());
        assertThat(recovery.getStatus()).isEqualTo("APPROVED");
        assertThat(recovery.getPendingFlag()).isNull();
        assertThat(recovery.getResultSmsStatus()).isEqualTo("MANUAL");
        assertThat(recovery.getHandledBy()).isEqualTo(9001L);
        verify(userMapper).update(eq(null), any());
        verify(accountState).invalidateTokens(any(User.class),
                eq(com.zhimu.server.biz.account.enums.TokenRevokeReason.ACCOUNT_RECOVERED));
        verify(accountState).revokeAllCodes(eq(10001L),
                eq(com.zhimu.server.biz.account.enums.RecoveryCodeRevokeReason.ACCOUNT_RECOVERED));
        ArgumentCaptor<com.zhimu.server.framework.oplog.OperationLogEntry> captor =
                ArgumentCaptor.forClass(com.zhimu.server.framework.oplog.OperationLogEntry.class);
        verify(operationLogRecorder).record(captor.capture());
        assertThat(captor.getValue().getOpAction()).isEqualTo("APPROVE");
        assertThat(captor.getValue().getBizType()).isEqualTo("ACCOUNT_RECOVERY");
        assertThat(String.valueOf(captor.getValue().getAfter())).doesNotContain(result.tempPassword());
        ArgumentCaptor<MessageCommand> message = ArgumentCaptor.forClass(MessageCommand.class);
        verify(messageService).send(message.capture());
        assertThat(message.getValue().getMsgType()).isEqualTo(com.zhimu.server.common.enums.MsgType.RECOVERY_RESULT);
        assertThat(message.getValue().getReceiverUserIds()).containsExactly(10001L);
        assertThat(message.getValue().getContent()).doesNotContain(result.tempPassword());
    }

    @Test
    void 管理端重新生成临时密码_只允许已通过_返回新密码() {
        AccountRecovery recovery = pending();
        recovery.setStatus("REJECTED");
        when(recoveryMapper.selectById(3021L)).thenReturn(recovery);
        BizException rejected = (BizException) catchException(() ->
                service.regenerateTempPassword(3021L, 9001L, null));
        assertThat(rejected.getMessage()).isEqualTo("已驳回的申请没有临时密码，不需要重新生成");

        recovery.setStatus("APPROVED");
        User user = user();
        user.setPhone("15147602288");
        when(userMapper.selectByIdForUpdate(10001L)).thenReturn(user);
        when(passwordEncoder.encode(anyString())).thenReturn("temp-hash");
        when(recoveryMapper.update(eq(null), any())).thenReturn(1);

        com.zhimu.server.biz.account.api.AccountRecoveryAdminService.TempPasswordResult result =
                service.regenerateTempPassword(3021L, 9001L, " ");

        assertThat(result.tempPassword()).isNotBlank();
        assertThat(result.phone()).isEqualTo("151****2288");
        assertThat(recovery.getResultSmsStatus()).isEqualTo("MANUAL");
        verify(accountState).invalidateTokens(any(User.class),
                eq(com.zhimu.server.biz.account.enums.TokenRevokeReason.ACCOUNT_RECOVERED));
        ArgumentCaptor<com.zhimu.server.framework.oplog.OperationLogEntry> captor =
                ArgumentCaptor.forClass(com.zhimu.server.framework.oplog.OperationLogEntry.class);
        verify(operationLogRecorder).record(captor.capture());
        assertThat(captor.getValue().getOpAction()).isEqualTo("REGENERATE_PASSWORD");
        assertThat(String.valueOf(captor.getValue().getAfter())).doesNotContain(result.tempPassword());
    }

    @Test
    void 管理端通过_版本不一致40902() {
        AccountRecovery recovery = pending();
        when(recoveryMapper.selectById(3021L)).thenReturn(recovery);
        BizException e = (BizException) catchException(() ->
                service.approve(3021L, 9001L, "OTHER", null, 3));
        assertThat(e.getCode()).isEqualTo(40902);
    }

    @Test
    void 管理端通过_已处理40903() {
        AccountRecovery recovery = pending();
        recovery.setStatus("APPROVED");
        when(recoveryMapper.selectById(3021L)).thenReturn(recovery);
        BizException e = (BizException) catchException(() ->
                service.approve(3021L, 9001L, "OTHER", null, 0));
        assertThat(e.getCode()).isEqualTo(40903);
    }

    @Test
    void 管理端通过_新手机号被占用40904() {
        AccountRecovery recovery = pending();
        when(recoveryMapper.selectById(3021L)).thenReturn(recovery);
        when(userMapper.selectByIdForUpdate(10001L)).thenReturn(user());
        User other = user();
        other.setId(10002L);
        when(userMapper.selectByPhone("15147602288")).thenReturn(other);
        BizException e = (BizException) catchException(() ->
                service.approve(3021L, 9001L, "OTHER", null, 0));
        assertThat(e.getCode()).isEqualTo(40904);
        assertThat(e.getMessage()).isEqualTo("新手机号已被其他账号注册，不能通过");
    }

    @Test
    void 管理端驳回_缺少原因40001_成功后保留原因并发站内消息_不发短信() {
        AccountRecovery recovery = pending();
        when(recoveryMapper.selectById(3021L)).thenReturn(recovery);
        BizException missing = (BizException) catchException(() ->
                service.reject(3021L, 9001L, "OTHER", " ", 0));
        assertThat(missing.getCode()).isEqualTo(40001);

        when(recoveryMapper.updateById(any(AccountRecovery.class))).thenReturn(1);
        service.reject(3021L, 9001L, "OTHER", "信息不完整", 0);

        assertThat(recovery.getStatus()).isEqualTo("REJECTED");
        assertThat(recovery.getPendingFlag()).isNull();
        assertThat(recovery.getHandleNote()).isEqualTo("信息不完整");
        assertThat(recovery.getResultSmsStatus()).isEqualTo("NOT_SENT");
        verify(operationLogRecorder).record(any());
        ArgumentCaptor<MessageCommand> message = ArgumentCaptor.forClass(MessageCommand.class);
        verify(messageService).send(message.capture());
        assertThat(message.getValue().getContent()).contains("信息不完整");
    }

    @Test
    void 待审核计数() {
        when(recoveryMapper.selectCount(any())).thenReturn(3L);
        assertThat(service.countPending()).isEqualTo(3L);
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
