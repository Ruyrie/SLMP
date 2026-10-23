package com.zhimu.slmp.finance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.BatchSettlementVO;
import com.zhimu.slmp.finance.model.vo.BusinessOverviewVO;
import com.zhimu.slmp.finance.model.vo.BusinessReportVO;
import com.zhimu.slmp.finance.model.vo.FinanceCategoryVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordPageVO;
import com.zhimu.slmp.finance.model.vo.FinanceSummaryVO;
import com.zhimu.slmp.finance.model.vo.MarketTrendVO;
import com.zhimu.slmp.finance.model.vo.ReceivableVO;
import com.zhimu.slmp.finance.model.vo.ReportTrendVO;
import com.zhimu.slmp.finance.model.vo.SaleAdviceVO;
import com.zhimu.slmp.finance.model.vo.SaleBatchOptionVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderItemVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderPageVO;
import com.zhimu.slmp.finance.util.SaleAmountCalculator;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mock 数据自检：routes.json 引用的文件都存在且为完整 ApiResponse；不为其他组件的接口写无条件规则；员工规则排在默认规则前；
 * 关键响应符合统一示例（东山猪场 10月结余 87,600 元、ZM2610-031 休药至 10月29日、ZM2609-012 出栏 98 头 188,160 元、
 * 应收合计与各出栏单一致、月份分组合计不含作废、核算锁定与补录 600 元、行情 30 天统计与走势一致）。
 */
public class FinanceMockDataTest {

    @Rule
    public TestRules rules = new TestRules();

    private static JsonArray routes() throws IOException {
        String text = new String(Files.readAllBytes(new File(TestRules.MOCK_DIR, "routes.json").toPath()),
                StandardCharsets.UTF_8);
        return JsonParser.parseString(text).getAsJsonArray();
    }

    @Test
    public void routesReferenceExistingResponses_andStayInModule() throws IOException {
        JsonArray routes = routes();
        assertTrue(routes.size() > 100);
        Set<String> staffSeen = new HashSet<>();
        Set<String> defaultSeen = new HashSet<>();
        for (JsonElement e : routes) {
            JsonObject r = e.getAsJsonObject();
            String path = r.get("path").getAsString();
            String key = r.get("method").getAsString() + " " + path;
            boolean own = path.startsWith("/api/v1/business-") || path.startsWith("/api/v1/finance-")
                    || path.startsWith("/api/v1/expense-subcategories") || path.startsWith("/api/v1/sale-")
                    || path.startsWith("/api/v1/batch-settlements") || path.startsWith("/api/v1/market-prices");
            boolean sharedWithCondition = (path.equals("/api/v1/record-logs") || path.equals("/api/v1/change-requests/form")
                    || path.equals("/api/v1/base/dicts/{dict_type}")) && r.has("when");
            assertTrue(path, own || sharedWithCondition);
            String file = r.get("file").getAsString();
            JsonObject resp = TestRules.mockResponse(file);
            assertTrue(file, resp.has("code") && resp.has("message") && resp.has("trace_id"));
            boolean staff = r.has("when") && r.getAsJsonObject("when").has("header");
            if (staff) {
                assertFalse("员工规则应在默认规则前：" + key, defaultSeen.contains(key));
                staffSeen.add(key);
            } else if (!r.has("when")) {
                assertFalse("同一接口只能有一条默认规则：" + key, defaultSeen.contains(key));
                defaultSeen.add(key);
            }
        }
        assertTrue(staffSeen.contains("GET /api/v1/business-overview"));
        assertTrue(new File(TestRules.MOCK_DIR, "export/report_sample.xlsx").isFile());
        assertTrue(new File(TestRules.MOCK_DIR, "export/report_sample.pdf").isFile());
    }

    @Test
    public void overviewMatchesUnifiedExample() throws IOException {
        BusinessOverviewVO o = TestRules.mock("overview.json", BusinessOverviewVO.class);
        assertEquals("2026-10-19", o.getToday());
        assertEquals(0, o.getMonthBalance().getBalanceAmount().compareTo(new BigDecimal("87600")));
        assertEquals(0, o.getMonthBalance().getIncome().subtract(o.getMonthBalance().getExpenseAmount())
                .compareTo(o.getMonthBalance().getBalanceAmount()));
        assertEquals(0, o.getPriceCard().getPrice().compareTo(new BigDecimal("15.6")));
        assertEquals(2, o.getTodos().getItems().size());
        assertEquals(Long.valueOf(9101), o.getTodos().getItems().get(0).getBizId());
        assertTrue(o.getTodos().getItems().get(0).isOverdue());

        // 应收合计 = 未收齐出栏单未收金额之和
        ReceivableVO r = TestRules.mock("receivables_all.json", ReceivableVO.class);
        BigDecimal sum = BigDecimal.ZERO;
        for (SaleOrderItemVO i : r.getList()) {
            sum = sum.add(i.getUnpaidAmount());
            assertTrue(FinanceConst.PAY_UNPAID.equals(i.getPaymentStatus())
                    || FinanceConst.PAY_PARTIAL.equals(i.getPaymentStatus()));
        }
        assertEquals(0, sum.compareTo(r.getSummary().getTotalUnpaidAmount()));
        assertEquals(0, sum.compareTo(o.getReceivable().getTotalUnpaidAmount()));
        assertEquals(Integer.valueOf(r.getList().size()), r.getTabCounts().get("ALL"));
    }

