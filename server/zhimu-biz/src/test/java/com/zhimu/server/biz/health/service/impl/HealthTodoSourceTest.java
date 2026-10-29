package com.zhimu.server.biz.health.service.impl;

import com.zhimu.server.biz.health.HealthTableInfo;
import com.zhimu.server.biz.health.entity.DisinfectTask;
import com.zhimu.server.biz.health.entity.ImmunePlan;
import com.zhimu.server.biz.health.mapper.DisinfectTaskMapper;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.mapper.ImmunePlanMapper;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealthTodoSourceTest {

    private final ImmunePlanMapper plans = mock(ImmunePlanMapper.class);
    private final DisinfectTaskMapper tasks = mock(DisinfectTaskMapper.class);
    private final HealthReferenceMapper references = mock(HealthReferenceMapper.class);
    private final BatchQueryService batches = mock(BatchQueryService.class);
    private final TodoSourceWriteService writer = mock(TodoSourceWriteService.class);
    private final HealthTodoSource source = new HealthTodoSource(plans, tasks, references, batches, writer);
    private final LocalDate today = LocalDate.of(2026, 10, 24);

    @BeforeEach
    void setUp() {
        HealthTableInfo.ensure();
    }

    @Test
    void registeringImmuneLinksTodoToCurrentPenAndActualRecord() {
        ImmunePlan plan = new ImmunePlan();
        plan.setId(160101L);
        plan.setFarmId(1001L);
        plan.setBatchId(3001L);
        plan.setPlanDate(today);
        plan.setStatus("PENDING");
        plan.setVaccineName("猪瘟活疫苗");
        when(batches.requireBatch(3001L)).thenReturn(new BatchInfo(1001L, 3001L, "ZM2610-031", 1101L,
                101L, today.minusDays(10), 200, "ACTIVE", List.of(2004L), 78, 205, 5, 0, 2003L));
        when(references.selectPenName(2004L)).thenReturn("4 号育肥舍");

        source.completeImmune(plan, 160201L, 10004L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TodoSource.TodoDraft>> drafts = ArgumentCaptor.forClass(List.class);
        verify(writer).upsertFromSource(eq(1001L), drafts.capture());
        TodoSource.TodoDraft draft = drafts.getValue().get(0);
        assertThat(draft.getPenId()).isEqualTo(2004L);
        assertThat(draft.getBizId()).isEqualTo(160101L);
        assertThat(draft.getBatchId()).isEqualTo(3001L);
        assertThat(draft.getDedupKey()).isEqualTo("IMMUNE_PLAN:160101");
        verify(writer).completeFromSource(1001L, "IMMUNIZATION", "IMMUNE_PLAN:160101", "IMMUNE_RECORD", 160201L, 10004L);
    }

    @Test
    void generateFirstMarksPlansMissedSevenDaysAfterPlanDate() {
        // 复测 N3：过了 7 天仍未登记的计划没有任何地方改成漏免；每日任务、启动补齐、计划页查询都会先走这里
        source.generate(TodoSource.TodoSourceContext.builder().farmId(1001L).date(today).build());

        verify(plans).markMissed(eq(1001L), eq(BeijingTime.today().minusDays(7)), any());
    }

    @Test
    void missedPlanTodoCarriesMissedNote() {
        ImmunePlan plan = new ImmunePlan();
        plan.setId(9005L);
        plan.setFarmId(1001L);
        plan.setPenId(2004L);
        plan.setPlanDate(today.minusDays(11));
        plan.setStatus("MISSED");
        plan.setVaccineName("口蹄疫灭活疫苗");
        when(plans.selectList(any())).thenReturn(List.of(plan));

        source.generate(TodoSource.TodoSourceContext.builder().farmId(1001L).date(today).build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TodoSource.TodoDraft>> drafts = ArgumentCaptor.forClass(List.class);
        verify(writer).upsertFromSource(eq(1001L), drafts.capture());
        assertThat(drafts.getValue().get(0).getStatusNote()).isEqualTo("漏免");
    }

    @Test
    void sourceClosesCancelledTasksAndCompletesTasksWithRealRecords() {
        DisinfectTask cancelled = task(1L, "CANCELLED", null);
        DisinfectTask done = task(2L, "DONE", 9501L);
        when(tasks.selectList(any())).thenReturn(List.of(cancelled, done));

        source.generate(TodoSource.TodoSourceContext.builder().farmId(1001L).date(today).build());

        verify(writer).ignoreFromSource(eq(1001L), eq("DISINFECTION"), eq("DISINFECT_TASK:1"), anyString());
        verify(writer).completeFromSource(1001L, "DISINFECTION", "DISINFECT_TASK:2", "DISINFECT_RECORD", 9501L, 0L);
        verify(writer, times(1)).upsertFromSource(eq(1001L), anyList());
    }

    private DisinfectTask task(Long id, String status, Long recordId) {
        DisinfectTask task = new DisinfectTask();
        task.setId(id);
        task.setFarmId(1001L);
        task.setPenId(2003L);
        task.setTaskDate(today);
        task.setStatus(status);
        task.setRecordId(recordId);
        task.setDisinfectantName("聚维酮碘溶液");
        task.setConcentration("1:500");
        return task;
    }
}
