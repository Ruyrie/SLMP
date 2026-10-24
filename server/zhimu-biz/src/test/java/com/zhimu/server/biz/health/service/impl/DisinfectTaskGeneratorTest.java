package com.zhimu.server.biz.health.service.impl;

import com.zhimu.server.biz.health.HealthTableInfo;
import com.zhimu.server.biz.health.entity.DisinfectPlan;
import com.zhimu.server.biz.health.entity.DisinfectTask;
import com.zhimu.server.biz.health.mapper.DisinfectPlanMapper;
import com.zhimu.server.biz.health.mapper.DisinfectTaskMapper;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DisinfectTaskGeneratorTest {

    private final DisinfectPlanMapper plans = mock(DisinfectPlanMapper.class);
    private final DisinfectTaskMapper tasks = mock(DisinfectTaskMapper.class);
    private final HealthReferenceMapper references = mock(HealthReferenceMapper.class);
    private final DisinfectTaskGenerator generator = new DisinfectTaskGenerator(plans, tasks, references);
    private final LocalDate today = LocalDate.of(2026, 10, 24);

    @BeforeEach
    void setUp() {
        HealthTableInfo.ensure();
    }

    @Test
    void catchesUpAfterDowntimeAndExtendsNextDayWithoutRecreatingCompletedTasks() {
        DisinfectPlan plan = plan();
        plan.setGeneratedUntil(today.minusDays(4));
        plan.setEndDate(today.plusDays(200));
        when(plans.selectList(any())).thenReturn(List.of(plan));
        when(plans.selectForUpdate(9201L)).thenReturn(plan);
        when(references.selectEnabledPlanPenIds(1001L, 9201L)).thenReturn(List.of(2003L));
        List<DisinfectTask> stored = new ArrayList<>();
        DisinfectTask completed = new DisinfectTask();
        completed.setPenId(2003L);
        completed.setTaskDate(today);
        completed.setStatus("DONE");
        completed.setRecordId(9501L);
        stored.add(completed);
        when(tasks.selectList(any())).thenAnswer(invocation -> List.copyOf(stored));
        when(tasks.insert(any(DisinfectTask.class))).thenAnswer(invocation -> {
            stored.add(invocation.getArgument(0));
            return 1;
        });

        assertThat(generator.ensureGenerated(1001L, today)).isEqualTo(89);
        assertThat(generator.ensureGenerated(1001L, today)).isZero();
        assertThat(generator.ensureGenerated(1001L, today.plusDays(1))).isEqualTo(1);

        assertThat(stored).hasSize(91);
        assertThat(completed.getStatus()).isEqualTo("DONE");
        assertThat(completed.getRecordId()).isEqualTo(9501L);
        assertThat(stored.stream().map(DisinfectTask::getTaskDate)).contains(today.plusDays(90));
    }

    @Test
    void followsCycleAndTreatsConcurrentDuplicateAsAlreadyGenerated() {
        DisinfectPlan plan = plan();
        plan.setCycleDays(3);
        when(tasks.selectList(any())).thenReturn(List.of());
        when(tasks.insert(any(DisinfectTask.class))).thenThrow(new DuplicateKeyException("slot exists"))
                .thenReturn(1);

        int count = generator.generate(plan, List.of(2003L), today, today.plusDays(3));

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<DisinfectTask> captured = ArgumentCaptor.forClass(DisinfectTask.class);
        verify(tasks, times(2)).insert(captured.capture());
        assertThat(captured.getAllValues()).extracting(DisinfectTask::getTaskDate)
                .containsExactly(today, today.plusDays(3));
    }

    @Test
    void pausedPlanFoundAfterLockDoesNotGenerate() {
        DisinfectPlan candidate = plan();
        DisinfectPlan paused = plan();
        paused.setStatus("PAUSED");
        when(plans.selectList(any())).thenReturn(List.of(candidate));
        when(plans.selectForUpdate(9201L)).thenReturn(paused);

        assertThat(generator.ensureGenerated(1001L, today)).isZero();

        verifyNoInteractions(tasks);
    }

    private DisinfectPlan plan() {
        DisinfectPlan result = new DisinfectPlan();
        result.setId(9201L);
        result.setFarmId(1001L);
        result.setStatus("ACTIVE");
        result.setStartDate(today);
        result.setEndDate(today.plusDays(6));
        result.setCycleDays(1);
        result.setCatalogId(7301L);
        result.setDisinfectantName("聚维酮碘溶液");
        return result;
    }
}
