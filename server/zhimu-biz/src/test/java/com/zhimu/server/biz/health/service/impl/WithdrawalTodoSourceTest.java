package com.zhimu.server.biz.health.service.impl;

import com.zhimu.server.biz.health.entity.BatchWithdrawal;
import com.zhimu.server.biz.health.mapper.BatchWithdrawalMapper;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawalTodoSourceTest {

    private final BatchWithdrawalMapper withdrawalMapper = mock(BatchWithdrawalMapper.class);
    private final BatchQueryService batchQueryService = mock(BatchQueryService.class);
    private final HealthReferenceMapper referenceMapper = mock(HealthReferenceMapper.class);
    private final TodoSourceWriteService todoWriter = mock(TodoSourceWriteService.class);
    private final WithdrawalTodoSource source =
            new WithdrawalTodoSource(withdrawalMapper, batchQueryService, referenceMapper, todoWriter);

    @Test
    void createsTodoForFutureReleaseAndIgnoresEndedOne() {
        BatchWithdrawal future = new BatchWithdrawal();
        future.setFarmId(1001L);
        future.setBatchId(3001L);
        future.setReleaseDate(LocalDate.now().plusDays(10));
        future.setDecisiveDrugName("阿莫西林可溶性粉");
        BatchWithdrawal ended = new BatchWithdrawal();
        ended.setFarmId(1001L);
        ended.setBatchId(3002L);
        ended.setReleaseDate(LocalDate.now().minusDays(1));
        when(withdrawalMapper.selectList(any())).thenReturn(List.of(future, ended));
        when(batchQueryService.listActive(1001L)).thenReturn(List.of(
                new BatchInfo(1001L, 3001L, "ZM2610-031", 101L, 102L, LocalDate.of(2026, 10, 1),
                        200, "ACTIVE", List.of(2003L), 96, 200, 0, 0, 2003L)));
        when(referenceMapper.selectPenName(2003L)).thenReturn("3 号育肥舍");
        when(todoWriter.upsertFromSource(eq(1001L), any())).thenReturn(List.of(90001L));

        source.generate(TodoSource.TodoSourceContext.builder().farmId(1001L).build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TodoSource.TodoDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(todoWriter).upsertFromSource(eq(1001L), captor.capture());
        TodoSource.TodoDraft draft = captor.getValue().get(0);
        assertThat(draft.getTodoType()).isEqualTo(WithdrawalTodoSource.WITHDRAWAL_END);
        assertThat(draft.getDedupKey()).isEqualTo("WITHDRAWAL:3001");
        assertThat(draft.getDueDate()).isEqualTo(future.getReleaseDate());
        assertThat(draft.getTitle()).contains("休药期结束");
        verify(todoWriter).ignoreFromSource(1001L, WithdrawalTodoSource.WITHDRAWAL_END,
                "WITHDRAWAL:3002", "休药期已结束");
    }

    @Test
    void skipsBatchThatIsNoLongerActive() {
        BatchWithdrawal future = new BatchWithdrawal();
        future.setFarmId(1001L);
        future.setBatchId(3999L);
        future.setReleaseDate(LocalDate.now().plusDays(3));
        when(withdrawalMapper.selectList(any())).thenReturn(List.of(future));
        when(batchQueryService.listActive(anyLong())).thenReturn(List.of());

        source.generate(TodoSource.TodoSourceContext.builder().farmId(1001L).build());

        verify(todoWriter, org.mockito.Mockito.never()).upsertFromSource(anyLong(), any());
    }
}
