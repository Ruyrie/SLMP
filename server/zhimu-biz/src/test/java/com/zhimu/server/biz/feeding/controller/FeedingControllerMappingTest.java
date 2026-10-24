package com.zhimu.server.biz.feeding.controller;

import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.biz.feeding.controller.FeedExecutionController;
import com.zhimu.server.biz.feeding.controller.FeedPlanController;
import com.zhimu.server.biz.feeding.controller.FeedRecordController;
import com.zhimu.server.biz.feeding.controller.FeedTaskController;
import com.zhimu.server.biz.feeding.controller.WeighController;
import com.zhimu.server.biz.feeding.service.FeedExecutionService;
import com.zhimu.server.biz.feeding.service.FeedPlanService;
import com.zhimu.server.biz.feeding.service.FeedRecordService;
import com.zhimu.server.biz.feeding.service.FeedTaskService;
import com.zhimu.server.biz.feeding.service.WeighService;
import com.zhimu.server.biz.feeding.support.FeedErrors;
import com.zhimu.server.biz.feeding.vo.FeedExecVO;
import com.zhimu.server.biz.feeding.vo.FeedPlanVO;
import com.zhimu.server.biz.feeding.vo.FeedRecordVO;
import com.zhimu.server.biz.feeding.vo.FeedTaskVO;
import com.zhimu.server.biz.feeding.vo.WeighVO;
import com.zhimu.server.common.api.PageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 24 个映射的 standalone MockMvc 测试：路径、方法、请求体与关键响应字段；含 40304。 */
@ExtendWith(MockitoExtension.class)
class FeedingControllerMappingTest {

