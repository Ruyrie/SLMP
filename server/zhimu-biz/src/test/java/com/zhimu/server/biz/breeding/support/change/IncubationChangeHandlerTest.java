package com.zhimu.server.biz.breeding.support.change;

import com.zhimu.server.biz.breeding.entity.Incubation;
import com.zhimu.server.biz.breeding.mapper.IncubationMapper;
import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeCardItem;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.SelfBredBatchService;
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

/** 孵化记录修改申请 handler：字段与 App Mock（change_form_incubation.json）一致。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncubationChangeHandlerTest {

    @Mock private BreedingChangeSupport change;
    @Mock private IncubationMapper incubationMapper;
    @Mock private SelfBredBatchService selfBredBatchService;

    private IncubationChangeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IncubationChangeHandler(change, incubationMapper, selfBredBatchService);
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 19, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        when(change.incubation(6004L)).thenReturn(incubation());
        when(change.penName(2301L)).thenReturn("1 号种鸡舍");
        when(change.penName(null)).thenReturn("");
        when(change.batchNo(3211L)).thenReturn("ZM2603-015");
        when(change.batchNo(3272L)).thenReturn("ZM2610-072");
        when(change.farmName(1002L)).thenReturn("西岭鸡场");
        when(change.userName(10001L)).thenReturn("王建国");
        when(change.monthDay(LocalDate.of(2026, 9, 28))).thenReturn("9月28日");
        when(incubationMapper.updateById(any(Incubation.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsIncubationTypeAndBreedingModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.INCUBATION);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.BREEDING);
    }

    @Test
    void loadRecordBuildsLocatorFromFlockBatch() {
        ChangeRecord record = handler.loadRecord(BizType.INCUBATION, 6004L);

        assertThat(record).isNotNull();
        assertThat(record.bizId()).isEqualTo(6004L);
        assertThat(record.farmId()).isEqualTo(1002L);
        assertThat(record.batchId()).isEqualTo(3211L);
        assertThat(record.penId()).isEqualTo(2301L);
        assertThat(record.recordLocator()).isEqualTo("1 号种鸡舍 · ZM2603-015 · 9月28日入孵");
        assertThat(record.recordTitle()).isEqualTo("孵化 · 1 号种鸡舍 · ZM2603-015");
        assertThat(record.recordSubtitle()).isEqualTo("西岭鸡场 · 9月28日入孵 · 王建国登记");
        assertThat(record.recordVersion()).isEqualTo("3");
        assertThat(handler.loadRecord(BizType.INCUBATION, 9999L)).isNull();
    }

    @Test
    void describeFormMatchesAppMockFields() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.INCUBATION, 6004L));

        assertThat(form.recordCard()).extracting(ChangeCardItem::label)
                .containsExactly("入孵蛋数", "受精蛋数", "出雏数", "健雏数", "新批次");
        assertThat(form.recordCard()).extracting(ChangeCardItem::value)
                .containsExactly("9,600 枚", "9,120 枚", "8,650 只", "8,480 只", "ZM2610-072");
        ChangeField hatched = form.fieldOf("hatched_count");
        assertThat(hatched.fieldName()).isEqualTo("出雏数");
        assertThat(hatched.value()).isEqualTo("8650");
        assertThat(hatched.valueUnit()).isEqualTo("只");
        assertThat(hatched.min()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(hatched.max()).isEqualByComparingTo(new BigDecimal("9120"));
        ChangeField healthy = form.fieldOf("healthy_count");
        assertThat(healthy.value()).isEqualTo("8480");
        assertThat(healthy.max()).isEqualByComparingTo(new BigDecimal("8650"));
        assertThat(healthy.recalcTags()).containsExactly("存栏", "孵化统计");
        assertThat(form.deleteAllowed()).isFalse();
        assertThat(form.voidAllowed()).isFalse();
        assertThat(form.reviewTip()).contains("健雏已转为新批次");
    }

    @Test
    void requiresRequestOnlyAfterHatchedAndTransferred() {
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.INCUBATION, 6004L))).isTrue();

        Incubation notTransferred = incubation();
        notTransferred.setBatchId(null);
        when(change.incubation(6004L)).thenReturn(notTransferred);
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.INCUBATION, 6004L))).isFalse();

        Incubation candled = incubation();
        candled.setStatus("CANDLED");
        when(change.incubation(6004L)).thenReturn(candled);
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.INCUBATION, 6004L))).isFalse();
    }

    @Test
    void validateChangeChecksHatchAndHealthyBounds() {
        ChangeRecord record = handler.loadRecord(BizType.INCUBATION, 6004L);
        ChangeForm form = handler.describeForm(record);

        ChangeValidation hatched = handler.validateChange(record, form, ChangeType.UPDATE, "hatched_count", "8600");
        assertThat(hatched.beforeValue()).isEqualTo("8650");
        assertThat(hatched.afterValue()).isEqualTo("8600");
        assertThat(hatched.afterDisplay()).isEqualTo("8600 只");

        ChangeValidation healthy = handler.validateChange(record, form, ChangeType.UPDATE, "healthy_count", "8500");
        assertThat(healthy.afterValue()).isEqualTo("8500");
        assertThat(healthy.impacts()).extracting(Enum::name).containsExactly("STOCK", "STATS");

        assertThat(messageOf(record, form, "healthy_count", "8700")).contains("健雏数不能多于出雏数 8650 只");
        assertThat(messageOf(record, form, "hatched_count", "8000")).contains("出雏数不能少于健雏数 8480 只");
        assertThat(messageOf(record, form, "hatched_count", "9200")).contains("不能多于受精蛋数 9,120 枚");
        assertThat(messageOf(record, form, "healthy_count", "8480")).contains("新值与原值相同");
    }

    @Test
    void applyHealthyCountAdjustsBatchAndWeakCount() {
        ChangeRecord record = handler.loadRecord(BizType.INCUBATION, 6004L);

        ChangeApplyResult result = handler.apply(request("healthy_count", "8400"), record, 9001L);

        assertThat(result.recalcCount()).isEqualTo(3);
        assertThat(result.recalcSummary()).contains("批次存栏");
        verify(selfBredBatchService).adjustEntry(3272L, -80, null);
        ArgumentCaptor<Incubation> captor = ArgumentCaptor.forClass(Incubation.class);
        verify(incubationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getHealthyCount()).isEqualTo(8400);
        assertThat(captor.getValue().getWeakCount()).isEqualTo(250);
        ArgumentCaptor<ChangeRequest> logCaptor = ArgumentCaptor.forClass(ChangeRequest.class);
        verify(change).logApply(logCaptor.capture(), eq(BizType.INCUBATION), eq(6004L), anyString(), eq(1002L),
                any(), any(), any());
        assertThat(logCaptor.getValue().getId()).isEqualTo(9001L);
    }

    @Test
    void validateForApproveRejectsHealthyDropBelowZeroStock() {
        BatchInfo batch = new BatchInfo(1002L, 3272L, "ZM2610-072", 2101L, 201L,
                LocalDate.of(2026, 10, 19), 0, "ENDED", List.of(2202L), 0, 8480, 0, 8480, 2202L);
        when(change.batchInfo(3272L)).thenReturn(batch);

        BizException error = (BizException) catchException(
                () -> handler.validateForApprove(request("healthy_count", "8000"),
                        handler.loadRecord(BizType.INCUBATION, 6004L)));

        assertThat(error.getCode()).isEqualTo(42205);
    }

    private String messageOf(ChangeRecord record, ChangeForm form, String field, String value) {
        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, ChangeType.UPDATE, field, value));
        assertThat(error.getCode()).isEqualTo(40001);
        return error.getMessage();
    }

    private static ChangeRequest request(String fieldCode, String value) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9001L);
        request.setChangeType("UPDATE");
        request.setFieldCode(fieldCode);
        request.setFarmId(1002L);
        request.setSubmittedBy(5002L);
        request.setSubmitterRole("OWNER");
        request.setReason("出雏数量登记有误");
        request.setBeforeJson("{\"before\":{\"" + fieldCode + "\":{\"name\":\"字段\",\"value\":\"8480\","
                + "\"display\":\"8480 只\",\"unit\":\"只\"}}}");
        request.setAfterJson("{\"after\":{\"" + fieldCode + "\":{\"name\":\"字段\",\"value\":\"" + value
                + "\",\"display\":\"" + value + " 只\",\"unit\":\"只\"}}}");
        return request;
    }

    private static Incubation incubation() {
        Incubation record = new Incubation();
        record.setId(6004L);
        record.setFarmId(1002L);
        record.setBreedingBatchId(3211L);
        record.setPenId(2301L);
        record.setBreedId(2101L);
        record.setIncubationDays(21);
        record.setSetDate(LocalDate.of(2026, 9, 28));
        record.setSetCount(9600);
        record.setExpectedHatchDate(LocalDate.of(2026, 10, 19));
        record.setCandleDate(LocalDate.of(2026, 10, 5));
        record.setFertileCount(9120);
        record.setHatchDate(LocalDate.of(2026, 10, 19));
        record.setHatchedCount(8650);
        record.setHealthyCount(8480);
        record.setWeakCount(170);
        record.setTransferToBatch(true);
        record.setTargetPenId(2202L);
        record.setBatchId(3272L);
        record.setStatus("HATCHED");
        record.setVersion(3);
        record.setCreatedBy(10001L);
        return record;
    }
}
