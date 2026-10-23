package com.zhimu.server.biz.feeding.support.change;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeCardItem;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.feeding.entity.BatchGrowth;
import com.zhimu.server.biz.feeding.entity.WeighRecord;
import com.zhimu.server.biz.feeding.entity.WeighRecordItem;
import com.zhimu.server.biz.feeding.mapper.BatchGrowthMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordItemMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordMapper;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 称重记录修改申请 handler：字段与 App Mock（change_form_weigh_record.json）一致。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeighRecordChangeHandlerTest {

    @Mock private FeedingChangeSupport change;
    @Mock private WeighRecordMapper weighRecordMapper;
    @Mock private WeighRecordItemMapper itemMapper;
    @Mock private BatchGrowthMapper batchGrowthMapper;

    private WeighRecordChangeHandler handler;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, WeighRecord.class);
        TableInfoHelper.initTableInfo(assistant, WeighRecordItem.class);
        TableInfoHelper.initTableInfo(assistant, BatchGrowth.class);
    }

    @BeforeEach
    void setUp() {
        handler = new WeighRecordChangeHandler(change, weighRecordMapper, itemMapper, batchGrowthMapper);
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 19, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        when(change.weighRecord(3300L)).thenReturn(record());
        when(change.weighRecord(3301L)).thenReturn(latestRecord());
        when(change.batch(3001L)).thenReturn(batch("ACTIVE"));
        when(change.penName(2003L)).thenReturn("3 号育肥舍");
        when(change.monthDay(LocalDate.of(2026, 10, 5))).thenReturn("10月5日");
        when(change.monthDay(LocalDate.of(2026, 10, 19))).thenReturn("10月19日");
        when(change.userName(10003L)).thenReturn("李明");
        when(change.growth(3001L)).thenReturn(growth());
        when(weighRecordMapper.updateById(any(WeighRecord.class))).thenReturn(1);
        when(batchGrowthMapper.updateById(any(BatchGrowth.class))).thenReturn(1);
        when(batchGrowthMapper.insert(any(BatchGrowth.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsWeighTypeAndFeedingModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.WEIGH_RECORD);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.FEEDING);
    }

    @Test
    void loadRecordBuildsLocatorFromBatchAndDate() {
        ChangeRecord record = handler.loadRecord(BizType.WEIGH_RECORD, 3300L);

        assertThat(record).isNotNull();
        assertThat(record.bizId()).isEqualTo(3300L);
        assertThat(record.farmId()).isEqualTo(1001L);
        assertThat(record.batchId()).isEqualTo(3001L);
        assertThat(record.penId()).isEqualTo(2003L);
        assertThat(record.recordLocator()).isEqualTo("ZM2610-031 · 10月5日称重");
        assertThat(record.recordTitle()).isEqualTo("称重记录 · 3 号育肥舍");
        assertThat(record.recordSubtitle()).isEqualTo("ZM2610-031 · 10月5日 · 李明登记");
        assertThat(record.recordVersion()).isEqualTo("0");
        assertThat(handler.loadRecord(BizType.WEIGH_RECORD, 9999L)).isNull();
    }

    @Test
    void describeFormMatchesAppMockFields() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.WEIGH_RECORD, 3300L));

        assertThat(form.recordCard()).extracting(ChangeCardItem::label).containsExactly("抽样头数", "平均体重");
        assertThat(form.recordCard()).extracting(ChangeCardItem::value).containsExactly("20 头", "48.6 千克");
        ChangeField field = form.fieldOf("avg_weight");
        assertThat(field).isNotNull();
        assertThat(field.fieldName()).isEqualTo("平均体重");
        assertThat(field.value()).isEqualTo("48.6");
        assertThat(field.valueUnit()).isEqualTo("千克");
        assertThat(field.inputType()).isEqualTo(ChangeField.NUMBER);
        assertThat(field.min()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(field.max()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(field.hint()).isEqualTo("标准 53.2 千克");
        assertThat(field.recalcTags()).containsExactly("日增重", "生长曲线");
        assertThat(form.linkedRules()).containsExactly("修改平均体重会重新计算日增重与生长曲线偏离");
        assertThat(form.deleteAllowed()).isFalse();
        assertThat(form.voidAllowed()).isFalse();
    }

    @Test
    void requiresRequestOnlyForEndedBatch() {
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.WEIGH_RECORD, 3300L))).isFalse();

        when(change.batch(3001L)).thenReturn(batch("ENDED"));
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.WEIGH_RECORD, 3300L))).isTrue();
    }

    @Test
    void validateChangeChecksWeightRangeAndPrecision() {
        ChangeRecord record = handler.loadRecord(BizType.WEIGH_RECORD, 3300L);
        ChangeForm form = handler.describeForm(record);

        ChangeValidation ok = handler.validateChange(record, form, ChangeType.UPDATE, "avg_weight", "50.0");
        assertThat(ok.fieldCode()).isEqualTo("avg_weight");
        assertThat(ok.beforeValue()).isEqualTo("48.6");
        assertThat(ok.beforeDisplay()).isEqualTo("48.6 千克");
        assertThat(ok.afterValue()).isEqualTo("50");
        assertThat(ok.afterDisplay()).isEqualTo("50 千克");
        assertThat(ok.impacts()).extracting(Enum::name).containsExactly("STATS");
        assertThat(ok.recalcTags()).containsExactly("日增重", "生长曲线");

        assertThat(messageOf(record, form, "48.6")).contains("新值与原值相同");
        assertThat(messageOf(record, form, "0")).contains("要大于 0 千克");
        assertThat(messageOf(record, form, "501")).contains("不能超过 500 千克");
        assertThat(messageOf(record, form, "48.55")).contains("最多 1 位小数");
    }

    @Test
    void rejectVoidAndDelete() {
        ChangeRecord record = handler.loadRecord(BizType.WEIGH_RECORD, 3300L);
        ChangeForm form = handler.describeForm(record);

        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, ChangeType.VOID, null, null));
        assertThat(error.getCode()).isEqualTo(42207);
    }

    @Test
    void applyRecalculatesOwnAndNextRecordGain() {
        when(weighRecordMapper.selectList(any())).thenReturn(List.of(nextRecord()));
        ChangeRecord record = handler.loadRecord(BizType.WEIGH_RECORD, 3300L);

        ChangeApplyResult result = handler.apply(request("50.0"), record, 9001L);

        assertThat(result.recalcCount()).isEqualTo(4);
        assertThat(result.recalcSummary()).contains("日增重");
        ArgumentCaptor<WeighRecord> captor = ArgumentCaptor.forClass(WeighRecord.class);
        verify(weighRecordMapper, atLeastOnce()).updateById(captor.capture());
        WeighRecord saved = captor.getAllValues().get(0);
        assertThat(saved.getAvgWeight()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(saved.getTotalWeight()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(saved.getDailyGain()).isEqualByComparingTo(new BigDecimal("1.25"));
        assertThat(saved.getGrowthResult()).isEqualTo("NORMAL");

        WeighRecord next = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(next.getId()).isEqualTo(3305L);
        assertThat(next.getGainBaseWeight()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(next.getDailyGain()).isEqualByComparingTo(new BigDecimal("0.00"));
        verify(batchGrowthMapper, never()).updateById(any(BatchGrowth.class));
        ArgumentCaptor<ChangeRequest> logCaptor = ArgumentCaptor.forClass(ChangeRequest.class);
        verify(change).logApply(logCaptor.capture(), eq(BizType.WEIGH_RECORD), eq(3300L), anyString(), eq(1001L),
                any(), any(), any());
        assertThat(logCaptor.getValue().getId()).isEqualTo(9001L);
    }

    @Test
    void applyLatestRecordRefreshesBatchGrowth() {
        when(weighRecordMapper.selectList(any())).thenReturn(List.of());
        ChangeRecord record = handler.loadRecord(BizType.WEIGH_RECORD, 3301L);

        handler.apply(request("62.0"), record, 9001L);

        ArgumentCaptor<BatchGrowth> captor = ArgumentCaptor.forClass(BatchGrowth.class);
        verify(batchGrowthMapper, atLeastOnce()).updateById(captor.capture());
        BatchGrowth growth = captor.getValue();
        assertThat(growth.getLastWeighId()).isEqualTo(3301L);
        assertThat(growth.getLastAvgWeight()).isEqualByComparingTo(new BigDecimal("62.00"));
        assertThat(growth.getGrowthStatus()).isEqualTo("NORMAL");
        assertThat(growth.getNextWeighDate()).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void applyIndividualRecordBecomesSampleAndClearsItems() {
        WeighRecord individual = record();
        individual.setId(3303L);
        individual.setMode("INDIVIDUAL");
        individual.setSampleCount(3);
        individual.setTotalWeight(new BigDecimal("54.00"));
        individual.setAvgWeight(new BigDecimal("18.00"));
        when(change.weighRecord(3303L)).thenReturn(individual);
        when(weighRecordMapper.selectList(any())).thenReturn(List.of());
        ChangeRecord record = handler.loadRecord(BizType.WEIGH_RECORD, 3303L);

        handler.apply(request("20.0"), record, 9001L);

        ArgumentCaptor<WeighRecord> captor = ArgumentCaptor.forClass(WeighRecord.class);
        verify(weighRecordMapper, atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getAllValues().get(0).getMode()).isEqualTo("SAMPLE");
        assertThat(captor.getAllValues().get(0).getTotalWeight()).isEqualByComparingTo(new BigDecimal("60.00"));
        verify(itemMapper).delete(any());
    }

    @Test
    void validateForApproveRejectsMissingSampleCount() {
        WeighRecord broken = record();
        broken.setSampleCount(0);
        when(change.weighRecord(3300L)).thenReturn(broken);

        BizException error = (BizException) catchException(
                () -> handler.validateForApprove(request("50.0"),
                        handler.loadRecord(BizType.WEIGH_RECORD, 3300L)));

        assertThat(error.getCode()).isEqualTo(40903);
    }

    private String messageOf(ChangeRecord record, ChangeForm form, String value) {
        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, ChangeType.UPDATE, "avg_weight", value));
        assertThat(error.getCode()).isEqualTo(40001);
        return error.getMessage();
    }

    private static ChangeRequest request(String value) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9001L);
        request.setChangeType("UPDATE");
        request.setFieldCode("avg_weight");
        request.setFarmId(1001L);
        request.setSubmittedBy(5001L);
        request.setSubmitterRole("OWNER");
        request.setReason("称重记录登记有误");
        request.setBeforeJson("{\"before\":{\"avg_weight\":{\"name\":\"平均体重\",\"value\":\"48.6\","
                + "\"display\":\"48.6 千克\",\"unit\":\"千克\"}}}");
        request.setAfterJson("{\"after\":{\"avg_weight\":{\"name\":\"平均体重\",\"value\":\"" + value
                + "\",\"display\":\"" + value + " 千克\",\"unit\":\"千克\"}}}");
        return request;
    }

    private static WeighRecord record() {
        WeighRecord record = new WeighRecord();
        record.setId(3300L);
        record.setFarmId(1001L);
        record.setBatchId(3001L);
        record.setWeighDate(LocalDate.of(2026, 10, 5));
        record.setDayAge(82);
        record.setStockCount(200);
        record.setMode("SAMPLE");
        record.setSampleCount(20);
        record.setTotalWeight(new BigDecimal("972.00"));
        record.setAvgWeight(new BigDecimal("48.60"));
        record.setSuggestedSample(30);
        record.setSampleLow(true);
        record.setStdWeight(new BigDecimal("53.20"));
        record.setAllowedDeviation(new BigDecimal("0.1000"));
        record.setDeviationRatio(new BigDecimal("-0.0865"));
        record.setGrowthResult("NORMAL");
        record.setGainBaseType("INTAKE");
        record.setGainBaseDate(LocalDate.of(2026, 10, 1));
        record.setGainBaseWeight(new BigDecimal("45.00"));
        record.setDailyGain(new BigDecimal("0.90"));
        record.setVersion(0);
        record.setCreatedBy(10003L);
        return record;
    }

    private static WeighRecord latestRecord() {
        WeighRecord record = record();
        record.setId(3301L);
        record.setWeighDate(LocalDate.of(2026, 10, 19));
        record.setDayAge(96);
        record.setAvgWeight(new BigDecimal("60.00"));
        record.setTotalWeight(new BigDecimal("1200.00"));
        record.setStdWeight(new BigDecimal("68.60"));
        record.setDeviationRatio(new BigDecimal("-0.1254"));
        record.setGrowthResult("SLOW");
        record.setGainBaseType("PREV_WEIGH");
        record.setGainBaseDate(LocalDate.of(2026, 10, 17));
        record.setGainBaseWeight(new BigDecimal("57.00"));
        record.setDailyGain(new BigDecimal("1.50"));
        record.setVersion(1);
        return record;
    }

    private static WeighRecord nextRecord() {
        WeighRecord record = record();
        record.setId(3305L);
        record.setWeighDate(LocalDate.of(2026, 10, 8));
        record.setAvgWeight(new BigDecimal("50.00"));
        record.setGainBaseDate(LocalDate.of(2026, 10, 5));
        record.setGainBaseWeight(new BigDecimal("48.60"));
        record.setDailyGain(new BigDecimal("0.47"));
        return record;
    }

    private static BatchGrowth growth() {
        BatchGrowth growth = new BatchGrowth();
        growth.setId(601L);
        growth.setFarmId(1001L);
        growth.setBatchId(3001L);
        growth.setWeighCyclePerMonth(2);
        growth.setCycleCustomized(false);
        growth.setLastWeighId(3301L);
        growth.setLastWeighDate(LocalDate.of(2026, 10, 19));
        growth.setLastAvgWeight(new BigDecimal("60.00"));
        growth.setGrowthStatus("SLOW");
        growth.setNextWeighDate(LocalDate.of(2026, 11, 2));
        growth.setVersion(0);
        return growth;
    }

    private static BatchInfo batch(String status) {
        return new BatchInfo(1001L, 3001L, "ZM2610-031", 1101L, 101L, LocalDate.of(2026, 10, 1), 200,
                status, List.of(2003L), 78, 205, 5, 0, 2003L);
    }
}
