package com.zhimu.server.biz.feeding.job;

import com.zhimu.server.biz.feeding.FeedingTableInfo;
import com.zhimu.server.biz.feeding.entity.FeedPlan;
import com.zhimu.server.biz.feeding.entity.FeedPlanMeal;
import com.zhimu.server.biz.feeding.entity.FeedPlanStage;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedPlanMapper;
import com.zhimu.server.biz.feeding.mapper.FeedPlanMealMapper;
import com.zhimu.server.biz.feeding.mapper.FeedPlanStageMapper;
import com.zhimu.server.biz.feeding.mapper.FeedRecordMapper;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.feeding.service.FeedTaskService;
import com.zhimu.server.biz.feeding.service.FeedTodoSource;
import com.zhimu.server.biz.feeding.support.FeedBatchPenRow;
import com.zhimu.server.biz.feeding.support.FeedLookup;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.today.api.TodoSource;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 每日饲喂任务生成：首次生成、重复执行幂等、已存在不覆盖、首存方案只补未来餐次。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedTaskGenerateJobTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate DATE = LocalDate.of(2026, 10, 19);

    @Mock private FeedTaskMapper taskMapper;
    @Mock private FeedPlanMapper planMapper;
    @Mock private FeedPlanStageMapper stageMapper;
    @Mock private FeedPlanMealMapper mealMapper;
    @Mock private FeedRecordMapper recordMapper;
    @Mock private FeedLookup lookup;
    @Mock private FeedTodoSource todoSource;

    private FeedTaskService taskService;
    private FeedTaskGenerateJob job;

    @BeforeAll
    static void initTableInfo() {
        FeedingTableInfo.ensure();
    }

    @BeforeEach
    void setUp() {
        taskService = new FeedTaskService(taskMapper, planMapper, stageMapper, mealMapper, recordMapper, lookup, org.mockito.Mockito.mock(com.zhimu.server.biz.feeding.api.FeedTaskReconciliationService.class));
        job = new FeedTaskGenerateJob(taskService, todoSource);
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-18T16:05:00Z"), ZONE));

        when(lookup.activeFarmIds()).thenReturn(List.of(1001L));
        when(lookup.requireActiveBatches(1001L)).thenReturn(List.of(batch()));
        when(lookup.batchPens(any(BatchInfo.class))).thenReturn(List.of(pen()));
        when(lookup.firstAssignee(1001L, 2003L)).thenReturn(10003L);
        when(planMapper.selectOne(any())).thenReturn(plan());
        when(stageMapper.selectList(any())).thenReturn(List.of(stage()));
        when(mealMapper.selectList(any())).thenReturn(List.of(breakfast(), dinner()));
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(todoSource.generate(any())).thenReturn(List.of(95001L));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void firstRunGeneratesEveryMealForTheDay() {
        int created = job.generateFor(DATE);

        assertThat(created).isEqualTo(2);
        ArgumentCaptor<FeedTask> captor = ArgumentCaptor.forClass(FeedTask.class);
        verify(taskMapper, times(2)).insert(captor.capture());
        List<FeedTask> tasks = captor.getAllValues();
        assertThat(tasks).extracting(FeedTask::getMealNo).containsExactly(1, 2);
        FeedTask breakfast = tasks.get(0);
        assertThat(breakfast.getFarmId()).isEqualTo(1001L);
        assertThat(breakfast.getBatchId()).isEqualTo(3001L);
        assertThat(breakfast.getPenId()).isEqualTo(2003L);
        assertThat(breakfast.getTaskDate()).isEqualTo(DATE);
        assertThat(breakfast.getMealName()).isEqualTo("早餐");
        assertThat(breakfast.getFeedTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(breakfast.getFeedAt()).isEqualTo(LocalDateTime.of(2026, 10, 19, 7, 0));
        assertThat(breakfast.getDeadlineAt()).isEqualTo(LocalDateTime.of(2026, 10, 19, 9, 0));
        assertThat(breakfast.getBackfillEndAt()).isEqualTo(LocalDateTime.of(2026, 10, 21, 0, 0));
        assertThat(breakfast.getPlanQty()).isEqualByComparingTo("310.00");
        assertThat(breakfast.getHeadCount()).isEqualTo(200);
        assertThat(breakfast.getAssigneeId()).isEqualTo(10003L);
        assertThat(breakfast.getStatus()).isEqualTo("PENDING");
        assertThat(breakfast.getVersion()).isZero();

        ArgumentCaptor<TodoSource.TodoSourceContext> context = ArgumentCaptor.forClass(TodoSource.TodoSourceContext.class);
        verify(todoSource).generate(context.capture());
        assertThat(context.getValue().getFarmId()).isEqualTo(1001L);
        assertThat(context.getValue().getDate()).isEqualTo(DATE);
    }

    @Test
    void secondRunDoesNotDuplicateExistingTasks() {
        when(taskMapper.selectCount(any())).thenReturn(1L);

        int created = job.generateFor(DATE);

        assertThat(created).isZero();
        verify(taskMapper, never()).insert(any(FeedTask.class));
        // 任务已存在时仍同步一次待办，保证上线当天/补跑时也能补齐模块03 数据
        verify(todoSource).generate(any());
    }

    @Test
    void existingTaskIsNotOverwritten() {
        when(taskMapper.selectCount(any())).thenReturn(1L);

        job.generateFor(DATE);

        verify(taskMapper, never()).update(any(FeedTask.class), any());
    }

    @Test
    void batchWithoutPlanGeneratesNothing() {
        when(planMapper.selectOne(any())).thenReturn(null);

        int created = job.generateFor(DATE);

        assertThat(created).isZero();
        verify(taskMapper, never()).insert(any(FeedTask.class));
    }

    @Test
    void firstPlanSaveOnlyGeneratesMealsAfterNow() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T04:00:00Z"), ZONE)); // 12:00

        taskService.generateForBatch(batch(), plan(), List.of(stage()));

        ArgumentCaptor<FeedTask> captor = ArgumentCaptor.forClass(FeedTask.class);
        verify(taskMapper).insert(captor.capture());
        assertThat(captor.getValue().getMealNo()).isEqualTo(2);
        assertThat(captor.getValue().getMealName()).isEqualTo("晚餐");
        verify(todoSource, never()).generate(any());
    }

    private static BatchInfo batch() {
        return new BatchInfo(1001L, 3001L, "ZM2610-031", 1101L, 11L,
                LocalDate.of(2026, 7, 15), 200, "ACTIVE", List.of(2003L),
                80, 205, 5, 0, 2003L);
    }

    private static FeedBatchPenRow pen() {
        FeedBatchPenRow pen = new FeedBatchPenRow();
        pen.setPenId(2003L);
        pen.setStockCount(200);
        return pen;
    }

    private static FeedPlan plan() {
        FeedPlan plan = new FeedPlan();
        plan.setId(501L);
        plan.setFarmId(1001L);
        plan.setBatchId(3001L);
        plan.setAbnormalThreshold(new BigDecimal("0.1000"));
        plan.setVersion(0);
        return plan;
    }

    private static FeedPlanStage stage() {
        FeedPlanStage stage = new FeedPlanStage();
        stage.setId(814L);
        stage.setFarmId(1001L);
        stage.setPlanId(501L);
        stage.setBatchId(3001L);
        stage.setSortNo(4);
        stage.setStageName("育肥后期");
        stage.setDayAgeStart(141);
        stage.setDayAgeEnd(null);
        stage.setFeedId(5101L);
        stage.setDailyQtyPerHead(new BigDecimal("3.100"));
        stage.setMealsPerDay(2);
        stage.setDeleted(false);
        return stage;
    }

    private static FeedPlanMeal breakfast() {
        return meal(1, "BREAKFAST", "早餐", LocalTime.of(7, 0));
    }

    private static FeedPlanMeal dinner() {
        return meal(2, "DINNER", "晚餐", LocalTime.of(17, 0));
    }

    private static FeedPlanMeal meal(int no, String code, String name, LocalTime time) {
        FeedPlanMeal meal = new FeedPlanMeal();
        meal.setId(8000L + no);
        meal.setFarmId(1001L);
        meal.setPlanId(501L);
        meal.setStageId(814L);
        meal.setMealNo(no);
        meal.setMealCode(code);
        meal.setMealName(name);
        meal.setFeedTime(time);
        return meal;
    }
}
