package com.zhimu.server.biz.herd.support.change;

import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeCardItem;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchEvent;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.mapper.BatchEventMapper;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.biz.herd.mapper.BatchTimelineMapper;
import com.zhimu.server.biz.herd.mapper.PenMapper;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** 入栏记录修改申请 handler：字段与 App Mock（change_form_batch_entry.json）一致。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchEntryChangeHandlerTest {

    @Mock private BatchMapper batchMapper;
    @Mock private BatchEventMapper eventMapper;
    @Mock private BatchPenMapper batchPenMapper;
    @Mock private BatchTimelineMapper timelineMapper;
    @Mock private PenMapper penMapper;
    @Mock private HerdSupport herdSupport;
    @Mock private OperationLogRecorder recorder;

    private BatchEntryChangeHandler handler;
    private final List<OperationLogEntry> logs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ChangeTableInfo.init(com.zhimu.server.biz.herd.entity.Batch.class,
                com.zhimu.server.biz.herd.entity.BatchEvent.class,
                com.zhimu.server.biz.herd.entity.BatchPen.class,
                com.zhimu.server.biz.herd.entity.BatchTimeline.class,
                com.zhimu.server.biz.herd.entity.Pen.class);
        HerdChangeSupport support = new HerdChangeSupport(batchMapper, eventMapper, batchPenMapper, timelineMapper,
                penMapper, herdSupport, recorder);
        handler = new BatchEntryChangeHandler(support, batchMapper, eventMapper);
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 19, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        when(herdSupport.pensByIds(List.of(2003L))).thenReturn(List.of(pen(2003L, "3 号育肥舍")));
        when(herdSupport.breedName(1101L)).thenReturn("杜长大");
        when(herdSupport.userName(10001L)).thenReturn("王建国");
        when(herdSupport.farmUnit(1001L)).thenReturn("HEAD");
        when(herdSupport.unitText("HEAD")).thenReturn("头");
        when(eventMapper.selectById(901L)).thenReturn(entry(205, LocalDate.of(2026, 10, 1)));
        when(batchMapper.selectByIdIncludingDeleted(3001L)).thenReturn(batch());
        doAnswer(invocation -> {
            logs.add(invocation.getArgument(0));
            return null;
        }).when(recorder).record(any());
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsEntryTypeAndPenStockModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.BATCH_ENTRY);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.PEN_STOCK);
    }

    @Test
    void loadRecordBuildsCardLocatorFromBatchEntryEvent() {
        ChangeRecord record = handler.loadRecord(BizType.BATCH_ENTRY, 901L);

        assertThat(record).isNotNull();
        assertThat(record.bizType()).isEqualTo(BizType.BATCH_ENTRY);
        assertThat(record.bizId()).isEqualTo(901L);
        assertThat(record.farmId()).isEqualTo(1001L);
        assertThat(record.batchId()).isEqualTo(3001L);
        assertThat(record.penId()).isEqualTo(2003L);
        assertThat(record.recordLocator()).isEqualTo("ZM2610-031 · 10月1日入栏");
        assertThat(record.recordTitle()).isEqualTo("入栏记录 · 3 号育肥舍");
        assertThat(record.recordSubtitle()).isEqualTo("杜长大 · 10月1日 · 王建国登记");
        assertThat(record.recordVersion()).isEqualTo("7");
        assertThat(record.deleted()).isFalse();
        assertThat(record.voided()).isFalse();
    }

    @Test
    void loadRecordFallsBackToBatchIdAndReturnsNullWhenMissing() {
        when(eventMapper.selectEntry(3001L)).thenReturn(entry(205, LocalDate.of(2026, 10, 1)));

        assertThat(handler.loadRecord(BizType.BATCH_ENTRY, 3001L)).isNotNull();
        assertThat(handler.loadRecord(BizType.BATCH_ENTRY, 404L)).isNull();
    }

    @Test
    void describeFormMatchesAppMockFields() {
        ChangeRecord record = handler.loadRecord(BizType.BATCH_ENTRY, 901L);

        ChangeForm form = handler.describeForm(record);

        assertThat(form.recordCard()).extracting(ChangeCardItem::label)
                .containsExactly("入栏数量", "入栏日龄", "平均体重");
        assertThat(form.recordCard()).extracting(ChangeCardItem::value)
                .containsExactly("205 头", "78 天", "45 千克");
        ChangeField field = form.fieldOf("head_count");
        assertThat(field).isNotNull();
        assertThat(field.fieldName()).isEqualTo("入栏数量");
        assertThat(field.value()).isEqualTo("205");
        assertThat(field.valueUnit()).isEqualTo("头");
        assertThat(field.inputType()).isEqualTo(ChangeField.NUMBER);
        assertThat(field.min()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(field.max()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(field.recalcTags()).containsExactly("存栏", "成本");
        assertThat(form.linkedRules()).containsExactly("修改入栏数量会重新计算存栏、死亡率与成本分摊");
        assertThat(form.deleteAllowed()).isFalse();
        assertThat(form.voidAllowed()).isFalse();
        assertThat(form.reviewTip()).isNotBlank();
    }

    @Test
    void requiresRequestWhenBatchHasFollowUpOrWindowClosed() {
        when(batchMapper.countFollowUpEvents(3001L)).thenReturn(1L);
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.BATCH_ENTRY, 901L))).isTrue();

        // 无后续记录且仍在 3 天直接修改期内：可以直接修改，不接受申请
        when(batchMapper.countFollowUpEvents(3001L)).thenReturn(0L);
        when(eventMapper.selectById(901L)).thenReturn(entry(205, LocalDate.of(2026, 10, 18)));
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.BATCH_ENTRY, 901L))).isFalse();

        // 超过 3 天直接修改期：必须提交申请
        when(eventMapper.selectById(901L)).thenReturn(entry(205, LocalDate.of(2026, 9, 1)));
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.BATCH_ENTRY, 901L))).isTrue();
    }

    @Test
    void validateChangeAcceptsNewCountAndRejectsSameOrOutOfRange() {
        ChangeRecord record = handler.loadRecord(BizType.BATCH_ENTRY, 901L);
        ChangeForm form = handler.describeForm(record);

        ChangeValidation ok = handler.validateChange(record, form, ChangeType.UPDATE, "head_count", "210");
        assertThat(ok.fieldCode()).isEqualTo("head_count");
        assertThat(ok.beforeValue()).isEqualTo("205");
        assertThat(ok.beforeDisplay()).isEqualTo("205 头");
        assertThat(ok.afterValue()).isEqualTo("210");
        assertThat(ok.afterDisplay()).isEqualTo("210 头");
        assertThat(ok.recalcTags()).containsExactly("存栏", "成本");

        assertThat(newValue(form, "205").getCode()).isEqualTo(40001);
        assertThat(newValueMessage(record, form, "0")).contains("不能小于 1 头");
        assertThat(newValueMessage(record, form, "10001")).contains("不能超过 10000 头");
        assertThat(newValueMessage(record, form, "2.5")).contains("需为大于 0 的整数");
    }

    @Test
    void applyUpdatesEntryCountBatchStockAndTimeline() {
        ChangeRecord record = handler.loadRecord(BizType.BATCH_ENTRY, 901L);
        when(batchPenMapper.addStock(anyLong(), anyLong(), anyInt(), any(), any())).thenReturn(1);
        when(penMapper.addStock(anyLong(), anyInt(), any())).thenReturn(1);
        when(batchMapper.update(any(), any())).thenReturn(1);
        when(eventMapper.update(any(), any())).thenReturn(1);
        when(timelineMapper.update(any(), any())).thenReturn(1);

        ChangeApplyResult result = handler.apply(request("head_count", "210"), record, 9001L);

        assertThat(result.recalcCount()).isEqualTo(3);
        assertThat(result.recalcSummary()).contains("批次存栏");
        assertThat(logs).hasSize(1);
        OperationLogEntry log = logs.get(0);
        assertThat(log.getOpType()).isEqualTo("RECORD_EDIT");
        assertThat(log.getOpAction()).isEqualTo("UPDATE");
        assertThat(log.getBizType()).isEqualTo("BATCH_ENTRY");
        assertThat(log.getBizId()).isEqualTo(901L);
        assertThat(log.getChangeRequestId()).isEqualTo(9001L);
        assertThat(log.getOperatorId()).isEqualTo(5001L);
        assertThat(log.getFarmId()).isEqualTo(1001L);
    }

    @Test
    void validateForApproveRejectsNegativeStock() {
        Batch batch = batch();
        batch.setCurrentCount(2);
        when(batchMapper.selectByIdIncludingDeleted(3001L)).thenReturn(batch);
        when(batchPenMapper.selectList(any())).thenReturn(List.of(batchPen()));

        BizException error = (BizException) catchException(
                () -> handler.validateForApprove(request("head_count", "1"), handler.loadRecord(BizType.BATCH_ENTRY, 901L)));

        assertThat(error.getCode()).isEqualTo(ErrorCode.COUNT_EXCEEDED.getCode());
    }

    private BizException newValue(ChangeForm form, String value) {
        return (BizException) catchException(
                () -> handler.validateChange(handler.loadRecord(BizType.BATCH_ENTRY, 901L), form,
                        ChangeType.UPDATE, "head_count", value));
    }

    private String newValueMessage(ChangeRecord record, ChangeForm form, String value) {
        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, ChangeType.UPDATE, "head_count", value));
        assertThat(error.getCode()).isEqualTo(40001);
        return error.getMessage();
    }

    private static ChangeRequest request(String fieldCode, String value) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9001L);
        request.setChangeType("UPDATE");
        request.setFieldCode(fieldCode);
        request.setFarmId(1001L);
        request.setSubmittedBy(5001L);
        request.setSubmitterRole("OWNER");
        request.setReason("登记数量填错");
        request.setAfterJson("{\"after\":{\"" + fieldCode + "\":{\"name\":\"入栏数量\",\"value\":\"" + value
                + "\",\"display\":\"" + value + " 头\",\"unit\":\"头\"}}}");
        return request;
    }

    private static Batch batch() {
        Batch batch = new Batch();
        batch.setId(3001L);
        batch.setFarmId(1001L);
        batch.setBatchNo("ZM2610-031");
        batch.setBreedId(1101L);
        batch.setEntryCount(205);
        batch.setCurrentCount(200);
        batch.setEntryAgeDays(78);
        batch.setEntryAvgWeight(new BigDecimal("45.00"));
        batch.setStatus("ACTIVE");
        batch.setVersion(7);
        batch.setDeleted(false);
        return batch;
    }

    private static BatchEvent entry(int quantity, LocalDate date) {
        BatchEvent event = new BatchEvent();
        event.setId(901L);
        event.setFarmId(1001L);
        event.setBatchId(3001L);
        event.setEventType("ENTRY");
        event.setEventDate(date);
        event.setPenId(2003L);
        event.setQuantity(quantity);
        event.setStatus("EFFECTIVE");
        event.setVersion(0);
        event.setDeleted(false);
        event.setCreatedBy(10001L);
        return event;
    }

    private static com.zhimu.server.biz.herd.entity.BatchPen batchPen() {
        com.zhimu.server.biz.herd.entity.BatchPen row = new com.zhimu.server.biz.herd.entity.BatchPen();
        row.setId(4001L);
        row.setFarmId(1001L);
        row.setBatchId(3001L);
        row.setPenId(2003L);
        row.setStockCount(200);
        return row;
    }

    private static Pen pen(Long id, String name) {
        Pen pen = new Pen();
        pen.setId(id);
        pen.setFarmId(1001L);
        pen.setPenName(name);
        pen.setStatus("ENABLED");
        pen.setCapacity(220);
        pen.setStockCount(200);
        return pen;
    }
}
