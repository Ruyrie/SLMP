package com.zhimu.server.biz.finance.service.impl;

import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.finance.FinanceTestTables;
import com.zhimu.server.biz.finance.api.BatchSalePort;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.mapper.BatchSettlementMapper;
import com.zhimu.server.biz.finance.mapper.BatchSettlementSupplementMapper;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.finance.mapper.SaleReceiptMapper;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.BatchSettlementService;
import com.zhimu.server.biz.finance.service.MarketPriceService;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.finance.support.ReceivableTodos;
import com.zhimu.server.biz.finance.vo.SaleOrderVos;
import com.zhimu.server.biz.finance.vo.SettlementVos;
import com.zhimu.server.biz.health.api.WithdrawalQueryService;
import com.zhimu.server.biz.health.api.WithdrawalQueryService.WithdrawalStatus;
import com.zhimu.server.biz.herd.api.BatchInfo;
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
import org.mockito.InjectMocks;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H1：出栏单登记 / 修改的休药期拦截与批次下拉的“休药期”都按有效用药记录算出的可出栏日期（健康模块同源），
 * 不再读出栏建议表；M6：出栏单 VO 带约定收款日期 due_date。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaleOrderWithdrawalTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    @Mock private SaleOrderMapper orderMapper;
    @Mock private SaleReceiptMapper receiptMapper;
    @Mock private FinanceRecordMapper recordMapper;
    @Mock private FinanceCategoryMapper categoryMapper;
    @Mock private BatchSettlementMapper settlementMapper;
    @Mock private BatchSettlementSupplementMapper supplementMapper;
    @Mock private WithdrawalQueryService withdrawalQueryService;
    @Mock private BatchSalePort batchSalePort;
    @Mock private BatchQueryService batchQueryService;
    @Mock private MarketPriceService marketPriceService;
    @Mock private AccountQueryService accountQueryService;
    @Mock private FinanceSupport support;
    @Mock private TodoSourceWriteService todoWriter;
    @Mock private BatchSettlementService batchSettlementService;
    @Mock private ReceivableTodos receivableTodos;
    @InjectMocks private SaleOrderServiceImpl service;

    private final BatchInfo medicated = batch(3002L, "ZM2610-019");
    private final BatchInfo clean = batch(3004L, "ZM2610-022");

    @BeforeEach
    void setUp() {
        FinanceTestTables.init();
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 29, 10, 0)
                .atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "withdrawal-test"));
        when(support.farmId()).thenReturn(1001L);
        when(support.requireBatch(3002L, 1001L)).thenReturn(medicated);
        when(support.requireBatch(3004L, 1001L)).thenReturn(clean);
        when(batchQueryService.listActive(1001L)).thenReturn(List.of(medicated, clean));
        when(batchSalePort.penStocks(anyLong())).thenReturn(List.of());
        // 3002 的用药记录：氟苯尼考粉末次用药 10-17、休药 20 天 → 11-06 起可出栏
        WithdrawalStatus status = new WithdrawalStatus(3002L, LocalDate.of(2026, 11, 6), "氟苯尼考粉",
                LocalDate.of(2026, 10, 17), 20);
        when(withdrawalQueryService.statusesOfFarm(1001L, TODAY)).thenReturn(Map.of(3002L, status));
        when(withdrawalQueryService.statusOf(1001L, 3002L, TODAY)).thenReturn(status);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        UserContext.clear();
    }

    @Test
    void batchOptionsMarkWithdrawalFromMedicationRecords() {
        List<SaleOrderVos.BatchOptionVO> options = service.batchOptions();

        SaleOrderVos.BatchOptionVO first = options.get(0);
        assertThat(first.getBatchNo()).isEqualTo("ZM2610-019");
        assertThat(first.getInWithdrawal()).isTrue();
        assertThat(first.getSellableDate()).isEqualTo("2026-11-06");
        assertThat(first.getWithdrawalDaysLeft()).isEqualTo(8);
        assertThat(first.getWithdrawalDrugName()).isEqualTo("氟苯尼考粉");
        SaleOrderVos.BatchOptionVO second = options.get(1);
        assertThat(second.getInWithdrawal()).isFalse();
        assertThat(second.getSellableDate()).isNull();
    }

    @Test
    void createRejectsBatchStillInWithdrawalEvenWithoutAnyAdvice() {
        assertThatThrownBy(() -> service.create(request(3002L, TODAY), null))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(42202);
                    assertThat(e.getMessage()).isEqualTo("ZM2610-019 在休药期内，11月6日后才能出栏");
                    assertThat(e.getData()).isEqualTo(Map.of("sellable_date", "2026-11-06"));
                });
        verify(orderMapper, never()).insert(any(SaleOrder.class));
        verify(batchSalePort, never()).saleOut(any(), any(), any(), any(), any());
    }

    @Test
    void backdatedSaleInsideAnEndedWithdrawalIsAFieldError() {
        LocalDate saleDate = LocalDate.of(2026, 10, 10);
        when(withdrawalQueryService.statusOf(1001L, 3004L, saleDate)).thenReturn(new WithdrawalStatus(3004L,
                LocalDate.of(2026, 10, 20), "阿莫西林可溶性粉", LocalDate.of(2026, 10, 6), 14));

        assertThatThrownBy(() -> service.create(request(3004L, saleDate), null))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40001);
                    assertThat(e.getMessage()).isEqualTo("出栏日期不能早于可出栏日期10月20日");
                });
        verify(orderMapper, never()).insert(any(SaleOrder.class));
    }

    @Test
    void batchWithoutMedicationCanBeSold() {
        service.create(request(3004L, TODAY), null);

        verify(orderMapper).insert(any(SaleOrder.class));
        verify(withdrawalQueryService).statusOf(1001L, 3004L, TODAY);
    }

    @Test
    void detailReturnsAgreedDueDateSoEditKeepsIt() {
        SaleOrder order = new SaleOrder();
        order.setId(9102L);
        order.setFarmId(1001L);
        order.setBatchId(3004L);
        order.setSaleDate(LocalDate.of(2026, 10, 20));
        order.setDueDate(LocalDate.of(2026, 11, 5));
        order.setEffectiveDueDate(LocalDate.of(2026, 11, 5));
        order.setQuantity(10);
        order.setPaymentStatus("UNPAID");
        order.setReceivedAmount(BigDecimal.ZERO);
        when(orderMapper.selectById(9102L)).thenReturn(order);
        when(batchSettlementService.detail(3004L)).thenReturn(new SettlementVos.BatchSettlementVO());

        SaleOrderVos.DetailVO vo = service.detail(9102L);
        assertThat(vo.getDueDate()).isEqualTo("2026-11-05");
        assertThat(vo.getEffectiveDueDate()).isEqualTo("2026-11-05");

        order.setDueDate(null);
        order.setEffectiveDueDate(LocalDate.of(2026, 10, 27));
        SaleOrderVos.DetailVO unset = service.detail(9102L);
        assertThat(unset.getDueDate()).isNull();
        assertThat(unset.getEffectiveDueDate()).isEqualTo("2026-10-27");
    }

    private static BatchInfo batch(Long id, String no) {
        return new BatchInfo(1001L, id, no, 1101L, 101L, TODAY.minusDays(100), 150, "ACTIVE", List.of(2001L),
                30, 150, 0, 0, 2001L);
    }

    private static FinanceRequests.SaleOrderRequest request(Long batchId, LocalDate saleDate) {
        FinanceRequests.SaleOrderRequest request = new FinanceRequests.SaleOrderRequest();
        request.setBatchId(batchId);
        request.setSaleDate(saleDate.toString());
        request.setQuantity(10);
        request.setTotalWeight(new BigDecimal("1100"));
        request.setPricingMethod("BY_WEIGHT");
        request.setUnitPrice(new BigDecimal("15.6"));
        request.setBuyerName("测试收购商");
        return request;
    }
}
