package com.zhimu.server.biz.breeding.support.change;

import com.zhimu.server.biz.breeding.entity.BreedingAnimal;
import com.zhimu.server.biz.breeding.entity.BreedingCycle;
import com.zhimu.server.biz.breeding.entity.FarrowingRecord;
import com.zhimu.server.biz.breeding.mapper.FarrowingRecordMapper;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 产仔记录修改申请 handler：字段与 App Mock（change_form_farrowing.json）一致。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FarrowingChangeHandlerTest {

    @Mock private BreedingChangeSupport change;
    @Mock private FarrowingRecordMapper farrowingRecordMapper;
    @Mock private SelfBredBatchService selfBredBatchService;

    private FarrowingChangeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FarrowingChangeHandler(change, farrowingRecordMapper, selfBredBatchService);
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 19, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        when(change.farrowing(8161L)).thenReturn(farrowing());
        when(change.animal(4161L)).thenReturn(animal());
        when(change.cycle(5061L)).thenReturn(cycle());
        when(change.penName(2007L)).thenReturn("产房");
        when(change.penName(null)).thenReturn("");
        when(change.batchNo(3052L)).thenReturn("ZM2609-052");
        when(change.batchNo(null)).thenReturn(null);
        when(change.farmName(1001L)).thenReturn("东山猪场");
        when(change.userName(10002L)).thenReturn("刘芳");
        when(change.userName(10001L)).thenReturn("王建国");
        when(change.monthDay(LocalDate.of(2026, 9, 17))).thenReturn("9月17日");
        when(change.monthDay(LocalDate.of(2026, 5, 26))).thenReturn("5月26日");
        when(change.breedName(1201L)).thenReturn("长大二元");
        when(farrowingRecordMapper.updateById(any(FarrowingRecord.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsFarrowingTypeAndBreedingModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.FARROWING);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.BREEDING);
    }

    @Test
    void loadRecordBuildsLocatorFromAnimalAndPen() {
        ChangeRecord record = handler.loadRecord(BizType.FARROWING, 8161L);

        assertThat(record).isNotNull();
        assertThat(record.bizId()).isEqualTo(8161L);
        assertThat(record.farmId()).isEqualTo(1001L);
        assertThat(record.batchId()).isEqualTo(3052L);
        assertThat(record.penId()).isEqualTo(2007L);
        assertThat(record.recordLocator()).isEqualTo("产房 · 0161 · 9月17日 产仔");
        assertThat(record.recordTitle()).isEqualTo("产仔 · 0161");
        assertThat(record.recordSubtitle()).isEqualTo("东山猪场 · 9月17日 · 刘芳登记");
        assertThat(record.recordVersion()).isEqualTo("3");
        assertThat(handler.loadRecord(BizType.FARROWING, 9999L)).isNull();
    }

    @Test
    void describeFormMatchesAppMockFields() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.FARROWING, 8161L));

        assertThat(form.recordCard()).extracting(ChangeCardItem::label)
                .containsExactly("母猪", "总产仔", "活仔", "死胎", "新批次");
        assertThat(form.recordCard()).extracting(ChangeCardItem::value)
                .containsExactly("0161 · 长大二元 · 第 2 胎", "12 头", "11 头", "1 头", "ZM2609-052");
        ChangeField date = form.fieldOf("farrow_date");
        assertThat(date.inputType()).isEqualTo(ChangeField.DATE);
        assertThat(date.value()).isEqualTo("2026-09-17");
        assertThat(date.recalcTags()).containsExactly("日龄", "饲喂与免疫计划");
        ChangeField total = form.fieldOf("total_born");
        assertThat(total.fieldName()).isEqualTo("总产仔数");
        assertThat(total.value()).isEqualTo("12");
        assertThat(total.min()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(total.max()).isEqualByComparingTo(new BigDecimal("50"));
        ChangeField live = form.fieldOf("live_born");
        assertThat(live.value()).isEqualTo("11");
        assertThat(live.hint()).isEqualTo("活仔 + 死胎须等于总产仔");
        assertThat(live.recalcTags()).containsExactly("存栏", "繁育统计");
        assertThat(form.fieldOf("stillborn").value()).isEqualTo("1");
        assertThat(form.deleteAllowed()).isFalse();
        assertThat(form.voidAllowed()).isFalse();
        assertThat(form.reviewTip()).contains("已转为新批次");
    }

    @Test
    void requiresRequestWhenTransferredOrWeaned() {
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.FARROWING, 8161L))).isTrue();

        FarrowingRecord notTransferred = farrowing();
        notTransferred.setBatchId(null);
        when(change.farrowing(8161L)).thenReturn(notTransferred);
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.FARROWING, 8161L))).isFalse();

        BreedingCycle weaned = cycle();
        weaned.setWeaningId(71161L);
        when(change.cycle(5061L)).thenReturn(weaned);
        assertThat(handler.requiresRequest(handler.loadRecord(BizType.FARROWING, 8161L))).isTrue();
    }

    @Test
    void validateChangeKeepsLivePlusStillbornEqualTotal() {
        ChangeRecord record = handler.loadRecord(BizType.FARROWING, 8161L);
        ChangeForm form = handler.describeForm(record);

        ChangeValidation live = handler.validateChange(record, form, ChangeType.UPDATE, "live_born", "12");
        assertThat(live.fieldCode()).isEqualTo("live_born");
        assertThat(live.beforeValue()).isEqualTo("11");
        assertThat(live.afterValue()).isEqualTo("12");
        assertThat(live.impacts()).extracting(Enum::name).containsExactly("STOCK", "STATS");

        ChangeValidation still = handler.validateChange(record, form, ChangeType.UPDATE, "stillborn", "3");
        assertThat(still.afterValue()).isEqualTo("3");

        ChangeValidation date = handler.validateChange(record, form, ChangeType.UPDATE, "farrow_date",
                "2026-09-20");
        assertThat(date.afterDisplay()).isEqualTo("2026-09-20");
        assertThat(date.recalcTags()).containsExactly("日龄", "饲喂与免疫计划");

        assertThat(messageOf(record, form, "live_born", "13")).contains("活仔数不能多于总产仔 12 头");
        assertThat(messageOf(record, form, "live_born", "11")).contains("新值与原值相同");
        assertThat(messageOf(record, form, "total_born", "10")).contains("总产仔数不能少于活仔数 11 头");
        assertThat(messageOf(record, form, "farrow_date", "2026-10-25")).contains("不能晚于今天");
        assertThat(messageOf(record, form, "farrow_date", "2026-05-01")).contains("不能早于配种日期");
    }

    @Test
    void applyLiveBornAdjustsBatchStockByDelta() {
        ChangeRecord record = handler.loadRecord(BizType.FARROWING, 8161L);

        ChangeApplyResult result = handler.apply(request("live_born", "12"), record, 9001L);

        assertThat(result.recalcCount()).isEqualTo(3);
        assertThat(result.recalcSummary()).contains("批次存栏");
        verify(selfBredBatchService).adjustEntry(3052L, 1, null);
        verify(farrowingRecordMapper).updateById(any(FarrowingRecord.class));
        ArgumentCaptor<ChangeRequest> captor = ArgumentCaptor.forClass(ChangeRequest.class);
        verify(change).logApply(captor.capture(), eq(BizType.FARROWING), eq(8161L), anyString(), eq(1001L),
                any(), any(), any());
        assertThat(captor.getValue().getId()).isEqualTo(9001L);
        assertThat(captor.getValue().getSubmittedBy()).isEqualTo(5001L);
    }

    @Test
    void applyFarrowDateSyncsBatchEntryDate() {
        ChangeRecord record = handler.loadRecord(BizType.FARROWING, 8161L);

        handler.apply(request("farrow_date", "2026-09-20"), record, 9001L);

        verify(selfBredBatchService).adjustEntry(3052L, 0, LocalDate.of(2026, 9, 20));
    }

    @Test
    void validateForApproveRejectsLiveDropBelowZeroStock() {
        BatchInfo batch = new BatchInfo(1001L, 3052L, "ZM2609-052", 1101L, 101L,
                LocalDate.of(2026, 9, 17), 0, "ENDED", List.of(2007L), 0, 11, 1, 10, 2007L);
        when(change.batchInfo(3052L)).thenReturn(batch);

        BizException error = (BizException) catchException(
                () -> handler.validateForApprove(request("live_born", "0"),
                        handler.loadRecord(BizType.FARROWING, 8161L)));

        assertThat(error.getCode()).isEqualTo(42205);
    }

    @Test
    void voidOrDeleteIsRejected() {
        ChangeRecord record = handler.loadRecord(BizType.FARROWING, 8161L);
        ChangeForm form = handler.describeForm(record);

        BizException error = (BizException) catchException(
                () -> handler.validateChange(record, form, ChangeType.VOID, null, null));

        assertThat(error.getCode()).isEqualTo(40903);
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
        request.setFarmId(1001L);
        request.setSubmittedBy(5001L);
        request.setSubmitterRole("OWNER");
        request.setReason("登记时填错，需要更正");
        request.setBeforeJson("{\"before\":{\"" + fieldCode + "\":{\"name\":\"字段\",\"value\":\"11\","
                + "\"display\":\"11 头\",\"unit\":\"头\"}}}");
        request.setAfterJson("{\"after\":{\"" + fieldCode + "\":{\"name\":\"字段\",\"value\":\"" + value
                + "\",\"display\":\"" + value + " 头\",\"unit\":\"头\"}}}");
        return request;
    }

    private static FarrowingRecord farrowing() {
        FarrowingRecord record = new FarrowingRecord();
        record.setId(8161L);
        record.setFarmId(1001L);
        record.setAnimalId(4161L);
        record.setCycleId(5061L);
        record.setParityNo(2);
        record.setFarrowDate(LocalDate.of(2026, 9, 17));
        record.setTotalBorn(12);
        record.setLiveBorn(11);
        record.setStillborn(1);
        record.setTransferToBatch(true);
        record.setTargetPenId(2007L);
        record.setOffspringBreedId(1101L);
        record.setBatchId(3052L);
        record.setVersion(3);
        record.setCreatedBy(10002L);
        return record;
    }

    private static BreedingAnimal animal() {
        BreedingAnimal animal = new BreedingAnimal();
        animal.setId(4161L);
        animal.setFarmId(1001L);
        animal.setEarTag("0161");
        animal.setBreedId(1201L);
        animal.setFilingDate(LocalDate.of(2025, 1, 10));
        animal.setStatus("EMPTY");
        animal.setVersion(3);
        return animal;
    }

    private static BreedingCycle cycle() {
        BreedingCycle cycle = new BreedingCycle();
        cycle.setId(5061L);
        cycle.setFarmId(1001L);
        cycle.setAnimalId(4161L);
        cycle.setFirstMatingDate(LocalDate.of(2026, 5, 26));
        cycle.setStatus("WEANED");
        return cycle;
    }
}
