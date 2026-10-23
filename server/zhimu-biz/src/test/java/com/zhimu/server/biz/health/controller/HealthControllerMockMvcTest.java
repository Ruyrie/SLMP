package com.zhimu.server.biz.health.controller;

import com.zhimu.server.biz.health.HealthTestMvc;
import com.zhimu.server.biz.health.service.DisinfectService;
import com.zhimu.server.biz.health.service.HealthRequestService;
import com.zhimu.server.biz.health.service.ImmunePlanService;
import com.zhimu.server.biz.health.service.ImmuneRecordService;
import com.zhimu.server.biz.health.service.MedicationService;
import com.zhimu.server.biz.health.vo.DeleteImpactVO;
import com.zhimu.server.biz.health.vo.DisinfectPlanDetailVO;
import com.zhimu.server.biz.health.vo.DisinfectPlanPreviewVO;
import com.zhimu.server.biz.health.vo.DisinfectPlanVO;
import com.zhimu.server.biz.health.vo.DisinfectRecordVO;
import com.zhimu.server.biz.health.vo.DisinfectTaskOptionVO;
import com.zhimu.server.biz.health.vo.DisinfectTaskPageVO;
import com.zhimu.server.biz.health.vo.DisinfectTaskVO;
import com.zhimu.server.biz.health.vo.DisinfectantOptionVO;
import com.zhimu.server.biz.health.vo.HealthRequestStatusVO;
import com.zhimu.server.biz.health.vo.ImmunePlanOptionVO;
import com.zhimu.server.biz.health.vo.ImmunePlanOverviewVO;
import com.zhimu.server.biz.health.vo.ImmunePlanVO;
import com.zhimu.server.biz.health.vo.ImmuneRecordVO;
import com.zhimu.server.biz.health.vo.ImmuneRegisterFormVO;
import com.zhimu.server.biz.health.vo.MedicationCreateResultVO;
import com.zhimu.server.biz.health.vo.MedicationDrugOptionVO;
import com.zhimu.server.biz.health.vo.MedicationDrugOptionsVO;
import com.zhimu.server.biz.health.vo.MedicationRecordVO;
import com.zhimu.server.biz.health.vo.MedicationTargetVO;
import com.zhimu.server.biz.health.vo.NotifyResultVO;
import com.zhimu.server.biz.health.vo.ReleaseDatePreviewVO;
import com.zhimu.server.biz.health.vo.ToastVO;
import com.zhimu.server.biz.health.vo.VaccineOptionVO;
import com.zhimu.server.biz.health.vo.WithdrawalDrugVO;
import com.zhimu.server.biz.health.vo.WithdrawalVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模块08 全部 40 个接口的映射与响应字段（对照 App routes.json 与 mock 样例）。
 * Service 全部 Mockito mock，只验证路径、方法、请求绑定与 JSON 形状。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HealthControllerMockMvcTest {

    private static final String JSON = "application/json";
    private static final String AUTH = "Bearer test";

    @Mock private ImmunePlanService immunePlanService;
    @Mock private ImmuneRecordService immuneRecordService;
    @Mock private MedicationService medicationService;
    @Mock private DisinfectService disinfectService;
    @Mock private HealthRequestService healthRequestService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = HealthTestMvc.builder(
                new ImmunePlanController(immunePlanService),
                new ImmuneRecordController(immuneRecordService),
                new MedicationController(medicationService),
                new DisinfectController(disinfectService),
                new HealthRequestController(healthRequestService)).build();
        stubImmune();
        stubMedication();
        stubDisinfect();
    }

    // ===== API-D01–D16 免疫 =====

    @Test
    void d01ToD16ImmuneRoutesReturnAppFields() throws Exception {
        mvc.perform(get("/api/v1/batches/3001/immune-plans/overview").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.batch_no").value("ZM2610-031"))
                .andExpect(jsonPath("$.data.pen_name").value("3 号育肥舍"))
                .andExpect(jsonPath("$.data.day_age").value(96))
                .andExpect(jsonPath("$.data.counts.all").value(5))
                .andExpect(jsonPath("$.data.pre_entry_items[0].vaccine_name").value("猪瘟活疫苗"))
                .andExpect(jsonPath("$.data.pre_entry_pending_count").value(1));

        mvc.perform(get("/api/v1/batches/3001/immune-plans").param("status", "ALL")
                        .param("page", "1").param("size", "20").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(8117))
                .andExpect(jsonPath("$.data.list[0].is_today").value(true))
                .andExpect(jsonPath("$.data.list[0].status_text").value("待接种"))
                .andExpect(jsonPath("$.data.list[0].actions[0]").value("REGISTER"))
                .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(post("/api/v1/immune-plans/8101/pre-entry-confirm").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batch_id").value(3001));

        mvc.perform(post("/api/v1/immune-plans/8101/pre-entry-join").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mvc.perform(post("/api/v1/immune-plans/8101/pre-entry-undo").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batch_id").value(3001));

        mvc.perform(post("/api/v1/immune-plans/8117/postpone").header("Authorization", AUTH)
                        .contentType(JSON)
                        .content("{\"new_date\":\"2026-10-22\",\"reason\":\"猪只发热\",\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTPONED"));

        mvc.perform(post("/api/v1/batches/3001/immune-plans").header("Authorization", AUTH)
                        .header("Idempotency-Key", "req-plan-1").contentType(JSON)
                        .content("{\"drug_id\":5001,\"plan_mode\":\"BY_DAY_AGE\",\"plan_age_day\":100,"
                                + "\"dose\":1,\"method_code\":\"IM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8117));

        mvc.perform(put("/api/v1/immune-plans/8117").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"dose\":2,\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dose").value(2));

        mvc.perform(delete("/api/v1/immune-plans/8117").param("version", "2").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/immune-plans/options").param("page", "1").param("size", "20")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].plan_id").value(8117))
                .andExpect(jsonPath("$.data.list[0].plan_date_text").value("今天"));

        mvc.perform(get("/api/v1/immune-plans/8117/register-form").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vaccine_name").value("猪瘟活疫苗"))
                .andExpect(jsonPath("$.data.default_lot.lot_no").value("2025081"))
                .andExpect(jsonPath("$.data.quantity_default").value(200));

        mvc.perform(get("/api/v1/vaccines/options").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("猪瘟活疫苗"))
                .andExpect(jsonPath("$.data[0].no_stock").value(false));

        mvc.perform(post("/api/v1/immune-records").header("Authorization", AUTH)
                        .header("Idempotency-Key", "req-imm-1").contentType(JSON)
                        .content("{\"plan_id\":8117,\"drug_id\":5001,\"drug_lot_id\":6001,\"dose\":1,"
                                + "\"quantity\":200,\"method_code\":\"IM\",\"vaccinate_date\":\"2026-10-19\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8801))
                .andExpect(jsonPath("$.data.quantity").value(200))
                .andExpect(jsonPath("$.data.todo_completed").value(true));

        mvc.perform(get("/api/v1/immune-records/8801").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batch_no").value("ZM2610-031"))
                .andExpect(jsonPath("$.data.change_state.can_edit").value(true));

        mvc.perform(put("/api/v1/immune-records/8801").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"quantity\":180,\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(200));
    }

    // ===== API-D17–D27 用药 =====

    @Test
    void d17ToD27MedicationRoutesReturnAppFields() throws Exception {
        mvc.perform(get("/api/v1/batches/3001/withdrawal").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_WITHDRAWAL"))
                .andExpect(jsonPath("$.data.release_date").value("2026-10-29"))
                .andExpect(jsonPath("$.data.days_left").value(10))
                .andExpect(jsonPath("$.data.drugs[0].tag").value("LATEST"));

        mvc.perform(get("/api/v1/batches/3001/medication-records").param("page", "1").param("size", "20")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].drug_name").value("氟苯尼考粉"))
                .andExpect(jsonPath("$.data.list[0].is_mine").value(false))
                .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(get("/api/v1/medication/targets").param("target_type", "BATCH")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].batch_no").value("ZM2610-031"));

        mvc.perform(get("/api/v1/medication/drug-options").param("batch_id", "3001")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].drug_name").value("阿莫西林可溶性粉"))
                .andExpect(jsonPath("$.data.list[0].withdrawal_days").value(14));

        mvc.perform(post("/api/v1/medication-records/preview").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"target_type\":\"BATCH\",\"batch_id\":3003,"
                                + "\"drug_id\":5201,\"first_date\":\"2026-10-19\",\"course_days\":5,\"dose\":0.3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.release_date").value("2026-11-06"));

        mvc.perform(post("/api/v1/medication-records").header("Authorization", AUTH)
                        .header("Idempotency-Key", "req-med-1").contentType(JSON)
                        .content("{\"target_type\":\"BATCH\",\"batch_id\":3003,\"drug_id\":5201,"
                                + "\"drug_lot_id\":6101,\"dose\":0.3,\"used_qty\":800,\"course_days\":5,"
                                + "\"first_date\":\"2026-10-19\",\"reason\":\"腹泻\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_ids[0]").value(5101))
                .andExpect(jsonPath("$.data.toast").value("已登记用药，可出栏日期为 11月6日 周五"));

        mvc.perform(get("/api/v1/medication-records/5101").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5101))
                .andExpect(jsonPath("$.data.lot_no").value("2025062"));

        mvc.perform(put("/api/v1/medication-records/5101").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"used_qty\":700,\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used_qty").value(800));

        mvc.perform(get("/api/v1/medication-records/5101/delete-impact").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.impact").value("ADVANCE"))
                .andExpect(jsonPath("$.data.notify_name").value("刘芳"));

        mvc.perform(delete("/api/v1/medication-records/5111").param("version", "2")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toast").value("已删除用药记录"));

        mvc.perform(post("/api/v1/medication-records/5101/notify-manager").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"action\":\"UPDATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notified_names[0]").value("刘芳"));
    }

    // ===== API-D28–D40 消毒 =====

    @Test
    void d28ToD40DisinfectRoutesReturnAppFields() throws Exception {
        mvc.perform(get("/api/v1/disinfect-plans").param("status", "ALL").param("page", "1").param("size", "20")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(62))
                .andExpect(jsonPath("$.data.list[0].pen_names_text").value("3 号育肥舍、4 号育肥舍"))
                .andExpect(jsonPath("$.data.tab_counts.all").value(3));

        mvc.perform(get("/api/v1/disinfect-plans/62").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.catalog_id").value(7301))
                .andExpect(jsonPath("$.data.pens[0].executor_text").value("李明"));

        mvc.perform(post("/api/v1/disinfect-plans/preview").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"pen_ids\":[2003,2004],\"cycle_days\":7,"
                                + "\"start_date\":\"2026-10-20\",\"end_date\":\"2026-12-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.times").value(11))
                .andExpect(jsonPath("$.data.task_count").value(22));

        mvc.perform(post("/api/v1/disinfect-plans").header("Authorization", AUTH)
                        .header("Idempotency-Key", "req-dplan-1").contentType(JSON)
                        .content("{\"pen_ids\":[2003,2004],\"cycle_days\":7,\"start_date\":\"2026-10-20\","
                                + "\"end_date\":\"2026-12-31\",\"catalog_id\":7301,\"concentration\":\"1:500\","
                                + "\"method_code\":\"SPRAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toast").value("已保存并启用，生成 22 条消毒任务"));

        mvc.perform(put("/api/v1/disinfect-plans/62").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"pen_ids\":[2003],\"cycle_days\":3,"
                                + "\"start_date\":\"2026-10-01\",\"end_date\":\"2026-12-31\",\"catalog_id\":7301,"
                                + "\"concentration\":\"1:300\",\"method_code\":\"SPRAY\",\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(62));

        mvc.perform(post("/api/v1/disinfect-plans/62/pause").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"reason\":\"空栏\",\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));

        mvc.perform(post("/api/v1/disinfect-plans/64/resume").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toast").exists());

        mvc.perform(get("/api/v1/disinfectants/options").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("聚维酮碘溶液"))
                .andExpect(jsonPath("$.data[0].stock_hint_type").value("NEAR_EXPIRY"));

        mvc.perform(get("/api/v1/disinfect-tasks").param("status", "ALL").param("page", "1").param("size", "20")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(70112))
                .andExpect(jsonPath("$.data.list[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$.data.tab_counts.overdue").value(1))
                .andExpect(jsonPath("$.data.overdue_banner.show").value(true));

        mvc.perform(get("/api/v1/disinfect-tasks/options").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].task_id").value(70113))
                .andExpect(jsonPath("$.data[0].plan_text").value("计划：聚维酮碘溶液 1:300 · 喷雾 · 每 3 天"));

        mvc.perform(post("/api/v1/disinfect-records").header("Authorization", AUTH)
                        .header("Idempotency-Key", "req-dis-1").contentType(JSON)
                        .content("{\"record_type\":\"PLAN_TASK\",\"task_id\":70114,"
                                + "\"actual_time\":\"2026-10-19 09:10:00\",\"drug_id\":5301,\"drug_lot_id\":6301,"
                                + "\"usage_qty\":0.5,\"concentration\":\"1:300\",\"method_code\":\"SPRAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7201))
                .andExpect(jsonPath("$.data.toast").value("已登记消毒，待办已自动完成"));

        mvc.perform(get("/api/v1/disinfect-records/7101").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record_type_text").value("计划任务"))
                .andExpect(jsonPath("$.data.photos[0].file_id").value(9003));

        mvc.perform(put("/api/v1/disinfect-records/7101").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"usage_qty\":0.6,\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7201));
    }

    // ===== API-D41 =====

    @Test
    void d41RequestStatusReturnsNotFoundShape() throws Exception {
        mvc.perform(get("/api/v1/health-records/by-request/req-missing").param("record_type", "IMMUNE_RECORD")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data.biz_ids").isArray());
    }

    // ===== fixtures =====

    private void stubImmune() {
        ImmunePlanOverviewVO overview = new ImmunePlanOverviewVO();
        overview.setBatchId(3001L);
        overview.setBatchNo("ZM2610-031");
        overview.setPenId(2003L);
        overview.setPenName("3 号育肥舍");
        overview.setBreedName("杜长大");
        overview.setCategoryId(101L);
        overview.setEntryDate(LocalDate.of(2026, 10, 1));
        overview.setEntryAgeDays(78);
        overview.setDayAge(96);
        overview.setCurrentCount(200);
        overview.setBatchEnded(false);
        overview.setHasTemplate(true);
        overview.setCanManage(true);
        ImmunePlanOverviewVO.Counts counts = new ImmunePlanOverviewVO.Counts();
        counts.setAll(5);
        counts.setPending(1);
        counts.setMissed(1);
        counts.setPostponed(1);
        counts.setDone(1);
        counts.setCancelled(1);
        overview.setCounts(counts);
        ImmunePlanOverviewVO.PreEntryItem preEntry = new ImmunePlanOverviewVO.PreEntryItem();
        preEntry.setPlanId(8101L);
        preEntry.setVaccineName("猪瘟活疫苗");
        preEntry.setImmuneRound("首免");
        preEntry.setTemplateAgeDay(25);
        preEntry.setVersion(1);
        overview.setPreEntryItems(List.of(preEntry));
        overview.setPreEntryPendingCount(1);
        when(immunePlanService.overview(anyLong())).thenReturn(overview);

        when(immunePlanService.list(eq(3001L), any())).thenReturn(PageVO.of(List.of(planVO()), 1, 1, 20));
        when(immunePlanService.preEntryConfirm(anyLong(), any())).thenReturn(overview);
        when(immunePlanService.preEntryJoin(anyLong(), any())).thenReturn(planVO());
        when(immunePlanService.preEntryUndo(anyLong(), any())).thenReturn(overview);
        when(immunePlanService.postpone(anyLong(), any())).thenAnswer(invocation -> {
            ImmunePlanVO vo = planVO();
            vo.setStatus("POSTPONED");
            vo.setStatusText("已延期");
            return vo;
        });
        when(immunePlanService.create(anyLong(), any())).thenReturn(planVO());
        when(immunePlanService.update(anyLong(), any())).thenAnswer(invocation -> {
            ImmunePlanVO vo = planVO();
            vo.setDose(new BigDecimal("2"));
            return vo;
        });
        when(immunePlanService.options(any())).thenReturn(PageVO.of(List.of(optionVO()), 1, 1, 20));
        when(immunePlanService.registerForm(anyLong(), isNull())).thenReturn(registerForm());
        when(immunePlanService.vaccineOptions(isNull())).thenReturn(List.of(vaccineVO()));

        when(immuneRecordService.create(any())).thenReturn(recordVO());
        when(immuneRecordService.detail(anyLong())).thenReturn(recordVO());
        when(immuneRecordService.update(anyLong(), any())).thenReturn(recordVO());
    }

    private void stubMedication() {
        when(medicationService.withdrawal(eq(3001L))).thenReturn(withdrawalVO());
        when(medicationService.records(eq(3001L), any())).thenReturn(PageVO.of(List.of(medicationVO()), 1, 1, 20));
        when(medicationService.targets(any())).thenReturn(List.of(targetVO()));
        when(medicationService.drugOptions(any())).thenReturn(drugOptionsVO());
        when(medicationService.preview(any())).thenReturn(previewVO());
        when(medicationService.create(any())).thenReturn(createResultVO());
        when(medicationService.detail(anyLong())).thenReturn(medicationVO());
        when(medicationService.update(anyLong(), any())).thenReturn(medicationVO());
        when(medicationService.deleteImpact(anyLong())).thenReturn(deleteImpactVO());
        when(medicationService.delete(anyLong(), any())).thenReturn(ToastVO.of("已删除用药记录"));
        when(medicationService.notifyManager(anyLong(), any())).thenReturn(notifyResultVO());
    }

    private void stubDisinfect() {
        when(disinfectService.plans(any())).thenReturn(PageVO.of(List.of(planListVO()), 3, 1, 20)
                .withTabCounts(java.util.Map.of("all", 3, "active", 2, "paused", 1)));
        when(disinfectService.plan(anyLong())).thenReturn(detailVO());
        when(disinfectService.preview(any())).thenReturn(previewPlanVO());
        when(disinfectService.create(any())).thenAnswer(invocation -> {
            DisinfectPlanVO vo = planListVO();
            vo.setTaskCount(22);
            vo.setToast("已保存并启用，生成 22 条消毒任务");
            return vo;
        });
        when(disinfectService.update(anyLong(), any())).thenReturn(planListVO());
        when(disinfectService.pause(anyLong(), any())).thenAnswer(invocation -> {
            DisinfectPlanVO vo = planListVO();
            vo.setStatus("PAUSED");
            vo.setStatusText("已暂停");
            vo.setPauseText("空栏，刘芳已暂停");
            return vo;
        });
        when(disinfectService.resume(anyLong(), any())).thenAnswer(invocation -> {
            DisinfectPlanVO vo = planListVO();
            vo.setToast("已启用计划，下次任务 10月20日");
            return vo;
        });
        when(disinfectService.disinfectants(any())).thenReturn(List.of(disinfectantVO()));
        when(disinfectService.tasks(any())).thenReturn(taskPageVO());
        when(disinfectService.taskOptions()).thenReturn(List.of(taskOptionVO()));
        when(disinfectService.createRecord(any())).thenReturn(taskRecordVO());
        when(disinfectService.record(anyLong())).thenReturn(taskRecordVO());
        when(disinfectService.updateRecord(anyLong(), any())).thenReturn(taskRecordVO());

        HealthRequestStatusVO notFound = new HealthRequestStatusVO();
        notFound.setStatus("NOT_FOUND");
        when(healthRequestService.findByRequest(any(), any())).thenReturn(notFound);
    }

    private ImmunePlanVO planVO() {
        ImmunePlanVO vo = new ImmunePlanVO();
        vo.setId(8117L);
        vo.setVersion(2);
        vo.setVaccineName("猪瘟活疫苗");
        vo.setImmuneRound("加强免疫");
        vo.setPlanMode("BY_DAY_AGE");
        vo.setPlanDate(LocalDate.of(2026, 10, 19));
        vo.setIsToday(true);
        vo.setDueTime("18:00");
        vo.setOverdue(false);
        vo.setAgeAtPlan(96);
        vo.setResponsibleText("李明负责");
        vo.setRemindText("前 1 天和当天各提醒一次");
        vo.setStatus("PENDING");
        vo.setStatusText("待接种");
        vo.setDose(BigDecimal.ONE);
        vo.setDoseUnit("头份 / 头");
        vo.setMethodCode("IM");
        vo.setMethodName("肌肉注射");
        vo.setActions(List.of("REGISTER", "POSTPONE", "EDIT", "DELETE"));
        return vo;
    }

    private ImmunePlanOptionVO optionVO() {
        ImmunePlanOptionVO vo = new ImmunePlanOptionVO();
        vo.setPlanId(8117L);
        vo.setTargetType("BATCH");
        vo.setBatchId(3001L);
        vo.setBatchNo("ZM2610-031");
        vo.setPenName("3 号育肥舍");
        vo.setVaccineName("猪瘟活疫苗");
        vo.setCurrentCount(200);
        vo.setPlanDate(LocalDate.of(2026, 10, 19));
        vo.setPlanDateText("今天");
        vo.setStatus("PENDING");
        vo.setStatusText("待接种");
        return vo;
    }

    private ImmuneRegisterFormVO registerForm() {
        ImmuneRegisterFormVO vo = new ImmuneRegisterFormVO();
        vo.setPlan(optionVO());
        vo.setDrugId(5001L);
        vo.setVaccineName("猪瘟活疫苗");
        vo.setDose(BigDecimal.ONE);
        vo.setTemplateDose(BigDecimal.ONE);
        vo.setDoseUnit("头份 / 头");
        vo.setQuantityDefault(200);
        vo.setCurrentCount(200);
        vo.setMethodCode("IM");
        vo.setBackfill(false);
        vo.setDateDefault(LocalDate.of(2026, 10, 19));
        vo.setDateMin(LocalDate.of(2026, 10, 1));
        vo.setDateMax(LocalDate.of(2026, 10, 19));
        vo.setDefaultExecutorId(10003L);
        vo.setCreatorName("王建国");
        com.zhimu.server.biz.health.vo.DrugLotVO lot = new com.zhimu.server.biz.health.vo.DrugLotVO();
        lot.setLotId(6001L);
        lot.setLotNo("2025081");
        lot.setExpireDate(LocalDate.of(2026, 12, 30));
        lot.setLotStatus("NORMAL");
        lot.setDaysToExpire(72);
        com.zhimu.server.common.vo.QtyVO qty = com.zhimu.server.common.vo.QtyVO.builder()
                .value(new BigDecimal("400")).unit("DOSE").unitText("头份").build();
        lot.setStock(qty);
        lot.setAvgPrice(new BigDecimal("2.35"));
        lot.setPriorityOut(true);
        lot.setInsufficient(false);
        lot.setCanGoScrap(false);
        vo.setDefaultLot(lot);
        return vo;
    }

    private VaccineOptionVO vaccineVO() {
        VaccineOptionVO vo = new VaccineOptionVO();
        vo.setDrugId(5001L);
        vo.setCatalogId(7001L);
        vo.setName("猪瘟活疫苗");
        vo.setDoseUnit("头份 / 头");
        vo.setStockUnit("头份");
        vo.setAvailableQty(new BigDecimal("1000"));
        vo.setNoStock(false);
        vo.setDefaultMethodCode("IM");
        return vo;
    }

    private ImmuneRecordVO recordVO() {
        ImmuneRecordVO vo = new ImmuneRecordVO();
        vo.setId(8801L);
        vo.setPlanId(8117L);
        vo.setVersion(1);
        vo.setBatchId(3001L);
        vo.setBatchNo("ZM2610-031");
        vo.setPenName("3 号育肥舍");
        vo.setVaccineName("猪瘟活疫苗");
        vo.setImmuneRound("加强免疫");
        vo.setDrugLotId(6001L);
        vo.setLotNo("2025081");
        vo.setLotExpireDate(LocalDate.of(2026, 12, 30));
        vo.setDose(BigDecimal.ONE);
        vo.setDoseUnit("头份 / 头");
        vo.setQuantity(200);
        vo.setTotalDose(new BigDecimal("200"));
        vo.setMethodCode("IM");
        vo.setMethodName("肌肉注射");
        vo.setVaccinateDate(LocalDate.of(2026, 10, 19));
        vo.setExecutorName("李明");
        vo.setCreatorName("李明");
        vo.setCreatedAt(java.time.LocalDateTime.of(2026, 10, 19, 9, 40));
        vo.setBackfill(false);
        ChangeRequestStateVO state = new ChangeRequestStateVO();
        state.setCanEdit(true);
        state.setEditDeadline(java.time.LocalDateTime.of(2026, 10, 19, 23, 59, 59));
        vo.setChangeState(state);
        vo.setActions(List.of("EDIT", "VIEW_LOG"));
        vo.setTodoCompleted(true);
        vo.setOverdueDone(false);
        vo.setUnitPrice(new BigDecimal("2.35"));
        vo.setCostAmount(new BigDecimal("470.00"));
        vo.setDrugId(5001L);
        vo.setExecutorId(10003L);
        return vo;
    }

    private WithdrawalVO withdrawalVO() {
        WithdrawalVO vo = new WithdrawalVO();
        vo.setBatchId(3001L);
        vo.setBatchNo("ZM2610-031");
        vo.setPenName("3 号育肥舍");
        vo.setCurrentCount(200);
        vo.setStatus("IN_WITHDRAWAL");
        vo.setStatusText("休药中");
        vo.setReleaseDate(LocalDate.of(2026, 10, 29));
        vo.setDaysLeft(10);
        vo.setDecisiveDrugName("氟苯尼考粉");
        vo.setLastMedDate(LocalDate.of(2026, 10, 9));
        vo.setWithdrawalDays(20);
        vo.setProgress(0.5);
        WithdrawalDrugVO drug = new WithdrawalDrugVO();
        drug.setCatalogId(7202L);
        drug.setDrugName("氟苯尼考粉");
        drug.setLastDate(LocalDate.of(2026, 10, 9));
        drug.setWithdrawalDays(20);
        drug.setEndDate(LocalDate.of(2026, 10, 29));
        drug.setTag("LATEST");
        drug.setTagText("最晚");
        vo.setDrugs(List.of(drug));
        return vo;
    }

    private MedicationRecordVO medicationVO() {
        MedicationRecordVO vo = new MedicationRecordVO();
        vo.setId(5101L);
        vo.setGroupNo("MG26105101");
        vo.setVersion(2);
        vo.setTargetType("BATCH");
        vo.setBatchId(3001L);
        vo.setBatchNo("ZM2610-031");
        vo.setPenId(2003L);
        vo.setPenName("3 号育肥舍");
        vo.setDrugId(5202L);
        vo.setDrugName("氟苯尼考粉");
        vo.setRouteCode("MIX_FEED");
        vo.setRouteName("混饲");
        vo.setLotNo("2025062");
        vo.setLotExpireDate(LocalDate.of(2027, 5, 31));
        vo.setDose(new BigDecimal("100"));
        vo.setDoseUnit("克 / 吨料");
        vo.setRecommendedDose(new BigDecimal("100"));
        vo.setOverDose(false);
        vo.setCourseDays(5);
        vo.setFirstDate(LocalDate.of(2026, 10, 5));
        vo.setLastDate(LocalDate.of(2026, 10, 9));
        vo.setWithdrawalDays(20);
        vo.setWithdrawalEndDate(LocalDate.of(2026, 10, 29));
        vo.setUsedQty(new BigDecimal("800"));
        vo.setUsedUnit("克");
        vo.setReason("咳嗽、采食下降");
        vo.setCreatorName("李明");
        vo.setExecutorName("李明");
        vo.setIsMine(false);
        vo.setLocked(false);
        ChangeRequestStateVO state = new ChangeRequestStateVO();
        state.setCanEdit(false);
        vo.setChangeState(state);
        vo.setActions(List.of("EDIT", "VIEW_LOG", "DELETE"));
        vo.setUnitPrice(new BigDecimal("0.12"));
        vo.setCostAmount(new BigDecimal("96.00"));
        vo.setDrugLotId(6111L);
        vo.setExecutorId(10003L);
        return vo;
    }

    private MedicationTargetVO targetVO() {
        MedicationTargetVO vo = new MedicationTargetVO();
        vo.setBatchId(3001L);
        vo.setBatchNo("ZM2610-031");
        vo.setPenName("3 号育肥舍");
        vo.setCurrentCount(200);
        vo.setCategoryId(101L);
        return vo;
    }

    private MedicationDrugOptionsVO drugOptionsVO() {
        MedicationDrugOptionsVO result = new MedicationDrugOptionsVO();
        MedicationDrugOptionVO vo = new MedicationDrugOptionVO();
        vo.setDrugId(5201L);
        vo.setCatalogId(7201L);
        vo.setDrugName("阿莫西林可溶性粉");
        vo.setSpec("10% · 500 克 / 袋");
        vo.setStockUnit("克");
        vo.setAvailableQty(new BigDecimal("3500"));
        vo.setRouteCode("MIX_WATER");
        vo.setRouteName("混饮");
        vo.setRecommendedDose(new BigDecimal("0.3"));
        vo.setDoseUnit("克 / 升水");
        vo.setRecommendedCourseDays(5);
        vo.setWithdrawalDays(14);
        vo.setApplicable(true);
        vo.setHintText("休药 14 天 · 禁用药不可选");
        result.setList(List.of(vo));
        return result;
    }

    private ReleaseDatePreviewVO previewVO() {
        ReleaseDatePreviewVO vo = new ReleaseDatePreviewVO();
        vo.setReady(true);
        vo.setLastDate(LocalDate.of(2026, 10, 23));
        vo.setReleaseDate(LocalDate.of(2026, 11, 6));
        vo.setReleaseText("11月6日 周五");
        vo.setUsedUnit("克");
        ReleaseDatePreviewVO.PerBatch perBatch = new ReleaseDatePreviewVO.PerBatch();
        perBatch.setBatchId(3003L);
        perBatch.setBatchNo("ZM2610-045");
        perBatch.setReleaseDate(LocalDate.of(2026, 11, 6));
        vo.setPerBatch(List.of(perBatch));
        return vo;
    }

    private MedicationCreateResultVO createResultVO() {
        MedicationCreateResultVO vo = new MedicationCreateResultVO();
        vo.setRecordIds(List.of(5101L));
        vo.setGroupNo("MG2610190005");
        MedicationCreateResultVO.PerBatch perBatch = new MedicationCreateResultVO.PerBatch();
        perBatch.setBatchNo("ZM2610-045");
        perBatch.setReleaseDate(LocalDate.of(2026, 11, 6));
        vo.setPerBatch(List.of(perBatch));
        vo.setToast("已登记用药，可出栏日期为 11月6日 周五");
        return vo;
    }

    private DeleteImpactVO deleteImpactVO() {
        DeleteImpactVO vo = new DeleteImpactVO();
        vo.setImpact("ADVANCE");
        vo.setRecordTitle("氟苯尼考粉 · 10月5日–10月9日");
        vo.setReleaseDateBefore(LocalDate.of(2026, 10, 29));
        vo.setReleaseDateAfter(LocalDate.of(2026, 10, 19));
        vo.setNotifyName("刘芳");
        vo.setNotifyRole("MANAGER");
        return vo;
    }

    private NotifyResultVO notifyResultVO() {
        NotifyResultVO vo = new NotifyResultVO();
        vo.getNotifiedNames().add("刘芳");
        return vo;
    }

    private DisinfectPlanVO planListVO() {
        DisinfectPlanVO vo = new DisinfectPlanVO();
        vo.setId(62L);
        vo.setVersion(3);
        vo.setPenIds(List.of(2003L, 2004L));
        vo.setPenNamesText("3 号育肥舍、4 号育肥舍");
        vo.setCycleDays(3);
        vo.setStartDate(LocalDate.of(2026, 10, 1));
        vo.setEndDate(LocalDate.of(2026, 12, 31));
        vo.setDueTime("17:00");
        vo.setDisinfectantName("聚维酮碘溶液");
        vo.setConcentration("1:300");
        vo.setMethodCode("SPRAY");
        vo.setMethodName("喷雾");
        vo.setStatus("ACTIVE");
        vo.setStatusText("启用中");
        vo.setNextTaskDate(LocalDate.of(2026, 10, 19));
        vo.setNextTaskText("今天");
        vo.setActions(List.of("PAUSE", "EDIT"));
        return vo;
    }

    private DisinfectPlanDetailVO detailVO() {
        DisinfectPlanDetailVO vo = new DisinfectPlanDetailVO();
        vo.setId(62L);
        vo.setVersion(3);
        vo.setPenIds(List.of(2003L, 2004L));
        vo.setPenNamesText("3 号育肥舍、4 号育肥舍");
        vo.setCycleDays(3);
        vo.setStartDate(LocalDate.of(2026, 10, 1));
        vo.setEndDate(LocalDate.of(2026, 12, 31));
        vo.setDueTime("17:00");
        vo.setDisinfectantName("聚维酮碘溶液");
        vo.setConcentration("1:300");
        vo.setMethodCode("SPRAY");
        vo.setMethodName("喷雾");
        vo.setStatus("ACTIVE");
        vo.setStatusText("启用中");
        vo.setCatalogId(7301L);
        vo.setDrugId(5301L);
        DisinfectPlanDetailVO.Pen pen = new DisinfectPlanDetailVO.Pen();
        pen.setPenId(2003L);
        pen.setPenName("3 号育肥舍");
        pen.setExecutorText("李明");
        vo.setPens(List.of(pen));
        vo.setActions(List.of("PAUSE", "EDIT"));
        return vo;
    }

    private DisinfectPlanPreviewVO previewPlanVO() {
        DisinfectPlanPreviewVO vo = new DisinfectPlanPreviewVO();
        vo.setReady(true);
        vo.setTimes(11);
        vo.setTaskCount(22);
        vo.setGenerateNowCount(22);
        vo.setPenText("已选 2 个栏舍，系统为每个栏舍各生成一条任务。");
        vo.setRemindText("10月20日至 12月31日共 11 次，每次每个栏舍 1 条。");
        return vo;
    }

    private DisinfectantOptionVO disinfectantVO() {
        DisinfectantOptionVO vo = new DisinfectantOptionVO();
        vo.setCatalogId(7301L);
        vo.setDrugId(5301L);
        vo.setName("聚维酮碘溶液");
        vo.setRecommendedConcentration("1:500");
        vo.setMethodCodes(List.of("SPRAY", "WIPE", "SOAK"));
        vo.setStockHintType("NEAR_EXPIRY");
        vo.setStockHintText("库存批号 2025033 临期，剩 20 天");
        vo.setStockUnit("升");
        return vo;
    }

    private DisinfectTaskPageVO taskPageVO() {
        DisinfectTaskVO task = new DisinfectTaskVO();
        task.setId(70112L);
        task.setPlanId(63L);
        task.setPenId(2002L);
        task.setPenName("2 号保育舍");
        task.setTaskDate(LocalDate.of(2026, 10, 18));
        task.setDateText("10月18日");
        task.setDueTime("17:00");
        task.setDisinfectantName("聚维酮碘溶液");
        task.setConcentration("1:500");
        task.setMethodName("喷雾");
        task.setCycleDays(2);
        task.setExecutorText("你和刘芳");
        task.setNoStaff(true);
        task.setStatus("OVERDUE");
        task.setStatusText("已逾期");
        task.setActions(List.of("BACKFILL"));
        DisinfectTaskPageVO vo = new DisinfectTaskPageVO();
        vo.setList(List.of(task));
        vo.setTotal(1);
        vo.setPage(1);
        vo.setSize(20);
        vo.setHasMore(false);
        vo.setTabCounts(java.util.Map.of("pending", 4, "done", 2, "overdue", 1));
        DisinfectTaskPageVO.OverdueBanner banner = new DisinfectTaskPageVO.OverdueBanner();
        banner.setShow(true);
        banner.setText("任务逾期后，系统会再提醒所有者和场长一次。");
        vo.setOverdueBanner(banner);
        return vo;
    }

    private DisinfectTaskOptionVO taskOptionVO() {
        DisinfectTaskOptionVO vo = new DisinfectTaskOptionVO();
        vo.setTaskId(70113L);
        vo.setPenId(2003L);
        vo.setPenName("3 号育肥舍");
        vo.setTaskDate(LocalDate.of(2026, 10, 19));
        vo.setDateText("10月19日 今天");
        vo.setOverdue(false);
        vo.setPlanText("计划：聚维酮碘溶液 1:300 · 喷雾 · 每 3 天");
        vo.setPlanConcentration("1:300");
        vo.setMethodCode("SPRAY");
        vo.setDrugId(5301L);
        vo.setDefaultLotId(6301L);
        vo.setDefaultExecutorId(10003L);
        return vo;
    }

    private DisinfectRecordVO taskRecordVO() {
        DisinfectRecordVO vo = new DisinfectRecordVO();
        vo.setId(7201L);
        vo.setVersion(1);
        vo.setRecordType("PLAN_TASK");
        vo.setRecordTypeText("计划任务");
        vo.setTaskId(70411L);
        vo.setTaskDate(LocalDate.of(2026, 10, 18));
        vo.setPlanText("计划：聚维酮碘溶液 1:500 · 喷雾 · 每 2 天");
        vo.setPlanConcentration("1:500");
        vo.setPenId(2001L);
        vo.setPenName("1 号保育舍");
        vo.setActualTime(java.time.LocalDateTime.of(2026, 10, 18, 16, 20));
        vo.setExecutorName("张伟");
        vo.setCreatorName("王建国");
        vo.setDrugLotId(6301L);
        vo.setLotNo("2025033");
        vo.setDisinfectantName("聚维酮碘溶液");
        vo.setUsageQty(new BigDecimal("0.5"));
        vo.setUsageUnit("升");
        vo.setConcentration("1:500");
        vo.setConcentrationConfirmed(false);
        vo.setMethodCode("SPRAY");
        vo.setMethodName("喷雾");
        com.zhimu.server.common.vo.MediaItemVO photo = new com.zhimu.server.common.vo.MediaItemVO();
        photo.setFileId(9003L);
        vo.setPhotos(List.of(photo));
        vo.setInBatchCost(true);
        vo.setInExecRate(true);
        vo.setBackfill(false);
        ChangeRequestStateVO state = new ChangeRequestStateVO();
        state.setCanEdit(true);
        vo.setChangeState(state);
        vo.setActions(List.of("EDIT", "VIEW_LOG"));
        vo.setToast("已登记消毒，待办已自动完成");
        return vo;
    }
}
