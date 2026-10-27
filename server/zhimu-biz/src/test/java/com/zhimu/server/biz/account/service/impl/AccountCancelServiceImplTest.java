package com.zhimu.server.biz.account.service.impl;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.AccountCancel;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.CancelBlockReason;
import com.zhimu.server.biz.account.enums.SmsScene;
import com.zhimu.server.biz.account.event.AccountCancelledEvent;
import com.zhimu.server.biz.account.mapper.AccountCancelMapper;
import com.zhimu.server.biz.account.mapper.AccountRecoveryMapper;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.request.AccountCancelRequest;
import com.zhimu.server.biz.account.service.SmsCodeService;
import com.zhimu.server.biz.account.support.AccountStateService;
import com.zhimu.server.biz.account.support.CancellationRules;
import com.zhimu.server.biz.account.test.FixedTime;
import com.zhimu.server.biz.account.vo.CancellationPreviewVO;
import com.zhimu.server.biz.common.api.ChangeRequestService;
import com.zhimu.server.biz.farm.api.CancelExitFarm;
import com.zhimu.server.biz.farm.api.CancelFarmDisposal;
import com.zhimu.server.biz.farm.api.CancelOwnedFarm;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.token.UserTokenService;
import com.zhimu.server.integration.ipregion.IpRegionResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountCancelServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AccountCancelMapper accountCancelMapper = mock(AccountCancelMapper.class);
    private final AccountRecoveryMapper recoveryMapper = mock(AccountRecoveryMapper.class);
    private final AccountStateService accountState = mock(AccountStateService.class);
    private final CancellationRules rules = mock(CancellationRules.class);
    private final SmsCodeService smsCodeService = mock(SmsCodeService.class);
    private final FarmAccountService farmAccountService = mock(FarmAccountService.class);
    private final ChangeRequestService changeRequestService = mock(ChangeRequestService.class);
    private final OperationLogRecorder operationLogRecorder = mock(OperationLogRecorder.class);
    private final UserTokenService userTokenService = mock(UserTokenService.class);
    private final AuthStateCache authStateCache = mock(AuthStateCache.class);
    private final IpRegionResolver ipRegionResolver = mock(IpRegionResolver.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AccountProperties properties = new AccountProperties();
    private AccountCancelServiceImpl service;

    @BeforeEach
    void setUp() {
        FixedTime.set();
        service = new AccountCancelServiceImpl(userMapper, accountCancelMapper, recoveryMapper, accountState, rules,
                smsCodeService, farmAccountService, changeRequestService, operationLogRecorder, userTokenService,
                authStateCache, ipRegionResolver, eventPublisher, properties);
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
        user.setRecoveredAt(LocalDateTime.of(2026, 10, 19, 8, 0));
        return user;
    }

    private static CancellationRules.CancelCheck check(boolean canCancel) {
        List<CancelOwnedFarm> farms = List.of(
                new CancelOwnedFarm(1001L, "东山猪场", "NORMAL", "正常", 870, "头", 3,
                        CancelFarmDisposal.TRANSFER_OR_DISABLE, true, null, null),
                new CancelOwnedFarm(1003L, "北湾牛场", "PENDING", "待审核", null, null, 0,
                        CancelFarmDisposal.DELETE_NOW, false, null, null));
        List<CancelExitFarm> exits = List.of(new CancelExitFarm(1002L, "西岭鸡场", "MANAGER", "场长"));
        return new CancellationRules.CancelCheck(farms, exits,
                canCancel ? List.of() : List.of(CancelBlockReason.RECOVERED_WITHIN_7_DAYS,
                        CancelBlockReason.OWNED_FARM_ACTIVE),
                canCancel ? null : java.time.LocalDate.of(2026, 10, 26),
                canCancel ? null : java.time.LocalDate.of(2027, 4, 17),
                "a1b2c3d4e5f60718", canCancel ? null : "东山猪场", null);
    }

    @Test
    void A19预览返回养殖场处置与摘要() {
        when(accountState.requireUser(10001L)).thenReturn(user());
        when(rules.evaluate(any(User.class))).thenReturn(check(false));
        when(rules.cancelAvailableDateText(any())).thenReturn("2026-10-26");

        CancellationPreviewVO vo = service.preview();

        assertThat(vo.getPhoneMasked()).isEqualTo("138****6620");
        assertThat(vo.getPersonalDataDesc()).isEqualTo("消息、AI 问答、待办设置、恢复码");
        assertThat(vo.getPhoneReleaseDate()).isEqualTo("2026-11-18");
        assertThat(vo.getChecklistVersion()).isEqualTo("a1b2c3d4e5f60718");
        assertThat(vo.getCanCancel()).isFalse();
        assertThat(vo.getBlockReasons()).containsExactly("RECOVERED_WITHIN_7_DAYS", "OWNED_FARM_ACTIVE");
        assertThat(vo.getCancelAvailableDate()).isEqualTo("2026-10-26");
        assertThat(vo.getFarmCleanupDate()).isEqualTo("2027-04-17");
        assertThat(vo.getOwnedFarms()).hasSize(2);
        assertThat(vo.getOwnedFarms().get(0).getDisposalText()).isEqualTo("仍在使用，须先转让或停用");
        assertThat(vo.getTransferableFarmCount()).isEqualTo(1);
        assertThat(vo.getExitFarms()).hasSize(1);
        assertThat(vo.getExitFarms().get(0).getRoleText()).isEqualTo("场长");
    }

    @Test
    void A20阻止原因返回40310且不写注销记录() {
        when(userMapper.selectByIdForUpdate(10001L)).thenReturn(user());
        CancellationRules.CancelCheck check = check(false);
        when(rules.evaluate(any(User.class))).thenReturn(check);
        when(rules.blockedException(check)).thenReturn(new BizException(
                com.zhimu.server.common.error.ErrorCode.CANCEL_NOT_ALLOWED,
                "东山猪场仍在使用，请先转让给场长或停用后再注销")
                .with("block_reasons", List.of("OWNED_FARM_ACTIVE")));

        BizException e = (BizException) catchException(() -> service.cancel(
                new AccountCancelRequest("123456", true, "a1b2c3d4e5f60718"), ClientType.APP_ANDROID));

        assertThat(e.getCode()).isEqualTo(40310);
        assertThat(e.getMessage()).contains("东山猪场");
        verify(accountCancelMapper, never()).insert(any(AccountCancel.class));
        verifyNoInteractions(smsCodeService, farmAccountService, userTokenService, authStateCache);
    }

    @Test
    void A20清单版本不一致返回40902() {
        when(userMapper.selectByIdForUpdate(10001L)).thenReturn(user());
        when(rules.evaluate(any(User.class))).thenReturn(check(true));

        BizException e = (BizException) catchException(() -> service.cancel(
                new AccountCancelRequest("123456", true, "oldversion"), ClientType.APP_ANDROID));

        assertThat(e.getCode()).isEqualTo(40902);
        assertThat(e.getMessage()).isEqualTo("内容已更新，请刷新后再修改");
        verifyNoInteractions(smsCodeService, accountCancelMapper, farmAccountService,
                userTokenService, authStateCache);
    }

    @Test
    void A20勾选未确认返回40001() {
        BizException e = (BizException) catchException(() -> service.cancel(
                new AccountCancelRequest("123456", false, "v"), ClientType.APP_ANDROID));
        assertThat(e.getCode()).isEqualTo(40001);
        assertThat(e.getMessage()).contains("我已了解注销后果");
    }

    @Test
    void A20注销成功写快照_清理关联数据并在提交后作废令牌() {
        when(userMapper.selectByIdForUpdate(10001L)).thenReturn(user());
        when(rules.evaluate(any(User.class))).thenReturn(check(true));
        when(changeRequestService.cancelPendingBySubmitter(10001L, "提交人注销")).thenReturn(2);
        doAnswer(invocation -> {
            AccountCancel record = invocation.getArgument(0);
            record.setId(7001L);
            return 1;
        }).when(accountCancelMapper).insert(any(AccountCancel.class));

        service.cancel(new AccountCancelRequest("123456", true, "a1b2c3d4e5f60718"), ClientType.APP_ANDROID);

        ArgumentCaptor<AccountCancel> captor = ArgumentCaptor.forClass(AccountCancel.class);
        verify(accountCancelMapper).insert(captor.capture());
        AccountCancel record = captor.getValue();
        assertThat(record.getPhoneMasked()).isEqualTo("138****6620");
        assertThat(record.getPhoneHash()).hasSize(64);
        assertThat(record.getRetainedFarmsJson()).isEqualTo("[]");
        assertThat(record.getDeletedFarmsJson()).contains("北湾牛场", "1003");
        assertThat(record.getExitedFarmsJson()).contains("西岭鸡场", "MANAGER");
        assertThat(record.getWithdrawnJson()).contains("change_request", "2");
        assertThat(record.getPhoneReleaseAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 30));
        assertThat(record.getCleanupStatus()).isEqualTo("PENDING");
        verify(smsCodeService).verifyAndConsume(com.zhimu.server.biz.account.enums.SmsScene.CANCEL_ACCOUNT,
                "13800006620", "123456", "sms_code");
        verify(accountState).revokeAllCodes(eq(10001L),
                eq(com.zhimu.server.biz.account.enums.RecoveryCodeRevokeReason.ACCOUNT_CANCELLED));
        verify(recoveryMapper).update(eq(null), any());
        verify(farmAccountService).handleAccountCancelled(10001L, "13800006620");
        verify(operationLogRecorder).record(any());
        verify(userTokenService).revokeAll(10001L);
        verify(authStateCache).evict(10001L);
    }

    @Test
    void invalidSmsCannotCancelAccountOrRemoveMemberships() {
        when(userMapper.selectByIdForUpdate(10001L)).thenReturn(user());
        when(rules.evaluate(any(User.class))).thenReturn(check(true));
        doThrow(new BizException(ErrorCode.SMS_CODE_WRONG, "验证码不正确"))
                .when(smsCodeService).verifyAndConsume(SmsScene.CANCEL_ACCOUNT,
                        "13800006620", "654321", "sms_code");

        BizException error = (BizException) catchException(() -> service.cancel(
                new AccountCancelRequest("654321", true, "a1b2c3d4e5f60718"),
                ClientType.APP_ANDROID));

        assertThat(error.getCode()).isEqualTo(ErrorCode.SMS_CODE_WRONG.getCode());
        verify(userMapper, never()).update(any(), any());
        verifyNoInteractions(accountCancelMapper, recoveryMapper, changeRequestService,
                farmAccountService, operationLogRecorder, userTokenService, authStateCache,
                eventPublisher);
    }

    @Test
    void cancellationRevokesSessionsAndPublishesCleanupEventOnlyAfterCommit() {
        when(userMapper.selectByIdForUpdate(10001L)).thenReturn(user());
        when(rules.evaluate(any(User.class))).thenReturn(check(true));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.cancel(new AccountCancelRequest("123456", true, "a1b2c3d4e5f60718"),
                    ClientType.APP_ANDROID);

            verify(farmAccountService).handleAccountCancelled(10001L, "13800006620");
            verifyNoInteractions(userTokenService, authStateCache, eventPublisher);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(userTokenService).revokeAll(10001L);
            verify(authStateCache).evict(10001L);
            verify(eventPublisher).publishEvent(new AccountCancelledEvent(10001L, "13800006620"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
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
