package com.zhimu.server.biz.finance.controller;

import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.biz.finance.service.BatchSettlementService;
import com.zhimu.server.biz.finance.service.BusinessReportService;
import com.zhimu.server.biz.finance.service.FinanceCategoryService;
import com.zhimu.server.biz.finance.service.FinanceRecordService;
import com.zhimu.server.biz.finance.service.MarketPriceService;
import com.zhimu.server.biz.finance.service.SaleAdviceService;
import com.zhimu.server.biz.finance.service.SaleOrderService;
import com.zhimu.server.biz.finance.support.FinanceErrors;
import com.zhimu.server.biz.finance.vo.FinanceRecordVos;
import com.zhimu.server.biz.finance.vo.MarketVos;
import com.zhimu.server.biz.finance.vo.OverviewVos;
import com.zhimu.server.biz.finance.vo.ReportVos;
import com.zhimu.server.biz.finance.vo.SaleAdviceVos;
import com.zhimu.server.biz.finance.vo.SaleOrderVos;
import com.zhimu.server.biz.finance.vo.SettlementVos;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模块10 全部 39 个映射（API-E01–E43，E08/E24/E25/E29 不对外）的 standalone MockMvc 测试：
 * 路径、HTTP 方法、snake_case 查询参数、请求体绑定与关键响应字段；字段值照 App Mock。
 */
@ExtendWith(MockitoExtension.class)
class FinanceControllersMockMvcTest {

