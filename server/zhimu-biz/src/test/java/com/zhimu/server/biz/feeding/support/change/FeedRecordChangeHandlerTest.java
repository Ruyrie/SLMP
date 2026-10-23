package com.zhimu.server.biz.feeding.support.change;

import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeCardItem;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.entity.FeedRecord;
import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.feeding.mapper.FeedRecordMapper;
import com.zhimu.server.biz.feeding.mapper.FeedTaskMapper;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 投喂记录修改申请 handler：字段与 App Mock（change_form_feed_record.json）一致。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedRecordChangeHandlerTest {

    @Mock private FeedingChangeSupport change;
    @Mock private FeedRecordMapper feedRecordMapper;
    @Mock private FeedTaskMapper feedTaskMapper;
    @Mock private FeedStockPort feedStockPort;

    private FeedRecordChangeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FeedRecordChangeHandler(change, feedRecordMapper, feedTaskMapper, feedStockPort);
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 19, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        when(change.feedRecord(7001L)).thenReturn(record());
        when(change.batch(3001L)).thenReturn(batch());
        when(change.penName(2003L)).thenReturn("3 号育肥舍");
        when(change.monthDay(LocalDate.of(2026, 10, 18))).thenReturn("10月18日");
        when(change.monthDay(LocalDate.of(2026, 10, 19))).thenReturn("10月19日");
        when(change.userName(10003L)).thenReturn("李明");
        when(change.feedName(1001L, 4001L)).thenReturn("育肥全价料");
        when(change.feedName(1001L, 4002L)).thenReturn("保育料");
        when(change.feedOptions(1001L)).thenReturn(List.of(
                new FeedStockPort.FeedBrief(4001L, "育肥全价料", "KG", new BigDecimal("500.0"), 1, true, true, null),
                new FeedStockPort.FeedBrief(4002L, "保育料", "KG", new BigDecimal("1200.0"), 5, false, true, null),
                new FeedStockPort.FeedBrief(4003L, "乳猪教槽料", "KG", BigDecimal.ZERO, null, false, false,
                        "结存 0 千克，不能出库")));
        when(change.feed(1001L, 4001L)).thenReturn(
                new FeedStockPort.FeedBrief(4001L, "育肥全价料", "KG", new BigDecimal("500.0"), 1, true, true, null));
        when(change.feed(1001L, 4002L)).thenReturn(
                new FeedStockPort.FeedBrief(4002L, "保育料", "KG", new BigDecimal("1200.0"), 5, false, true, null));
        when(change.task(70104L)).thenReturn(task());
        when(feedRecordMapper.updateById(any(FeedRecord.class))).thenReturn(1);
        when(feedTaskMapper.updateById(any(FeedTask.class))).thenReturn(1);
        when(feedStockPort.deductForFeeding(any())).thenReturn(FeedStockPort.DeductResult.ok(9001L));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsFeedTypeAndFeedingModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.FEED_RECORD);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.FEEDING);
    }

    @Test
    void loadRecordBuildsLocatorFromPenBatchAndMeal() {
        ChangeRecord record = handler.loadRecord(BizType.FEED_RECORD, 7001L);

        assertThat(record).isNotNull();
        assertThat(record.bizId()).isEqualTo(7001L);
        assertThat(record.farmId()).isEqualTo(1001L);
        assertThat(record.batchId()).isEqualTo(3001L);
        assertThat(record.penId()).isEqualTo(2003L);
        assertThat(record.recordLocator()).isEqualTo("3 号育肥舍 · ZM2610-031 · 10月18日 晚餐");
        assertThat(record.recordTitle()).isEqualTo("投喂记录 · 3 号育肥舍");
        assertThat(record.recordSubtitle()).isEqualTo("ZM2610-031 · 10月18日 晚餐 · 李明登记");
        assertThat(record.recordVersion()).isEqualTo("3");
        assertThat(record.voided()).isFalse();
        assertThat(handler.loadRecord(BizType.FEED_RECORD, 9999L)).isNull();
    }

    @Test
    void describeFormMatchesAppMockFields() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.FEED_RECORD, 7001L));

        assertThat(form.recordCard()).extracting(ChangeCardItem::label)
                .containsExactly("饲料", "实际投喂量", "计划量");
        assertThat(form.recordCard()).extracting(ChangeCardItem::value)
                .containsExactly("育肥全价料", "140 千克", "160 千克");
        ChangeField feed = form.fieldOf("feed_id");
        assertThat(feed.inputType()).isEqualTo(ChangeField.SELECT);
        assertThat(feed.value()).isEqualTo("育肥全价料");
        assertThat(feed.options()).hasSize(2);
        assertThat(feed.options().get(0).getValue()).isEqualTo("4001");
        assertThat(feed.options().get(1).getLabel()).isEqualTo("保育料");
        assertThat(feed.recalcTags()).containsExactly("饲料成本", "经营报表");
        ChangeField qty = form.fieldOf("actual_qty");
        assertThat(qty.fieldName()).isEqualTo("实际投喂量");
        assertThat(qty.value()).isEqualTo("140");
        assertThat(qty.valueUnit()).isEqualTo("千克");
        assertThat(qty.min()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(qty.max()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(qty.hint()).isEqualTo("计划 160 千克");
        assertThat(qty.recalcTags()).containsExactly("头均采食量", "料肉比", "饲料成本", "经营报表");
        assertThat(form.linkedRules()).containsExactly("修改实际投喂量会同步调整饲料库存出库数量");
        assertThat(form.voidAllowed()).isTrue();
        assertThat(form.voidNotice()).contains("140 千克育肥全价料退回库存", "（10月19日 24:00 前）");
        assertThat(form.voidRecalcTags()).containsExactly("库存（退回饲料）", "成本", "统计");
        assertThat(form.reviewTip()).contains("饲料库存和成本统计");
    }

    @Test
    void requiresRequestOnlyForPastDates() {
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.FEED_RECORD, 7001L))).isTrue();

        FeedRecord today = record();
        today.setFeedDate(LocalDate.of(2026, 10, 19));
        when(change.feedRecord(7001L)).thenReturn(today);
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.FEED_RECORD, 7001L))).isFalse();
    }

    @Test
    void validateChangeChecksQuantityRangePrecisionAndStock() {
        ChangeRecord record = handler.loadRecord(BizType.FEED_RECORD, 7001L);
        ChangeForm form = handler.describeForm(record);

        ChangeValidation ok = handler.validateChange(record, form, ChangeType.UPDATE, "actual_qty", "160");
        assertThat(ok.fieldCode()).isEqualTo("actual_qty");
        assertThat(ok.beforeValue()).isEqualTo("140");
        assertThat(ok.beforeDisplay()).isEqualTo("140 千克");
        assertThat(ok.afterValue()).isEqualTo("160");
        assertThat(ok.afterDisplay()).isEqualTo("160 千克");
        assertThat(ok.recalcTags()).containsExactly("头均采食量", "料肉比", "饲料成本", "经营报表");

        ChangeValidation feed = handler.validateChange(record, form, ChangeType.UPDATE, "feed_id", "保育料");
        assertThat(feed.afterValue()).isEqualTo("4002");
        assertThat(feed.afterDisplay()).isEqualTo("保育料");

        ChangeValidation voided = handler.validateChange(record, form, ChangeType.VOID, null, null);
        assertThat(voided.hasField()).isFalse();
        assertThat(voided.recalcTags()).containsExactly("库存（退回饲料）", "成本", "统计");

        assertThat(messageOf(record, form, "actual_qty", "140")).contains("新值与原值相同");
        assertThat(messageOf(record, form, "actual_qty", "0")).contains("要大于 0 千克");
        assertThat(messageOf(record, form, "actual_qty", "1001")).contains("不能超过 1000 千克");
        assertThat(messageOf(record, form, "actual_qty", "140.55")).contains("最多 1 位小数");
        assertThat(messageOf(record, form, "feed_id", "不存在的饲料")).contains("请选择饲料");
    }

    @Test
    void validateChangeRejectsQuantityOverStock() {
        when(change.feed(1001L, 4001L)).thenReturn(
                new FeedStockPort.FeedBrief(4001L, "育肥全价料", "KG", new BigDecimal("10.0"), 1, true, true, null));
        ChangeRecord record = handler.loadRecord(BizType.FEED_RECORD, 7001L);
        ChangeForm form = handler.describeForm(record);

        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, ChangeType.UPDATE, "actual_qty", "200"));

        assertThat(error.getCode()).isEqualTo(42201);
        assertThat(error.getMessage()).contains("库存不足", "结存 10 千克");
    }

    @Test
    void applyUpdateRecalculatesDeviationAndRedispatchesStock() {
        ChangeRecord record = handler.loadRecord(BizType.FEED_RECORD, 7001L);

        ChangeApplyResult result = handler.apply(request("UPDATE", "actual_qty", "160"), record, 9001L);

        assertThat(result.recalcSummary()).contains("投喂偏差");
        ArgumentCaptor<FeedRecord> captor = ArgumentCaptor.forClass(FeedRecord.class);
        verify(feedRecordMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        FeedRecord saved = captor.getValue();
        assertThat(saved.getActualQty()).isEqualByComparingTo(new BigDecimal("160.00"));
        assertThat(saved.getDeviationRatio()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(saved.getAbnormalType()).isEqualTo("NONE");
        verify(feedStockPort).rollbackForFeeding(8001L);
        verify(feedStockPort).deductForFeeding(any());
        ArgumentCaptor<ChangeRequest> logCaptor = ArgumentCaptor.forClass(ChangeRequest.class);
        verify(change).logApply(logCaptor.capture(), eq(BizType.FEED_RECORD), eq(7001L), anyString(), eq(1001L),
                any(), any(), any());
        assertThat(logCaptor.getValue().getId()).isEqualTo(9001L);
    }

    @Test
    void applyVoidRestoresStockAndTaskStatus() {
        ChangeRecord record = handler.loadRecord(BizType.FEED_RECORD, 7001L);

        ChangeApplyResult result = handler.apply(request("VOID", null, null), record, 9001L);

        assertThat(result.recalcSummary()).contains("退回饲料库存");
        ArgumentCaptor<FeedRecord> captor = ArgumentCaptor.forClass(FeedRecord.class);
        verify(feedRecordMapper).updateById(captor.capture());
        FeedRecord saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("VOID");
        assertThat(saved.getVoidBy()).isEqualTo(9001L);
        assertThat(saved.getActiveTaskId()).isNull();
        verify(feedStockPort).rollbackForFeeding(8001L);
        ArgumentCaptor<FeedTask> taskCaptor = ArgumentCaptor.forClass(FeedTask.class);
        verify(feedTaskMapper).updateById(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo("OVERDUE");
        assertThat(taskCaptor.getValue().getRecordId()).isNull();
    }

    @Test
    void validateForApproveRejectsInsufficientStock() {
        when(change.feed(1001L, 4001L)).thenReturn(
                new FeedStockPort.FeedBrief(4001L, "育肥全价料", "KG", new BigDecimal("30.0"), 1, true, true, null));

        BizException error = (BizException) catchException(
                () -> handler.validateForApprove(request("UPDATE", "actual_qty", "300"),
                        handler.loadRecord(BizType.FEED_RECORD, 7001L)));

        assertThat(error.getCode()).isEqualTo(42201);
    }

    private String messageOf(ChangeRecord record, ChangeForm form, String field, String value) {
        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, ChangeType.UPDATE, field, value));
        assertThat(error.getCode()).isEqualTo(40001);
        return error.getMessage();
    }

    private static ChangeRequest request(String changeType, String fieldCode, String value) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9001L);
        request.setChangeType(changeType);
        request.setFieldCode(fieldCode);
        request.setFarmId(1001L);
        request.setSubmittedBy(5001L);
        request.setSubmitterRole("OWNER");
        request.setReason("登记时投喂量填错");
        request.setBeforeJson("{\"before\":{\"actual_qty\":{\"name\":\"实际投喂量\",\"value\":\"140\","
                + "\"display\":\"140 千克\",\"unit\":\"千克\"}}}");
        if (fieldCode == null) {
            request.setAfterJson("{\"after\":{}}");
        } else {
            request.setAfterJson("{\"after\":{\"" + fieldCode + "\":{\"name\":\"字段\",\"value\":\"" + value
                    + "\",\"display\":\"" + value + "\",\"unit\":\"千克\"}}}");
        }
        return request;
    }

    private static FeedRecord record() {
        FeedRecord record = new FeedRecord();
        record.setId(7001L);
        record.setFarmId(1001L);
        record.setBatchId(3001L);
        record.setPenId(2003L);
        record.setTaskId(70104L);
        record.setActiveTaskId(70104L);
        record.setFeedDate(LocalDate.of(2026, 10, 18));
        record.setMealNo(3);
        record.setMealName("晚餐");
        record.setFeedId(4001L);
        record.setPlanQty(new BigDecimal("160.00"));
        record.setActualQty(new BigDecimal("140.00"));
        record.setDeviationRatio(new BigDecimal("-0.1250"));
        record.setAbnormalType("UNDER");
        record.setAbnormalThreshold(new BigDecimal("0.1000"));
        record.setExecutorId(10003L);
        record.setIsProxy(false);
        record.setIsBackfill(false);
        record.setStockFlowId(8001L);
        record.setStatus("NORMAL");
        record.setVersion(3);
        record.setCreatedBy(10003L);
        return record;
    }

    private static FeedTask task() {
        FeedTask task = new FeedTask();
        task.setId(70104L);
        task.setFarmId(1001L);
        task.setBatchId(3001L);
        task.setPenId(2003L);
        task.setTaskDate(LocalDate.of(2026, 10, 18));
        task.setMealNo(3);
        task.setMealName("晚餐");
        task.setStatus("DONE");
        task.setRecordId(7001L);
        task.setDeadlineAt(LocalDateTime.of(2026, 10, 18, 19, 0));
        task.setBackfillEndAt(LocalDateTime.of(2026, 10, 20, 0, 0));
        task.setVersion(1);
        return task;
    }

    private static BatchInfo batch() {
        return new BatchInfo(1001L, 3001L, "ZM2610-031", 1101L, 101L, LocalDate.of(2026, 10, 1), 200,
                "ACTIVE", List.of(2003L), 78, 205, 5, 0, 2003L);
    }
}
