package com.zhimu.server.biz.health.support.change;

import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.health.entity.ImmunePlan;
import com.zhimu.server.biz.health.entity.ImmuneRecord;
import com.zhimu.server.biz.health.mapper.ImmunePlanMapper;
import com.zhimu.server.biz.health.mapper.ImmuneRecordMapper;
import com.zhimu.server.biz.stock.api.DrugStockCommand;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 接种记录修改申请：表单与 App mock 一致、批号/库存校验、审核回写与删除回滚。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImmuneRecordChangeHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 10, 0).atZone(ZONE).toInstant(), ZONE);

    @Mock private ImmuneRecordMapper recordMapper;
    @Mock private ImmunePlanMapper planMapper;
    @Mock private DrugMapper drugMapper;
    @Mock private DrugLotMapper drugLotMapper;
    @Mock private DrugStockPort drugStockPort;
    @Mock private com.zhimu.server.admin.base.api.DrugCatalogQueryService drugCatalogQueryService;
    @Mock private HealthChangeSupport support;

    private ImmuneRecordChangeHandler handler;
    private ImmuneRecord record;
    private DrugLot lot;

    @BeforeEach
    void setUp() {
        com.zhimu.server.biz.common.support.TableInfos.ensure(com.zhimu.server.biz.health.entity.ImmunePlan.class);
        BeijingTime.setClock(CLOCK);
        handler = new ImmuneRecordChangeHandler(recordMapper, planMapper, drugMapper, drugLotMapper,
                drugStockPort, drugCatalogQueryService, support);
        record = new ImmuneRecord();
        record.setId(5201L);
        record.setFarmId(1001L);
        record.setBatchId(3001L);
        record.setPenId(2003L);
        record.setPlanId(8101L);
        record.setDrugId(4001L);
        record.setVaccineName("口蹄疫灭活疫苗");
        record.setDrugLotId(5001L);
        record.setLotNo("FMD20260901");
        record.setDose(new BigDecimal("2"));
        record.setDoseUnit("ML_PER_HEAD");
        record.setQuantity(200);
        record.setTotalDose(new BigDecimal("400"));
        record.setVaccinateDate(LocalDate.of(2026, 10, 8));
        record.setExecutorId(10003L);
        record.setVersion(2);
        record.setDeleted(false);
        record.setCreatedAt(LocalDateTime.of(2026, 10, 8, 9, 0));
        when(recordMapper.selectByIdIncludingDeleted(5201L)).thenReturn(record);
        when(support.penName(2003L)).thenReturn("3 号育肥舍");
        when(support.batchNo(3001L)).thenReturn("ZM2610-031");
        when(support.userName(10003L)).thenReturn("李明");
        when(support.currentStock(1001L, 3001L, 2003L)).thenReturn(200);

        lot = new DrugLot();
        lot.setId(5001L);
        lot.setDrugId(4001L);
        lot.setLotNo("FMD20260901");
        lot.setExpireDate(LocalDate.of(2027, 3, 1));
        lot.setStockQty(new BigDecimal("1000"));
        lot.setVersion(1);
        when(drugLotMapper.selectById(5001L)).thenReturn(lot);
        Drug drug = new Drug();
        drug.setId(4001L);
        drug.setName("口蹄疫灭活疫苗");
        drug.setUnit("ML");
        when(drugMapper.selectById(4001L)).thenReturn(drug);
        when(drugStockPort.deduct(any(DrugStockCommand.class)))
                .thenReturn(new StockConsumeResult(88L, new BigDecimal("1.5000"), new BigDecimal("750.00"),
                        new BigDecimal("600"), "FMD20260901", List.of()));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsImmuneRecordAndHealthModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.IMMUNE_RECORD);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.HEALTH);
    }

    @Test
    void loadRecordUsesBatchVaccineAndDate() {
        ChangeRecord loaded = handler.loadRecord(BizType.IMMUNE_RECORD, 5201L);

        assertThat(loaded).isNotNull();
        assertThat(loaded.recordLocator()).isEqualTo("ZM2610-031 · 口蹄疫灭活疫苗 · 10月8日");
        assertThat(loaded.recordTitle()).isEqualTo("接种记录 · 3 号育肥舍");
        assertThat(loaded.recordSubtitle()).isEqualTo("口蹄疫灭活疫苗 · 10月8日 · 李明登记");
        assertThat(loaded.recordVersion()).isEqualTo("2");
        assertThat(loaded.batchId()).isEqualTo(3001L);
        assertThat(loaded.penId()).isEqualTo(2003L);
        assertThat(handler.loadRecord(BizType.IMMUNE_RECORD, 9999L)).isNull();
    }

    @Test
    void requiresRequestOnlyOutsideRegistrationDay() {
        ChangeRecord loaded = handler.loadRecord(BizType.IMMUNE_RECORD, 5201L);
        assertThat(handler.requiresRequest(loaded)).isTrue();

        record.setCreatedAt(LocalDateTime.of(2026, 10, 19, 8, 0));
        assertThat(handler.requiresRequest(loaded)).isFalse();
    }

    @Test
    void describeFormMatchesAppMock() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.IMMUNE_RECORD, 5201L));

        assertThat(form.fields()).hasSize(1);
        ChangeField field = form.fields().get(0);
        assertThat(field.fieldCode()).isEqualTo("vaccinated_count");
        assertThat(field.fieldName()).isEqualTo("接种头数");
        assertThat(field.value()).isEqualTo("200");
        assertThat(field.valueUnit()).isEqualTo("头");
        assertThat(field.inputType()).isEqualTo(ChangeField.NUMBER);
        assertThat(field.min()).isEqualByComparingTo("1");
        assertThat(field.max()).isEqualByComparingTo("200");
        assertThat(field.hint()).isEqualTo("存栏 200 头");
        assertThat(field.recalcTags()).containsExactly("疫苗库存", "免疫档案");
        assertThat(form.recordCard()).extracting(com.zhimu.server.biz.common.change.ChangeCardItem::label)
                .containsExactly("疫苗", "接种头数", "剂量");
        assertThat(form.recordCard().get(2).value()).isEqualTo("2 毫升 / 头");
        assertThat(form.voidAllowed()).isFalse();
        assertThat(form.deleteAllowed()).isTrue();
    }

    @Test
    void validateChangeChecksCountRange() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.IMMUNE_RECORD, 5201L));
        ChangeRecord loaded = handler.loadRecord(BizType.IMMUNE_RECORD, 5201L);

        ChangeValidation validation = handler.validateChange(loaded, form, ChangeType.UPDATE,
                "vaccinated_count", "180");
        assertThat(validation.afterValue()).isEqualTo("180");
        assertThat(validation.afterDisplay()).isEqualTo("180 头");
        assertThat(validation.impacts()).containsExactly(ChangeImpact.INVENTORY, ChangeImpact.COST,
                ChangeImpact.STATS);

        assertThatThrownBy(() -> handler.validateChange(loaded, form, ChangeType.UPDATE,
                "vaccinated_count", "0"))
                .isInstanceOf(BizException.class).hasMessageContaining("必须大于 0");
        assertThatThrownBy(() -> handler.validateChange(loaded, form, ChangeType.UPDATE,
                "vaccinated_count", "201"))
                .isInstanceOf(BizException.class).hasMessageContaining("不能超过存栏 200 头");
        assertThatThrownBy(() -> handler.validateChange(loaded, form, ChangeType.UPDATE,
                "vaccinated_count", "200"))
                .isInstanceOf(BizException.class).hasMessageContaining("新值与原值相同");
    }

    @Test
    void validateForApproveChecksLotAndStock() {
        ChangeRequest request = request("UPDATE", "{\"after\":{\"vaccinated_count\":{\"value\":\"250\"}}}");
        handler.validateForApprove(request, handler.loadRecord(BizType.IMMUNE_RECORD, 5201L));

        lot.setStockQty(BigDecimal.ZERO);
        assertThatThrownBy(() -> handler.validateForApprove(request,
                handler.loadRecord(BizType.IMMUNE_RECORD, 5201L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("疫苗库存不足");

        lot.setStockQty(new BigDecimal("1000"));
        lot.setRecalledAt(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThatThrownBy(() -> handler.validateForApprove(request,
                handler.loadRecord(BizType.IMMUNE_RECORD, 5201L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已被平台停用");
    }

    @Test
    void applyUpdateRestoresAndDeductsVaccine() {
        ChangeRequest request = request("UPDATE", "{\"after\":{\"vaccinated_count\":{\"value\":\"250\"}}}");

        ChangeApplyResult result = handler.apply(request, handler.loadRecord(BizType.IMMUNE_RECORD, 5201L), 90001L);

        assertThat(result.recalcCount()).isEqualTo(2);
        verify(drugStockPort).restore(DrugStockCommand.BIZ_IMMUNE_RECORD, 5201L);
        ArgumentCaptor<DrugStockCommand> command = ArgumentCaptor.forClass(DrugStockCommand.class);
        verify(drugStockPort).deduct(command.capture());
        assertThat(command.getValue().qty()).isEqualByComparingTo("500");
        assertThat(command.getValue().flowType()).isEqualTo(DrugStockCommand.FLOW_VACCINE_USE);
        assertThat(command.getValue().bizId()).isEqualTo(5201L);
        ArgumentCaptor<ImmuneRecord> saved = ArgumentCaptor.forClass(ImmuneRecord.class);
        verify(recordMapper).updateById(saved.capture());
        assertThat(saved.getValue().getQuantity()).isEqualTo(250);
        assertThat(saved.getValue().getTotalDose()).isEqualByComparingTo("500");
    }

    @Test
    void applyDeleteRestoresStockDeletesRecordAndResetsPlan() {
        ImmunePlan plan = new ImmunePlan();
        plan.setId(8101L);
        plan.setStatus("DONE");
        plan.setRecordId(5201L);
        when(planMapper.selectById(8101L)).thenReturn(plan);
        ChangeRequest request = request("DELETE", "{\"after\":{}}");

        ChangeApplyResult result = handler.apply(request, handler.loadRecord(BizType.IMMUNE_RECORD, 5201L), 90001L);

        assertThat(result.recalcSummary()).isEqualTo("已重新计算疫苗库存和免疫档案");
        verify(drugStockPort).restore(DrugStockCommand.BIZ_IMMUNE_RECORD, 5201L);
        verify(recordMapper).deleteById(5201L);
        verify(recordMapper, never()).updateById(any(ImmuneRecord.class));
        ArgumentCaptor<ImmunePlan> savedPlan = ArgumentCaptor.forClass(ImmunePlan.class);
        verify(planMapper).updateById(savedPlan.capture());
        assertThat(savedPlan.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(savedPlan.getValue().getRecordId()).isNull();
    }

    private static ChangeRequest request(String changeType, String afterJson) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9101L);
        request.setBizType("IMMUNE_RECORD");
        request.setBizId(5201L);
        request.setFarmId(1001L);
        request.setChangeType(changeType);
        request.setFieldCode("UPDATE".equals(changeType) ? "vaccinated_count" : null);
        request.setFieldName("接种头数");
        request.setAfterJson(afterJson);
        request.setReason("登记有误");
        request.setSubmittedBy(10001L);
        request.setSubmitterRole("OWNER");
        return request;
    }
}
