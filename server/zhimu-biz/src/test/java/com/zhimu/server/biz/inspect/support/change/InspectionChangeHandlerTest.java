package com.zhimu.server.biz.inspect.support.change;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.entity.InspectionItemResult;
import com.zhimu.server.biz.inspect.mapper.InspectReferenceMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionItemResultMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 巡检修改申请 handler：U01 表单与 App mock 一致、U02 校验、审核回写异常头数与删除。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionChangeHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 10, 0).atZone(ZONE).toInstant(), ZONE);

    @Mock private InspectionMapper inspectionMapper;
    @Mock private InspectionItemResultMapper itemResultMapper;
    @Mock private InspectReferenceMapper referenceMapper;
    @Mock private AccountQueryService accountQueryService;
    @Mock private OperationLogRecorder operationLogRecorder;

    private InspectionChangeHandler handler;
    private Inspection inspection;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(CLOCK);
        handler = new InspectionChangeHandler(inspectionMapper, itemResultMapper, referenceMapper,
                accountQueryService, operationLogRecorder);
        inspection = new Inspection();
        inspection.setId(6101L);
        inspection.setFarmId(1001L);
        inspection.setPenId(2003L);
        inspection.setBatchId(3001L);
        inspection.setInspectDate(LocalDate.of(2026, 10, 16));
        inspection.setInspectStatus("DONE");
        inspection.setLocationStatus("ON_SITE");
        inspection.setStockSnapshot(200);
        inspection.setRegistrantId(10001L);
        inspection.setIsBackfill(true);
        inspection.setVersion(2);
        inspection.setDeleted(false);
        when(inspectionMapper.selectByIdIncludingDeleted(6101L)).thenReturn(inspection);
        when(referenceMapper.selectPenName(anyLong())).thenAnswer(invocation ->
                invocation.getArgument(0, Long.class) == 2003L ? "3 号育肥舍" : "未知栏舍");
        when(accountQueryService.getBrief(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return new AccountBrief(id, id == 10001L ? "王建国" : "李明", "13800000000", "NORMAL", false);
        });
        when(itemResultMapper.selectByInspectionId(6101L)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsInspectionAndHealthSourceModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.INSPECTION);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.INSPECTION);
    }

    @Test
    void loadRecordBuildsLocatorTitleSubtitleAndVersion() {
        ChangeRecord record = handler.loadRecord(BizType.INSPECTION, 6101L);

        assertThat(record).isNotNull();
        assertThat(record.bizId()).isEqualTo(6101L);
        assertThat(record.farmId()).isEqualTo(1001L);
        assertThat(record.batchId()).isEqualTo(3001L);
        assertThat(record.penId()).isEqualTo(2003L);
        assertThat(record.recordLocator()).isEqualTo("3 号育肥舍 · 10月16日巡检");
        assertThat(record.recordTitle()).isEqualTo("巡检记录 · 3 号育肥舍");
        assertThat(record.recordSubtitle()).isEqualTo("10月16日 · 王建国补录");
        assertThat(record.recordVersion()).isEqualTo("2");
        assertThat(record.deleted()).isFalse();
        assertThat(record.voided()).isFalse();
    }

    @Test
    void loadRecordReturnsNullWhenMissing() {
        assertThat(handler.loadRecord(BizType.INSPECTION, 9999L)).isNull();
        assertThat(handler.loadRecord(BizType.INSPECTION, null)).isNull();
    }

    @Test
    void requiresRequestOnlyForDoneRecordsOutsideThreeDayWindow() {
        ChangeRecord record = handler.loadRecord(BizType.INSPECTION, 6101L);
        // 10月16日 的记录，10月19日 10:00 仍在第 3 天 24:00 前
        assertThat(handler.requiresRequest(record)).isFalse();

        inspection.setInspectDate(LocalDate.of(2026, 10, 15));
        assertThat(handler.requiresRequest(record)).isTrue();

        inspection.setInspectStatus("PENDING");
        assertThat(handler.requiresRequest(record)).isFalse();
    }

    @Test
    void describeFormMatchesAppMockFields() {
        InspectionItemResult normal = new InspectionItemResult();
        normal.setOptionAbnormal(false);
        InspectionItemResult abnormal = new InspectionItemResult();
        abnormal.setOptionAbnormal(true);
        when(itemResultMapper.selectByInspectionId(6101L)).thenReturn(List.of(normal, abnormal, normal));

        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.INSPECTION, 6101L));

        assertThat(form.fields()).hasSize(1);
        ChangeField field = form.fields().get(0);
        assertThat(field.fieldCode()).isEqualTo("abnormal_count");
        assertThat(field.fieldName()).isEqualTo("异常头数");
        assertThat(field.value()).isEqualTo("1");
        assertThat(field.valueUnit()).isEqualTo("头");
        assertThat(field.inputType()).isEqualTo(ChangeField.NUMBER);
        assertThat(field.max()).isEqualByComparingTo("200");
        assertThat(field.min()).isEqualByComparingTo("0");
        assertThat(field.hint()).isEqualTo("存栏 200 头");
        assertThat(field.recalcTags()).containsExactly("巡检统计");
        assertThat(form.recordCard()).extracting(com.zhimu.server.biz.common.change.ChangeCardItem::label)
                .containsExactly("结果", "位置");
        assertThat(form.recordCard().get(0).value()).isEqualTo("1 项异常");
        assertThat(form.recordCard().get(1).value()).isEqualTo("现场");
        assertThat(form.linkedRules()).containsExactly("修改异常头数会同步更新当天巡检统计");
        assertThat(form.voidAllowed()).isFalse();
        assertThat(form.deleteAllowed()).isFalse();
    }

    @Test
    void validateChangeAcceptsNewAbnormalCount() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.INSPECTION, 6101L));

        ChangeValidation validation = handler.validateChange(handler.loadRecord(BizType.INSPECTION, 6101L),
                form, ChangeType.UPDATE, "abnormal_count", "5");

        assertThat(validation.fieldCode()).isEqualTo("abnormal_count");
        assertThat(validation.fieldName()).isEqualTo("异常头数");
        assertThat(validation.unit()).isEqualTo("头");
        assertThat(validation.beforeValue()).isEqualTo("0");
        assertThat(validation.afterValue()).isEqualTo("5");
        assertThat(validation.afterDisplay()).isEqualTo("5 头");
        assertThat(validation.impacts()).containsExactly(ChangeImpact.STATS);
    }

    @Test
    void validateChangeRejectsBadValues() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.INSPECTION, 6101L));
        ChangeRecord record = handler.loadRecord(BizType.INSPECTION, 6101L);

        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE, "unknown", "1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请选择要修改的项目");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE, "abnormal_count", "abc"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请填写整数");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE, "abnormal_count", "201"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能超过 200 头");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE, "abnormal_count", "0"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("新值与原值相同");
    }

    @Test
    void applyUpdateWritesAbnormalFlagAndAuditLog() {
        ChangeRequest request = request("UPDATE", "abnormal_count",
                "{\"after\":{\"abnormal_count\":{\"value\":\"5\"}}}");
        request.setReason("现场确认 5 头异常");

        ChangeApplyResult result = handler.apply(request, handler.loadRecord(BizType.INSPECTION, 6101L), 90001L);

        assertThat(result.recalcCount()).isEqualTo(1);
        assertThat(result.recalcSummary()).isEqualTo("已重新计算巡检统计");
        ArgumentCaptor<Inspection> captor = ArgumentCaptor.forClass(Inspection.class);
        verify(inspectionMapper).updateById(captor.capture());
        Inspection saved = captor.getValue();
        assertThat(saved.getIsAbnormal()).isTrue();
        assertThat(saved.getAbnormalSource()).isEqualTo("MANUAL");
        assertThat(saved.getAbnormalSummary()).isEqualTo("异常 5 头");
        assertThat(saved.getIsManualMarked()).isTrue();
        assertThat(saved.getManualReason()).isEqualTo("现场确认 5 头异常");
        assertThat(saved.getManualMarkedBy()).isEqualTo(90001L);
        assertThat(saved.getUpdatedBy()).isEqualTo(90001L);

        ArgumentCaptor<OperationLogEntry> log = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(operationLogRecorder).record(log.capture());
        assertThat(log.getValue().getOpType()).isEqualTo("RECORD_EDIT");
        assertThat(log.getValue().getOpAction()).isEqualTo("UPDATE");
        assertThat(log.getValue().getBizType()).isEqualTo("INSPECTION");
        assertThat(log.getValue().getChangeRequestId()).isEqualTo(9001L);
    }

    @Test
    void applyUpdateToZeroClearsAbnormalStateAndReadsSummaryAgain() {
        inspection.setIsAbnormal(true);
        inspection.setAbnormalSummary("异常 5 头");
        inspection.setIsManualMarked(true);
        ChangeRequest request = request("UPDATE", "abnormal_count",
                "{\"after\":{\"abnormal_count\":{\"value\":\"0\"}}}");

        handler.apply(request, handler.loadRecord(BizType.INSPECTION, 6101L), 90001L);

        ArgumentCaptor<Inspection> captor = ArgumentCaptor.forClass(Inspection.class);
        verify(inspectionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIsAbnormal()).isFalse();
        assertThat(captor.getValue().getAbnormalSummary()).isNull();
        assertThat(captor.getValue().getIsManualMarked()).isFalse();
    }

    @Test
    void applyDeleteMarksRecordDeleted() {
        ChangeRequest request = request("DELETE", null, "{\"after\":{}}");

        ChangeApplyResult result = handler.apply(request, handler.loadRecord(BizType.INSPECTION, 6101L), 90001L);

        assertThat(result.recalcSummary()).isEqualTo("已重新计算巡检统计");
        verify(inspectionMapper).deleteById(6101L);
        verify(inspectionMapper, never()).updateById(any(Inspection.class));
        ArgumentCaptor<OperationLogEntry> log = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(operationLogRecorder).record(log.capture());
        assertThat(log.getValue().getOpAction()).isEqualTo("DELETE");
    }

    private static ChangeRequest request(String changeType, String fieldCode, String afterJson) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9001L);
        request.setBizType("INSPECTION");
        request.setBizId(6101L);
        request.setFarmId(1001L);
        request.setChangeType(changeType);
        request.setFieldCode(fieldCode);
        request.setFieldName("异常头数");
        request.setAfterJson(afterJson);
        request.setReason("登记有误");
        request.setSubmittedBy(10001L);
        request.setSubmitterRole("OWNER");
        return request;
    }
}
