package com.zhimu.server.biz.health.support.change;

import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.health.entity.BatchWithdrawal;
import com.zhimu.server.biz.health.entity.MedicationRecord;
import com.zhimu.server.biz.health.mapper.BatchWithdrawalMapper;
import com.zhimu.server.biz.health.mapper.MedicationRecordMapper;
import com.zhimu.server.biz.stock.api.DrugStockCommand;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
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
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 用药记录修改申请：只有删除（U01 无可改字段），审核通过后回滚库存并重算可出栏日期。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MedicationRecordChangeHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 10, 0).atZone(ZONE).toInstant(), ZONE);

    @Mock private MedicationRecordMapper recordMapper;
    @Mock private BatchWithdrawalMapper withdrawalMapper;
    @Mock private DrugStockPort drugStockPort;
    @Mock private HealthChangeSupport support;

    private MedicationRecordChangeHandler handler;
    private MedicationRecord record;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(CLOCK);
        handler = new MedicationRecordChangeHandler(recordMapper, withdrawalMapper, drugStockPort, support);
        record = new MedicationRecord();
        record.setId(5101L);
        record.setFarmId(1001L);
        record.setBatchId(3001L);
        record.setPenId(2003L);
        record.setDrugName("氟苯尼考粉");
        record.setDrugLotId(5003L);
        record.setFirstDate(LocalDate.of(2026, 10, 5));
        record.setLastDate(LocalDate.of(2026, 10, 9));
        record.setWithdrawalEndDate(LocalDate.of(2026, 10, 29));
        record.setWithdrawalDays(20);
        record.setExecutorId(10003L);
        record.setVersion(2);
        record.setDeleted(false);
        record.setCreatedAt(LocalDateTime.of(2026, 10, 5, 9, 0));
        when(recordMapper.selectByIdIncludingDeleted(5101L)).thenReturn(record);
        when(support.penName(2003L)).thenReturn("3 号育肥舍");
        when(support.batchNo(3001L)).thenReturn("ZM2610-031");
        when(support.userName(10003L)).thenReturn("李明");

        BatchWithdrawal withdrawal = new BatchWithdrawal();
        withdrawal.setId(601L);
        withdrawal.setFarmId(1001L);
        withdrawal.setBatchId(3001L);
        withdrawal.setReleaseDate(LocalDate.of(2026, 10, 29));
        when(withdrawalMapper.selectByBatchId(3001L)).thenReturn(withdrawal);
        when(withdrawalMapper.selectForUpdate(3001L)).thenReturn(withdrawal);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsMedicationRecordAndHealthModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.MEDICATION_RECORD);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.HEALTH);
    }

    @Test
    void loadRecordBuildsBatchDrugAndRangeLocator() {
        ChangeRecord loaded = handler.loadRecord(BizType.MEDICATION_RECORD, 5101L);

        assertThat(loaded.recordLocator()).isEqualTo("ZM2610-031 · 氟苯尼考粉 · 10月5日–10月9日");
        assertThat(loaded.recordTitle()).isEqualTo("用药记录 · 3 号育肥舍");
        assertThat(loaded.recordSubtitle()).isEqualTo("氟苯尼考粉 · 10月5日–10月9日 · 李明登记");
        assertThat(loaded.recordVersion()).isEqualTo("2");
        assertThat(handler.loadRecord(BizType.MEDICATION_RECORD, 9999L)).isNull();
    }

    @Test
    void requiresRequestOutsideRegistrationDay() {
        ChangeRecord loaded = handler.loadRecord(BizType.MEDICATION_RECORD, 5101L);
        assertThat(handler.requiresRequest(loaded)).isTrue();

        record.setCreatedAt(LocalDateTime.of(2026, 10, 19, 9, 0));
        assertThat(handler.requiresRequest(loaded)).isFalse();
    }

    @Test
    void describeFormHasNoFieldsLikeAppMock() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.MEDICATION_RECORD, 5101L));

        assertThat(form.fields()).isEmpty();
        assertThat(form.recordCard()).isEmpty();
        assertThat(form.linkedRules()).isEmpty();
        assertThat(form.reviewTip()).isEqualTo("审核通过前，记录和可出栏日期保持不变。");
        assertThat(form.deleteAllowed()).isTrue();
        assertThat(form.voidAllowed()).isFalse();
    }

    @Test
    void validateDeleteReturnsSellableDateImpacts() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.MEDICATION_RECORD, 5101L));

        ChangeValidation validation = handler.validateChange(
                handler.loadRecord(BizType.MEDICATION_RECORD, 5101L), form, ChangeType.DELETE, null, null);

        assertThat(validation.hasField()).isFalse();
        assertThat(validation.impacts()).containsExactly(ChangeImpact.SELLABLE_DATE, ChangeImpact.INVENTORY,
                ChangeImpact.COST);
        assertThat(validation.recalcTags()).containsExactly("可出栏日期", "药品库存");
    }

    @Test
    void validateUpdateRejectedBecauseOnlyDeleteIsSupported() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.MEDICATION_RECORD, 5101L));

        assertThatThrownBy(() -> handler.validateChange(handler.loadRecord(BizType.MEDICATION_RECORD, 5101L),
                form, ChangeType.UPDATE, "used_qty", "100"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用药记录不支持直接修改");
    }

    @Test
    void applyDeleteRestoresStockAndRecalculatesReleaseDate() {
        MedicationRecord remaining = new MedicationRecord();
        remaining.setId(5102L);
        remaining.setDrugName("阿莫西林");
        remaining.setLastDate(LocalDate.of(2026, 10, 12));
        remaining.setWithdrawalEndDate(LocalDate.of(2026, 10, 22));
        remaining.setWithdrawalDays(10);
        when(recordMapper.selectEffective(1001L, 3001L)).thenReturn(List.of(remaining));
        ChangeRequest request = request("DELETE");

        ChangeApplyResult result = handler.apply(request, handler.loadRecord(BizType.MEDICATION_RECORD, 5101L),
                90001L);

        assertThat(result.recalcSummary()).isEqualTo("已重算可出栏日期，当前为 10月22日");
        verify(drugStockPort).restore(DrugStockCommand.BIZ_MEDICATION_RECORD, 5101L);
        verify(recordMapper).deleteById(5101L);
        ArgumentCaptor<BatchWithdrawal> saved = ArgumentCaptor.forClass(BatchWithdrawal.class);
        verify(withdrawalMapper).updateById(saved.capture());
        assertThat(saved.getValue().getReleaseDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(saved.getValue().getDecisiveRecordId()).isEqualTo(5102L);
        assertThat(saved.getValue().getWithdrawalDays()).isEqualTo(10);
    }

    @Test
    void applyUpdateRejected() {
        ChangeRequest request = request("UPDATE");
        request.setFieldCode("used_qty");

        assertThatThrownBy(() -> handler.apply(request,
                handler.loadRecord(BizType.MEDICATION_RECORD, 5101L), 90001L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用药记录不支持直接修改");
    }

    private static ChangeRequest request(String changeType) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9301L);
        request.setBizType("MEDICATION_RECORD");
        request.setBizId(5101L);
        request.setFarmId(1001L);
        request.setChangeType(changeType);
        request.setAfterJson("{\"after\":{}}");
        request.setReason("登记有误");
        request.setSubmittedBy(10001L);
        request.setSubmitterRole("OWNER");
        return request;
    }
}
