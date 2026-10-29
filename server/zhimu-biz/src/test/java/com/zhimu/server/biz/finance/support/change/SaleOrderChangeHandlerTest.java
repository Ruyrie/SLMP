package com.zhimu.server.biz.finance.support.change;

import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeCardItem;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.finance.api.BatchSalePort;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.entity.SaleReceipt;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.finance.mapper.SaleReceiptMapper;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 出栏单修改申请：unit_price / quantity（含 App JSON 口径）表单、已收金额校验、作废回写。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaleOrderChangeHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 10, 0).atZone(ZONE).toInstant(), ZONE);

    @Mock private SaleOrderMapper orderMapper;
    @Mock private SaleReceiptMapper receiptMapper;
    @Mock private FinanceRecordMapper financeRecordMapper;
    @Mock private BatchQueryService batchQueryService;
    @Mock private BatchSalePort batchSalePort;
    @Mock private AccountQueryService accountQueryService;
    @Mock private OperationLogRecorder operationLogRecorder;

    @Mock private com.zhimu.server.biz.finance.service.BatchSettlementService batchSettlementService;
    private SaleOrderChangeHandler handler;
    private SaleOrder order;

    @BeforeEach
    void setUp() {
        com.zhimu.server.biz.common.support.TableInfos.ensure(com.zhimu.server.biz.finance.entity.SaleOrder.class);
        BeijingTime.setClock(CLOCK);
        handler = new SaleOrderChangeHandler(orderMapper, receiptMapper, financeRecordMapper,
                batchQueryService, batchSalePort, accountQueryService, operationLogRecorder, batchSettlementService,
                org.mockito.Mockito.mock(com.zhimu.server.biz.finance.support.ReceivableTodos.class));
        order = new SaleOrder();
        order.setId(9102L);
        order.setFarmId(1001L);
        order.setBatchId(3001L);
        order.setPenId(2003L);
        order.setSaleNo("CL20261013001");
        order.setPenAllocations("[{\"pen_id\":2003,\"pen_name\":\"3 号育肥舍\",\"quantity\":98}]");
        order.setSaleDate(LocalDate.of(2026, 10, 13));
        order.setQuantity(98);
        order.setTotalWeight(new BigDecimal("11760.00"));
        order.setAvgWeight(new BigDecimal("120.0"));
        order.setPricingMethod("BY_WEIGHT");
        order.setUnitPrice(new BigDecimal("16.00"));
        order.setSaleAmount(new BigDecimal("188160"));
        order.setReceivedAmount(new BigDecimal("100000"));
        order.setUnpaidAmount(new BigDecimal("88160"));
        order.setReceiptCount(1);
        order.setPaymentStatus("PARTIAL");
        order.setContentVersion(3);
        order.setVersion(2);
        order.setCreatedBy(10001L);
        when(orderMapper.selectById(9102L)).thenReturn(order);
        when(orderMapper.updateById(any(SaleOrder.class))).thenReturn(1);
        when(receiptMapper.updateById(any(SaleReceipt.class))).thenReturn(1);
        when(financeRecordMapper.updateById(any(FinanceRecord.class))).thenReturn(1);

        BatchInfo batch = new BatchInfo(1001L, 3001L, "ZM2609-012", 1L, 101L,
                LocalDate.of(2026, 6, 1), 0, "ACTIVE", List.of(2003L), 120, 100, 0, 98, 2003L);
        when(batchQueryService.requireBatch(3001L)).thenReturn(batch);
        when(accountQueryService.getBrief(any())).thenAnswer(invocation ->
                new AccountBrief(invocation.getArgument(0), "王建国", "13800000000", "NORMAL", false));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void supportsSaleOrderAndSaleModule() {
        assertThat(handler.supportedBizTypes()).containsExactly(BizType.SALE_ORDER);
        assertThat(handler.sourceModule()).isEqualTo(SourceModule.SALE);
    }

    @Test
    void loadRecordBuildsBatchLocatorAndAmounts() {
        ChangeRecord record = handler.loadRecord(BizType.SALE_ORDER, 9102L);

        assertThat(record.recordLocator()).isEqualTo("ZM2609-012 · 10月13日出栏");
        assertThat(record.recordTitle()).isEqualTo("出栏单 ZM2609-012");
        assertThat(record.recordSubtitle()).isEqualTo("11,760 千克 · 已收 100,000 元");
        assertThat(record.recordVersion()).isEqualTo("3");
        assertThat(record.batchId()).isEqualTo(3001L);
        assertThat(record.penId()).isEqualTo(2003L);
    }

    @Test
    void requiresRequestOnlyWhenReceived() {
        ChangeRecord record = handler.loadRecord(BizType.SALE_ORDER, 9102L);
        assertThat(handler.requiresRequest(record)).isTrue();

        order.setReceivedAmount(BigDecimal.ZERO);
        assertThat(handler.requiresRequest(record)).isFalse();

        order.setReceivedAmount(new BigDecimal("100000"));
        order.setPaymentStatus("VOIDED");
        assertThat(handler.requiresRequest(record)).isFalse();
    }

    @Test
    void describeFormMatchesAppMock() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.SALE_ORDER, 9102L));

        assertThat(form.fields()).hasSize(2);
        ChangeField price = form.fields().get(0);
        assertThat(price.fieldCode()).isEqualTo("unit_price");
        assertThat(price.fieldName()).isEqualTo("单价");
        assertThat(price.value()).isEqualTo("16.00");
        assertThat(price.valueUnit()).isEqualTo("元/千克");
        assertThat(price.min()).isEqualByComparingTo("0");
        assertThat(price.max()).isEqualByComparingTo("99999.99");
        ChangeField quantity = form.fields().get(1);
        assertThat(quantity.fieldCode()).isEqualTo("quantity");
        assertThat(quantity.value()).isEqualTo("98");
        assertThat(quantity.valueUnit()).isEqualTo("头");
        assertThat(quantity.min()).isEqualByComparingTo("1");
        assertThat(quantity.max()).isEqualByComparingTo("98");
        assertThat(form.recordCard()).extracting(ChangeCardItem::label)
                .containsExactly("销售金额", "已收");
        assertThat(form.recordCard().get(0).value()).isEqualTo("188,160 元");
        assertThat(form.recordCard().get(1).value()).isEqualTo("100,000 元");
        assertThat(form.voidAllowed()).isTrue();
        assertThat(form.voidNotice()).isEqualTo("审核通过后出栏单作废，已收款生成的收入同步作废");
        assertThat(form.voidRecalcTags()).containsExactly("销售金额", "批次盈亏", "存栏");
    }

    @Test
    void validateUnitPriceAllowsApprovedCorrectionBelowReceived() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.SALE_ORDER, 9102L));
        ChangeRecord record = handler.loadRecord(BizType.SALE_ORDER, 9102L);

        ChangeValidation validation = handler.validateChange(record, form, ChangeType.UPDATE,
                "unit_price", "15.5");
        assertThat(validation.afterValue()).isEqualTo("15.50");
        assertThat(validation.impacts()).containsExactly(ChangeImpact.INCOME, ChangeImpact.COST,
                ChangeImpact.STATS);

        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "unit_price", "16.00"))
                .isInstanceOf(BizException.class).hasMessageContaining("新单价与原单价相同");
        assertThat(handler.validateChange(record, form, ChangeType.UPDATE, "unit_price", "8.00").afterValue())
                .isEqualTo("8.00");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "unit_price", "100000"))
                .isInstanceOf(BizException.class).hasMessageContaining("单价不能超过");
    }

    @Test
    void validateQuantityAcceptsPlainAndJsonValues() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.SALE_ORDER, 9102L));
        ChangeRecord record = handler.loadRecord(BizType.SALE_ORDER, 9102L);

        ChangeValidation plain = handler.validateChange(record, form, ChangeType.UPDATE,
                "quantity", "90");
        assertThat(plain.afterValue()).isEqualTo("90");
        assertThat(plain.impacts()).contains(ChangeImpact.STOCK);

        // App E08：数量联动总重的 JSON 字符串（任务书第 9 条）
        String jsonValue = "{\"quantity\":96,\"total_weight\":11520}";
        ChangeValidation json = handler.validateChange(record, form, ChangeType.UPDATE,
                "quantity", jsonValue);
        assertThat(json.afterValue()).isEqualTo(jsonValue);
        assertThat(json.afterDisplay()).isEqualTo("96 头");

        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "quantity", "99"))
                .isInstanceOf(BizException.class).hasMessageContaining("不能超过 98 头");
        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.UPDATE,
                "quantity", "98"))
                .isInstanceOf(BizException.class).hasMessageContaining("新数量与原数量相同");
    }

    @Test
    void validateVoidReturnsImpactsAndDeleteRejected() {
        ChangeForm form = handler.describeForm(handler.loadRecord(BizType.SALE_ORDER, 9102L));
        ChangeRecord record = handler.loadRecord(BizType.SALE_ORDER, 9102L);

        ChangeValidation voidValidation = handler.validateChange(record, form, ChangeType.VOID, null, null);
        assertThat(voidValidation.hasField()).isFalse();
        assertThat(voidValidation.impacts()).containsExactly(ChangeImpact.INCOME, ChangeImpact.COST,
                ChangeImpact.STATS, ChangeImpact.STOCK);

        assertThatThrownBy(() -> handler.validateChange(record, form, ChangeType.DELETE, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("只能申请作废");
    }

    @Test
    void applyQuantityUpdateRecalculatesAmountsAndBatchStock() {
        ChangeRequest request = request("UPDATE", "quantity",
                "{\"after\":{\"quantity\":{\"value\":\"{\\\"quantity\\\":90,\\\"total_weight\\\":10800}\"}}}");

        ChangeApplyResult result = handler.apply(request, handler.loadRecord(BizType.SALE_ORDER, 9102L), 90001L);

        assertThat(result.recalcCount()).isEqualTo(3);
        ArgumentCaptor<SaleOrder> saved = ArgumentCaptor.forClass(SaleOrder.class);
        verify(orderMapper).updateById(saved.capture());
        SaleOrder value = saved.getValue();
        assertThat(value.getQuantity()).isEqualTo(90);
        assertThat(value.getTotalWeight()).isEqualByComparingTo("10800");
        assertThat(value.getSaleAmount()).isEqualByComparingTo("172800");
        assertThat(value.getUnpaidAmount()).isEqualByComparingTo("72800");
        assertThat(value.getContentVersion()).isEqualTo(4);
        assertThat(value.getPenAllocations()).contains("\"quantity\":90");
        verify(batchSalePort).adjustSale(anyLong(), anyLong(), any(), anyMap(), anyLong());
    }

    @Test
    void applyVoidVoidsReceiptsAndFinanceRecords() {
        SaleReceipt receipt = new SaleReceipt();
        receipt.setId(8001L);
        receipt.setSaleOrderId(9102L);
        receipt.setAmount(new BigDecimal("100000"));
        receipt.setStatus("NORMAL");
        receipt.setFinanceRecordId(7001L);
        when(receiptMapper.selectNormalByOrderId(9102L)).thenReturn(List.of(receipt));
        FinanceRecord finance = new FinanceRecord();
        finance.setId(7001L);
        finance.setStatus("NORMAL");
        when(financeRecordMapper.selectById(7001L)).thenReturn(finance);
        ChangeRequest request = request("VOID", null, "{\"after\":{}}");

        handleVoid(request);

        ArgumentCaptor<SaleReceipt> savedReceipt = ArgumentCaptor.forClass(SaleReceipt.class);
        verify(receiptMapper).updateById(savedReceipt.capture());
        assertThat(savedReceipt.getValue().getStatus()).isEqualTo("VOID");
        ArgumentCaptor<FinanceRecord> savedFinance = ArgumentCaptor.forClass(FinanceRecord.class);
        verify(financeRecordMapper).updateById(savedFinance.capture());
        assertThat(savedFinance.getValue().getStatus()).isEqualTo("VOID");
        ArgumentCaptor<SaleOrder> savedOrder = ArgumentCaptor.forClass(SaleOrder.class);
        verify(orderMapper).updateById(savedOrder.capture());
        assertThat(savedOrder.getValue().getPaymentStatus()).isEqualTo("VOIDED");
        assertThat(savedOrder.getValue().getVoidSource()).isEqualTo("CHANGE_REQUEST");
        assertThat(savedOrder.getValue().getVoidedBy()).isEqualTo(90001L);
        verify(batchSalePort).restoreSale(anyLong(), anyLong(), any(), anyMap(), anyLong());
    }

    @Test
    void approvedLowerPriceCorrectsReceiptAndIncomeTogether() {
        SaleReceipt receipt = receipt(901L, "100000", 701L);
        FinanceRecord income = income(701L, "100000");
        when(receiptMapper.selectNormalByOrderId(9102L)).thenReturn(List.of(receipt));
        when(financeRecordMapper.selectById(701L)).thenReturn(income);
        var change = request("UPDATE", "unit_price", "{\"after\":{\"unit_price\":{\"value\":\"8.00\"}}}");
        handler.validateForApprove(change, handler.loadRecord(BizType.SALE_ORDER, 9102L));
        handleVoid(change);
        assertThat(order.getSaleAmount()).isEqualByComparingTo("94080");
        assertThat(order.getReceivedAmount()).isEqualByComparingTo("94080");
        assertThat(order.getUnpaidAmount()).isZero();
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
        assertThat(receipt.getAmount()).isEqualByComparingTo("94080");
        assertThat(income.getAmount()).isEqualByComparingTo("94080");
        verify(receiptMapper).updateById(receipt);
        verify(financeRecordMapper).updateById(income);
        verify(batchSettlementService).recalculateForSale(1001L, 3001L);
    }

    @Test
    void correctionAcrossMultipleReceiptsVoidsNewestAndAdjustsRemainder() {
        SaleReceipt early = receipt(901L, "40000", 701L), late = receipt(902L, "60000", 702L);
        FinanceRecord earlyIncome = income(701L, "40000"), lateIncome = income(702L, "60000");
        when(receiptMapper.selectNormalByOrderId(9102L)).thenReturn(List.of(early, late));
        when(financeRecordMapper.selectById(701L)).thenReturn(earlyIncome);
        when(financeRecordMapper.selectById(702L)).thenReturn(lateIncome);
        handleVoid(request("UPDATE", "unit_price", "{\"after\":{\"unit_price\":{\"value\":\"2.00\"}}}"));
        assertThat(order.getReceivedAmount()).isEqualByComparingTo("23520");
        assertThat(order.getReceiptCount()).isEqualTo(1);
        assertThat(late.getStatus()).isEqualTo("VOID");
        assertThat(lateIncome.getStatus()).isEqualTo("VOID");
        assertThat(early.getAmount()).isEqualByComparingTo("23520");
        assertThat(earlyIncome.getAmount()).isEqualByComparingTo("23520");
    }

    private SaleReceipt receipt(long id, String amount, long incomeId) {
        SaleReceipt receipt = new SaleReceipt();
        receipt.setId(id); receipt.setFarmId(1001L); receipt.setSaleOrderId(9102L);
        receipt.setAmount(new BigDecimal(amount)); receipt.setFinanceRecordId(incomeId); receipt.setStatus("NORMAL");
        return receipt;
    }

    private FinanceRecord income(long id, String amount) {
        FinanceRecord income = new FinanceRecord();
        income.setId(id); income.setFarmId(1001L); income.setAmount(new BigDecimal(amount)); income.setStatus("NORMAL");
        return income;
    }

    private void handleVoid(ChangeRequest request) {
        handler.apply(request, handler.loadRecord(BizType.SALE_ORDER, 9102L), 90001L);
    }

    @Test
    void onSubmittedAndClosedMaintainPendingRequestId() {
        ChangeRequest request = request("UPDATE", "unit_price",
                "{\"after\":{\"unit_price\":{\"value\":\"15.50\"}}}");
        request.setId(9202L);
        handler.onSubmitted(request);
        assertThat(order.getPendingChangeRequestId()).isEqualTo(9202L);

        order.setPendingChangeRequestId(9202L);
        handler.onClosed(request);
        assertThat(order.getPendingChangeRequestId()).isNull();
        // updateById 跳过 null：必须显式写 pending_change_request_id = NULL，且只清本申请
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SaleOrder>> clear =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(orderMapper).update(org.mockito.ArgumentMatchers.isNull(), clear.capture());
        assertThat(clear.getValue().getSqlSet()).contains("pending_change_request_id=");
        assertThat(clear.getValue().getSqlSegment()).contains("pending_change_request_id");
    }

    private static ChangeRequest request(String changeType, String fieldCode, String afterJson) {
        ChangeRequest request = new ChangeRequest();
        request.setId(9201L);
        request.setBizType("SALE_ORDER");
        request.setBizId(9102L);
        request.setFarmId(1001L);
        request.setChangeType(changeType);
        request.setFieldCode(fieldCode);
        request.setFieldName("unit_price".equals(fieldCode) ? "单价" : "数量");
        request.setAfterJson(afterJson);
        request.setReason("出栏单登记有误");
        request.setSubmittedBy(10001L);
        request.setSubmitterRole("OWNER");
        return request;
    }
}
