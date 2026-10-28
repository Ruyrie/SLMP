package com.zhimu.server.biz.finance.service.impl;

import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.finance.FinanceTestTables;
import com.zhimu.server.biz.finance.api.BatchSalePort;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.entity.SaleReceipt;
import com.zhimu.server.biz.finance.mapper.BatchSettlementMapper;
import com.zhimu.server.biz.finance.mapper.BatchSettlementSupplementMapper;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.mapper.SaleAdviceMapper;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.finance.mapper.SaleReceiptMapper;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.MarketPriceService;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.finance.vo.SaleOrderVos;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleOrderReceiptServiceImplTest {

    @Mock private SaleOrderMapper orderMapper;
    @Mock private SaleReceiptMapper receiptMapper;
    @Mock private FinanceRecordMapper recordMapper;
    @Mock private FinanceCategoryMapper categoryMapper;
    @Mock private BatchSettlementMapper settlementMapper;
    @Mock private BatchSettlementSupplementMapper supplementMapper;
    @Mock private SaleAdviceMapper adviceMapper;
    @Mock private BatchSalePort batchSalePort;
    @Mock private BatchQueryService batchQueryService;
    @Mock private MarketPriceService marketPriceService;
    @Mock private AccountQueryService accountQueryService;
    @Mock private FinanceSupport support;
    @Mock private TodoSourceWriteService todoWriter;
    @Mock private com.zhimu.server.biz.finance.service.BatchSettlementService batchSettlementService;
    @Mock private com.zhimu.server.biz.finance.support.ReceivableTodos receivableTodos;
    @InjectMocks private SaleOrderServiceImpl service;

    private SaleOrder order;
    private final List<SaleReceipt> savedReceipts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        FinanceTestTables.init();
        lenient().when(batchSettlementService.detail(any())).thenReturn(new com.zhimu.server.biz.finance.vo.SettlementVos.BatchSettlementVO());
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "receipt-test"));
        order = new SaleOrder();
        order.setId(9102L);
        order.setFarmId(1001L);
        order.setBatchId(3009L);
        order.setSaleNo("CL261026001");
        order.setSaleDate(BeijingTime.today());
        order.setSaleAmount(new BigDecimal("33920.00"));
        order.setReceivedAmount(new BigDecimal("10000.00"));
        order.setUnpaidAmount(new BigDecimal("23920.00"));
        order.setPaymentStatus("PARTIAL");
        order.setReceiptCount(1);
        when(support.farmId()).thenReturn(1001L);
        when(orderMapper.selectById(9102L)).thenReturn(order);
        when(receiptMapper.selectOne(any())).thenAnswer(call -> savedReceipts.stream().findFirst().orElse(null));
        lenient().when(receiptMapper.selectList(any())).thenAnswer(call -> List.copyOf(savedReceipts));
        lenient().when(receiptMapper.insert(any(SaleReceipt.class))).thenAnswer(call -> {
            SaleReceipt receipt = call.getArgument(0);
            receipt.setId(9201L);
            savedReceipts.add(receipt);
            return 1;
        });
        lenient().when(recordMapper.insert(any(FinanceRecord.class))).thenAnswer(call -> {
            FinanceRecord record = call.getArgument(0);
            record.setId(9301L);
            return 1;
        });
        lenient().when(orderMapper.updateById(any(SaleOrder.class))).thenReturn(1);
        FinanceCategory category = new FinanceCategory();
        category.setId(101L);
        lenient().when(categoryMapper.selectOne(any())).thenReturn(category);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void remainingBalanceCreatesOneIncomeAndReplaysAsPaidOff() {
        SaleOrderVos.DetailVO first = service.addReceipt(9102L, request("23920"), "same-receipt");
        SaleOrderVos.DetailVO replay = service.addReceipt(9102L, request("23920"), "same-receipt");

        assertThat(first.getReceiptResult().getPaidOff()).isTrue();
        assertThat(replay.getReceiptResult().getPaidOff()).isTrue();
        assertThat(replay.getReceivedAmount()).isEqualByComparingTo("33920.00");
        assertThat(replay.getUnpaidAmount()).isZero();
        assertThat(replay.getPaymentStatus()).isEqualTo("PAID");
        assertThat(order.getReceiptCount()).isEqualTo(2);
        assertThat(order.getPaidOffDate()).isEqualTo(BeijingTime.today());
        verify(receiptMapper, times(1)).insert(any(SaleReceipt.class));
        verify(orderMapper, times(1)).updateById(any(SaleOrder.class));
        ArgumentCaptor<FinanceRecord> income = ArgumentCaptor.forClass(FinanceRecord.class);
        verify(recordMapper, times(1)).insert(income.capture());
        assertThat(income.getValue().getRecordType()).isEqualTo("INCOME");
        assertThat(income.getValue().getSourceType()).isEqualTo("SALE_RECEIPT");
        assertThat(income.getValue().getSourceId()).isEqualTo(9201L);
        assertThat(income.getValue().getAmount()).isEqualByComparingTo("23920.00");
        assertThat(savedReceipts.get(0).getFinanceRecordId()).isEqualTo(9301L);
        verify(todoWriter, times(1)).completeFromSource(1001L, "RECEIVABLE", "SALE_ORDER:9102",
                "SALE_RECEIPT", 9201L, 10001L);
    }

    @Test
    void halfBalanceReplayDoesNotClaimPaidOffWhenReceiptEqualsRemainingBalance() {
        service.addReceipt(9102L, request("11960"), "partial-receipt");
        SaleOrderVos.DetailVO replay = service.addReceipt(9102L, request("11960"), "partial-receipt");

        assertThat(replay.getReceiptResult().getPaidOff()).isFalse();
        assertThat(replay.getReceiptResult().getUnpaidAmount()).isEqualByComparingTo("11960.00");
        assertThat(replay.getPaymentStatus()).isEqualTo("PARTIAL");
        verify(receiptMapper, times(1)).insert(any(SaleReceipt.class));
        verify(recordMapper, times(1)).insert(any(FinanceRecord.class));
        verifyNoInteractions(todoWriter);
    }

    @Test
    void rejectsOverpaymentAndFractionalCentsBeforeCreatingAnyReceipt() {
        assertThatThrownBy(() -> service.addReceipt(9102L, request("23920.01"), "too-much"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.addReceipt(9102L, request("100.005"), "fractional-cent"))
                .isInstanceOf(BizException.class);
        verify(receiptMapper, never()).insert(any(SaleReceipt.class));
        verify(recordMapper, never()).insert(any(FinanceRecord.class));
        verify(orderMapper, never()).updateById(any(SaleOrder.class));
        verifyNoInteractions(todoWriter);
    }

    private FinanceRequests.SaleReceiptRequest request(String amount) {
        FinanceRequests.SaleReceiptRequest request = new FinanceRequests.SaleReceiptRequest();
        request.setAmount(new BigDecimal(amount));
        request.setReceiptDate(LocalDate.now(BeijingTime.clock()).toString());
        request.setPaymentMethod("BANK_TRANSFER");
        return request;
    }
}