    @Mock
    private FeedTaskService taskService;
    @Mock
    private FeedRecordService recordService;
    @Mock
    private FeedPlanService planService;
    @Mock
    private FeedExecutionService executionService;
    @Mock
    private WeighService weighService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestMvc.builder(new FeedTaskController(taskService), new FeedRecordController(recordService),
                new FeedPlanController(planService), new FeedExecutionController(executionService),
                new WeighController(weighService)).build();
    }

    // G01
    @Test
    void todaySummary() throws Exception {
        FeedTaskVO.TodaySummary summary = new FeedTaskVO.TodaySummary();
        summary.setDate("2026-10-19");
        summary.setTaskTotal(9);
        summary.setDoneCount(2);
        when(taskService.todaySummary()).thenReturn(summary);
        mvc.perform(get("/api/v1/feed-tasks/today/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.date").value("2026-10-19"))
                .andExpect(jsonPath("$.data.task_total").value(9))
                .andExpect(jsonPath("$.data.done_count").value(2));
    }

    // G02
    @Test
    void todayTasks() throws Exception {
        FeedTaskVO.Item item = new FeedTaskVO.Item();
        item.setTaskId(70107L);
        item.setStatus("PENDING");
        when(taskService.today(any())).thenReturn(new PageVO<>(List.of(item), 1, 1, 20));
        mvc.perform(get("/api/v1/feed-tasks/today").param("status", "PENDING").param("pen_id", "2003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].task_id").value(70107))
                .andExpect(jsonPath("$.data.list[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.has_more").value(false));
    }

    // G03
    @Test
    void taskForm() throws Exception {
        FeedTaskVO.Form form = new FeedTaskVO.Form();
        form.setTaskId(70107L);
        form.setCanRegister(false);
        form.setBlockReason("DONE");
        when(taskService.form(70107L)).thenReturn(form);
        mvc.perform(get("/api/v1/feed-tasks/70107"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task_id").value(70107))
                .andExpect(jsonPath("$.data.can_register").value(false))
                .andExpect(jsonPath("$.data.block_reason").value("DONE"));
    }

    // 今日任务补生成
    @Test
    void generateTodayTasks() throws Exception {
        FeedTaskVO.GenerateToday result = new FeedTaskVO.GenerateToday();
        result.setGenerated(4);
        result.setSkipped(2);
        when(taskService.generateToday()).thenReturn(result);
        mvc.perform(post("/api/v1/feed-tasks/generate-today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.generated").value(4))
                .andExpect(jsonPath("$.data.skipped").value(2));
    }

    // G04
    @Test
    void createRecord() throws Exception {
        FeedRecordVO.Detail detail = new FeedRecordVO.Detail();
        detail.setRecordId(7106L);
        detail.setIsProxy(false);
        detail.setTodoCompleted(true);
        when(recordService.create(any(), eq("idem-1"))).thenReturn(detail);
        mvc.perform(post("/api/v1/feed-records")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task_id":70108,"feed_id":4001,"actual_qty":158.0,"remark":"ok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_id").value(7106))
                .andExpect(jsonPath("$.data.todo_completed").value(true))
                .andExpect(jsonPath("$.message").value("已登记投喂，待办已自动完成"));
    }

    // G05
    @Test
    void recordDetail() throws Exception {
        FeedRecordVO.Detail detail = new FeedRecordVO.Detail();
        detail.setRecordId(7106L);
        detail.setViewMode("EDIT");
        detail.setReadonlyReason(null);
        when(recordService.detail(7106L)).thenReturn(detail);
        mvc.perform(get("/api/v1/feed-records/7106"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_id").value(7106))
                .andExpect(jsonPath("$.data.view_mode").value("EDIT"));
    }

    // G06
    @Test
    void updateRecord() throws Exception {
        FeedRecordVO.Detail detail = new FeedRecordVO.Detail();
        detail.setRecordId(7106L);
        detail.setActualQty(new BigDecimal("150.00"));
        when(recordService.update(eq(7106L), any())).thenReturn(detail);
        mvc.perform(put("/api/v1/feed-records/7106")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feed_id":4001,"actual_qty":150.0,"version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_id").value(7106))
                .andExpect(jsonPath("$.data.actual_qty").value(150.0))
                .andExpect(jsonPath("$.message").value("已修改投喂记录，饲料出库已同步更正"));
    }

    // G07
    @Test
    void voidRecord() throws Exception {
        FeedRecordVO.Detail detail = new FeedRecordVO.Detail();
        detail.setRecordId(7106L);
        detail.setStatus("VOID");
        when(recordService.voidRecord(eq(7106L), any())).thenReturn(detail);
        mvc.perform(post("/api/v1/feed-records/7106/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOID"))
                .andExpect(jsonPath("$.message").value("已作废投喂记录，饲料出库已同步作废"));
    }

    // G08
    @Test
    void lookupRecord() throws Exception {
        FeedRecordVO.Lookup lookup = new FeedRecordVO.Lookup();
        lookup.setSaved(true);
        when(recordService.lookup("idem-1")).thenReturn(lookup);
        mvc.perform(get("/api/v1/feed-records/lookup").param("request_id", "idem-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(true));
    }

    // G09
    @Test
    void planBatchOptions() throws Exception {
        FeedPlanVO.BatchOption option = new FeedPlanVO.BatchOption();
        option.setBatchId(3001L);
        option.setHasPlan(true);
        when(planService.batchOptions()).thenReturn(List.of(option));
        mvc.perform(get("/api/v1/feed-plans/batch-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].batch_id").value(3001))
                .andExpect(jsonPath("$.data[0].has_plan").value(true));
    }

    // G10
    @Test
    void getPlan() throws Exception {
        FeedPlanVO.Plan plan = new FeedPlanVO.Plan();
        plan.setPlanId(501L);
        plan.setHasPlan(true);
        when(planService.getPlan(3001L)).thenReturn(plan);
        mvc.perform(get("/api/v1/batches/3001/feed-plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan_id").value(501))
                .andExpect(jsonPath("$.data.has_plan").value(true));
    }

    // G11
    @Test
    void getStandard() throws Exception {
        FeedPlanVO.StdApply apply = new FeedPlanVO.StdApply();
        apply.setStdAvailable(true);
        apply.setStdVersionId(201L);
        when(planService.getStandard(3001L)).thenReturn(apply);
        mvc.perform(get("/api/v1/batches/3001/feed-plan/standard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.std_available").value(true))
                .andExpect(jsonPath("$.data.std_version_id").value(201));
    }

    // G12
    @Test
    void savePlan() throws Exception {
        FeedPlanVO.Plan plan = new FeedPlanVO.Plan();
        plan.setPlanId(501L);
        when(planService.save(eq(3001L), any())).thenReturn(plan);
        mvc.perform(put("/api/v1/batches/3001/feed-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"abnormal_threshold":0.1,"version":3,
                                 "stages":[{"stage_name":"育肥前期","day_age_start":61,"day_age_end":100,
                                 "feed_id":4001,"daily_qty_per_head":2.4,"meals_per_day":3,
                                 "meal_times":["07:00","11:30","17:00"]}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan_id").value(501))
                .andExpect(jsonPath("$.message").value("已保存饲喂方案"));
    }

    // G14
    @Test
    void execSummary() throws Exception {
        FeedExecVO.Summary summary = new FeedExecVO.Summary();
        summary.setExecRate(new BigDecimal("0.9524"));
        summary.setPlannedMeals(21);
        when(executionService.summary(any())).thenReturn(summary);
        mvc.perform(get("/api/v1/feed-executions/summary").param("batch_id", "3001").param("range", "LAST_7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exec_rate").value(0.9524))
                .andExpect(jsonPath("$.data.planned_meals").value(21));
    }

    // G15
    @Test
    void execAttention() throws Exception {
        FeedExecVO.AttentionItem item = new FeedExecVO.AttentionItem();
        item.setType("ABNORMAL");
        when(executionService.attention(any(), eq(1), eq(20)))
                .thenReturn(new PageVO<>(List.of(item), 1, 1, 20));
        mvc.perform(get("/api/v1/feed-executions/attention")
                        .param("batch_id", "3001").param("range", "LAST_7").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].type").value("ABNORMAL"));
    }

    // G16
    @Test
    void execDay() throws Exception {
        FeedExecVO.Day day = new FeedExecVO.Day();
        day.setDate("2026-10-18");
        when(executionService.day(any())).thenReturn(day);
        mvc.perform(get("/api/v1/feed-executions/daily")
                        .param("batch_id", "3001").param("range", "LAST_7").param("date", "2026-10-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-10-18"));
    }

    // G18
    @Test
    void weighBatchOptions() throws Exception {
        WeighVO.BatchOption option = new WeighVO.BatchOption();
        option.setBatchId(3001L);
        option.setGrowthStatus("SLOW");
        when(weighService.batchOptions(false)).thenReturn(List.of(option));
        mvc.perform(get("/api/v1/weigh-records/batch-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].batch_id").value(3001))
                .andExpect(jsonPath("$.data[0].growth_status").value("SLOW"));
    }

    // G19
    @Test
    void weighForm() throws Exception {
        WeighVO.Form form = new WeighVO.Form();
        form.setWeighDate("2026-10-19");
        form.setSuggestedSample(30);
        when(weighService.form(3001L, "2026-10-19")).thenReturn(form);
        mvc.perform(get("/api/v1/batches/3001/weigh-form").param("date", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weigh_date").value("2026-10-19"))
                .andExpect(jsonPath("$.data.suggested_sample").value(30));
    }

    // G20
    @Test
    void createWeigh() throws Exception {
        WeighVO.Record record = new WeighVO.Record();
        record.setId(3301L);
        record.setGrowthChange("SET_SLOW");
        record.setNextWeighDate("2026-11-02");
        when(weighService.create(eq(3001L), any(), eq("idem-2"))).thenReturn(record);
        mvc.perform(post("/api/v1/batches/3001/weigh-records")
                        .header("Idempotency-Key", "idem-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weigh_date":"2026-10-19","mode":"SAMPLE","sample_count":20,"total_weight":1200.0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3301))
                .andExpect(jsonPath("$.data.growth_change").value("SET_SLOW"))
                .andExpect(jsonPath("$.data.next_weigh_date").value("2026-11-02"))
                .andExpect(jsonPath("$.message").value("已登记称重，批次已标为生长偏慢"));
    }

    // G21
    @Test
    void weighDetail() throws Exception {
        WeighVO.Record record = new WeighVO.Record();
        record.setId(3301L);
        record.setAvgWeight(new BigDecimal("60.00"));
        when(weighService.detail(3301L)).thenReturn(record);
        mvc.perform(get("/api/v1/weigh-records/3301"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3301))
                .andExpect(jsonPath("$.data.avg_weight").value(60.0));
    }

    // G22
    @Test
    void updateWeigh() throws Exception {
        WeighVO.Record record = new WeighVO.Record();
        record.setId(3301L);
        record.setVersion(1);
        when(weighService.update(eq(3301L), any())).thenReturn(record);
        mvc.perform(put("/api/v1/weigh-records/3301")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"SAMPLE","sample_count":20,"total_weight":1180.0,"version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3301))
                .andExpect(jsonPath("$.message").value("已修改称重记录，生长曲线已重新计算"));
    }

    // G23
    @Test
    void updateCycle() throws Exception {
        WeighVO.Cycle cycle = new WeighVO.Cycle();
        cycle.setPerMonth(3);
        cycle.setIntervalDays(9);
        when(weighService.updateCycle(eq(3001L), any())).thenReturn(cycle);
        mvc.perform(put("/api/v1/batches/3001/weigh-cycle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"per_month\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.per_month").value(3))
                .andExpect(jsonPath("$.data.interval_days").value(9))
                .andExpect(jsonPath("$.message").value("已修改称重周期，下次称重日期已重新推算"));
    }

    // G24
    @Test
    void growthCurve() throws Exception {
        WeighVO.Curve curve = new WeighVO.Curve();
        curve.setXMin(78);
        curve.setXMax(111);
        when(weighService.curve(3001L, "3002")).thenReturn(curve);
        mvc.perform(get("/api/v1/batches/3001/growth-curve").param("compare_batch_ids", "3002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.x_min").value(78))
                .andExpect(jsonPath("$.data.x_max").value(111));
    }

    // G25
    @Test
    void weighRecords() throws Exception {
        WeighVO.RecordItem item = new WeighVO.RecordItem();
        item.setId(3301L);
        item.setAction("EDIT");
        when(weighService.records(3001L, 1, 20)).thenReturn(new PageVO<>(List.of(item), 1, 1, 20));
        mvc.perform(get("/api/v1/batches/3001/weigh-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(3301))
                .andExpect(jsonPath("$.data.list[0].action").value("EDIT"));
    }

    // G26
    @Test
    void compareOptions() throws Exception {
        WeighVO.CompareOption option = new WeighVO.CompareOption();
        option.setBatchId(3002L);
        option.setStatus("ACTIVE");
        when(weighService.compareOptions(3001L)).thenReturn(List.of(option));
        mvc.perform(get("/api/v1/batches/3001/growth-compare-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].batch_id").value(3002))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    void staffPenForbiddenReturns40304() throws Exception {
        when(taskService.form(70101L)).thenThrow(FeedErrors.penForbidden());
        mvc.perform(get("/api/v1/feed-tasks/70101"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40304))
                .andExpect(jsonPath("$.message").value("你没有这个栏舍的登记权限"));
    }

    @Test
    void notFoundReturns40401() throws Exception {
        when(recordService.detail(9999L)).thenThrow(FeedErrors.notFound());
        mvc.perform(get("/api/v1/feed-records/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }
}
