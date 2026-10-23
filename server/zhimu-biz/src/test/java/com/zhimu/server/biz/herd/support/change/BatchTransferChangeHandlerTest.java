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
import com.zhimu.server.biz.herd.entity.BatchPen;
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

/** 转栏记录修改申请 handler：字段与 App Mock（change_form_batch_transfer.json）一致。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchTransferChangeHandlerTest {

    @Mock private BatchMapper batchMapper;
    @Mock private BatchEventMapper eventMapper;
    @Mock private BatchPenMapper batchPenMapper;
    @Mock private BatchTimelineMapper timelineMapper;
    @Mock private PenMapper penMapper;
    @Mock private HerdSupport herdSupport;
    @Mock private OperationLogRecorder recorder;

    private BatchTransferChangeHandler handler;
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
        handler = new BatchTransferChangeHandler(support, eventMapper);
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 19, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        when(herdSupport.pensByIds(List.of(2003L))).thenReturn(List.of(pen(2003L, "3 号育肥舍")));
        when(herdSupport.pensByIds(List.of(2004L))).thenReturn(List.of(pen(2004L, "4 号育肥舍")));
        when(herdSupport.userName(10002L)).thenReturn("刘芳");
        when(herdSupport.unitText(any())).thenReturn("头");
        when(herdSupport.farmUnit(anyLong())).thenReturn("HEAD");
        when(eventMapper.selectById(3501L)).thenReturn(transferEvent(3501L));
        when(batchMapper.selectByIdIncludingDeleted(3002L)).thenReturn(batch());
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
    void supportsTransferTypeAndPenStockModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.BATCH_TRANSFER);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.PEN_STOCK);
    }

    @Test
    void loadRecordUsesTransferEventAndBothPenNames() {
        ChangeRecord record = handler.loadRecord(BizType.BATCH_TRANSFER, 3501L);

        assertThat(record).isNotNull();
        assertThat(record.bizId()).isEqualTo(3501L);
        assertThat(record.farmId()).isEqualTo(1001L);
        assertThat(record.batchId()).isEqualTo(3002L);
        assertThat(record.penId()).isEqualTo(2004L);
        assertThat(record.recordLocator()).isEqualTo("ZM2610-019 · 10月12日转栏");
        assertThat(record.recordTitle()).isEqualTo("转栏记录 · 3 号育肥舍");
        assertThat(record.recordSubtitle()).isEqualTo("4 号育肥舍 → 3 号育肥舍 · 10月12日 · 刘芳登记");
        assertThat(record.recordVersion()).isEqualTo("0");
        assertThat(record.deleted()).isFalse();
        assertThat(record.voided()).isFalse();
        assertThat(handler.loadRecord(BizType.BATCH_TRANSFER, 999L)).isNull();
    }

    @Test
    void describeFormMatchesAppMockFields() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.BATCH_TRANSFER, 3501L));

        assertThat(form.recordCard()).extracting(ChangeCardItem::label).containsExactly("转出", "转入", "数量");
        assertThat(form.recordCard()).extracting(ChangeCardItem::value)
                .containsExactly("4 号育肥舍", "3 号育肥舍", "50 头");
        ChangeField field = form.fieldOf("transfer_count");
        assertThat(field).isNotNull();
        assertThat(field.fieldName()).isEqualTo("数量");
        assertThat(field.value()).isEqualTo("50");
        assertThat(field.valueUnit()).isEqualTo("头");
        assertThat(field.inputType()).isEqualTo(ChangeField.NUMBER);
        assertThat(field.min()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(field.max()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(field.recalcTags()).containsExactly("存栏");
        assertThat(form.linkedRules()).containsExactly("修改转栏数量会同时调整两个栏舍的存栏");
        assertThat(form.deleteAllowed()).isFalse();
        assertThat(form.voidAllowed()).isFalse();
    }

    @Test
    void requiresRequestWhenOlderThanThreeDays() {
        when(eventMapper.selectById(3501L)).thenReturn(transferEvent(3501L));
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.BATCH_TRANSFER, 3501L))).isTrue();

        BatchEvent recent = transferEvent(3501L);
        recent.setEventDate(LocalDate.of(2026, 10, 19));
        when(eventMapper.selectById(3501L)).thenReturn(recent);
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.BATCH_TRANSFER, 3501L))).isFalse();
    }

    @Test
    void requiresRequestOnEndedBatchThrows42211() {
        Batch ended = batch();
        ended.setStatus("ENDED");
        when(batchMapper.selectByIdIncludingDeleted(3002L)).thenReturn(ended);

        BizException error = (BizException) catchException(
                () -> handler.requiresRequest(handler.loadRecord(BizType.BATCH_TRANSFER, 3501L)));

        assertThat(error.getCode()).isEqualTo(42211);
    }

    @Test
    void validateChangeChecksRangeAndAvailableStock() {
        ChangeRecord record = handler.loadRecord(BizType.BATCH_TRANSFER, 3501L);
        ChangeForm form = handler.describeForm(record);

        ChangeValidation ok = handler.validateChange(record, form, ChangeType.UPDATE, "transfer_count", "30");
        assertThat(ok.beforeValue()).isEqualTo("50");
        assertThat(ok.afterValue()).isEqualTo("30");
        assertThat(ok.afterDisplay()).isEqualTo("30 头");
        assertThat(ok.impacts()).extracting(Enum::name).containsExactly("STOCK", "STATS");

        assertThat(messageOf(record, form, "201")).contains("不能超过 200 头");
        assertThat(messageOf(record, form, "50")).contains("新值与原值相同");
    }

    @Test
    void validateChangeRejectsQuantityOverPenStock() {
        when(batchPenMapper.selectList(any())).thenReturn(List.of(batchPen(2004L, 10)));
        ChangeRecord record = handler.loadRecord(BizType.BATCH_TRANSFER, 3501L);
        ChangeForm form = handler.describeForm(record);

        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, ChangeType.UPDATE, "transfer_count", "150"));

        assertThat(error.getCode()).isEqualTo(42205);
    }

    @Test
    void applyMovesStockBetweenBothPens() {
        when(batchPenMapper.selectList(any())).thenReturn(List.of(batchPen(2004L, 100)));
        when(batchPenMapper.addStock(anyLong(), anyLong(), anyInt(), any(), any())).thenReturn(1);
        when(batchPenMapper.deduct(anyLong(), anyLong(), anyInt(), any(), any())).thenReturn(1);
        when(penMapper.addStock(anyLong(), anyInt(), any())).thenReturn(1);
        when(batchMapper.update(any(), any())).thenReturn(1);
        when(eventMapper.update(any(), any())).thenReturn(1);
        when(timelineMapper.update(any(), any())).thenReturn(1);
        ChangeRecord record = handler.loadRecord(BizType.BATCH_TRANSFER, 3501L);

        ChangeApplyResult result = handler.apply(request("transfer_count", "30"), record, 9001L);

        assertThat(result.recalcCount()).isEqualTo(2);
        assertThat(result.recalcSummary()).contains("存栏");
        assertThat(logs).hasSize(1);
        OperationLogEntry log = logs.get(0);
        assertThat(log.getOpType()).isEqualTo("RECORD_EDIT");
        assertThat(log.getBizType()).isEqualTo("BATCH_TRANSFER");
        assertThat(log.getChangeRequestId()).isEqualTo(9001L);
        assertThat(log.getBefore()).asString().contains("50 头");
        assertThat(log.getAfter()).asString().contains("30 头");
    }

    private String messageOf(ChangeRecord record, ChangeForm form, String value) {
        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, ChangeType.UPDATE, "transfer_count", value));
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
        request.setReason("转栏数量填错");
        request.setAfterJson("{\"after\":{\"" + fieldCode + "\":{\"name\":\"数量\",\"value\":\"" + value
                + "\",\"display\":\"" + value + " 头\",\"unit\":\"头\"}}}");
        return request;
    }

    private static BatchEvent transferEvent(Long id) {
        BatchEvent event = new BatchEvent();
        event.setId(id);
        event.setFarmId(1001L);
        event.setBatchId(3002L);
        event.setEventType("TRANSFER");
        event.setEventDate(LocalDate.of(2026, 10, 12));
        event.setPenId(2004L);
        event.setToPenId(2003L);
        event.setQuantity(50);
        event.setStatus("EFFECTIVE");
        event.setVersion(0);
        event.setDeleted(false);
        event.setCreatedBy(10002L);
        return event;
    }

    private static Batch batch() {
        Batch batch = new Batch();
        batch.setId(3002L);
        batch.setFarmId(1001L);
        batch.setBatchNo("ZM2610-019");
        batch.setBreedId(1101L);
        batch.setEntryCount(152);
        batch.setCurrentCount(150);
        batch.setStatus("ACTIVE");
        batch.setVersion(5);
        batch.setDeleted(false);
        return batch;
    }

    private static BatchPen batchPen(Long penId, int stock) {
        BatchPen row = new BatchPen();
        row.setId(1L);
        row.setFarmId(1001L);
        row.setBatchId(3002L);
        row.setPenId(penId);
        row.setStockCount(stock);
        return row;
    }

    private static Pen pen(Long id, String name) {
        Pen pen = new Pen();
        pen.setId(id);
        pen.setFarmId(1001L);
        pen.setPenName(name);
        pen.setStatus("ENABLED");
        pen.setCapacity(220);
        pen.setStockCount(150);
        return pen;
    }
}
