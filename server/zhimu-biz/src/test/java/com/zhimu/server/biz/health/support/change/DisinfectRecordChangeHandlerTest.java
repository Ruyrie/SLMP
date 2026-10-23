package com.zhimu.server.biz.health.support.change;

import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.health.entity.DisinfectRecord;
import com.zhimu.server.biz.health.entity.DisinfectTask;
import com.zhimu.server.biz.health.mapper.DisinfectRecordMapper;
import com.zhimu.server.biz.health.mapper.DisinfectTaskMapper;
import com.zhimu.server.biz.stock.api.DrugStockCommand;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 消毒记录修改申请：drug_qty 用量表单、3 天期限、批号库存校验与审核回写。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisinfectRecordChangeHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 10, 0).atZone(ZONE).toInstant(), ZONE);

    @Mock private DisinfectRecordMapper recordMapper;
    @Mock private DisinfectTaskMapper taskMapper;
    @Mock private DrugMapper drugMapper;
    @Mock private DrugLotMapper drugLotMapper;
    @Mock private DrugStockPort drugStockPort;
    @Mock private com.zhimu.server.admin.base.api.DrugCatalogQueryService drugCatalogQueryService;
    @Mock private HealthChangeSupport support;

    private DisinfectRecordChangeHandler handler;
    private DisinfectRecord record;
    private DrugLot lot;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(CLOCK);
        handler = new DisinfectRecordChangeHandler(recordMapper, taskMapper, drugMapper, drugLotMapper,
                drugStockPort, drugCatalogQueryService, support);
        record = new DisinfectRecord();
        record.setId(5301L);
        record.setFarmId(1001L);
        record.setPenId(2003L);
        record.setRecordType("PLAN_TASK");
        record.setTaskId(7101L);
        record.setDrugId(4501L);
        record.setDisinfectantName("聚维酮碘溶液");
        record.setDrugLotId(5501L);
        record.setLotNo("PVP20260901");
        record.setUsageQty(new BigDecimal("2"));
        record.setUsageUnit("L");
        record.setConcentration("1:300");
        record.setMethodCode("SPRAY");
        record.setActualTime(LocalDateTime.of(2026, 10, 17, 9, 30));
        record.setExecutorId(10003L);
        record.setVersion(2);
        record.setDeleted(false);
        record.setCreatedAt(LocalDateTime.of(2026, 10, 17, 9, 30));
        when(recordMapper.selectByIdIncludingDeleted(5301L)).thenReturn(record);
        when(support.penName(2003L)).thenReturn("3 号育肥舍");
        when(support.userName(10003L)).thenReturn("李明");

        lot = new DrugLot();
        lot.setId(5501L);
        lot.setLotNo("PVP20260901");
        lot.setExpireDate(LocalDate.of(2027, 6, 1));
        lot.setStockQty(new BigDecimal("50"));
        when(drugLotMapper.selectById(5501L)).thenReturn(lot);
        when(drugStockPort.deduct(any(DrugStockCommand.class)))
                .thenReturn(new StockConsumeResult(99L, new BigDecimal("12.0000"), new BigDecimal("36.00"),
                        new BigDecimal("47"), "PVP20260901", List.of()));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsDisinfectRecordAndHealthModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.DISINFECT_RECORD);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.HEALTH);
    }

    @Test
    void loadRecordBuildsPenDateLocator() {
        ChangeRecord loaded = handler.loadRecord(BizType.DISINFECT_RECORD, 5301L);

        assertThat(loaded.recordLocator()).isEqualTo("3 号育肥舍 · 10月17日消毒");
        assertThat(loaded.recordTitle()).isEqualTo("消毒记录 · 3 号育肥舍");
        assertThat(loaded.recordSubtitle()).isEqualTo("聚维酮碘溶液 · 10月17日 · 李明登记");
        assertThat(loaded.recordVersion()).isEqualTo("2");
        assertThat(loaded.batchId()).isNull();
        assertThat(loaded.penId()).isEqualTo(2003L);
    }

    @Test
    void requiresRequestAfterThreeDays() {
        ChangeRecord loaded = handler.loadRecord(BizType.DISINFECT_RECORD, 5301L);
        // 10月17日 的记录，10月19日 仍在 3 天内
        assertThat(handler.requiresRequest(loaded)).isFalse();

        record.setActualTime(LocalDateTime.of(2026, 10, 15, 9, 0));
        assertThat(handler.requiresRequest(loaded)).isTrue();
    }

    @Test
    void describeFormMatchesAppMock() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.DISINFECT_RECORD, 5301L));

        assertThat(form.fields()).hasSize(1);
        ChangeField field = form.fields().get(0);
        assertThat(field.fieldCode()).isEqualTo("drug_qty");
        assertThat(field.fieldName()).isEqualTo("用量");
        assertThat(field.value()).isEqualTo("2");
        assertThat(field.valueUnit()).isEqualTo("升");
        assertThat(field.inputType()).isEqualTo(ChangeField.NUMBER);
        assertThat(field.min()).isEqualByComparingTo("0");
        assertThat(field.max()).isEqualByComparingTo("100");
        assertThat(field.recalcTags()).containsExactly("消毒剂库存");
        assertThat(form.recordCard()).extracting(com.zhimu.server.biz.common.change.ChangeCardItem::label)
                .containsExactly("消毒剂", "用量", "方式");
        assertThat(form.recordCard().get(0).value()).isEqualTo("聚维酮碘溶液 1:300");
        assertThat(form.recordCard().get(1).value()).isEqualTo("2 升");
        assertThat(form.recordCard().get(2).value()).isEqualTo("喷雾");
    }

    @Test
    void validateChangeChecksUsage() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.DISINFECT_RECORD, 5301L));
        ChangeRecord loaded = handler.loadRecord(BizType.DISINFECT_RECORD, 5301L);

        ChangeValidation validation = handler.validateChange(loaded, form, ChangeType.UPDATE, "drug_qty", "3");
        assertThat(validation.afterValue()).isEqualTo("3");
        assertThat(validation.afterDisplay()).isEqualTo("3 升");
        assertThat(validation.impacts()).containsExactly(ChangeImpact.INVENTORY, ChangeImpact.COST,
                ChangeImpact.STATS);

        assertThatThrownBy(() -> handler.validateChange(loaded, form, ChangeType.UPDATE, "drug_qty", "0"))
                .isInstanceOf(BizException.class).hasMessageContaining("用量必须大于 0");
        assertThatThrownBy(() -> handler.validateChange(loaded, form, ChangeType.UPDATE, "drug_qty", "101"))
                .isInstanceOf(BizException.class).hasMessageContaining("不能超过 100升");
        assertThatThrownBy(() -> handler.validateChange(loaded, form, ChangeType.UPDATE, "drug_qty", "2"))
                .isInstanceOf(BizException.class).hasMessageContaining("新值与原值相同");
    }

    @Test
    void validateForApproveChecksLotStockAndExpiry() {
        ChangeRequest request = request("UPDATE", "{\"after\":{\"drug_qty\":{\"value\":\"10\"}}}");
        handler.validateForApprove(request, handler.loadRecord(BizType.DISINFECT_RECORD, 5301L));

        lot.setStockQty(BigDecimal.ZERO);
        assertThatThrownBy(() -> handler.validateForApprove(request,
                handler.loadRecord(BizType.DISINFECT_RECORD, 5301L)))
                .isInstanceOf(BizException.class).hasMessageContaining("消毒剂库存不足");

        lot.setStockQty(new BigDecimal("50"));
        lot.setExpireDate(LocalDate.of(2026, 10, 1));
        assertThatThrownBy(() -> handler.validateForApprove(request,
                handler.loadRecord(BizType.DISINFECT_RECORD, 5301L)))
                .isInstanceOf(BizException.class).hasMessageContaining("已过期");
    }

    @Test
    void applyUpdateRestoresAndDeductsDisinfectant() {
        ChangeRequest request = request("UPDATE", "{\"after\":{\"drug_qty\":{\"value\":\"3\"}}}");

        ChangeApplyResult result = handler.apply(request, handler.loadRecord(BizType.DISINFECT_RECORD, 5301L), 90001L);

        assertThat(result.recalcCount()).isEqualTo(2);
        verify(drugStockPort).restore(DrugStockCommand.BIZ_DISINFECT_RECORD, 5301L);
        ArgumentCaptor<DrugStockCommand> command = ArgumentCaptor.forClass(DrugStockCommand.class);
        verify(drugStockPort).deduct(command.capture());
        assertThat(command.getValue().qty()).isEqualByComparingTo("3");
        assertThat(command.getValue().flowType()).isEqualTo(DrugStockCommand.FLOW_DISINFECT_USE);
        ArgumentCaptor<DisinfectRecord> saved = ArgumentCaptor.forClass(DisinfectRecord.class);
        verify(recordMapper).updateById(saved.capture());
        assertThat(saved.getValue().getUsageQty()).isEqualByComparingTo("3");
    }

    @Test
    void applyDeleteRestoresStockAndReopensTask() {
        DisinfectTask task = new DisinfectTask();
        task.setId(7101L);
        task.setTaskDate(LocalDate.of(2026, 10, 17));
        task.setStatus("DONE");
        task.setRecordId(5301L);
        when(taskMapper.selectById(7101L)).thenReturn(task);
        ChangeRequest request = request("VOID", "{\"after\":{}}");

        handler.apply(request, handler.loadRecord(BizType.DISINFECT_RECORD, 5301L), 90001L);

        verify(drugStockPort).restore(DrugStockCommand.BIZ_DISINFECT_RECORD, 5301L);
        verify(recordMapper).deleteById(5301L);
        ArgumentCaptor<DisinfectTask> savedTask = ArgumentCaptor.forClass(DisinfectTask.class);
        verify(taskMapper).updateById(savedTask.capture());
        assertThat(savedTask.getValue().getStatus()).isEqualTo("OVERDUE");
        assertThat(savedTask.getValue().getRecordId()).isNull();
    }

    private static ChangeRequest request(String changeType, String afterJson) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9201L);
        request.setBizType("DISINFECT_RECORD");
        request.setBizId(5301L);
        request.setFarmId(1001L);
        request.setChangeType(changeType);
        request.setFieldCode("UPDATE".equals(changeType) ? "drug_qty" : null);
        request.setFieldName("用量");
        request.setAfterJson(afterJson);
        request.setReason("登记有误");
        request.setSubmittedBy(10001L);
        request.setSubmitterRole("OWNER");
        return request;
    }
}
