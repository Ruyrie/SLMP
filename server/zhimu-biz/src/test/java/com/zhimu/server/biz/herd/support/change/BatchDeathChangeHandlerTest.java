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

/** 死淘记录修改申请 handler：字段与 App Mock（change_form_batch_death.json）一致。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchDeathChangeHandlerTest {

    @Mock private BatchMapper batchMapper;
    @Mock private BatchEventMapper eventMapper;
    @Mock private BatchPenMapper batchPenMapper;
    @Mock private BatchTimelineMapper timelineMapper;
    @Mock private PenMapper penMapper;
    @Mock private HerdSupport herdSupport;
    @Mock private OperationLogRecorder recorder;

    private BatchDeathChangeHandler handler;
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
        handler = new BatchDeathChangeHandler(support, eventMapper);
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 19, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        when(herdSupport.pensByIds(List.of(2003L))).thenReturn(List.of(pen(2003L, "3 号育肥舍")));
        when(herdSupport.userName(10003L)).thenReturn("李明");
        when(herdSupport.userName(10001L)).thenReturn("王建国");
        when(herdSupport.unitText(any())).thenReturn("头");
        when(herdSupport.farmUnit(anyLong())).thenReturn("HEAD");
        when(eventMapper.selectById(3601L)).thenReturn(deathEvent());
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
    void supportsDeathTypeAndPenStockModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.BATCH_DEATH);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.PEN_STOCK);
    }

    @Test
    void loadRecordUsesDeathEventAndExecutorName() {
        ChangeRecord record = handler.loadRecord(BizType.BATCH_DEATH, 3601L);

        assertThat(record).isNotNull();
        assertThat(record.bizId()).isEqualTo(3601L);
        assertThat(record.farmId()).isEqualTo(1001L);
        assertThat(record.batchId()).isEqualTo(3001L);
        assertThat(record.penId()).isEqualTo(2003L);
        assertThat(record.recordLocator()).isEqualTo("ZM2610-031 · 10月17日死淘");
        assertThat(record.recordTitle()).isEqualTo("死淘记录 · 3 号育肥舍");
        assertThat(record.recordSubtitle()).isEqualTo("病死 2 头 · 10月17日 · 李明登记");
        assertThat(record.recordVersion()).isEqualTo("0");
        assertThat(handler.loadRecord(BizType.BATCH_DEATH, 999L)).isNull();
    }

    @Test
    void describeFormMatchesAppMockFields() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.BATCH_DEATH, 3601L));

        assertThat(form.recordCard()).extracting(ChangeCardItem::label).containsExactly("原因", "数量");
        assertThat(form.recordCard()).extracting(ChangeCardItem::value).containsExactly("病死", "2 头");
        ChangeField field = form.fieldOf("death_count");
        assertThat(field).isNotNull();
        assertThat(field.fieldName()).isEqualTo("数量");
        assertThat(field.value()).isEqualTo("2");
        assertThat(field.valueUnit()).isEqualTo("头");
        assertThat(field.inputType()).isEqualTo(ChangeField.NUMBER);
        assertThat(field.min()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(field.max()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(field.recalcTags()).containsExactly("存栏", "死亡率");
        assertThat(form.linkedRules()).containsExactly("修改死淘数量会重新计算存栏与死亡率");
        assertThat(form.reviewTip()).isNotBlank();
    }

    @Test
    void requiresRequestOnlyAfterThreeDayWindow() {
        BatchEvent older = deathEvent();
        older.setEventDate(LocalDate.of(2026, 10, 14));
        when(eventMapper.selectById(3601L)).thenReturn(older);
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.BATCH_DEATH, 3601L))).isTrue();

        BatchEvent recent = deathEvent();
        recent.setEventDate(LocalDate.of(2026, 10, 18));
        when(eventMapper.selectById(3601L)).thenReturn(recent);
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.BATCH_DEATH, 3601L))).isFalse();
    }

    @Test
    void validateChangeChecksRangeAndAvailableStock() {
        when(batchPenMapper.selectList(any())).thenReturn(List.of(batchPen(200)));
        ChangeRecord record = handler.loadRecord(BizType.BATCH_DEATH, 3601L);
        ChangeForm form = handler.describeForm(record);

        ChangeValidation ok = handler.validateChange(record, form, ChangeType.UPDATE, "death_count", "5");
        assertThat(ok.beforeValue()).isEqualTo("2");
        assertThat(ok.afterValue()).isEqualTo("5");
        assertThat(ok.afterDisplay()).isEqualTo("5 头");
        assertThat(ok.recalcTags()).containsExactly("存栏", "死亡率");

        assertThat(messageOf(record, form, "0")).contains("不能小于 1 头");
        assertThat(messageOf(record, form, "201")).contains("不能超过 200 头");
        assertThat(messageOf(record, form, "2")).contains("新值与原值相同");
    }

    @Test
    void validateChangeRejectsDeathOverAvailableStock() {
        when(batchPenMapper.selectList(any())).thenReturn(List.of(batchPen(1)));
        ChangeRecord record = handler.loadRecord(BizType.BATCH_DEATH, 3601L);
        ChangeForm form = handler.describeForm(record);

        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, ChangeType.UPDATE, "death_count", "10"));

        assertThat(error.getCode()).isEqualTo(42205);
    }

    @Test
    void applyRestoresThenDeductsNewQuantity() {
        when(batchPenMapper.selectList(any())).thenReturn(List.of(batchPen(200)));
        when(batchPenMapper.addStock(anyLong(), anyLong(), anyInt(), any(), any())).thenReturn(1);
        when(batchPenMapper.deduct(anyLong(), anyLong(), anyInt(), any(), any())).thenReturn(1);
        when(penMapper.addStock(anyLong(), anyInt(), any())).thenReturn(1);
        when(batchMapper.update(any(), any())).thenReturn(1);
        when(eventMapper.update(any(), any())).thenReturn(1);
        when(timelineMapper.update(any(), any())).thenReturn(1);
        ChangeRecord record = handler.loadRecord(BizType.BATCH_DEATH, 3601L);

        ChangeApplyResult result = handler.apply(request("death_count", "5"), record, 9001L);

        assertThat(result.recalcCount()).isEqualTo(3);
        assertThat(result.recalcSummary()).contains("存栏");
        assertThat(logs).hasSize(1);
        OperationLogEntry log = logs.get(0);
        assertThat(log.getBizType()).isEqualTo("BATCH_DEATH");
        assertThat(log.getChangeRequestId()).isEqualTo(9001L);
        assertThat(log.getBefore()).asString().contains("2 头");
        assertThat(log.getAfter()).asString().contains("5 头");
    }

    private String messageOf(ChangeRecord record, ChangeForm form, String value) {
        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, ChangeType.UPDATE, "death_count", value));
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
        request.setReason("死淘数量填错");
        request.setAfterJson("{\"after\":{\"" + fieldCode + "\":{\"name\":\"数量\",\"value\":\"" + value
                + "\",\"display\":\"" + value + " 头\",\"unit\":\"头\"}}}");
        return request;
    }

    private static BatchEvent deathEvent() {
        BatchEvent event = new BatchEvent();
        event.setId(3601L);
        event.setFarmId(1001L);
        event.setBatchId(3001L);
        event.setEventType("DEATH");
        event.setEventDate(LocalDate.of(2026, 10, 17));
        event.setPenId(2003L);
        event.setQuantity(2);
        event.setReasonCode("SICK");
        event.setExecutorUserId(10003L);
        event.setCreatedBy(10001L);
        event.setStatus("EFFECTIVE");
        event.setVersion(0);
        event.setDeleted(false);
        return event;
    }

    private static Batch batch() {
        Batch batch = new Batch();
        batch.setId(3001L);
        batch.setFarmId(1001L);
        batch.setBatchNo("ZM2610-031");
        batch.setBreedId(1101L);
        batch.setEntryCount(205);
        batch.setCurrentCount(200);
        batch.setDeathCount(5);
        batch.setStatus("ACTIVE");
        batch.setVersion(7);
        batch.setDeleted(false);
        return batch;
    }

    private static BatchPen batchPen(int stock) {
        BatchPen row = new BatchPen();
        row.setId(4001L);
        row.setFarmId(1001L);
        row.setBatchId(3001L);
        row.setPenId(2003L);
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
        pen.setStockCount(200);
        return pen;
    }
}
