package com.zhimu.server.biz.inspect.service.impl;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.DictQueryService;
import com.zhimu.server.admin.base.vo.BaseDictItemVO;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.inspect.InspectFixtures;
import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.entity.AiDiagnosis;
import com.zhimu.server.biz.inspect.entity.DiagnosisHandle;
import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.mapper.AbnormalReportMapper;
import com.zhimu.server.biz.inspect.mapper.AbnormalReportSymptomMapper;
import com.zhimu.server.biz.inspect.mapper.AiDiagnosisAdviceMapper;
import com.zhimu.server.biz.inspect.mapper.AiDiagnosisCauseMapper;
import com.zhimu.server.biz.inspect.mapper.AiDiagnosisMapper;
import com.zhimu.server.biz.inspect.mapper.DiagnosisHandleMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import com.zhimu.server.biz.inspect.query.LinkableQuery;
import com.zhimu.server.biz.inspect.query.LinkableRow;
import com.zhimu.server.biz.inspect.query.ReportFormQuery;
import com.zhimu.server.biz.inspect.request.AbnormalReportCreateRequest;
import com.zhimu.server.biz.inspect.request.HandleSaveRequest;
import com.zhimu.server.biz.inspect.service.DiagnosisService;
import com.zhimu.server.biz.inspect.support.InspectSupport;
import com.zhimu.server.biz.inspect.vo.DiagnosisStateVO;
import com.zhimu.server.biz.inspect.vo.HandleSaveResultVO;
import com.zhimu.server.biz.inspect.vo.LinkableReportVO;
import com.zhimu.server.biz.inspect.vo.ReportCreateResultVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbnormalReportServiceImplTest {

    @Mock private AbnormalReportMapper reportMapper;
    @Mock private AbnormalReportSymptomMapper symptomMapper;
    @Mock private AiDiagnosisMapper diagnosisMapper;
    @Mock private AiDiagnosisCauseMapper causeMapper;
    @Mock private AiDiagnosisAdviceMapper adviceMapper;
    @Mock private DiagnosisHandleMapper handleMapper;
    @Mock private InspectionMapper inspectionMapper;
    @Mock private InspectSupport support;
    @Mock private BizMediaService bizMediaService;
    @Mock private DictQueryService dictQueryService;
    @Mock private BaseDataQueryService baseDataQueryService;
    @Mock private BreedQueryService breedQueryService;
    @Mock private DiagnosisService diagnosisService;

    private AbnormalReportServiceImpl service;
    private BatchInfo batch;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(InspectFixtures.CLOCK);
        InspectFixtures.loginOwner();
        service = new AbnormalReportServiceImpl(reportMapper, symptomMapper, diagnosisMapper, causeMapper,
                adviceMapper, handleMapper, inspectionMapper, support, bizMediaService, dictQueryService,
                baseDataQueryService, breedQueryService, diagnosisService);
        batch = InspectFixtures.batch(3001L, "ZM2610-031", 101L, 1101L, LocalDate.of(2026, 10, 1), 78,
                200, 2003L);
        lenient().when(support.member()).thenAnswer(invocation -> FarmContext.require());
        lenient().when(support.farmId()).thenReturn(1001L);
        lenient().when(support.farmName()).thenReturn("东山猪场");
        lenient().when(support.userId()).thenReturn(10001L);
        lenient().when(support.penName(anyLong())).thenReturn("3 号育肥舍");
        lenient().when(support.requirePen(anyLong()))
                .thenAnswer(invocation -> InspectFixtures.pen(invocation.getArgument(0), "YF-03",
                        "3 号育肥舍", 200));
        lenient().when(support.requireActiveBatch(anyLong())).thenReturn(batch);
        lenient().when(support.batchPenStock(anyLong(), anyLong())).thenReturn(200);
        lenient().when(support.memberMap()).thenReturn(Map.of(
                10001L, InspectFixtures.member(10001L, "王建国", "OWNER"),
                10002L, InspectFixtures.member(10002L, "刘芳", "MANAGER"),
                10003L, InspectFixtures.member(10003L, "李明", "STAFF"),
                10004L, InspectFixtures.member(10004L, "张伟", "STAFF")));
        lenient().when(support.memberList()).thenReturn(List.of(
                InspectFixtures.member(10001L, "王建国", "OWNER"),
                InspectFixtures.member(10002L, "刘芳", "MANAGER")));
        lenient().when(support.userName(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            return id == 10001L ? "王建国" : id == 10002L ? "刘芳" : id == 10003L ? "李明" : "张伟";
        });
        BaseDictItemVO symptom = new BaseDictItemVO();
        symptom.setId(401L);
        symptom.setName("腹泻");
        symptom.setSortNo(1);
        lenient().when(dictQueryService.listForApp(eq("SYMPTOM"), any(), eq(101L), anyLong()))
                .thenReturn(List.of(symptom));
        lenient().when(symptomMapper.selectByReportId(anyLong())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        InspectFixtures.logout();
    }

    @Test
    void staffCannotViewOtherPersonsReport() {
        InspectFixtures.loginStaff(10003L, Set.of(2003L));
        AbnormalReport report = InspectFixtures.report(6002L, "DIAGNOSED", "DONE", 10004L, 2003L, 3001L, null);
        when(reportMapper.selectById(6002L)).thenReturn(report);

        assertThatThrownBy(() -> service.detail(6002L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.ROLE_FORBIDDEN.getCode());
    }

    @Test
    void stateReturnsAiStateAndFailureText() {
        AbnormalReport report = InspectFixtures.report(6003L, "PENDING", "UNAVAILABLE", 10003L, 2003L, 3001L,
                null);
        when(reportMapper.selectById(6003L)).thenReturn(report);
        when(diagnosisService.lastFailureText(report)).thenReturn("诊断请求超时");

        DiagnosisStateVO state = service.state(6003L);

        assertThat(state.getReportId()).isEqualTo(6003L);
        assertThat(state.getAiState()).isEqualTo("UNAVAILABLE");
        assertThat(state.getLastFailureText()).isEqualTo("诊断请求超时");
        assertThat(state.isAutoRetryPending()).isTrue();
    }

    @Test
    void rediagnoseWhileDiagnosingReturns40903() {
        AbnormalReport report = InspectFixtures.report(6003L, "PENDING", "DIAGNOSING", 10003L, 2003L, 3001L,
                null);
        when(reportMapper.selectById(6003L)).thenReturn(report);

        assertThatThrownBy(() -> service.rediagnose(6003L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.STATE_CHANGED.getCode());
    }

    @Test
    void rediagnoseCreatesManualRetryAndStartsAsyncExecution() {
        AbnormalReport report = InspectFixtures.report(6003L, "PENDING", "FAILED", 10003L, 2003L, 3001L, null);
        when(reportMapper.selectById(6003L)).thenReturn(report);
        AiDiagnosis attempt = InspectFixtures.diagnosis(9903L, 6003L, "RUNNING");
        when(diagnosisService.createAttempt(report, "MANUAL_RETRY", 10001L)).thenAnswer(invocation -> {
            report.setAiState("DIAGNOSING");
            return attempt;
        });

        DiagnosisStateVO state = service.rediagnose(6003L);

        assertThat(state.getAiState()).isEqualTo("DIAGNOSING");
        verify(diagnosisService).executeAsync(9903L);
    }

    @Test
    void saveHandleOnClosedReportReturns40903() {
        AbnormalReport report = InspectFixtures.report(6001L, "CLOSED", "DONE", 10003L, 2003L, 3001L, 50121L);
        when(reportMapper.selectById(6001L)).thenReturn(report);

        assertThatThrownBy(() -> service.saveHandle(6001L, handleRequest(9, 3, true)))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.STATE_CHANGED.getCode());
    }

    @Test
    void saveHandleClosesReportWhenTotalEqualsSickCount() {
        AbnormalReport report = InspectFixtures.report(6001L, "DIAGNOSED", "DONE", 10003L, 2003L, 3001L, 50121L);
        report.setCurrentDiagnosisId(9901L);
        when(reportMapper.selectById(6001L)).thenReturn(report);
        AiDiagnosis diagnosis = InspectFixtures.diagnosis(9901L, 6001L, "SUCCESS");
        when(diagnosisMapper.selectById(9901L)).thenReturn(diagnosis);
        when(handleMapper.selectByReportId(6001L)).thenReturn(null);
        when(handleMapper.insert(any( DiagnosisHandle.class))).thenAnswer(invocation -> {
            DiagnosisHandle handle = invocation.getArgument(0);
            handle.setId(7702L);
            return 1;
        });

        HandleSaveResultVO result = service.saveHandle(6001L, handleRequest(9, 3, true));

        assertThat(result.getStatus()).isEqualTo("CLOSED");
        assertThat(result.isClosed()).isTrue();
        assertThat(result.getHandleId()).isEqualTo(7702L);
        assertThat(result.getDeathPrompt()).isNotNull();
        assertThat(result.getDeathPrompt().getCulledCount()).isEqualTo(3);
        assertThat(report.getClosedAt()).isNotNull();
    }

    @Test
    void createReportStartsFirstDiagnosisAttempt() {
        AbnormalReportCreateRequest request = new AbnormalReportCreateRequest();
        request.setClientRequestId("req-6001");
        request.setPenId(2003L);
        request.setBatchId(3001L);
        request.setSickCount(12);
        request.setSymptomIds(List.of(401L));
        when(reportMapper.selectByClientRequestId(1001L, "req-6001")).thenReturn(null);
        when(reportMapper.countSameDay(1001L, 3001L, LocalDate.of(2026, 10, 19))).thenReturn(0);
        when(reportMapper.insert(any(AbnormalReport.class))).thenAnswer(invocation -> {
            AbnormalReport report = invocation.getArgument(0);
            report.setId(6001L);
            return 1;
        });
        AiDiagnosis attempt = InspectFixtures.diagnosis(9901L, 6001L, "RUNNING");
        when(diagnosisService.createAttempt(any(AbnormalReport.class), eq("SUBMIT"), eq(null)))
                .thenReturn(attempt);

        ReportCreateResultVO result = service.create(request);

        assertThat(result.getReportId()).isEqualTo(6001L);
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getAiState()).isEqualTo("DIAGNOSING");
        verify(symptomMapper).insert(any(com.zhimu.server.biz.inspect.entity.AbnormalReportSymptom.class));
        verify(diagnosisService).executeAsync(9901L);
    }

    @Test
    void cannotReportBackfilledInspection() {
        InspectFixtures.loginStaff(10003L, Set.of(2003L));
        Inspection inspection = InspectFixtures.inspection(50081L, 2003L, 3001L,
                LocalDate.of(2026, 10, 16), "DONE", true, 10003L, null);
        inspection.setIsBackfill(true);
        when(inspectionMapper.selectById(50081L)).thenReturn(inspection);
        ReportFormQuery query = new ReportFormQuery();
        query.setInspectionId(50081L);

        assertThatThrownBy(() -> service.formContext(query))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    void linkableReturnsLabelWithPenAndMonthDay() {
        LinkableRow row = new LinkableRow();
        row.setReportId(5988L);
        row.setReportedAt(LocalDateTime.of(2026, 10, 18, 15, 20));
        row.setPenId(2003L);
        row.setPenName("3 号育肥舍");
        row.setBatchId(3001L);
        row.setBatchNo("ZM2610-031");
        row.setStatus("DIAGNOSED");
        when(reportMapper.selectLinkable(eq(1001L), eq(3001L), eq(null), any()))
                .thenReturn(List.of(row));
        LinkableQuery query = new LinkableQuery();
        query.setBatchId(3001L);

        List<LinkableReportVO> result = service.linkable(query);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo("10月18日 AI 诊断 · 3 号育肥舍");
    }

    private static HandleSaveRequest handleRequest(int cured, int culled, boolean confirmClose) {
        HandleSaveRequest request = new HandleSaveRequest();
        request.setDiagnostician("赵磊（执业兽医）");
        request.setTreatment("隔离、消毒、补液");
        request.setUsedDrug(true);
        request.setCuredCount(cured);
        request.setCulledCount(culled);
        request.setAiMatch("MATCH");
        request.setConfirmClose(confirmClose);
        return request;
    }
}
