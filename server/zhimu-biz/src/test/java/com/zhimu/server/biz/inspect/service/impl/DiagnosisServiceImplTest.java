package com.zhimu.server.biz.inspect.service.impl;

import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.inspect.InspectFixtures;
import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.entity.AbnormalReportSymptom;
import com.zhimu.server.biz.inspect.entity.AiDiagnosis;
import com.zhimu.server.biz.inspect.mapper.AbnormalReportMapper;
import com.zhimu.server.biz.inspect.mapper.AbnormalReportSymptomMapper;
import com.zhimu.server.biz.inspect.mapper.AiDiagnosisMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionItemResultMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import com.zhimu.server.biz.inspect.support.DiagnosisNotifier;
import com.zhimu.server.biz.inspect.support.InspectSupport;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.integration.ai.AiChatRequest;
import com.zhimu.server.integration.ai.AiChatResult;
import com.zhimu.server.integration.ai.AiGateway;
import com.zhimu.server.integration.ai.BannedDrugGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiagnosisServiceImplTest {

    private static final String VALID_JSON = """
            {"conclusion":"疑似猪流行性腹泻","urgency":"ATTENTION",
             "causes":[{"disease_name":"猪流行性腹泻","likelihood":"HIGH","confidence":0.72}],
             "advices":["立即隔离","栏舍消毒"],"suggest_vet":true}
            """;

    @Mock private AiGateway aiGateway;
    @Mock private AbnormalReportMapper reportMapper;
    @Mock private AbnormalReportSymptomMapper symptomMapper;
    @Mock private AiDiagnosisMapper diagnosisMapper;
    @Mock private InspectionMapper inspectionMapper;
    @Mock private InspectionItemResultMapper itemResultMapper;
    @Mock private BatchQueryService batchQueryService;
    @Mock private BaseDataQueryService baseDataQueryService;
    @Mock private BreedQueryService breedQueryService;
    @Mock private BizMediaService bizMediaService;
    @Mock private InspectSupport support;
    @Mock private DiagnosisWriter writer;
    @Mock private ObjectProvider<BannedDrugGuard> bannedDrugGuard;
    @Mock private DiagnosisNotifier notifier;

    private DiagnosisServiceImpl service;
    private AiDiagnosis diagnosis;
    private AbnormalReport report;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(InspectFixtures.CLOCK);
        service = new DiagnosisServiceImpl(aiGateway, reportMapper, symptomMapper, diagnosisMapper,
                inspectionMapper, itemResultMapper, batchQueryService, baseDataQueryService, breedQueryService,
                bizMediaService, support, writer, bannedDrugGuard, notifier);
        diagnosis = InspectFixtures.diagnosis(9901L, 6001L, "RUNNING");
        report = InspectFixtures.report(6001L, "PENDING", "DIAGNOSING", 10003L, 2003L, 3001L, 50121L);
        report.setSickRate(new BigDecimal("0.0600"));
        lenient().when(diagnosisMapper.selectById(9901L)).thenReturn(diagnosis);
        lenient().when(reportMapper.selectById(6001L)).thenReturn(report);
        lenient().when(aiGateway.isEnabled()).thenReturn(true);
        BatchInfo batch = InspectFixtures.batch(3001L, "ZM2610-031", 101L, 1101L,
                LocalDate.of(2026, 10, 1), 78, 200, 2003L);
        lenient().when(batchQueryService.requireBatch(3001L)).thenReturn(batch);
        lenient().when(baseDataQueryService.getCategory(101L)).thenReturn(
                new CategoryInfo(101L, "育肥猪", "PIG", "猪", "HEAD", "头", false, true));
        lenient().when(breedQueryService.info(1101L))
                .thenReturn(new BreedQueryService.BreedInfo(1101L, 101L, "杜长大", null, null, null, null, true));
        lenient().when(support.farmName()).thenReturn("东山猪场");
        lenient().when(support.penName(2003L)).thenReturn("3 号育肥舍");
        AbnormalReportSymptom symptom = new AbnormalReportSymptom();
        symptom.setReportId(6001L);
        symptom.setSymptomId(401L);
        symptom.setSymptomName("腹泻");
        symptom.setSortNo(1);
        lenient().when(symptomMapper.selectByReportId(6001L)).thenReturn(List.of(symptom));
        lenient().when(bizMediaService.list(any(), eq(6001L))).thenReturn(List.of(
                MediaItemVO.builder().fileId(9005L).mediaType("IMAGE").url("https://file/1.jpg").build()));
        java.util.Map<Long, String> recipients = new java.util.LinkedHashMap<>();
        recipients.put(10003L, "李明");
        recipients.put(10001L, "王建国");
        lenient().when(notifier.recipients(any())).thenReturn(recipients);
        lenient().when(bannedDrugGuard.getIfAvailable()).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void executeCallsGatewayAndWritesSuccess() {
        when(aiGateway.chat(any(AiChatRequest.class))).thenReturn(AiChatResult.builder()
                .content(VALID_JSON).model("qwen3.8-max").provider("dashscope").costMs(1200).build());

        service.execute(9901L);

        verify(writer).success(eq(9901L), eq("qwen3.8-max"), anyLong(), any(), eq(VALID_JSON),
                eq("李明、王建国"));
    }

    @Test
    void successSendsDiagnosisMessageToNotifiedMembers() {
        when(aiGateway.chat(any(AiChatRequest.class))).thenReturn(AiChatResult.builder()
                .content(VALID_JSON).model("qwen3.8-max").provider("dashscope").costMs(1200).build());
        when(writer.success(eq(9901L), any(), anyLong(), any(), any(), any())).thenReturn(report);

        service.execute(9901L);

        verify(notifier).diagnosed(eq(report), any(), eq("3 号育肥舍"), eq("ZM2610-031"), eq("猪流行性腹泻"));
    }

    @Test
    void failureSendsFailedMessageOnce() {
        when(aiGateway.chat(any(AiChatRequest.class))).thenThrow(new BizException(ErrorCode.AI_TIMEOUT));
        when(writer.failure(eq(9901L), eq("TIMEOUT"), anyLong(), any(), any())).thenReturn(report);

        service.execute(9901L);

        verify(notifier).failed(eq(report), any(), eq("3 号育肥舍"), eq("ZM2610-031"), any());
    }

    @Test
    void invalidOutputWritesInvalidOutputFailure() {
        when(aiGateway.chat(any(AiChatRequest.class))).thenReturn(AiChatResult.builder()
                .content("不是 JSON").model("qwen3.8-max").build());

        service.execute(9901L);

        verify(writer).failure(eq(9901L), eq("INVALID_OUTPUT"), anyLong(), eq("不是 JSON"), eq("李明、王建国"));
    }

    @Test
    void gatewayTimeoutWritesTimeoutFailure() {
        when(aiGateway.chat(any(AiChatRequest.class))).thenThrow(new RuntimeException("请求超时"));

        service.execute(9901L);

        verify(writer).failure(eq(9901L), eq("TIMEOUT"), anyLong(), any(), any());
    }

    @Test
    void disabledGatewayWritesServiceUnavailable() {
        when(aiGateway.isEnabled()).thenReturn(false);

        service.execute(9901L);

        verify(writer).failure(eq(9901L), eq("SERVICE_UNAVAILABLE"), eq(0L), eq(null), eq("李明、王建国"));
    }

    @Test
    void gateway50201WritesServiceUnavailable() {
        when(aiGateway.chat(any(AiChatRequest.class))).thenThrow(new BizException(ErrorCode.AI_UNAVAILABLE));

        service.execute(9901L);

        verify(writer).failure(eq(9901L), eq("SERVICE_UNAVAILABLE"), anyLong(), any(), any());
    }

    @Test
    void gateway50401WritesTimeout() {
        when(aiGateway.chat(any(AiChatRequest.class))).thenThrow(new BizException(ErrorCode.AI_TIMEOUT));

        service.execute(9901L);

        verify(writer).failure(eq(9901L), eq("TIMEOUT"), anyLong(), any(), any());
    }

    @Test
    void createAttemptSetsReportDiagnosingAndAttemptNumber() {
        when(diagnosisMapper.maxAttemptNo(6001L)).thenReturn(0);
        when(diagnosisMapper.insert(any(AiDiagnosis.class))).thenAnswer(invocation -> {
            AiDiagnosis entity = invocation.getArgument(0);
            entity.setId(9901L);
            return 1;
        });
        report.setAiState("UNAVAILABLE");

        AiDiagnosis attempt = service.createAttempt(report, "MANUAL_RETRY", 10001L);

        assertThat(attempt.getId()).isEqualTo(9901L);
        assertThat(attempt.getAttemptNo()).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo("RUNNING");
        assertThat(attempt.getTriggerType()).isEqualTo("MANUAL_RETRY");
        assertThat(report.getAiState()).isEqualTo("DIAGNOSING");
        verify(reportMapper).updateById(report);
    }

    @Test
    void lastFailureTextMapsFailReason() {
        AiDiagnosis failed = InspectFixtures.diagnosis(9903L, 6001L, "TIMEOUT");
        failed.setFailReason("TIMEOUT");
        when(diagnosisMapper.selectLatestByReportId(6001L)).thenReturn(failed);
        report.setAiState("UNAVAILABLE");

        assertThat(service.lastFailureText(report)).isEqualTo("诊断请求超时");
    }
}
