package com.zhimu.server.biz.health.service.impl;

import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.health.HealthTableInfo;
import com.zhimu.server.biz.health.entity.DisinfectRecord;
import com.zhimu.server.biz.health.entity.DisinfectTask;
import com.zhimu.server.biz.health.mapper.*;
import com.zhimu.server.biz.health.query.FarmDrugRow;
import com.zhimu.server.biz.health.request.DisinfectRecordCreateRequest;
import com.zhimu.server.biz.health.request.DisinfectRecordUpdateRequest;
import com.zhimu.server.biz.health.support.HealthAudit;
import com.zhimu.server.biz.health.support.HealthSupport;
import com.zhimu.server.biz.stock.api.DrugLotOption;
import com.zhimu.server.biz.stock.api.DrugStockCommand;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证登记和修改实际调用库存端口，而不是只在响应里写 todo_completed。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisinfectServiceImplTest {

    @Mock private DisinfectPlanMapper plans;
    @Mock private DisinfectPlanPenMapper planPens;
    @Mock private DisinfectTaskMapper tasks;
    @Mock private DisinfectRecordMapper records;
    @Mock private HealthReferenceMapper references;
    @Mock private HealthSupport support;
    @Mock private HealthAudit audit;
    @Mock private BizMediaService media;
    @Mock private DrugStockPort stock;
    @Mock private DisinfectTaskGenerator generator;
    @Mock private HealthTodoSource todos;

    private DisinfectServiceImpl service;
    private DisinfectTask task;
    private DisinfectRecord saved;

    @BeforeEach
    void setUp() {
        HealthTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-24T02:00:00Z"), BeijingTime.ZONE));
        service = new DisinfectServiceImpl(plans, planPens, tasks, records, references, support, audit,
                media, stock, generator, todos);
        when(support.farmId()).thenReturn(1001L);
        when(support.managerView()).thenReturn(true);
        when(support.defaultExecutorId(2003L)).thenReturn(10003L);
        when(support.penEnabled(2003L)).thenReturn(true);
        when(support.penName(2003L)).thenReturn("3 号育肥舍");
        FarmDrugRow drug = new FarmDrugRow();
        drug.setId(5301L);
        drug.setCatalogId(7301L);
        drug.setCategory("DISINFECTANT");
        drug.setName("聚维酮碘溶液");
        drug.setUnit("L");
        when(references.selectDrugs(1001L)).thenReturn(List.of(drug));
        DrugLotOption lot = new DrugLotOption(6301L, "2025033", LocalDate.of(2026, 11, 8),
                15, "NEAR_EXPIRY", "临期", null, true, false, false, new BigDecimal("9.0000"), 5);
        when(support.lotsFor(5301L, "DISINFECT")).thenReturn(List.of(lot));
        when(support.findLot(any(), eq(6301L))).thenReturn(lot);
        when(stock.deduct(any(DrugStockCommand.class))).thenReturn(new StockConsumeResult(
                170001L, new BigDecimal("9.0000"), new BigDecimal("4.50"), new BigDecimal("39.50"), "2025033", List.of()));
        task = new DisinfectTask();
        task.setId(9404L);
        task.setFarmId(1001L);
        task.setPenId(2003L);
        task.setTaskDate(BeijingTime.today());
        task.setDueTime(LocalTime.of(17, 0));
        task.setStatus("PENDING");
        task.setConcentration("1:500");
        task.setVersion(0);
        when(tasks.selectForUpdate(9404L)).thenReturn(task);
        when(tasks.update(isNull(), any())).thenReturn(1);
        when(records.insert(any(DisinfectRecord.class))).thenAnswer(invocation -> {
            saved = invocation.getArgument(0);
            saved.setId(9504L);
            saved.setCreatedAt(BeijingTime.now());
            return 1;
        });
        when(records.selectById(9504L)).thenAnswer(invocation -> saved);
        when(records.updateById(any(DisinfectRecord.class))).thenReturn(1);
        when(tasks.selectById(9404L)).thenReturn(task);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void registerDeductsStockAndCompletesExactlyTheTaskTodo() {
        var result = service.createRecord(request("PLAN_TASK"));

        ArgumentCaptor<DrugStockCommand> command = ArgumentCaptor.forClass(DrugStockCommand.class);
        verify(stock).deduct(command.capture());
        assertThat(command.getValue().bizType()).isEqualTo("DISINFECT_RECORD");
        assertThat(command.getValue().bizId()).isEqualTo(9504L);
        assertThat(command.getValue().penId()).isEqualTo(2003L);
        assertThat(command.getValue().qty()).isEqualByComparingTo("0.5");
        verify(todos).completeDisinfect(task, 9504L, 10003L);
        assertThat(result.getTodoCompleted()).isTrue();
        assertThat(result.getUnitPrice()).isEqualByComparingTo("9");
        assertThat(result.getCostAmount()).isEqualByComparingTo("4.5");
        assertThat(saved.getStockFlowId()).isEqualTo(170001L);
    }

    @Test
    void emptyPenStillDeductsSuppliesWithoutChargingBatchOrCompletingTask() {
        service.createRecord(request("EMPTY_PEN"));

        ArgumentCaptor<DrugStockCommand> command = ArgumentCaptor.forClass(DrugStockCommand.class);
        verify(stock).deduct(command.capture());
        assertThat(command.getValue().batchId()).isNull();
        assertThat(command.getValue().penId()).isNull();
        verifyNoInteractions(todos);
    }

    @Test
    void editRestoresOldConsumptionBeforeDeductingNewQuantity() {
        service.createRecord(request("PLAN_TASK"));
        clearInvocations(stock, todos);
        DisinfectRecordUpdateRequest update = new DisinfectRecordUpdateRequest();
        update.setVersion(0);
        update.setUsageQty(new BigDecimal("0.7"));
        update.setFileIds(null);

        service.updateRecord(9504L, update);

        var order = inOrder(stock);
        order.verify(stock).restore("DISINFECT_RECORD", 9504L);
        ArgumentCaptor<DrugStockCommand> command = ArgumentCaptor.forClass(DrugStockCommand.class);
        order.verify(stock).deduct(command.capture());
        assertThat(command.getValue().qty()).isEqualByComparingTo("0.7");
    }

    private DisinfectRecordCreateRequest request(String type) {
        DisinfectRecordCreateRequest request = new DisinfectRecordCreateRequest();
        request.setRecordType(type);
        request.setTaskId(9404L);
        request.setPenId(2003L);
        request.setDrugId(5301L);
        request.setDrugLotId(6301L);
        request.setUsageQty(new BigDecimal("0.5"));
        request.setConcentration("1:500");
        request.setMethodCode("SPRAY");
        request.setActualTime(BeijingTime.now().minusMinutes(5));
        request.setFileIds(null);
        return request;
    }
}
