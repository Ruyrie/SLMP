package com.zhimu.server.biz.feeding.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhimu.server.biz.feeding.FeedingTableInfo;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.zhimu.server.biz.feeding.service.FeedTaskReconciliationServiceImpl.BATCH_ENDED_REASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedTaskReconciliationServiceTest {
    @Mock private FeedTaskMapper mapper;
    @Mock private BatchQueryService batches;
    @Mock private ObjectProvider<TodoSourceWriteService> writerProvider;
    @Mock private TodoSourceWriteService writer;
    private FeedTaskReconciliationServiceImpl service;

    @BeforeEach
    void setup() {
        FeedingTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-27T07:00:00Z"), ZoneId.of("Asia/Shanghai")));
        service = new FeedTaskReconciliationServiceImpl(mapper, batches, writerProvider);
    }

    @AfterEach
    void cleanup() {
        BeijingTime.setClock(null);
    }

    @Test
    void cancelsOnlyUnregisteredTasksOfEndedBatchAndClosesMatchingTodos() {
        FeedTask pending = task(10L, 20L, "PENDING");
        FeedTask overdue = task(11L, 20L, "OVERDUE");
        FeedTask done = task(12L, 20L, "DONE");
        FeedTask registered = task(13L, 20L, "PENDING");
        registered.setRecordId(99L);
        when(mapper.selectList(any())).thenReturn(List.of(pending, overdue, done, registered,
                task(14L, 20L, "MISSED"), task(15L, 20L, "MANUAL_DONE")));
        when(batches.requireBatch(20L)).thenReturn(batch(20L, 1L, "ENDED", 0));
        when(mapper.update(any(FeedTask.class), any(Wrapper.class))).thenReturn(1);
        when(writerProvider.getObject()).thenReturn(writer);

        service.reconcileFarm(1L);

        verify(batches, times(1)).requireBatch(20L);
        verify(writer).ignoreFromSource(1L, "FEEDING", "FEED_TASK:10", BATCH_ENDED_REASON);
        verify(writer).ignoreFromSource(1L, "FEEDING", "FEED_TASK:11", BATCH_ENDED_REASON);
        verifyNoMoreInteractions(writer);
        ArgumentCaptor<LambdaUpdateWrapper<FeedTask>> updates = updateCaptor();
        verify(mapper, times(2)).update(any(FeedTask.class), updates.capture());
        for (var update : updates.getAllValues()) {
            assertThat(update.getSqlSegment()).contains("farm_id", "status", "record_id IS NULL");
            assertThat(update.getSqlSet()).contains("version = version + 1");
            assertThat(update.getParamNameValuePairs().values()).contains("CANCELLED", BATCH_ENDED_REASON, 1L);
        }
    }

    @Test
    void keepsActiveBatchesAndOtherFarmDataUntouched() {
        FeedTask wrongFarmTask = task(13L, 23L, "PENDING");
        wrongFarmTask.setFarmId(2L);
        when(mapper.selectList(any())).thenReturn(List.of(task(10L, 20L, "PENDING"),
                task(11L, 21L, "PENDING"), wrongFarmTask));
        when(batches.requireBatch(20L)).thenReturn(batch(20L, 1L, "ACTIVE", 119));
        when(batches.requireBatch(21L)).thenReturn(batch(21L, 2L, "ENDED", 0));

        service.reconcileFarm(1L);

        verify(mapper, never()).update(any(FeedTask.class), any(Wrapper.class));
        verifyNoInteractions(writerProvider);
        verify(batches, never()).requireBatch(23L);
    }

    @Test
    void clearsOpenTasksWhenCurrentStockIsZero() {
        when(mapper.selectList(any())).thenReturn(List.of(task(10L, 20L, "PENDING")));
        when(batches.requireBatch(20L)).thenReturn(batch(20L, 1L, "ACTIVE", 0));
        when(mapper.update(any(FeedTask.class), any(Wrapper.class))).thenReturn(1);
        when(writerProvider.getObject()).thenReturn(writer);

        service.reconcileFarm(1L);

        verify(writer).ignoreFromSource(1L, "FEEDING", "FEED_TASK:10", BATCH_ENDED_REASON);
    }

    @Test
    void concurrentRegistrationDoesNotCloseTheTodo() {
        when(mapper.selectList(any())).thenReturn(List.of(task(10L, 20L, "PENDING")));
        when(batches.requireBatch(20L)).thenReturn(batch(20L, 1L, "ENDED", 0));
        when(mapper.update(any(FeedTask.class), any(Wrapper.class))).thenReturn(0);

        service.reconcileFarm(1L);

        verifyNoInteractions(writerProvider);
    }

    @Test
    void repeatedReconciliationDoesNotRewriteCancelledTask() {
        FeedTask task = task(10L, 20L, "CANCELLED");
        task.setCloseReason(BATCH_ENDED_REASON);
        when(mapper.selectList(any())).thenReturn(List.of(task));
        when(batches.requireBatch(20L)).thenReturn(batch(20L, 1L, "ENDED", 0));
        when(writerProvider.getObject()).thenReturn(writer);

        service.reconcileFarm(1L);
        service.reconcileFarm(1L);

        verify(mapper, never()).update(any(FeedTask.class), any(Wrapper.class));
        verify(writer, times(2)).ignoreFromSource(1L, "FEEDING", "FEED_TASK:10", BATCH_ENDED_REASON);
    }

    @Test
    void saleReversalRestoresOnlyAutomaticallyCancelledTasksStillWithinBackfillWindow() {
        FeedTask future = task(10L, 20L, "CANCELLED");
        future.setCloseReason(BATCH_ENDED_REASON);
        FeedTask overdue = task(11L, 20L, "CANCELLED");
        overdue.setCloseReason(BATCH_ENDED_REASON);
        overdue.setDeadlineAt(BeijingTime.now().minusHours(3));
        FeedTask expired = task(12L, 20L, "CANCELLED");
        expired.setCloseReason(BATCH_ENDED_REASON);
        expired.setBackfillEndAt(BeijingTime.now());
        FeedTask manual = task(13L, 20L, "CANCELLED");
        manual.setCloseReason("用户取消");
        when(mapper.selectList(any())).thenReturn(List.of(future, overdue, expired, manual));
        when(batches.requireBatch(20L)).thenReturn(batch(20L, 1L, "ACTIVE", 20));
        when(mapper.update(any(FeedTask.class), any(Wrapper.class))).thenReturn(1);
        when(writerProvider.getObject()).thenReturn(writer);

        service.reconcileFarm(1L);

        verify(writer).reopenAutoIgnoredFromSource(1L, "FEEDING", "FEED_TASK:10", BATCH_ENDED_REASON);
        verify(writer).reopenAutoIgnoredFromSource(1L, "FEEDING", "FEED_TASK:11", BATCH_ENDED_REASON);
        verifyNoMoreInteractions(writer);
        ArgumentCaptor<LambdaUpdateWrapper<FeedTask>> updates = updateCaptor();
        verify(mapper, times(2)).update(any(FeedTask.class), updates.capture());
        assertThat(updates.getAllValues().get(0).getParamNameValuePairs().values()).contains("PENDING");
        assertThat(updates.getAllValues().get(1).getParamNameValuePairs().values()).contains("OVERDUE");
    }

    @Test
    void missingBatchIsSkippedButDatabaseErrorsArePropagated() {
        when(mapper.selectList(any())).thenReturn(List.of(task(10L, 20L, "PENDING")));
        when(batches.requireBatch(20L)).thenThrow(new BizException(ErrorCode.NOT_FOUND));
        service.reconcileFarm(1L);
        doThrow(new IllegalStateException("database unavailable")).when(batches).requireBatch(20L);

        assertThatThrownBy(() -> service.reconcileFarm(1L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(writerProvider);
    }

    @Test
    void guardsEmptyFarmAndConstrainsQueryToTheRequestedFarm() {
        service.reconcileFarm(null);
        verifyNoInteractions(mapper, batches, writerProvider);
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<FeedTask> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("farm_id", "record_id IS NULL", "close_reason");
            return List.of();
        });

        service.reconcileFarm(1L);

        verifyNoInteractions(batches, writerProvider);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<LambdaUpdateWrapper<FeedTask>> updateCaptor() {
        return ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    }

    private static FeedTask task(long id, long batch, String status) {
        FeedTask task = new FeedTask();
        task.setId(id);
        task.setFarmId(1L);
        task.setBatchId(batch);
        task.setStatus(status);
        task.setTaskDate(BeijingTime.today());
        task.setDeadlineAt(BeijingTime.now().plusHours(3));
        task.setBackfillEndAt(BeijingTime.now().plusDays(2));
        return task;
    }

    private static BatchInfo batch(long id, long farm, String status, int count) {
        return new BatchInfo(farm, id, "B" + id, 1101L, 101L, BeijingTime.today(), count, status,
                count == 0 ? List.of() : List.of(2306L), 90, 119, 0, 0, 2306L);
    }
}