    @Mock
    private FinanceRecordService recordService;
    @Mock
    private FinanceCategoryService categoryService;
    @Mock
    private SaleOrderService saleOrderService;
    @Mock
    private BatchSettlementService settlementService;
    @Mock
    private MarketPriceService marketPriceService;
    @Mock
    private SaleAdviceService saleAdviceService;
    @Mock
    private BusinessReportService reportService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestMvc.builder(
                new FinanceController(recordService, categoryService),
                new SaleOrderController(saleOrderService),
                new BatchSettlementController(settlementService),
                new MarketController(marketPriceService, saleAdviceService),
                new BusinessReportController(reportService)).build();
    }

    // ===== 收支（E01–E13、E43）=====

    /** E01 经营概览 */
    @Test
    void overview() throws Exception {
        OverviewVos.BusinessOverviewVO vo = new OverviewVos.BusinessOverviewVO();
        vo.setToday("2026-10-19");
        vo.setPendingSupplementCount(2);
        OverviewVos.MonthBalance balance = new OverviewVos.MonthBalance();
        balance.setIncome(new BigDecimal("400000.00"));
        balance.setExpenseAmount(new BigDecimal("312400.00"));
        balance.setBalanceAmount(new BigDecimal("87600.00"));
        balance.setBalanceMomRate(new BigDecimal("0.1235"));
        vo.setMonthBalance(balance);
        when(recordService.overview()).thenReturn(vo);

        mvc.perform(get("/api/v1/business-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.today").value("2026-10-19"))
                .andExpect(jsonPath("$.data.pending_supplement_count").value(2))
                .andExpect(jsonPath("$.data.month_balance.income").value(400000.00))
                .andExpect(jsonPath("$.data.month_balance.expense_amount").value(312400.00))
                .andExpect(jsonPath("$.data.month_balance.balance_mom_rate").value(0.1235));
    }

    /** E02 月度汇总（scope 可空） */
    @Test
    void monthlySummary() throws Exception {
        OverviewVos.FinanceSummaryVO vo = new OverviewVos.FinanceSummaryVO();
        vo.setPeriodStart("2026-10-01");
        vo.setPeriodEnd("2026-10-31");
        vo.setIsCurrentMonth(true);
        vo.setIncome(new BigDecimal("400000.00"));
        vo.setRecordCount(46);
        vo.setAutoCount(18);
        vo.setManualCount(28);
        OverviewVos.CategoryShareVO share = new OverviewVos.CategoryShareVO();
        share.setCategoryId(101L);
        share.setName("饲料");
        share.setAmount(new BigDecimal("180000.00"));
        share.setShareRate(new BigDecimal("0.5760"));
        vo.setExpenseShares(List.of(share));
        when(recordService.monthlySummary("2026-10", null)).thenReturn(vo);

        mvc.perform(get("/api/v1/finance-records/monthly-summary").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period_start").value("2026-10-01"))
                .andExpect(jsonPath("$.data.is_current_month").value(true))
                .andExpect(jsonPath("$.data.income").value(400000.00))
                .andExpect(jsonPath("$.data.record_count").value(46))
                .andExpect(jsonPath("$.data.expense_shares[0].name").value("饲料"))
                .andExpect(jsonPath("$.data.expense_shares[0].share_rate").value(0.5760));
    }

    /** E03 收支流水 */
    @Test
    void listRecords() throws Exception {
        FinanceRecordVos.RecordVO record = new FinanceRecordVos.RecordVO();
        record.setId(8202L);
        record.setAmount(new BigDecimal("3200.00"));
        record.setRecordDate("2026-10-19");
        record.setSourceType("INBOUND");
        record.setSourceLabel("来自饲料入库单");
        FinanceRecordVos.RecordPageVO page = new FinanceRecordVos.RecordPageVO();
        page.setList(List.of(record));
        page.setTotal(46);
        page.setPage(1);
        page.setSize(20);
        page.setHasMore(true);
        FinanceRecordVos.Totals totals = new FinanceRecordVos.Totals();
        totals.setIncome(new BigDecimal("400000.00"));
        totals.setExpenseAmount(new BigDecimal("312400.00"));
        page.setTotals(totals);
        when(recordService.listRecords(any())).thenReturn(page);

        mvc.perform(get("/api/v1/finance-records")
                        .param("month", "2026-10").param("type", "ALL")
                        .param("category_ids", "101,102").param("batch_id", "3009")
                        .param("has_voucher", "true").param("keyword", "人工")
                        .param("scope", "ALL").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(8202))
                .andExpect(jsonPath("$.data.list[0].amount").value(3200.00))
                .andExpect(jsonPath("$.data.list[0].source_label").value("来自饲料入库单"))
                .andExpect(jsonPath("$.data.total").value(46))
                .andExpect(jsonPath("$.data.has_more").value(true))
                .andExpect(jsonPath("$.data.totals.income").value(400000.00))
                .andExpect(jsonPath("$.data.totals.expense_amount").value(312400.00));
    }

    /** E04 收支详情 */
    @Test
    void recordDetail() throws Exception {
        FinanceRecordVos.DetailVO vo = new FinanceRecordVos.DetailVO();
        vo.setId(8202L);
        vo.setAmount(new BigDecimal("3200.00"));
        vo.setNote("9 月补录");
        vo.setVersion(3);
        when(recordService.detail(8202L)).thenReturn(vo);

        mvc.perform(get("/api/v1/finance-records/8202"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8202))
                .andExpect(jsonPath("$.data.amount").value(3200.00))
                .andExpect(jsonPath("$.data.note").value("9 月补录"))
                .andExpect(jsonPath("$.data.version").value(3));
    }

    /** E05 记一笔 */
    @Test
    void createRecord() throws Exception {
        FinanceRecordVos.RecordVO vo = new FinanceRecordVos.RecordVO();
        vo.setId(8207L);
        vo.setAmount(new BigDecimal("3200.00"));
        vo.setRecordType("EXPENSE");
        vo.setStatus("NORMAL");
        when(recordService.create(any(), eq("idem-m10-1"))).thenReturn(vo);

        mvc.perform(post("/api/v1/finance-records")
                        .header("Idempotency-Key", "idem-m10-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"record_type":"EXPENSE","amount":3200.00,"category_id":101,
                                 "subcategory_id":12,"note":"9 月补录","record_date":"2026-10-19",
                                 "file_ids":[51001,51002]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8207))
                .andExpect(jsonPath("$.data.amount").value(3200.00))
                .andExpect(jsonPath("$.data.record_type").value("EXPENSE"));
    }

    /** E06 修改手工记录 */
    @Test
    void updateRecord() throws Exception {
        FinanceRecordVos.RecordVO vo = new FinanceRecordVos.RecordVO();
        vo.setId(8202L);
        vo.setAmount(new BigDecimal("3300.00"));
        when(recordService.update(eq(8202L), any())).thenReturn(vo);

        mvc.perform(put("/api/v1/finance-records/8202")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"record_type":"EXPENSE","amount":3300.00,"category_id":101,"version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8202))
                .andExpect(jsonPath("$.data.amount").value(3300.00));
    }

    /** E07 作废手工记录 */
    @Test
    void voidRecord() throws Exception {
        FinanceRecordVos.RecordVO vo = new FinanceRecordVos.RecordVO();
        vo.setId(8202L);
        vo.setStatus("VOID");
        vo.setStatusText("已作废");
        when(recordService.voidRecord(8202L, 3)).thenReturn(vo);

        mvc.perform(post("/api/v1/finance-records/8202/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOID"))
                .andExpect(jsonPath("$.data.status_text").value("已作废"));
    }

    /** E09 疑似重复检查 */
    @Test
    void duplicateCheck() throws Exception {
        FinanceRecordVos.DuplicateCheckVO vo = new FinanceRecordVos.DuplicateCheckVO();
        vo.setHit(true);
        vo.setRecordId(8205L);
        vo.setCategoryName("饲料");
        vo.setAmount(new BigDecimal("3200.00"));
        vo.setRecordDate("2026-10-19");
        vo.setSourceLabel("来自饲料入库单");
        when(categoryService.duplicateCheck(eq("EXPENSE"), eq(101L), any(), any(), eq(null))).thenReturn(vo);

        mvc.perform(get("/api/v1/finance-records/duplicate-check")
                        .param("record_type", "EXPENSE").param("category_id", "101")
                        .param("amount", "3200.00").param("record_date", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hit").value(true))
                .andExpect(jsonPath("$.data.record_id").value(8205))
                .andExpect(jsonPath("$.data.category_name").value("饲料"))
                .andExpect(jsonPath("$.data.source_label").value("来自饲料入库单"));
    }

    /** E10 一级分类与本场子类 */
    @Test
    void categories() throws Exception {
        FinanceRecordVos.SubcategoryVO sub = new FinanceRecordVos.SubcategoryVO();
        sub.setId(12L);
        sub.setName("预混料");
        sub.setCreatedAt("2026-10-12 09:30:00");
        FinanceRecordVos.CategoryVO vo = new FinanceRecordVos.CategoryVO();
        vo.setId(101L);
        vo.setCode("FEED");
        vo.setName("饲料");
        vo.setDirection("EXPENSE");
        vo.setAutoSource(true);
        vo.setSubcategories(List.of(sub));
        when(categoryService.list("EXPENSE")).thenReturn(List.of(vo));

        mvc.perform(get("/api/v1/finance-categories").param("direction", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].code").value("FEED"))
                .andExpect(jsonPath("$.data[0].auto_source").value(true))
                .andExpect(jsonPath("$.data[0].subcategories[0].name").value("预混料"));
    }

    /** E11 添加子类 */
    @Test
    void addSubcategory() throws Exception {
        FinanceRecordVos.SubcategoryVO vo = new FinanceRecordVos.SubcategoryVO();
        vo.setId(12L);
        vo.setName("预混料");
        when(categoryService.add(any(), eq("idem-sub-1"))).thenReturn(vo);

        mvc.perform(post("/api/v1/expense-subcategories")
                        .header("Idempotency-Key", "idem-sub-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_id\":101,\"name\":\"预混料\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.name").value("预混料"));
    }

    /** E12 子类改名 */
    @Test
    void renameSubcategory() throws Exception {
        FinanceRecordVos.SubcategoryVO vo = new FinanceRecordVos.SubcategoryVO();
        vo.setId(12L);
        vo.setName("预混料A");
        when(categoryService.rename(eq(12L), any())).thenReturn(vo);

        mvc.perform(put("/api/v1/expense-subcategories/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"预混料A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.name").value("预混料A"));
    }

    /** E13 删除子类 */
    @Test
    void deleteSubcategory() throws Exception {
        mvc.perform(delete("/api/v1/expense-subcategories/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"));
    }

    /** E43 按请求编号查询 */
    @Test
    void submission() throws Exception {
        FinanceRecordVos.SubmissionVO vo = new FinanceRecordVos.SubmissionVO();
        vo.setSaved(true);
        vo.setBizType("FINANCE_RECORD");
        vo.setBizId(8202L);
        when(recordService.submission("req-20261019-001")).thenReturn(vo);

        mvc.perform(get("/api/v1/business-submissions/req-20261019-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(true))
                .andExpect(jsonPath("$.data.biz_type").value("FINANCE_RECORD"))
                .andExpect(jsonPath("$.data.biz_id").value(8202));
    }

    // ===== 出栏与收款（E14–E23）=====

    /** E14 可出栏批次与休药状态 */
    @Test
    void batchOptions() throws Exception {
        SaleOrderVos.PenStock pen = new SaleOrderVos.PenStock();
        pen.setPenId(2003L);
        pen.setPenName("育肥1舍");
        pen.setStockCount(420);
        SaleOrderVos.BatchOptionVO vo = new SaleOrderVos.BatchOptionVO();
        vo.setBatchId(3001L);
        vo.setBatchNo("ZM2610-031");
        vo.setCategoryId(101L);
        vo.setCountUnitText("头");
        vo.setDayAge(96);
        vo.setCurrentCount(870);
        vo.setInWithdrawal(true);
        vo.setWithdrawalDaysLeft(5);
        vo.setWithdrawalDrugName("氟苯尼考");
        vo.setPens(List.of(pen));
        when(saleOrderService.batchOptions()).thenReturn(List.of(vo));

        mvc.perform(get("/api/v1/sale-orders/batch-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].batch_id").value(3001))
                .andExpect(jsonPath("$.data[0].batch_no").value("ZM2610-031"))
                .andExpect(jsonPath("$.data[0].in_withdrawal").value(true))
                .andExpect(jsonPath("$.data[0].withdrawal_days_left").value(5))
                .andExpect(jsonPath("$.data[0].pens[0].stock_count").value(420));
    }

    /** E15 登记预填 */
    @Test
    void prefill() throws Exception {
        SaleOrderVos.MarketPrice price = new SaleOrderVos.MarketPrice();
        price.setProvinceName("内蒙古");
        price.setCategoryName("育肥猪");
        price.setPrice(new BigDecimal("18.60"));
        price.setPriceUnitText("元/千克");
        price.setPriceDate("2026-10-19");
        SaleOrderVos.PrefillVO vo = new SaleOrderVos.PrefillVO();
        vo.setMarketPrice(price);
        vo.setRecentBuyers(List.of("赤峰雨润", "宁城双汇"));
        vo.setLastBuyer("赤峰雨润");
        vo.setLastPaymentMethod("BANK_TRANSFER");
        when(saleOrderService.prefill(3001L, "2026-10-20")).thenReturn(vo);

        mvc.perform(get("/api/v1/sale-orders/prefill")
                        .param("batch_id", "3001").param("sale_date", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.market_price.price").value(18.60))
                .andExpect(jsonPath("$.data.market_price.price_unit_text").value("元/千克"))
                .andExpect(jsonPath("$.data.recent_buyers[0]").value("赤峰雨润"))
                .andExpect(jsonPath("$.data.last_payment_method").value("BANK_TRANSFER"));
    }

    /** E16 登记出栏单 */
    @Test
    void createOrder() throws Exception {
        SaleOrderVos.CreatedVO vo = new SaleOrderVos.CreatedVO();
        vo.setId(9107L);
        vo.setBatchNo("ZM2610-031");
        vo.setRemainingCount(450);
        vo.setCountUnitText("头");
        vo.setBatchEnded(false);
        when(saleOrderService.create(any(), eq("idem-order-1"))).thenReturn(vo);

        mvc.perform(post("/api/v1/sale-orders")
                        .header("Idempotency-Key", "idem-order-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"batch_id":3001,"sale_date":"2026-10-20","quantity":420,
                                 "pen_quantities":[{"pen_id":2003,"quantity":420}],
                                 "total_weight":30870.00,"pricing_method":"BY_WEIGHT",
                                 "unit_price":18.60,"buyer_name":"赤峰雨润","due_date":"2026-10-27"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9107))
                .andExpect(jsonPath("$.data.remaining_count").value(450))
                .andExpect(jsonPath("$.data.batch_ended").value(false));
    }

    /** E17 出栏单列表（App 不传 month） */
    @Test
    void listOrders() throws Exception {
        SaleOrderVos.ItemVO item = new SaleOrderVos.ItemVO();
        item.setId(9102L);
        item.setBatchNo("ZM2609-012");
        item.setSaleAmount(new BigDecimal("114048.00"));
        item.setPaymentStatus("PARTIAL");
        item.setPaymentStatusText("部分收款");
        item.setDueState("DUE_SOON");
        item.setDaysToDue(2);
        SaleOrderVos.MonthGroup group = new SaleOrderVos.MonthGroup();
        group.setMonth("2026-10");
        group.setLabel("10 月");
        group.setCount(1);
        group.setSaleAmount(new BigDecimal("114048.00"));
        SaleOrderVos.OrderPageVO page = new SaleOrderVos.OrderPageVO();
        page.setList(List.of(item));
        page.setTotal(1);
        page.setPage(1);
        page.setSize(20);
        page.setMonthGroups(List.of(group));
        when(saleOrderService.list(any())).thenReturn(page);

        mvc.perform(get("/api/v1/sale-orders")
                        .param("status", "PARTIAL").param("keyword", "赤峰")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(9102))
                .andExpect(jsonPath("$.data.list[0].payment_status_text").value("部分收款"))
                .andExpect(jsonPath("$.data.list[0].days_to_due").value(2))
                .andExpect(jsonPath("$.data.month_groups[0].label").value("10 月"))
                .andExpect(jsonPath("$.data.month_groups[0].sale_amount").value(114048.00));
    }

    /** E18 待收款 */
    @Test
    void receivables() throws Exception {
        SaleOrderVos.ItemVO item = new SaleOrderVos.ItemVO();
        item.setId(9101L);
        item.setUnpaidAmount(new BigDecimal("0.00"));
        SaleOrderVos.ReceivableSummary summary = new SaleOrderVos.ReceivableSummary();
        summary.setTotalUnpaidAmount(new BigDecimal("68448.00"));
        summary.setOrderCount(1);
        summary.setOverdueCount(0);
        summary.setNearestDueDate("2026-10-21");
        SaleOrderVos.ReceivableVO page = new SaleOrderVos.ReceivableVO();
        page.setList(List.of(item));
        page.setTotal(1);
        page.setPage(1);
        page.setSize(20);
        page.setSummary(summary);
        page.setReminderReceivers("OWNER_AND_MANAGERS");
        page.setRemindRuleText("约定收款日当天提醒你和场长");
        when(saleOrderService.receivables(any())).thenReturn(page);

        mvc.perform(get("/api/v1/sale-orders/receivables")
                        .param("due_filter", "OVERDUE").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.total_unpaid_amount").value(68448.00))
                .andExpect(jsonPath("$.data.summary.nearest_due_date").value("2026-10-21"))
                .andExpect(jsonPath("$.data.reminder_receivers").value("OWNER_AND_MANAGERS"));
    }

    /** E19 出栏单详情 */
    @Test
    void orderDetail() throws Exception {
        SaleOrderVos.DetailVO vo = new SaleOrderVos.DetailVO();
        vo.setId(9102L);
        vo.setSaleNo("XS261020-002");
        vo.setPricingMethod("BY_WEIGHT");
        vo.setPricingMethodText("按重量");
        vo.setUnitPrice(new BigDecimal("18.60"));
        vo.setTotalWeight(new BigDecimal("6131.60"));
        vo.setSaleAmount(new BigDecimal("114048.00"));
        SaleOrderVos.Actions actions = new SaleOrderVos.Actions();
        actions.setCanReceive(true);
        actions.setCanEdit(false);
        actions.setChangeInReview(false);
        vo.setActions(actions);
        SaleOrderVos.SettlementBrief brief = new SaleOrderVos.SettlementBrief();
        brief.setStatus("RUNNING");
        brief.setProfit(new BigDecimal("22608.00"));
        brief.setPendingSupplementCount(1);
        vo.setSettlementBrief(brief);
        when(saleOrderService.detail(9102L)).thenReturn(vo);

        mvc.perform(get("/api/v1/sale-orders/9102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9102))
                .andExpect(jsonPath("$.data.sale_no").value("XS261020-002"))
                .andExpect(jsonPath("$.data.pricing_method_text").value("按重量"))
                .andExpect(jsonPath("$.data.actions.can_receive").value(true))
                .andExpect(jsonPath("$.data.settlement_brief.pending_supplement_count").value(1));
    }

    /** E20 修改未收款出栏单 */
    @Test
    void updateOrder() throws Exception {
        SaleOrderVos.DetailVO vo = new SaleOrderVos.DetailVO();
        vo.setId(9106L);
        vo.setVersion(2);
        vo.setContentVersion(2);
        when(saleOrderService.update(eq(9106L), any())).thenReturn(vo);

        mvc.perform(put("/api/v1/sale-orders/9106")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":400,"total_weight":29400.00,"pricing_method":"BY_WEIGHT",
                                 "unit_price":18.20,"version":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9106))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    /** E21 作废未收款出栏单 */
    @Test
    void voidOrder() throws Exception {
        SaleOrderVos.DetailVO vo = new SaleOrderVos.DetailVO();
        vo.setId(9103L);
        vo.setPaymentStatus("VOIDED");
        vo.setVoidReason("登记错误");
        when(saleOrderService.voidOrder(eq(9103L), any())).thenReturn(vo);

        mvc.perform(post("/api/v1/sale-orders/9103/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"登记错误\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment_status").value("VOIDED"))
                .andExpect(jsonPath("$.data.void_reason").value("登记错误"));
    }

    /** E22 登记收款 */
    @Test
    void addReceipt() throws Exception {
        SaleOrderVos.ReceiptResult result = new SaleOrderVos.ReceiptResult();
        result.setAmount(new BigDecimal("88160.00"));
        result.setUnpaidAmount(new BigDecimal("0.00"));
        result.setPaidOff(true);
        SaleOrderVos.DetailVO vo = new SaleOrderVos.DetailVO();
        vo.setId(9102L);
        vo.setPaymentStatus("PAID");
        vo.setReceiptResult(result);
        when(saleOrderService.addReceipt(eq(9102L), any(), eq("idem-receipt-1"))).thenReturn(vo);

        mvc.perform(post("/api/v1/sale-orders/9102/receipts")
                        .header("Idempotency-Key", "idem-receipt-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":88160.00,"receipt_date":"2026-10-21","payment_method":"BANK_TRANSFER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment_status").value("PAID"))
                .andExpect(jsonPath("$.data.receipt_result.paid_off").value(true))
                .andExpect(jsonPath("$.data.receipt_result.unpaid_amount").value(0.00));
    }

    /** E23 修改申请预计结果 */
    @Test
    void changePreview() throws Exception {
        SaleOrderVos.ChangePreviewVO vo = new SaleOrderVos.ChangePreviewVO();
        vo.setSaleAmountBefore(new BigDecimal("114048.00"));
        vo.setUnpaidAmountBefore(new BigDecimal("88160.00"));
        vo.setReceivedAmount(new BigDecimal("25888.00"));
        vo.setSaleAmountAfter(new BigDecimal("128405.00"));
        vo.setUnpaidAmountAfter(new BigDecimal("102517.00"));
        vo.setTurnToLoss(false);
        when(saleOrderService.preview(eq(9102L), any())).thenReturn(vo);

        mvc.perform(post("/api/v1/sale-orders/9102/change-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"change_type":"EDIT","field_code":"new_unit_price","new_unit_price":20.95}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sale_amount_before").value(114048.00))
                .andExpect(jsonPath("$.data.sale_amount_after").value(128405.00))
                .andExpect(jsonPath("$.data.turn_to_loss").value(false));
    }

    // ===== 批次核算（E26–E28）=====

    /** E26 批次选择与待确认补录数 */
    @Test
    void settlementOptions() throws Exception {
        SettlementVos.OptionVO option = new SettlementVos.OptionVO();
        option.setBatchId(3009L);
        option.setBatchNo("ZM2609-045");
        option.setBatchStatus("ACTIVE");
        option.setSettlementStatus("LOCKED");
        option.setPendingSupplementCount(2);
        SettlementVos.OptionPageVO page = new SettlementVos.OptionPageVO();
        page.setList(List.of(option));
        page.setTotal(1);
        page.setPage(1);
        page.setSize(20);
        page.setFarmPendingSupplementCount(3);
        when(settlementService.options(any())).thenReturn(page);

        mvc.perform(get("/api/v1/batch-settlements")
                        .param("keyword", "2609").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].batch_no").value("ZM2609-045"))
                .andExpect(jsonPath("$.data.list[0].settlement_status").value("LOCKED"))
                .andExpect(jsonPath("$.data.list[0].pending_supplement_count").value(2))
                .andExpect(jsonPath("$.data.farm_pending_supplement_count").value(3));
    }

    /** E27 批次核算 */
    @Test
    void settlementDetail() throws Exception {
        SettlementVos.BatchSettlementVO vo = new SettlementVos.BatchSettlementVO();
        vo.setSettlementId(7009L);
        vo.setStatus("LOCKED");
        vo.setProfit(new BigDecimal("52608.00"));
        vo.setSaleIncome(new BigDecimal("216000.00"));
        vo.setCostTotal(new BigDecimal("163392.00"));
        vo.setPerHeadBasis("SOLD");
        vo.setPerHeadCount(420);
        vo.setVersion(4);
        SettlementVos.SettlementBatch batch = new SettlementVos.SettlementBatch();
        batch.setBatchId(3009L);
        batch.setBatchNo("ZM2609-045");
        batch.setDayAge(168);
        vo.setBatch(batch);
        when(settlementService.detail(3009L)).thenReturn(vo);

        mvc.perform(get("/api/v1/batch-settlements/3009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settlement_id").value(7009))
                .andExpect(jsonPath("$.data.status").value("LOCKED"))
                .andExpect(jsonPath("$.data.per_head_basis").value("SOLD"))
                .andExpect(jsonPath("$.data.batch.batch_no").value("ZM2609-045"))
                .andExpect(jsonPath("$.data.batch.day_age").value(168));
    }

    /** E28 确认补录并重算 */
    @Test
    void confirmSupplements() throws Exception {
        SettlementVos.BatchSettlementVO vo = new SettlementVos.BatchSettlementVO();
        vo.setSettlementId(7009L);
        SettlementVos.CostItem feedCost = new SettlementVos.CostItem();
        feedCost.setCostGroup("FEED");
        feedCost.setCostGroupText("饲料");
        feedCost.setAmount(new BigDecimal("110400.00"));
        vo.setCosts(List.of(feedCost));
        vo.setVersion(5);
        when(settlementService.confirm(eq(3009L), any())).thenReturn(vo);

        mvc.perform(post("/api/v1/batch-settlements/3009/confirm-supplements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplement_ids\":[8801,8802],\"version\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settlement_id").value(7009))
                .andExpect(jsonPath("$.data.costs[0].amount").value(110400.00))
                .andExpect(jsonPath("$.data.version").value(5));
    }

    // ===== 行情与 AI 出栏建议（E30–E36）=====

    /** E30 行情筛选项与默认值 */
    @Test
    void marketFilters() throws Exception {
        MarketVos.Province province = new MarketVos.Province();
        province.setCode("150000");
        province.setName("内蒙古");
        MarketVos.CategoryOption category = new MarketVos.CategoryOption();
        category.setId(101L);
        category.setName("育肥猪");
        MarketVos.BreedOption breed = new MarketVos.BreedOption();
        breed.setCategoryId(101L);
        breed.setBreedId(1101L);
        breed.setName("杜长大");
        MarketVos.PriceTypeOption priceType = new MarketVos.PriceTypeOption();
        priceType.setCode("SLAUGHTER");
        priceType.setName("出栏价");
        MarketVos.MarketDefaults defaults = new MarketVos.MarketDefaults();
        defaults.setProvinceCode("150000");
        defaults.setCategoryId(101L);
        defaults.setBreedId(0L);
        defaults.setPriceType("SLAUGHTER");
        MarketVos.FilterVO vo = new MarketVos.FilterVO();
        vo.setProvinces(List.of(province));
        vo.setCategories(List.of(category));
        vo.setBreeds(List.of(breed));
        vo.setPriceTypes(List.of(priceType));
        vo.setDefaults(defaults);
        vo.setDefaultDesc("内蒙古 · 育肥猪 · 出栏价");
        when(marketPriceService.filters()).thenReturn(vo);

        mvc.perform(get("/api/v1/market-prices/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provinces[0].code").value("150000"))
                .andExpect(jsonPath("$.data.breeds[0].name").value("杜长大"))
                .andExpect(jsonPath("$.data.price_types[0].name").value("出栏价"))
                .andExpect(jsonPath("$.data.defaults.price_type").value("SLAUGHTER"))
                .andExpect(jsonPath("$.data.default_desc").value("内蒙古 · 育肥猪 · 出栏价"));
    }

    /** E31 最新价、涨跌、走势、区间统计 */
    @Test
    void marketTrend() throws Exception {
        MarketVos.Latest latest = new MarketVos.Latest();
        latest.setPrice(new BigDecimal("18.60"));
        latest.setPriceUnitText("元/千克");
        latest.setPriceDate("2026-10-19");
        MarketVos.PriceChange change = new MarketVos.PriceChange();
        change.setChangeRate(new BigDecimal("0.0123"));
        change.setDirection("UP");
        change.setCompareDate("2026-10-18");
        change.setComparePrice(new BigDecimal("18.37"));
        MarketVos.PricePoint point = new MarketVos.PricePoint();
        point.setDate("2026-10-19");
        point.setPrice(new BigDecimal("18.60"));
        MarketVos.PriceStats stats = new MarketVos.PriceStats();
        stats.setMaxPrice(new BigDecimal("19.20"));
        stats.setMinPrice(new BigDecimal("17.80"));
        stats.setAvgPrice(new BigDecimal("18.42"));
        MarketVos.TrendVO vo = new MarketVos.TrendVO();
        vo.setLatest(latest);
        vo.setChange(change);
        vo.setSeries(List.of(point));
        vo.setStats(stats);
        vo.setEmpty(false);
        when(marketPriceService.trend(any())).thenReturn(vo);

        mvc.perform(get("/api/v1/market-prices/trend")
                        .param("province_code", "150000").param("category_id", "101")
                        .param("breed_id", "1101").param("price_type", "SLAUGHTER")
                        .param("range_days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latest.price").value(18.60))
                .andExpect(jsonPath("$.data.change.direction").value("UP"))
                .andExpect(jsonPath("$.data.series[0].date").value("2026-10-19"))
                .andExpect(jsonPath("$.data.stats.avg_price").value(18.42))
                .andExpect(jsonPath("$.data.empty").value(false));
    }

    /** E32 每日价格 */
    @Test
    void dailyPrices() throws Exception {
        MarketVos.DailyPriceVO item = new MarketVos.DailyPriceVO();
        item.setPriceDate("2026-10-19");
        item.setPrice(new BigDecimal("18.60"));
        item.setChangeRate(new BigDecimal("0.0123"));
        item.setDirection("UP");
        item.setCorrected(false);
        when(marketPriceService.daily(any())).thenReturn(new PageVO<>(List.of(item), 30, 1, 20));

        mvc.perform(get("/api/v1/market-prices/daily")
                        .param("province_code", "150000").param("category_id", "101")
                        .param("breed_id", "1101").param("price_type", "SLAUGHTER")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].price_date").value("2026-10-19"))
                .andExpect(jsonPath("$.data.list[0].change_rate").value(0.0123))
                .andExpect(jsonPath("$.data.total").value(30));
    }

    /** E33 批次最近一次建议 */
    @Test
    void latestAdvice() throws Exception {
        SaleAdviceVos.SaleAdviceVO vo = advice("7101", "SUCCESS");
        when(saleAdviceService.latest(3001L)).thenReturn(vo);

        mvc.perform(get("/api/v1/sale-advices/latest").param("batch_id", "3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7101))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.conclusion").value("SELL_SOON"))
                .andExpect(jsonPath("$.data.conclusion_text").value("建议近期出栏"));
    }

    /** E34 重新生成出栏建议 */
    @Test
    void regenerateAdvice() throws Exception {
        SaleAdviceVos.SaleAdviceVO vo = advice("7102", "GENERATING");
        when(saleAdviceService.regenerate(3001L)).thenReturn(vo);

        mvc.perform(post("/api/v1/sale-advices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batch_id\":3001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7102))
                .andExpect(jsonPath("$.data.status").value("GENERATING"));
    }

    /** E35 历史建议 */
    @Test
    void adviceHistory() throws Exception {
        when(saleAdviceService.history(any()))
                .thenReturn(new PageVO<>(List.of(advice("7101", "SUCCESS")), 1, 1, 20));

        mvc.perform(get("/api/v1/sale-advices")
                        .param("batch_id", "3001").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(7101))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    /** E36 建议全文与状态轮询 */
    @Test
    void adviceDetail() throws Exception {
        when(saleAdviceService.get(7201L)).thenReturn(advice("7201", "SUCCESS"));

        mvc.perform(get("/api/v1/sale-advices/7201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7201))
                .andExpect(jsonPath("$.data.batch_no").value("ZM2610-031"));
    }

    // ===== 经营报表（E37–E42）=====

    /** E37 已生成期次 */
    @Test
    void reportPeriods() throws Exception {
        ReportVos.ReportPeriod period = new ReportVos.ReportPeriod();
        period.setPeriodStart("2026-10-12");
        period.setPeriodEnd("2026-10-18");
        period.setLabel("10月12日—18日");
        period.setGeneratedAt("2026-10-19 06:10:00");
        ReportVos.ReportPeriodVO vo = new ReportVos.ReportPeriodVO();
        vo.setPeriods(List.of(period));
        vo.setNextGenerationDate("2026-10-26");
        when(reportService.periods("WEEKLY", null)).thenReturn(vo);

        mvc.perform(get("/api/v1/business-reports/periods").param("report_type", "WEEKLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periods[0].label").value("10月12日—18日"))
                .andExpect(jsonPath("$.data.next_generation_date").value("2026-10-26"));
    }

    /** E38 报表指标与对比 */
    @Test
    void reportView() throws Exception {
        ReportVos.ReportPeriodInfo period = new ReportVos.ReportPeriodInfo();
        period.setStart("2026-10-01");
        period.setEnd("2026-10-19");
        period.setLabel("10月1日—19日");
        ReportVos.StockMetric stock = new ReportVos.StockMetric();
        stock.setBegin(870);
        stock.setEnd(450);
        stock.setChange(-420);
        ReportVos.Mortality mortality = new ReportVos.Mortality();
        mortality.setRate(new BigDecimal("0.0069"));
        mortality.setDeathCount(6);
        ReportVos.ReportMetrics metrics = new ReportVos.ReportMetrics();
        metrics.setStock(List.of(stock));
        metrics.setMortality(mortality);
        ReportVos.DataCheck check = new ReportVos.DataCheck();
        check.setHasIssue(true);
        check.setBatchCount(1);
        ReportVos.BusinessReportVO vo = new ReportVos.BusinessReportVO();
        vo.setPeriod(period);
        vo.setScope("ALL");
        vo.setEmpty(false);
        vo.setDataCheck(check);
        vo.setMetrics(metrics);
        when(reportService.view(any())).thenReturn(vo);

        mvc.perform(get("/api/v1/business-reports/view")
                        .param("report_type", "MONTHLY").param("period_start", "2026-10-01")
                        .param("scope", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period.label").value("10月1日—19日"))
                .andExpect(jsonPath("$.data.scope").value("ALL"))
                .andExpect(jsonPath("$.data.metrics.stock[0].begin").value(870))
                .andExpect(jsonPath("$.data.metrics.stock[0].change").value(-420))
                .andExpect(jsonPath("$.data.metrics.mortality.death_count").value(6))
                .andExpect(jsonPath("$.data.data_check.has_issue").value(true));
    }

    /** E39 指标逐日趋势 */
    @Test
    void reportTrend() throws Exception {
        ReportVos.TrendPoint point = new ReportVos.TrendPoint();
        point.setDate("2026-10-19");
        point.setValue(new BigDecimal("870"));
        ReportVos.StockEquation equation = new ReportVos.StockEquation();
        equation.setBegin(870);
        equation.setEntry(0);
        equation.setDeath(6);
        equation.setSold(420);
        equation.setEnd(450);
        ReportVos.ReportTrendVO vo = new ReportVos.ReportTrendVO();
        vo.setMetric("STOCK");
        vo.setPoints(List.of(point));
        vo.setEquation(equation);
        when(reportService.trend(any())).thenReturn(vo);

        mvc.perform(get("/api/v1/business-reports/trend")
                        .param("report_type", "MONTHLY").param("metric", "STOCK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metric").value("STOCK"))
                .andExpect(jsonPath("$.data.points[0].date").value("2026-10-19"))
                .andExpect(jsonPath("$.data.equation.end").value(450));
    }

    /** E40 读取 AI 摘要 */
    @Test
    void reportSummary() throws Exception {
        ReportVos.ReportSummaryVO vo = new ReportVos.ReportSummaryVO();
        vo.setStatus("SUCCESS");
        vo.setHighlight("本月出栏 420 头，售价高于行情");
        vo.setProblem("饲料成本偏高");
        vo.setSuggestion("关注料肉比");
        vo.setGeneratedAt("2026-10-19 06:20:00");
        vo.setOutdated(false);
        when(reportService.summary(any())).thenReturn(vo);

        mvc.perform(get("/api/v1/business-reports/ai-summary")
                        .param("report_type", "MONTHLY").param("scope", "FARM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.highlight").value("本月出栏 420 头，售价高于行情"))
                .andExpect(jsonPath("$.data.outdated").value(false));
    }

    /** E41 重新生成 AI 摘要 */
    @Test
    void regenerateSummary() throws Exception {
        ReportVos.ReportSummaryVO vo = new ReportVos.ReportSummaryVO();
        vo.setStatus("GENERATING");
        when(reportService.regenerate(any())).thenReturn(vo);

        mvc.perform(post("/api/v1/business-reports/ai-summary/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"report_type\":\"MONTHLY\",\"period_start\":\"2026-10-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("GENERATING"));
    }

    /** E42 导出 */
    @Test
    void reportExport() throws Exception {
        ReportVos.ReportExportVO vo = new ReportVos.ReportExportVO();
        vo.setExportId(9101L);
        vo.setFileId(61001L);
        vo.setFileName("经营报表_2026-10.xlsx");
        vo.setDownloadUrl("https://files.example.com/61001");
        vo.setExpireAt("2026-10-26 06:30:00");
        when(reportService.export(any())).thenReturn(vo);

        mvc.perform(post("/api/v1/business-reports/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"report_type":"MONTHLY","period_start":"2026-10-01","scope":"ALL","format":"XLSX"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.export_id").value(9101))
                .andExpect(jsonPath("$.data.file_name").value("经营报表_2026-10.xlsx"))
                .andExpect(jsonPath("$.data.download_url").value("https://files.example.com/61001"));
    }

    // ===== 错误映射 =====

    @Test
    void notFoundReturns40401() throws Exception {
        when(recordService.detail(9999L)).thenThrow(new BizException(ErrorCode.NOT_FOUND));
        mvc.perform(get("/api/v1/finance-records/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void invalidParamReturns40001WithFieldErrors() throws Exception {
        when(categoryService.add(any(), any())).thenThrow(FinanceErrors.field("name", "请输入子类名称"));
        mvc.perform(post("/api/v1/expense-subcategories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_id\":101,\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.field_errors[0].field").value("name"));
    }

    @Test
    void noReceiptChangeReturns40903() throws Exception {
        when(saleOrderService.update(eq(9103L), any()))
                .thenThrow(new BizException(ErrorCode.STATE_CHANGED, "已收款出栏单不能修改"));
        mvc.perform(put("/api/v1/sale-orders/9103")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":400,\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
    }

    private static SaleAdviceVos.SaleAdviceVO advice(String id, String status) {
        SaleAdviceVos.SaleAdviceVO vo = new SaleAdviceVos.SaleAdviceVO();
        vo.setId(Long.valueOf(id));
        vo.setBatchId(3001L);
        vo.setBatchNo("ZM2610-031");
        vo.setPenName("育肥1舍");
        vo.setStatus(status);
        vo.setConclusion("SELL_SOON");
        vo.setConclusionText("建议近期出栏");
        vo.setInWithdrawal(false);
        return vo;
    }
}
