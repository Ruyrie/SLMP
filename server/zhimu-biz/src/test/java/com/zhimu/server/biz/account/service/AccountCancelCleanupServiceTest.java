package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.AccountCancel;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.mapper.AccountCancelMapper;
import com.zhimu.server.biz.account.mapper.AccountPersonalCleanupMapper;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.support.Secrets;
import com.zhimu.server.biz.common.api.AccountFileCleanupService;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.biz.today.entity.PushDevice;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.integration.push.PushClient;
import com.zhimu.server.integration.push.PushResult;
import com.zhimu.server.integration.push.PushStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 纯内存 mock，不连接数据库/Redis/SDK；事务提交后才保留清理进度快照。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountCancelCleanupServiceTest {
    private static final long USER_ID = 909901L;
    private static final long MESSAGE_ID = 909902L;
    private static final String PHONE = "19900009991";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 12, 0);
    @Mock private AccountCancelMapper cancelMapper;
    @Mock private UserMapper userMapper;
    @Mock private AccountPersonalCleanupMapper personalMapper;
    @Mock private BizMediaService media;
    @Mock private AccountFileCleanupService files;
    @Mock private FarmAccountService farms;
    @Mock private PushClient push;
    @Mock private PlatformTransactionManager transactions;
    private final AtomicReference<AccountCancel> committed = new AtomicReference<>();
    private final AtomicReference<AccountCancel> pending = new AtomicReference<>();
    private User user;
    private AccountCancelCleanupService service;

    @BeforeEach
    void setUp() {
        clock(NOW);
        user = new User();
        user.setId(USER_ID);
        user.setStatus("CANCELLED");
        user.setPhone(PHONE);
        user.setCancelledAt(NOW);
        AccountCancel row = new AccountCancel();
        row.setId(909951L);
        row.setUserId(USER_ID);
        row.setCreatedAt(NOW);
        row.setPhoneHash(Secrets.sha256Hex("zhimu-account-cancel:" + PHONE));
        row.setPhoneReleaseAt(NOW.plusDays(30));
        row.setNextRunAt(NOW);
        row.setCleanupStatus("PENDING");
        row.setRetryCount(0);
        committed.set(row);
        when(userMapper.selectByIdForUpdate(USER_ID)).thenReturn(user);
        when(cancelMapper.selectByUserForUpdate(USER_ID)).thenAnswer(ignored -> copy(committed.get()));
        when(cancelMapper.updateCleanupProgress(any(), any())).thenAnswer(call -> {
            pending.set(copy(call.getArgument(0)));
            return 1;
        });
        when(transactions.getTransaction(any())).thenAnswer(call -> {
            TransactionDefinition definition = call.getArgument(0);
            assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            pending.set(null);
            return new SimpleTransactionStatus();
        });
        doAnswer(ignored -> {
            if (pending.get() != null) {
                committed.set(pending.getAndSet(null));
            }
            return null;
        }).when(transactions).commit(any());
        doAnswer(ignored -> { pending.set(null); return null; }).when(transactions).rollback(any());
        when(personalMapper.lockDevices(USER_ID)).thenReturn(List.of());
        when(personalMapper.selectAiMessageIds(USER_ID)).thenReturn(List.of(MESSAGE_ID));
        when(personalMapper.unbindDevice(eq(USER_ID), anyLong(), any())).thenReturn(1);
        when(personalMapper.releasePhone(eq(USER_ID), any(), eq(PHONE), any())).thenReturn(1);
        service = new AccountCancelCleanupService(cancelMapper, userMapper, personalMapper, media,
                files, farms, push, new AccountProperties(), transactions);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        assertThat(TenantSkip.isSkipped()).isFalse();
    }

    @Test
    void clearsOnlyCancelledUsersPersonalDataAndWaitsForPhoneDeadline() {
        service.cleanup(USER_ID);

        verify(personalMapper).deleteMessages(USER_ID);
        verify(personalMapper).deletePushRecords(USER_ID);
        verify(personalMapper).deleteReminderSettings(USER_ID);
        verify(personalMapper).deleteCoursePlayRecords(USER_ID);
        verify(personalMapper).deleteCourseProgress(USER_ID);
        verify(personalMapper).redactRecoveryCodes(USER_ID, NOW);
        verify(personalMapper).clearPersonalProfileLinks(USER_ID, NOW);
        var order = inOrder(media, personalMapper, files);
        order.verify(media).removeAll(BizType.AI_MESSAGE, MESSAGE_ID);
        order.verify(personalMapper).deleteAiMediaLinks(USER_ID);
        order.verify(personalMapper).deleteAiCitations(USER_ID);
        order.verify(personalMapper).deleteAiCorrections(USER_ID);
        order.verify(personalMapper).redactAiReviewSnapshots(USER_ID);
        order.verify(personalMapper).detachKnowledgeTaskSources(USER_ID);
        order.verify(personalMapper).deleteAiMessages(USER_ID);
        order.verify(personalMapper).deleteAiSessions(USER_ID);
        order.verify(files).queuePersonalFiles(USER_ID);
        verify(personalMapper, never()).releasePhone(anyLong(), any(), any(), any());
        assertThat(committed.get().getPersonalCleanedAt()).isEqualTo(NOW);
        assertThat(committed.get().getFarmCleanedAt()).isEqualTo(NOW);
        assertThat(committed.get().getPhoneReleasedAt()).isNull();
        assertThat(committed.get().getCleanupStatus()).isEqualTo("WAITING");
        assertThat(committed.get().getNextRunAt()).isEqualTo(NOW.plusDays(30));

        service.cleanup(USER_ID); // 未到期，不能再次执行删除或再次解绑。
        verify(personalMapper, times(1)).deleteMessages(USER_ID);
        verify(files, times(1)).queuePersonalFiles(USER_ID);
    }

    @Test
    void freshPendingRunsImmediatelyWhenDatabaseRoundedNextRunSlightlyAhead() {
        committed.get().setNextRunAt(NOW.plusNanos(800_000_000));

        service.cleanup(USER_ID);

        verify(personalMapper).deleteMessages(USER_ID);
        verify(files).queuePersonalFiles(USER_ID);
        assertThat(committed.get().getPersonalCleanedAt()).isEqualTo(NOW);
        assertThat(committed.get().getCleanupStatus()).isEqualTo("WAITING");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"FAILED", "WAITING"})
    void futureRetryOrWaitingDeadlineIsStillRespected(String cleanupStatus) {
        committed.get().setCleanupStatus(cleanupStatus);
        committed.get().setNextRunAt(NOW.plusNanos(800_000_000));

        service.cleanup(USER_ID);

        verifyNoInteractions(personalMapper, media, files, farms, push);
        assertThat(committed.get().getPersonalCleanedAt()).isNull();
        assertThat(committed.get().getCleanupStatus()).isEqualTo(cleanupStatus);
        verify(cancelMapper, never()).updateCleanupProgress(any(), any());
    }

    @Test
    void activeAccountWithStalePendingCleanupNeverLosesAnyData() {
        user.setStatus("NORMAL");

        service.cleanup(USER_ID);

        verifyNoInteractions(personalMapper, media, files, farms, push);
        assertThat(committed.get().getPersonalCleanedAt()).isNull();
        assertThat(committed.get().getCleanupStatus()).isEqualTo("FAILED");
        assertThat(committed.get().getNextRunAt()).isAfter(NOW);
        verify(transactions).rollback(any());
    }

    @Test
    void mismatchedCancellationTimestampIsQuarantinedWithoutDeletingData() {
        user.setCancelledAt(NOW.minusDays(2));
        service.cleanup(USER_ID);
        verifyNoInteractions(personalMapper, media, files, farms, push);
        assertThat(committed.get().getCleanupStatus()).isEqualTo("FAILED");
    }

    @Test
    void failedPersonalTransactionDoesNotMarkStageCompleteAndIsRetried() {
        doThrow(new IllegalStateException("do not persist private text"))
                .when(media).removeAll(BizType.AI_MESSAGE, MESSAGE_ID);

        service.cleanup(USER_ID);

        verify(transactions).rollback(any());
        assertThat(committed.get().getPersonalCleanedAt()).isNull();
        assertThat(committed.get().getRetryCount()).isEqualTo(1);
        assertThat(committed.get().getNextRunAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(committed.get().getLastError()).isEqualTo("PERSONAL:IllegalStateException");
        verify(personalMapper, never()).deleteAiMessages(USER_ID);
        verifyNoInteractions(files, farms);
    }

    @Test
    void pushUnbindFailureRetriesBeforeDeclaringPersonalDataCleaned() {
        PushDevice device = new PushDevice();
        device.setId(909903L);
        device.setUserId(USER_ID);
        device.setRegistrationId("isolated-cleanup-device");
        device.setAliasBound(true);
        when(personalMapper.lockDevices(USER_ID)).thenReturn(List.of(device));
        when(push.isEnabled()).thenReturn(true);
        when(push.unbindAlias("isolated-cleanup-device"))
                .thenReturn(new PushResult(PushStatus.FAILED, List.of(), 500, "private upstream error"))
                .thenReturn(PushResult.sent(null));

        service.cleanup(USER_ID);
        assertThat(committed.get().getPersonalCleanedAt()).isNull();
        assertThat(committed.get().getCleanupStatus()).isEqualTo("FAILED");
        verify(personalMapper, never()).deleteMessages(USER_ID);

        clock(NOW.plusMinutes(2));
        service.cleanup(USER_ID);
        verify(push, times(2)).unbindAlias("isolated-cleanup-device");
        verify(personalMapper).unbindDevice(USER_ID, device.getId(), NOW.plusMinutes(2));
        assertThat(committed.get().getPersonalCleanedAt()).isEqualTo(NOW.plusMinutes(2));
        assertThat(committed.get().getLastError()).isNull();
        assertThat(committed.get().getRetryCount()).isZero();
    }

    @Test
    void missingPushConfigurationCannotPretendPreviouslyBoundDeviceWasUnbound() {
        PushDevice device = new PushDevice();
        device.setId(909903L);
        device.setUserId(USER_ID);
        device.setRegistrationId("isolated-cleanup-device");
        device.setAliasBound(true);
        when(personalMapper.lockDevices(USER_ID)).thenReturn(List.of(device));
        when(push.isEnabled()).thenReturn(false);

        service.cleanup(USER_ID);

        assertThat(committed.get().getCleanupStatus()).isEqualTo("FAILED");
        assertThat(committed.get().getPersonalCleanedAt()).isNull();
        verify(personalMapper, never()).unbindDevice(anyLong(), anyLong(), any());
    }

    @Test
    void fileDeletionRetryPreservesCompletedPersonalStageAndDoesNotPretendDone() {
        AtomicBoolean hasPending = new AtomicBoolean(true);
        when(files.hasPending(USER_ID)).thenAnswer(ignored -> hasPending.get());
        doThrow(new IllegalStateException("storage unavailable"))
                .doAnswer(ignored -> { hasPending.set(false); return null; })
                .when(files).purgeQueued(USER_ID);

        service.cleanup(USER_ID);
        assertThat(committed.get().getPersonalCleanedAt()).isEqualTo(NOW);
        assertThat(committed.get().getCleanupStatus()).isEqualTo("FAILED");
        assertThat(committed.get().getLastError()).isEqualTo("FILES:IllegalStateException");

        clock(NOW.plusMinutes(2));
        service.cleanup(USER_ID);
        verify(personalMapper, times(1)).deleteMessages(USER_ID);
        verify(files, times(1)).queuePersonalFiles(USER_ID);
        assertThat(committed.get().getCleanupStatus()).isEqualTo("WAITING");
        assertThat(committed.get().getNextRunAt()).isEqualTo(NOW.plusDays(30));
    }

    @Test
    void releasesOnlyOriginalCancelledUserPhoneAfterThirtyDaysThenCompletes() {
        makeOldCancellation();
        service.cleanup(USER_ID);

        verify(personalMapper).releasePhone(USER_ID, user.getCancelledAt(), PHONE, NOW);
        assertThat(committed.get().getPhoneReleasedAt()).isEqualTo(NOW);
        assertThat(committed.get().getCleanupStatus()).isEqualTo("DONE");
        assertThat(committed.get().getNextRunAt()).isNull();
        service.cleanup(USER_ID);
        verify(personalMapper, times(1)).releasePhone(anyLong(), any(), any(), any());
    }

    @Test
    void phoneHashMismatchNeverReleasesAChangedPhone() {
        makeOldCancellation();
        user.setPhone("19900009992");
        service.cleanup(USER_ID);

        verify(personalMapper, never()).releasePhone(anyLong(), any(), any(), any());
        assertThat(committed.get().getPhoneReleasedAt()).isNull();
        assertThat(committed.get().getCleanupStatus()).isEqualTo("FAILED");
        assertThat(committed.get().getPersonalCleanedAt()).isNotNull();
    }

    @Test
    void alreadyReleasedPhoneIsIdempotentAndMultipleFarmDeadlinesStayWaiting() {
        makeOldCancellation();
        user.setPhone(null); // 注册时已惰性释放旧号码；不修改新用户记录。
        committed.get().setFarmCleanedAt(null);
        when(farms.cleanupCancelledOwnerFarms(USER_ID, NOW)).thenReturn(NOW.plusDays(60));

        service.cleanup(USER_ID);

        verify(personalMapper, never()).releasePhone(anyLong(), any(), any(), any());
        assertThat(committed.get().getPhoneReleasedAt()).isEqualTo(NOW);
        assertThat(committed.get().getFarmCleanedAt()).isNull();
        assertThat(committed.get().getFarmCleanupAt()).isEqualTo(NOW.plusDays(60));
        assertThat(committed.get().getNextRunAt()).isEqualTo(NOW.plusDays(60));
        assertThat(committed.get().getCleanupStatus()).isEqualTo("WAITING");
    }

    private void makeOldCancellation() {
        LocalDateTime cancelledAt = NOW.minusDays(31);
        user.setCancelledAt(cancelledAt);
        committed.get().setCreatedAt(cancelledAt);
        committed.get().setPhoneReleaseAt(cancelledAt.plusDays(30));
        committed.get().setPersonalCleanedAt(cancelledAt);
        committed.get().setFarmCleanedAt(cancelledAt);
    }

    private static AccountCancel copy(AccountCancel value) {
        AccountCancel result = new AccountCancel();
        BeanUtils.copyProperties(value, result);
        return result;
    }

    private static void clock(LocalDateTime time) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        BeijingTime.setClock(Clock.fixed(time.atZone(zone).toInstant(), zone));
    }
}