    @Test
    public void saleOrdersAreConsistent() throws IOException {
        SaleOrderPageVO p = TestRules.mock("orders_all.json", SaleOrderPageVO.class);
        assertEquals(Integer.valueOf(5), p.getTabCounts().get("ALL"));
        BigDecimal oct = BigDecimal.ZERO;
        int octCount = 0;
        for (SaleOrderItemVO o : p.getList()) {
            if (!FinanceConst.PAY_VOIDED.equals(o.getPaymentStatus())) {
                BigDecimal expected = SaleAmountCalculator.saleAmount(FinanceConst.BY_WEIGHT, o.getQuantity(),
                        o.getTotalWeight(), unitPrice(o.getId()));
                assertEquals("出栏单 " + o.getId(), 0, expected.compareTo(o.getSaleAmount()));
                assertEquals(0, o.getReceivedAmount().add(o.getUnpaidAmount()).compareTo(o.getSaleAmount()));
            }
            if (o.getSaleDate().startsWith("2026-10")) {
                octCount++;
                if (!FinanceConst.PAY_VOIDED.equals(o.getPaymentStatus())) {
                    oct = oct.add(o.getSaleAmount());
                }
            }
        }
        SaleOrderPageVO.MonthGroup g = p.getMonthGroups().get(0);
        assertEquals("2026-10", g.getMonth());
        assertEquals(octCount, g.getCount());
        assertEquals(0, oct.compareTo(g.getSaleAmount()));

        SaleOrderDetailVO d = TestRules.mock("order_9102.json", SaleOrderDetailVO.class);
        assertEquals("ZM2609-012", d.getBatchNo());
        assertEquals(98, d.getQuantity());
        assertEquals(0, d.getSaleAmount().compareTo(new BigDecimal("188160")));
        assertEquals(2, d.getReceipts().size());
        assertTrue(d.getActions().isCanApplyChange());
        assertNull(d.getPendingChange());
        SaleOrderDetailVO paid = TestRules.mock("receipt_9102_paid.json", SaleOrderDetailVO.class);
        assertTrue(paid.getReceiptResult().isPaidOff());
        assertEquals(0, paid.getUnpaidAmount().signum());
        SaleOrderDetailVO d9101 = TestRules.mock("order_9101.json", SaleOrderDetailVO.class);
        assertNotNull(d9101.getPendingChange());
        assertTrue(d9101.getActions().isChangeInReview());
    }

    private static BigDecimal unitPrice(long id) {
        if (id == 9101) {
            return new BigDecimal("15.20");
        }
        if (id == 9102) {
            return new BigDecimal("16.00");
        }
        if (id == 9103) {
            return new BigDecimal("15.60");
        }
        return new BigDecimal("14.80");
    }

    @Test
    public void batchesAndWithdrawal() throws IOException {
        List<SaleBatchOptionVO> list = TestRules.mock("batch_options.json",
                new TypeToken<List<SaleBatchOptionVO>>() {
                }.getType());
        SaleBatchOptionVO b = list.get(0);
        assertEquals(3001, b.getBatchId());
        assertEquals("ZM2610-031", b.getBatchNo());
        assertEquals(96, b.getDayAge());
        assertEquals(200, b.getCurrentCount());
        assertTrue(b.isInWithdrawal());
        assertEquals("2026-10-29", b.getSellableDate());
        assertEquals(Integer.valueOf(10), b.getWithdrawalDaysLeft());
        SaleBatchOptionVO b2 = list.get(1);
        assertEquals("ZM2610-019", b2.getBatchNo());
        assertFalse(b2.isInWithdrawal());
        assertEquals(150, b2.getCurrentCount());
        SaleBatchOptionVO multi = list.get(2);
        int sum = 0;
        for (SaleBatchOptionVO.PenStock s : multi.getPens()) {
            sum += s.getStockCount();
        }
        assertEquals(multi.getCurrentCount(), sum);
    }

