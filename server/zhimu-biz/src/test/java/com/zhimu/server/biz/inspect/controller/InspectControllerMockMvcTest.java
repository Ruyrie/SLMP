package com.zhimu.server.biz.inspect.controller;

import com.zhimu.server.biz.inspect.InspectFixtures;
import com.zhimu.server.biz.inspect.InspectTestMvc;
import com.zhimu.server.biz.inspect.service.AbnormalReportService;
import com.zhimu.server.biz.inspect.service.InspectionService;
import com.zhimu.server.biz.inspect.service.InspectionSettingService;
import com.zhimu.server.biz.inspect.vo.DiagnosisDetailVO;
import com.zhimu.server.biz.inspect.vo.DiagnosisResultVO;
import com.zhimu.server.biz.inspect.vo.DiagnosisStateVO;
import com.zhimu.server.biz.inspect.vo.HandleSaveResultVO;
import com.zhimu.server.biz.inspect.vo.InspectionDetailVO;
import com.zhimu.server.biz.inspect.vo.InspectionFormVO;
import com.zhimu.server.biz.inspect.vo.InspectionListItemVO;
import com.zhimu.server.biz.inspect.vo.InspectionSettingVO;
import com.zhimu.server.biz.inspect.vo.InspectionSubmitResultVO;
import com.zhimu.server.biz.inspect.vo.InspectionSummaryVO;
import com.zhimu.server.biz.inspect.vo.LinkableReportVO;
import com.zhimu.server.biz.inspect.vo.PenInspectionOverviewVO;
import com.zhimu.server.biz.inspect.vo.PenInspectionRowVO;
import com.zhimu.server.biz.inspect.vo.RecentIssueVO;
import com.zhimu.server.biz.inspect.vo.ReportCreateResultVO;
import com.zhimu.server.biz.inspect.vo.ReportFormVO;
import com.zhimu.server.biz.inspect.vo.SymptomVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectControllerMockMvcTest {

    private static final String JSON = "application/json";
    private static final String AUTH = "Bearer test";

    @Mock private InspectionService inspectionService;
    @Mock private InspectionSettingService settingService;
    @Mock private AbnormalReportService reportService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = InspectTestMvc.builder(new InspectionController(inspectionService),
                new InspectionSettingController(settingService),
                new AbnormalReportController(reportService)).build();
        stubSuccess();
    }

    @AfterEach
    void tearDown() {
        InspectFixtures.logout();
    }

    @Test
    void i01ToI12AndI25RoutesReturnAppFields() throws Exception {
        mvc.perform(get("/api/v1/inspections/summary").param("date", "2026-10-19")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.farm_name").value("东山猪场"))
                .andExpect(jsonPath("$.data.should_count").value(4))
                .andExpect(jsonPath("$.data.completion_rate").value(0.5))
                .andExpect(jsonPath("$.data.first_abnormal_pen_name").value("3 号育肥舍"));

        mvc.perform(get("/api/v1/inspections").param("date", "2026-10-19").param("status", "ALL")
                        .param("page", "1").param("size", "20").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].display_status").value("ABNORMAL"))
                .andExpect(jsonPath("$.data.list[0].pen_name").value("3 号育肥舍"))
                .andExpect(jsonPath("$.data.tab_counts.ALL").value(4))
                .andExpect(jsonPath("$.data.has_more").value(false));

        mvc.perform(get("/api/v1/inspections/recent-issues").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].issue_type").value("OVERDUE"))
                .andExpect(jsonPath("$.data[0].inspection_id").value(50101));

        mvc.perform(get("/api/v1/inspection-settings/current").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deadline_time").value("18:00"))
                .andExpect(jsonPath("$.data.remind_time").value("17:00"));

        mvc.perform(put("/api/v1/inspection-settings/current").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"deadline_time\":\"17:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deadline_time").value("17:00"));

        mvc.perform(get("/api/v1/inspections/form-context").param("pen_id", "2003")
                        .param("mode", "REGISTER").param("inspect_date", "2026-10-19")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("REGISTER"))
                .andExpect(jsonPath("$.data.pen.pen_name").value("3 号育肥舍"))
                .andExpect(jsonPath("$.data.template.template_id").value(11))
                .andExpect(jsonPath("$.data.template.items[0].options[0].option_id").value(1011))
                .andExpect(jsonPath("$.data.farm_latitude").value(42.3025));

        mvc.perform(post("/api/v1/inspections").header("Authorization", AUTH)
                        .header("Idempotency-Key", "req-1").contentType(JSON)
                        .content("""
                                {"pen_id":2003,"client_request_id":"req-1",
                                 "item_results":[{"item_id":101,"option_id":1011},
                                                 {"item_id":102,"option_id":1021},
                                                 {"item_id":103,"option_id":1033}],
                                 "location":{"latitude":42.3025,"longitude":118.8516,
                                             "accuracy_m":20,"client_status":"OK"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inspection_id").value(50121))
                .andExpect(jsonPath("$.data.is_abnormal").value(true));

        mvc.perform(put("/api/v1/inspections/50121").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"version\":1,\"remark\":\"补充\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        mvc.perform(post("/api/v1/inspections/backfills").header("Authorization", AUTH)
                        .header("Idempotency-Key", "req-2").contentType(JSON)
                        .content("""
                                {"pen_id":2002,"inspect_date":"2026-10-18","executor_id":10002,
                                 "client_request_id":"req-2",
                                 "item_results":[{"item_id":101,"option_id":1011},
                                                 {"item_id":102,"option_id":1021},
                                                 {"item_id":103,"option_id":1031}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_backfill").value(true));

        mvc.perform(get("/api/v1/inspections/50121").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inspect_status").value("DONE"))
                .andExpect(jsonPath("$.data.item_results[0].option_name").value("水样"))
                .andExpect(jsonPath("$.data.edit_mode").value("DIRECT"))
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(get("/api/v1/pens/2003/inspections").param("filter", "ALL")
                        .param("page", "1").param("size", "20").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].tags[0]").value("ABNORMAL"))
                .andExpect(jsonPath("$.data.list[0].inspect_date").value("2026-10-19"))
                // App mock history_2003.json 的键是 backfill_expired（不是 is_backfill_expired）
                .andExpect(jsonPath("$.data.list[0].backfill_expired").value(false));

        mvc.perform(get("/api/v1/pens/2003/inspection-overview").param("to", "2026-10-19")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pen_id").value(2003))
                .andExpect(jsonPath("$.data.recent7_done_days").value(7))
                .andExpect(jsonPath("$.data.days[0].day_tag").value("ABNORMAL"));

        mvc.perform(get("/api/v1/inspections/by-request/req-1").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inspection_id").value(50121));
    }

    @Test
    void i14ToI26RoutesReturnAppFields() throws Exception {
        mvc.perform(get("/api/v1/abnormal-reports/form-context").param("inspection_id", "50121")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("INSPECTION"))
                .andExpect(jsonPath("$.data.pens[0].batches[0].batch_id").value(3001))
                .andExpect(jsonPath("$.data.symptoms[0].symptom_id").value(401))
                .andExpect(jsonPath("$.data.max_media_count").value(6));

        mvc.perform(get("/api/v1/symptoms").param("batch_id", "3001").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("腹泻"));

        mvc.perform(post("/api/v1/abnormal-reports").header("Authorization", AUTH)
                        .header("Idempotency-Key", "req-3").contentType(JSON)
                        .content("""
                                {"client_request_id":"req-3","pen_id":2003,"batch_id":3001,
                                 "sick_count":12,"symptom_ids":[401,402],
                                 "description":"12 头排水样稀粪，部分呕吐。"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report_id").value(6001))
                .andExpect(jsonPath("$.data.ai_state").value("DIAGNOSING"))
                .andExpect(jsonPath("$.data.poll_interval_sec").value(3));

        mvc.perform(get("/api/v1/abnormal-reports/6001").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DIAGNOSED"))
                .andExpect(jsonPath("$.data.report.sick_rate").value(0.06))
                .andExpect(jsonPath("$.data.diagnosis.causes[0].disease_name").value("猪流行性腹泻"))
                .andExpect(jsonPath("$.data.permissions.can_handle").value(true));

        mvc.perform(get("/api/v1/abnormal-reports/6003/diagnosis-state").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ai_state").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.last_failure_text").value("诊断请求超时"));

        mvc.perform(post("/api/v1/abnormal-reports/6003/rediagnoses").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ai_state").value("DIAGNOSING"));

        mvc.perform(put("/api/v1/abnormal-reports/6001/handle").header("Authorization", AUTH)
                        .contentType(JSON)
                        .content("""
                                {"diagnostician":"赵磊（执业兽医）","treatment":"隔离、消毒、补液",
                                 "used_drug":true,"cured_count":9,"culled_count":2,
                                 "ai_match":"MATCH","confirm_close":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HANDLED"))
                .andExpect(jsonPath("$.data.death_prompt.culled_count").value(2));

        mvc.perform(get("/api/v1/abnormal-reports/linkable").param("batch_id", "3001")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].report_id").value(5988))
                .andExpect(jsonPath("$.data[0].label").value("10月18日 AI 诊断 · 3 号育肥舍"));

        mvc.perform(get("/api/v1/abnormal-reports/by-request/req-3").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report_id").value(6001));
    }

    @Test
    void businessErrorsBecomeHttpStatusAndCode() throws Exception {
        when(reportService.detail(eq(6002L))).thenThrow(new BizException(ErrorCode.ROLE_FORBIDDEN,
                "你没有这条诊断记录的查看权限。员工只能查看自己上报的诊断。"));
        mvc.perform(get("/api/v1/abnormal-reports/6002").header("Authorization", AUTH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303))
                .andExpect(jsonPath("$.message").value("你没有这条诊断记录的查看权限。员工只能查看自己上报的诊断。"));

        when(inspectionService.findByRequest(eq("missing"))).thenThrow(new BizException(ErrorCode.NOT_FOUND));
        mvc.perform(get("/api/v1/inspections/by-request/missing").header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        when(settingService.update(any())).thenThrow(new BizException(ErrorCode.ROLE_FORBIDDEN,
                "只有所有者和场长可以设置巡检截止时间"));
        mvc.perform(put("/api/v1/inspection-settings/current").header("Authorization", AUTH)
                        .contentType(JSON).content("{\"deadline_time\":\"17:00\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303));
    }

    private void stubSuccess() {
        InspectionSummaryVO summary = new InspectionSummaryVO();
        summary.setFarmName("东山猪场");
        summary.setDate("2026-10-19");
        summary.setDeadlineTime("18:00");
        summary.setRemindTime("17:00");
        summary.setScope("ALL_PENS");
        summary.setShouldCount(4);
        summary.setDoneCount(2);
        summary.setPendingCount(2);
        summary.setAbnormalCount(1);
        summary.setCompletionRate(new BigDecimal("0.5000"));
        summary.setFirstAbnormalPenName("3 号育肥舍");
        summary.setFirstAbnormalReported(false);
        lenient().when(inspectionService.summary(any())).thenReturn(summary);

        InspectionListItemVO item = new InspectionListItemVO();
        item.setInspectionId(50121L);
        item.setPenId(2003L);
        item.setPenCode("YF-03");
        item.setPenName("3 号育肥舍");
        item.setDisplayStatus("ABNORMAL");
        item.setDisplayStatusText("异常");
        item.setBatchNos(List.of("ZM2610-031"));
        item.setStockCount(200);
        item.setVersion(1);
        PageVO<InspectionListItemVO> page = PageVO.of(List.of(item), 4, 1, 20);
        page.setTabCounts(Map.of("ALL", 4, "PENDING", 2, "ABNORMAL", 1, "DONE", 1));
        lenient().when(inspectionService.dayList(any())).thenReturn(page);

        RecentIssueVO issue = new RecentIssueVO();
        issue.setInspectionId(50101L);
        issue.setPenId(2003L);
        issue.setPenName("3 号育肥舍");
        issue.setInspectDate("2026-10-18");
        issue.setIssueType("OVERDUE");
        issue.setIssueTypeText("超时");
        lenient().when(inspectionService.recentIssues()).thenReturn(List.of(issue));

        InspectionSettingVO setting = new InspectionSettingVO();
        setting.setFarmName("东山猪场");
        setting.setDeadlineTime("18:00");
        setting.setRemindTime("17:00");
        setting.setDefaultDeadline("18:00");
        setting.setQuickOptions(List.of("16:00", "17:00", "18:00", "19:00"));
        setting.setMinTime("06:00");
        setting.setMaxTime("23:00");
        lenient().when(settingService.current()).thenReturn(setting);
        InspectionSettingVO changed = new InspectionSettingVO();
        changed.setDeadlineTime("17:00");
        changed.setRemindTime("16:00");
        lenient().when(settingService.update(any())).thenReturn(changed);

        InspectionFormVO form = new InspectionFormVO();
        form.setMode("REGISTER");
        InspectionFormVO.FormPen pen = new InspectionFormVO.FormPen();
        pen.setPenId(2003L);
        pen.setPenCode("YF-03");
        pen.setPenName("3 号育肥舍");
        form.setPen(pen);
        InspectionFormVO.InspectTemplate template = new InspectionFormVO.InspectTemplate();
        template.setTemplateId(11L);
        template.setTemplateVersion(2);
        InspectionFormVO.TemplateItem templateItem = new InspectionFormVO.TemplateItem();
        templateItem.setItemId(101L);
        templateItem.setItemName("精神状态");
        templateItem.setSortNo(1);
        InspectionFormVO.TemplateOption option = new InspectionFormVO.TemplateOption();
        option.setOptionId(1011L);
        option.setOptionName("正常");
        option.setIsAbnormal(false);
        option.setSortNo(1);
        templateItem.setOptions(List.of(option));
        template.setItems(List.of(templateItem));
        form.setTemplate(template);
        form.setFarmLatitude(42.3025);
        form.setFarmLongitude(118.8516);
        lenient().when(inspectionService.formContext(any())).thenReturn(form);

        InspectionSubmitResultVO submit = new InspectionSubmitResultVO();
        submit.setInspectionId(50121L);
        submit.setVersion(1);
        submit.setPenName("3 号育肥舍");
        submit.setInspectDate("2026-10-19");
        submit.setIsAbnormal(true);
        submit.setIsOverdue(false);
        submit.setIsBackfill(false);
        submit.setTodoCompleted(true);
        lenient().when(inspectionService.register(any())).thenReturn(submit);
        InspectionSubmitResultVO modified = new InspectionSubmitResultVO();
        modified.setInspectionId(50121L);
        modified.setVersion(2);
        modified.setPenName("3 号育肥舍");
        lenient().when(inspectionService.modify(anyLong(), any())).thenReturn(modified);
        InspectionSubmitResultVO backfill = new InspectionSubmitResultVO();
        backfill.setInspectionId(50082L);
        backfill.setIsBackfill(true);
        lenient().when(inspectionService.backfill(any())).thenReturn(backfill);
        lenient().when(inspectionService.findByRequest(any())).thenReturn(submit);

        InspectionDetailVO detail = new InspectionDetailVO();
        detail.setInspectionId(50121L);
        detail.setPenId(2003L);
        detail.setPenName("3 号育肥舍");
        detail.setInspectStatus("DONE");
        detail.setInspectStatusText("已巡");
        detail.setEditMode("DIRECT");
        detail.setVersion(1);
        InspectionDetailVO.ItemResult result = new InspectionDetailVO.ItemResult();
        result.setItemId(103L);
        result.setItemName("粪便");
        result.setOptionId(1033L);
        result.setOptionName("水样");
        result.setOptionAbnormal(true);
        result.setSortNo(3);
        detail.setItemResults(List.of(result));
        lenient().when(inspectionService.detail(anyLong())).thenReturn(detail);

        PenInspectionRowVO row = new PenInspectionRowVO();
        row.setInspectionId(50121L);
        row.setInspectDate("2026-10-19");
        row.setTags(List.of("ABNORMAL", "ON_SITE"));
        row.setBackfillExpired(false);
        lenient().when(inspectionService.penHistory(anyLong(), any()))
                .thenReturn(PageVO.of(List.of(row), 1, 1, 20));

        PenInspectionOverviewVO overview = new PenInspectionOverviewVO();
        overview.setPenId(2003L);
        overview.setPenName("3 号育肥舍");
        overview.setRecent7DoneDays(7);
        overview.setRecent7ShouldDays(7);
        PenInspectionOverviewVO.DayCell cell = new PenInspectionOverviewVO.DayCell();
        cell.setDate("2026-10-19");
        cell.setDayTag("ABNORMAL");
        overview.setDays(List.of(cell));
        lenient().when(inspectionService.penOverview(anyLong(), any())).thenReturn(overview);

        ReportFormVO reportForm = new ReportFormVO();
        reportForm.setSource("INSPECTION");
        reportForm.setSourceText("已从今天 09:12 的巡检带入栏舍、批次和照片");
        reportForm.setInspectionId(50121L);
        ReportFormVO.ReportPen reportPen = new ReportFormVO.ReportPen();
        reportPen.setPenId(2003L);
        reportPen.setPenName("3 号育肥舍");
        ReportFormVO.ReportBatch reportBatch = new ReportFormVO.ReportBatch();
        reportBatch.setBatchId(3001L);
        reportBatch.setBatchNo("ZM2610-031");
        reportBatch.setCategoryId(101L);
        reportBatch.setStockCount(200);
        reportPen.setBatches(List.of(reportBatch));
        reportForm.setPens(List.of(reportPen));
        SymptomVO symptom = new SymptomVO();
        symptom.setSymptomId(401L);
        symptom.setName("腹泻");
        symptom.setSortNo(1);
        reportForm.setSymptoms(List.of(symptom));
        reportForm.setMaxMediaCount(6);
        lenient().when(reportService.formContext(any())).thenReturn(reportForm);
        lenient().when(reportService.symptoms(anyLong())).thenReturn(List.of(symptom));

        ReportCreateResultVO created = new ReportCreateResultVO();
        created.setReportId(6001L);
        created.setStatus("PENDING");
        created.setAiState("DIAGNOSING");
        created.setPollIntervalSec(3);
        created.setPollMaxSec(90);
        lenient().when(reportService.create(any())).thenReturn(created);
        lenient().when(reportService.findByRequest(any())).thenReturn(created);

        DiagnosisDetailVO diagnosis = new DiagnosisDetailVO();
        diagnosis.setReportId(6001L);
        diagnosis.setVersion(1);
        diagnosis.setStatus("DIAGNOSED");
        diagnosis.setStatusText("已诊断");
        diagnosis.setAiState("DONE");
        diagnosis.setAiStateText("已完成");
        DiagnosisDetailVO.DiagnosisReport reportInfo = new DiagnosisDetailVO.DiagnosisReport();
        reportInfo.setPenId(2003L);
        reportInfo.setPenName("3 号育肥舍");
        reportInfo.setSickCount(12);
        reportInfo.setStockSnapshot(200);
        reportInfo.setSickRate(new BigDecimal("0.0600"));
        diagnosis.setReport(reportInfo);
        DiagnosisResultVO diagnosisResult = new DiagnosisResultVO();
        diagnosisResult.setDiagnosisId(9901L);
        diagnosisResult.setConclusion("发病 12 头，达到存栏的 5% 以上。");
        DiagnosisResultVO.DiagnosisCause cause = new DiagnosisResultVO.DiagnosisCause();
        cause.setRankNo(1);
        cause.setDiseaseName("猪流行性腹泻");
        diagnosisResult.setCauses(List.of(cause));
        diagnosis.setDiagnosis(diagnosisResult);
        DiagnosisDetailVO.DiagnosisPermissions permissions = new DiagnosisDetailVO.DiagnosisPermissions();
        permissions.setCanHandle(true);
        diagnosis.setPermissions(permissions);
        lenient().when(reportService.detail(anyLong())).thenReturn(diagnosis);

        DiagnosisStateVO unavailable = new DiagnosisStateVO();
        unavailable.setReportId(6003L);
        unavailable.setStatus("PENDING");
        unavailable.setAiState("UNAVAILABLE");
        unavailable.setLastFailureText("诊断请求超时");
        lenient().when(reportService.state(anyLong())).thenReturn(unavailable);
        DiagnosisStateVO diagnosing = new DiagnosisStateVO();
        diagnosing.setReportId(6003L);
        diagnosing.setStatus("PENDING");
        diagnosing.setAiState("DIAGNOSING");
        lenient().when(reportService.rediagnose(anyLong())).thenReturn(diagnosing);

        HandleSaveResultVO handle = new HandleSaveResultVO();
        handle.setReportId(6001L);
        handle.setHandleId(7702L);
        handle.setStatus("HANDLED");
        handle.setCulledCount(2);
        HandleSaveResultVO.DeathPrompt deathPrompt = new HandleSaveResultVO.DeathPrompt();
        deathPrompt.setCulledCount(2);
        handle.setDeathPrompt(deathPrompt);
        handle.setVersion(1);
        lenient().when(reportService.saveHandle(anyLong(), any())).thenReturn(handle);

        LinkableReportVO linkable = new LinkableReportVO();
        linkable.setReportId(5988L);
        linkable.setLabel("10月18日 AI 诊断 · 3 号育肥舍");
        lenient().when(reportService.linkable(any())).thenReturn(List.of(linkable));
    }
}
