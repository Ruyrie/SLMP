package com.zhimu.server.biz.finance.service.impl;

import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.finance.FinanceTestTables;
import com.zhimu.server.biz.finance.api.BatchSalePort;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.mapper.*;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.MarketPriceService;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.today.api.TodoSourceWriteService;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleOrderCreateServiceImplTest {
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
    @InjectMocks private SaleOrderServiceImpl service;
    private FinanceRequests.SaleOrderRequest request;

    @BeforeEach
    void setUp() {
        FinanceTestTables.init();
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "create-test"));
        when(support.farmId()).thenReturn(1001L);
        when(support.requireBatch(3001L, 1001L)).thenReturn(new BatchInfo(1001L, 3001L, "ZM2610-019",
                1101L, 101L, BeijingTime.today().minusDays(50), 200000, "ACTIVE", List.of(2001L),
                34, 200000, 0, 0, 2001L));
        request = new FinanceRequests.SaleOrderRequest();
        request.setBatchId(3001L);
        request.setSaleDate(BeijingTime.today().toString());
        request.setQuantity(150);
        request.setTotalWeight(new BigDecimal("1000"));
        request.setPricingMethod("BY_WEIGHT");
        request.setUnitPrice(new BigDecimal("15.6"));
        request.setBuyerName("测试收购商");
    }

    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void rejectsWeightOverflowBeforeWritingOrDeductingStock() {
        request.setTotalWeight(new BigDecimal("100000000"));
        assertThatThrownBy(() -> service.create(request, null)).isInstanceOf(BizException.class)
                .hasMessageContaining("总重不能超过");
        verify(orderMapper, never()).insert(any(SaleOrder.class));
        verifyNoInteractions(batchSalePort);
    }

    @ParameterizedTest @ValueSource(strings = {"BY_WEIGHT", "BY_HEAD"})
    void rejectsCalculatedAmountOverflow(String method) {
        request.setPricingMethod(method);
        request.setQuantity(100001);
        request.setTotalWeight(new BigDecimal("100001"));
        request.setUnitPrice(new BigDecimal("99999.99"));
        assertThatThrownBy(() -> service.create(request, null)).isInstanceOf(BizException.class)
                .hasMessageContaining("销售金额不能超过");
        verify(orderMapper, never()).insert(any(SaleOrder.class));
        verify(batchSalePort, never()).saleOut(any(), any(), any(), any(), any());
    }

    @Test void acceptsMaximumWeightWhenAmountFits() {
        request.setTotalWeight(new BigDecimal("99999999.99"));
        request.setUnitPrice(BigDecimal.ONE);
        service.create(request, null);
        ArgumentCaptor<SaleOrder> saved = ArgumentCaptor.forClass(SaleOrder.class);
        verify(orderMapper).insert(saved.capture());
        assertThat(saved.getValue().getSaleAmount()).isEqualByComparingTo("100000000");
        verify(batchSettlementService).recalculateForSale(1001L, 3001L);
    }

    @Test void usesGlobalNumberAndContinuesBeyond999WithoutLeakingTenantSkip() {
        String prefix = "CL" + BeijingTime.today().format(DateTimeFormatter.ofPattern("yyMMdd"));
        SaleOrder otherFarm = new SaleOrder();
        otherFarm.setFarmId(2002L);
        otherFarm.setSaleNo(prefix + "1000");
        when(orderMapper.selectList(any())).thenAnswer(call -> {
            assertThat(TenantSkip.isSkipped()).isTrue();
            return List.of(otherFarm);
        });
        doAnswer(call -> {
            assertThat(TenantSkip.isSkipped()).isFalse();
            SaleOrder saved = call.getArgument(0);
            assertThat(saved.getFarmId()).isEqualTo(1001L);
            assertThat(saved.getSaleNo()).isEqualTo(prefix + "1001");
            return 1;
        }).when(orderMapper).insert(any(SaleOrder.class));
        service.create(request, null);
        assertThat(TenantSkip.isSkipped()).isFalse();
    }

    @Test void advancesOnConcurrentNumberConflictAndDeductsStockOnlyOnce() {
        List<String> attempts = new ArrayList<>();
        when(orderMapper.insert(any(SaleOrder.class))).thenAnswer(call -> {
            SaleOrder order = call.getArgument(0);
            attempts.add(order.getSaleNo());
            if (attempts.size() == 1) throw new DuplicateKeyException("Duplicate entry for key 't_sale_order.uk_sale_order_no'");
            order.setId(9001L);
            return 1;
        });
        service.create(request, null);
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0)).endsWith("001");
        assertThat(attempts.get(1)).endsWith("002");
        verify(batchSalePort, times(1)).saleOut(eq(1001L), eq(3001L), any(), any(), eq(9001L));
    }

    @Test void doesNotRetryAnIdempotencyKeyConflictAsANumberConflict() {
        DuplicateKeyException duplicate = new DuplicateKeyException("Duplicate entry for key 'uk_sale_order_req'");
        when(orderMapper.insert(any(SaleOrder.class))).thenThrow(duplicate);
        assertThatThrownBy(() -> service.create(request, null)).isSameAs(duplicate);
        verify(orderMapper, times(1)).insert(any(SaleOrder.class));
        verify(batchSalePort, never()).saleOut(any(), any(), any(), any(), any());
    }
}
