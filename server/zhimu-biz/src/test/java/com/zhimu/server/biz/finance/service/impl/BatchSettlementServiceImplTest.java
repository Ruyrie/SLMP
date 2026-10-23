package com.zhimu.server.biz.finance.service.impl;

import com.zhimu.server.biz.finance.FinanceTestTables;
import com.zhimu.server.biz.finance.api.StockCostPort;
import com.zhimu.server.biz.finance.entity.BatchSettlement;
import com.zhimu.server.biz.finance.entity.BatchSettlementSupplement;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.entity.SaleOrder;
import com.zhimu.server.biz.finance.mapper.BatchSettlementMapper;
import com.zhimu.server.biz.finance.mapper.BatchSettlementSupplementMapper;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.mapper.SaleOrderMapper;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.finance.vo.SettlementVos;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 批次核算口径：收入=未作废出栏单金额，成本=饲料/引种/兽药/其他，其他费用取本批次支出（跳过只标记分类），
 * 已确认补录按成本项幂等叠加；在养按在栏数、锁定按出栏数摊到每头。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchSettlementServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Long FARM_ID = 1001L;
    private static final Long BATCH_ID = 3009L;

    @Mock private BatchSettlementMapper settlementMapper;
    @Mock private BatchSettlementSupplementMapper supplementMapper;
    @Mock private SaleOrderMapper orderMapper;
    @Mock private FinanceRecordMapper recordMapper;
    @Mock private FinanceCategoryMapper categoryMapper;
    @Mock private StockCostPort stockCostPort;
    @Mock private BatchQueryService batchQueryService;
    @Mock private FinanceSupport support;

    private BatchSettlementServiceImpl service;

    @BeforeEach
    void setUp() {
        FinanceTestTables.init();
        service = new BatchSettlementServiceImpl(settlementMapper, supplementMapper, orderMapper, recordMapper,
                categoryMapper, stockCostPort, batchQueryService, support);
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), ZONE));
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));

        lenient().when(support.farmId()).thenReturn(FARM_ID);
        lenient().when(support.requireBatch(BATCH_ID, FARM_ID)).thenReturn(batch(100));
        lenient().when(support.penNames(any())).thenReturn(Map.of(2005L, "5 号育肥舍"));
        lenient().when(support.breedName(1101L)).thenReturn("杜长大");
        lenient().when(support.categoryName(101L)).thenReturn("育肥猪");
        lenient().when(support.countUnitText(101L)).thenReturn("头");
        lenient().when(stockCostPort.costOf(BATCH_ID)).thenReturn(new StockCostPort.BatchCost(
                new BigDecimal("120000"), new BigDecimal("25930"), new BigDecimal("8000"), BigDecimal.ZERO));
        lenient().when(settlementMapper.updateById(any(BatchSettlement.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        UserContext.clear();
    }

    @Test
    void runningSettlementRecomputesFromOrdersCostsAndRecords() {
        BatchSettlement settlement = settlement("RUNNING", 0);
        settlement.setIntroUnitPrice(new BigDecimal("500"));
        when(settlementMapper.selectOne(any())).thenReturn(settlement);
        when(orderMapper.selectList(any())).thenReturn(orders());
        when(recordMapper.selectList(any())).thenReturn(records());
        when(categoryMapper.selectById(108L)).thenReturn(category(false));
        when(categoryMapper.selectById(105L)).thenReturn(category(true));
        // 第一次：recompute 查已确认补录（无）；第二次：toVo 查待确认补录（1 条 600 元）
        when(supplementMapper.selectList(any())).thenReturn(List.of(), List.of(pendingSupplement()));

        SettlementVos.BatchSettlementVO vo = service.detail(BATCH_ID);

        // 收入 188160 + 30000，出栏 98 + 20
        assertThat(vo.getSaleIncome()).isEqualByComparingTo("218160.00");
        assertThat(vo.getBatch().getSoldCount()).isEqualTo(98);
        assertThat(vo.getReceivedAmount()).isEqualByComparingTo("100000.00");
        // 成本 120000 饲料 + 50000 引种（500×100）+ 8000 兽药 + 6000 其他（1000 元只标记分类被跳过）
        assertThat(vo.getCostTotal()).isEqualByComparingTo("184000.00");
        assertThat(vo.getProfit()).isEqualByComparingTo("34160.00");
        assertThat(vo.getFeedKg()).isEqualByComparingTo("25930.00");
        // 未锁定按在栏数 100 摊
        assertThat(vo.getPerHeadBasis()).isEqualTo("STOCK");
        assertThat(vo.getPerHeadCount()).isEqualTo(100);
        assertThat(vo.getCostPerHead()).isEqualByComparingTo("1840.00");
        assertThat(vo.getProfitPerHead()).isEqualByComparingTo("341.60");
        assertThat(vo.getCosts()).hasSize(4);
        assertThat(vo.getCosts()).extracting(SettlementVos.CostItem::getCostGroup)
                .containsExactly("FEED", "INTRO", "DRUG", "OTHER");
        assertThat(vo.getCosts().get(0).getShareRate()).isEqualByComparingTo("0.6522");
        assertThat(vo.getCosts().get(1).getNote()).isEqualTo("100 头 × 500 元");
        // 待确认补录 600 元 → 预览其他费用 6600、总成本 184600、利润 33560
        assertThat(vo.getSupplements()).hasSize(1);
        assertThat(vo.getPreviewAfter().getCostOther()).isEqualByComparingTo("6600.00");
        assertThat(vo.getPreviewAfter().getCostTotal()).isEqualByComparingTo("184600.00");
        assertThat(vo.getPreviewAfter().getProfit()).isEqualByComparingTo("33560.00");
        assertThat(vo.getBatch().getDayAge()).isEqualTo(334);
        assertThat(vo.getBatch().getPenName()).isEqualTo("5 号育肥舍");
    }

    @Test
    void confirmAppliesSupplementsIdempotentlyAndMatchesMock() {
        // settlement_3009_confirmed.json：确认 600 元运输费后其他费用 7000→7600、总成本 185600、利润 2560
        BatchSettlement settlement = settlement("LOCKED", 2);
        settlement.setIntroUnitPrice(new BigDecimal("500"));
        settlement.setCostOther(new BigDecimal("7000"));
        settlement.setCostTotal(new BigDecimal("185000"));
        settlement.setProfit(new BigDecimal("3160"));
        settlement.setSoldCount(98);
        when(support.requireBatch(BATCH_ID, FARM_ID)).thenReturn(batch(0));
        when(settlementMapper.selectOne(any())).thenReturn(settlement);
        when(orderMapper.selectList(any())).thenReturn(List.of(order(9102L, 98, "188160", "100000")));
        when(recordMapper.selectList(any())).thenReturn(records());
        when(categoryMapper.selectById(108L)).thenReturn(category(false));
        when(categoryMapper.selectById(105L)).thenReturn(category(false));
        BatchSettlementSupplement supplement = pendingSupplement();
        // 1 次 confirm 查待确认；2 次 recompute 查已确认；3 次 toVo 查待确认（已确认后为空）；4 次查最近成本记录
        when(supplementMapper.selectList(any()))
                .thenReturn(List.of(supplement), List.of(supplement), List.of(), List.of(supplement));

        SettlementVos.BatchSettlementVO vo = service.confirm(BATCH_ID, confirmRequest(2));

        assertThat(supplement.getStatus()).isEqualTo("CONFIRMED");
        assertThat(supplement.getConfirmedBy()).isEqualTo(10001L);
        assertThat(vo.getStatus()).isEqualTo("LOCKED");
        assertThat(vo.getCosts()).extracting(SettlementVos.CostItem::getCostGroup)
                .containsExactly("FEED", "INTRO", "DRUG", "OTHER");
        assertThat(vo.getCosts().get(3).getAmount()).isEqualByComparingTo("7600.00");
        assertThat(vo.getCostTotal()).isEqualByComparingTo("185600.00");
        assertThat(vo.getProfit()).isEqualByComparingTo("2560.00");
        assertThat(vo.getPerHeadBasis()).isEqualTo("SOLD");
        assertThat(vo.getPerHeadCount()).isEqualTo(98);
        assertThat(vo.getCostPerHead()).isEqualByComparingTo("1893.88");
        assertThat(vo.getProfitPerHead()).isEqualByComparingTo("26.12");
        assertThat(vo.getSupplements()).isEmpty();
        assertThat(vo.getPreviewAfter()).isNull();
        assertThat(vo.getRecentCostRecords()).hasSize(1);
    }

    @Test
    void detailCreatesSettlementOnFirstView() {
        when(settlementMapper.selectOne(any())).thenReturn(null);
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(supplementMapper.selectList(any())).thenReturn(List.of());

        SettlementVos.BatchSettlementVO vo = service.detail(BATCH_ID);

        assertThat(vo.getStatus()).isEqualTo("RUNNING");
        assertThat(vo.getSaleIncome()).isEqualByComparingTo("0.00");
        assertThat(vo.getCostTotal()).isEqualByComparingTo("128000.00");
        assertThat(vo.getProfit()).isEqualByComparingTo("-128000.00");
    }

    @Test
    void confirmRejectsSettlementNotLocked() {
        BatchSettlement settlement = settlement("RUNNING", 1);
        when(settlementMapper.selectOne(any())).thenReturn(settlement);

        assertThatThrownBy(() -> service.confirm(BATCH_ID, confirmRequest(1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("补录已被处理");
    }

    @Test
    void confirmRejectsEmptySupplementIds() {
        when(settlementMapper.selectOne(any())).thenReturn(settlement("LOCKED", 1));

        assertThatThrownBy(() -> service.confirm(BATCH_ID,
                new FinanceRequests.SupplementConfirmRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请选择要确认的补录");
    }

    @Test
    void confirmRejectsPartiallyHandledSupplements() {
        when(settlementMapper.selectOne(any())).thenReturn(settlement("LOCKED", 1));
        when(supplementMapper.selectList(any())).thenReturn(List.of(pendingSupplement()));

        FinanceRequests.SupplementConfirmRequest request = new FinanceRequests.SupplementConfirmRequest();
        request.setSupplementIds(List.of(401L, 402L));
        request.setVersion(1);
        assertThatThrownBy(() -> service.confirm(BATCH_ID, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("补录已被处理");
    }

    // ===== 夹具 =====

    private static BatchInfo batch(int currentCount) {
        return new BatchInfo(FARM_ID, BATCH_ID, "ZM2609-012", 1101L, 101L, LocalDate.of(2026, 4, 28),
                currentCount, "ACTIVE", List.of(2005L), 160, 100, 2, 98, 2005L);
    }

    private static BatchSettlement settlement(String status, int version) {
        BatchSettlement settlement = new BatchSettlement();
        settlement.setId(6009L);
        settlement.setFarmId(FARM_ID);
        settlement.setBatchId(BATCH_ID);
        settlement.setStatus(status);
        settlement.setVersion(version);
        settlement.setSaleIncome(new BigDecimal("188160"));
        settlement.setCostFeed(new BigDecimal("120000"));
        settlement.setFeedKg(new BigDecimal("25930"));
        settlement.setCostIntro(new BigDecimal("50000"));
        settlement.setIntroCount(100);
        settlement.setCostDrug(new BigDecimal("8000"));
        settlement.setCostOther(new BigDecimal("7000"));
        settlement.setCostTotal(new BigDecimal("185000"));
        settlement.setProfit(new BigDecimal("3160"));
        settlement.setSoldCount(98);
        return settlement;
    }

    private static List<SaleOrder> orders() {
        return List.of(order(9102L, 98, "188160", "100000"), order(9103L, 20, "30000", "0"));
    }

    private static SaleOrder order(Long id, int quantity, String saleAmount, String receivedAmount) {
        SaleOrder order = new SaleOrder();
        order.setId(id);
        order.setFarmId(FARM_ID);
        order.setBatchId(BATCH_ID);
        order.setSaleDate(LocalDate.of(2026, 10, 13));
        order.setQuantity(quantity);
        order.setTotalWeight(new BigDecimal("11760"));
        order.setUnitPrice(new BigDecimal("16.00"));
        order.setSaleAmount(new BigDecimal(saleAmount));
        order.setReceivedAmount(new BigDecimal(receivedAmount));
        order.setPaymentStatus("PARTIAL");
        return order;
    }

    private static List<FinanceRecord> records() {
        return List.of(record(108L, "6000"), record(105L, "1000"));
    }

    private static FinanceRecord record(Long categoryId, String amount) {
        FinanceRecord record = new FinanceRecord();
        record.setFarmId(FARM_ID);
        record.setBatchId(BATCH_ID);
        record.setCategoryId(categoryId);
        record.setRecordType("EXPENSE");
        record.setStatus("NORMAL");
        record.setAmount(new BigDecimal(amount));
        record.setRecordDate(LocalDate.of(2026, 10, 17));
        return record;
    }

    private static FinanceCategory category(boolean markOnly) {
        FinanceCategory category = new FinanceCategory();
        category.setDirection("EXPENSE");
        category.setStatus("ENABLED");
        category.setMarkOnly(markOnly);
        return category;
    }

    private static BatchSettlementSupplement pendingSupplement() {
        BatchSettlementSupplement supplement = new BatchSettlementSupplement();
        supplement.setId(401L);
        supplement.setFarmId(FARM_ID);
        supplement.setBatchId(BATCH_ID);
        supplement.setSettlementId(6009L);
        supplement.setBizDate(LocalDate.of(2026, 10, 17));
        supplement.setTitle("运输费");
        supplement.setCostGroup("OTHER");
        supplement.setAmount(new BigDecimal("600"));
        supplement.setQtyDelta(BigDecimal.ZERO);
        supplement.setStatus("PENDING");
        return supplement;
    }

    private static FinanceRequests.SupplementConfirmRequest confirmRequest(int version) {
        FinanceRequests.SupplementConfirmRequest request = new FinanceRequests.SupplementConfirmRequest();
        request.setSupplementIds(List.of(401L));
        request.setVersion(version);
        return request;
    }
}