    @Test
    public void financeSummaryAndRecords() throws IOException {
        FinanceSummaryVO s = TestRules.mock("summary_202610.json", FinanceSummaryVO.class);
        assertTrue(s.isCurrentMonth());
        assertEquals(0, s.getBalanceAmount().compareTo(new BigDecimal("87600")));
        assertEquals(0, s.getPrevBalanceAmount().compareTo(new BigDecimal("66300")));
        BigDecimal shareSum = BigDecimal.ZERO;
        for (com.zhimu.slmp.finance.model.vo.CategoryShareVO c : s.getExpenseShares()) {
            shareSum = shareSum.add(c.getAmount());
        }
        assertEquals(0, shareSum.compareTo(s.getExpenseAmount()));
        FinanceSummaryVO all = TestRules.mock("summary_all.json", FinanceSummaryVO.class);
        assertEquals(2, all.getFarms().size());
        assertEquals("南坡羊场", all.getStaffFarmNames().get(0));
        assertTrue(all.getExpenseShares().isEmpty());

        FinanceRecordPageVO page = TestRules.mock("records_202610.json", FinanceRecordPageVO.class);
        assertEquals(8, page.getList().size());
        assertNotNull(page.getTotals());
        List<FinanceCategoryVO> expense = TestRules.mock("categories_expense.json",
                new TypeToken<List<FinanceCategoryVO>>() {
                }.getType());
        assertEquals(7, expense.size());
        assertEquals("FEED", expense.get(0).getCode());
        assertEquals(3, expense.get(0).getSubcategories().size());
    }

    @Test
    public void settlementAndMarket() throws IOException {
        BatchSettlementVO s = TestRules.mock("settlement_3009.json", BatchSettlementVO.class);
        assertEquals(FinanceConst.SETTLEMENT_LOCKED, s.getStatus());
        assertEquals(0, s.getSaleIncome().subtract(s.getCostTotal()).compareTo(s.getProfit()));
        BigDecimal costs = BigDecimal.ZERO;
        for (BatchSettlementVO.CostItem c : s.getCosts()) {
            costs = costs.add(c.getAmount());
        }
        assertEquals(0, costs.compareTo(s.getCostTotal()));
        assertEquals(1, s.getSupplements().size());
        assertEquals(0, s.getPreviewAfter().getProfit().compareTo(new BigDecimal("2560")));
        BatchSettlementVO c = TestRules.mock("settlement_3009_confirmed.json", BatchSettlementVO.class);
        assertTrue(c.getSupplements().isEmpty());
        assertEquals(0, c.getProfit().compareTo(new BigDecimal("2560")));

        MarketTrendVO t = TestRules.mock("trend_30.json", MarketTrendVO.class);
        assertEquals(30, t.getSeries().size());
        assertEquals(0, t.getLatest().getPrice().compareTo(new BigDecimal("15.6")));
        assertEquals("2026-09-25", t.getStats().getMinDate());
        assertEquals(FinanceConst.UP, t.getChange().getDirection());
        boolean gap = false;
        for (MarketTrendVO.PricePoint pt : t.getSeries()) {
            gap |= pt.getPrice() == null;
        }
        assertTrue("10月1日未发布，折线断开", gap);
    }

    @Test
    public void adviceAndReport() throws IOException {
        SaleAdviceVO a = TestRules.mock("advice_3001.json", SaleAdviceVO.class);
        assertEquals(FinanceConst.NOT_SELLABLE, a.getConclusion());
        assertTrue(a.isInWithdrawal());
        assertTrue(a.getWindowStart().compareTo(a.getSellableDate()) >= 0);
        SaleAdviceVO g = TestRules.mock("advice_3002_generating.json", SaleAdviceVO.class);
        assertEquals(FinanceConst.GEN_GENERATING, g.getStatus());
        SaleAdviceVO done = TestRules.mock("advice_7302.json", SaleAdviceVO.class);
        assertEquals(g.getId(), done.getId());
        assertEquals(FinanceConst.SELL_SOON, done.getConclusion());

        BusinessReportVO r = TestRules.mock("report_to_date.json", BusinessReportVO.class);
        assertFalse(r.getDataCheck().isHasIssue());
        assertEquals(870, r.getMetrics().getStock().get(0).getEnd());
        BusinessReportVO m = TestRules.mock("report_monthly.json", BusinessReportVO.class);
        assertTrue(m.getDataCheck().isHasIssue());
        assertEquals(2, m.getDataCheck().getItems().size());
        ReportTrendVO stock = TestRules.mock("trend_stock.json", ReportTrendVO.class);
        ReportTrendVO.StockEquation e = stock.getEquation();
        assertEquals(e.getEnd(), e.getBegin() + e.getEntry() - e.getDeath() - e.getSold());
        assertEquals(e.getEnd(), stock.getPoints().get(stock.getPoints().size() - 1).getValue().intValue());
    }
}
