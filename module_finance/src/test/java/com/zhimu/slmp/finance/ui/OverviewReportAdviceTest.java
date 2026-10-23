package com.zhimu.slmp.finance.ui;

import static com.zhimu.slmp.finance.TestRules.plain;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.finance.TestRules;
import com.zhimu.slmp.finance.data.AdviceComposer;
import com.zhimu.slmp.finance.data.DirectAdviceRepository;
import com.zhimu.slmp.finance.data.FinanceRepository;
import com.zhimu.slmp.finance.data.MarketRepository;
import com.zhimu.slmp.finance.data.ReportRepository;
import com.zhimu.slmp.finance.data.SaleOrderRepository;
import com.zhimu.slmp.finance.data.SettlementRepository;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.ReportQuery;
import com.zhimu.slmp.finance.model.request.SupplementConfirmRequest;
import com.zhimu.slmp.finance.model.vo.BatchSettlementVO;
import com.zhimu.slmp.finance.model.vo.BusinessOverviewVO;
import com.zhimu.slmp.finance.model.vo.BusinessReportVO;
import com.zhimu.slmp.finance.model.vo.FinanceSummaryVO;
import com.zhimu.slmp.finance.model.vo.MarketTrendVO;
import com.zhimu.slmp.finance.model.vo.ReportPeriodVO;
import com.zhimu.slmp.finance.model.vo.ReportSummaryVO;
import com.zhimu.slmp.finance.model.vo.ReportTrendVO;
import com.zhimu.slmp.finance.model.vo.SaleAdviceVO;
import com.zhimu.slmp.finance.model.vo.SaleBatchOptionVO;
import com.zhimu.slmp.finance.model.vo.SettlementOptionPageVO;
import com.zhimu.slmp.finance.ui.advice.SaleAdviceViewModel;
import com.zhimu.slmp.finance.ui.home.FinanceHomeViewModel;
import com.zhimu.slmp.finance.ui.report.BusinessReportViewModel;
import com.zhimu.slmp.finance.ui.settlement.BatchSettlementViewModel;
import com.zhimu.slmp.finance.ui.summary.FinanceSummaryViewModel;
import com.zhimu.slmp.finance.util.ReportFileHelper;
import com.zhimu.slmp.network.GsonFactory;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;

/**
 * E01 派生文字、E02 汇总说明与占比切换、E10 补录确认、E12 建议轮询与直连校验、E13 期次未生成与摘要轮询。
 */
public class OverviewReportAdviceTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private FarmContext farm() {
        FarmContext farm = mock(FarmContext.class);
        FarmContextVO ctx = GsonFactory.get().fromJson("{\"farm_id\":1001,\"name\":\"东山猪场\",\"role\":\"OWNER\"}",
                FarmContextVO.class);
        when(farm.getContext()).thenReturn(new MutableLiveData<>(ctx));
        when(farm.current()).thenReturn(ctx);
        when(farm.farmIdOrZero()).thenReturn(1001L);
        when(farm.currentFarmId()).thenReturn(1001L);
        when(farm.isOwnerOrManager()).thenReturn(true);
        return farm;
    }

    @Test
    public void home_derivedTexts() throws IOException {
        FinanceRepository repo = mock(FinanceRepository.class);
        when(repo.getOverview()).thenReturn(Single.just(TestRules.mock("overview.json", BusinessOverviewVO.class)));
        FinanceHomeViewModel vm = new FinanceHomeViewModel(repo, farm());
        vm.attachSavedState(new SavedStateHandle());
        vm.onFarmChanged(1001L);
        assertEquals("较上月同期 +32.1%", plain(vm.getBalanceMomText().getValue()));
        assertEquals("本月出栏 3 张", vm.getSalesSubtitle().getValue());
        assertEquals("本月 46 笔", vm.getRecordSubtitle().getValue());
        assertEquals("3 张 · 1 张已逾期", vm.getReceivableNote().getValue());
        assertTrue(vm.getReceivableOverdue().getValue());
        assertEquals("10月19日 周一 · 本月数据截至今天", vm.getDateNote().getValue());
        assertEquals(2, vm.getTodoItems().getValue().size());
        assertFalse(vm.getShowAllTodos().getValue());
        vm.onTodoClick(vm.getTodoItems().getValue().get(0));
        assertEquals(9101L, vm.getOpenPage().getValue().get("sale_order_id"));
    }

    @Test
    public void summary_notesAndShares() throws IOException {
        FinanceRepository repo = mock(FinanceRepository.class);
        when(repo.getMonthlySummary("2026-10", null)).thenReturn(Single.just(TestRules.mock("summary_202610.json",
                FinanceSummaryVO.class)));
        when(repo.getMonthlySummary("2026-09", null)).thenReturn(Single.just(TestRules.mock("summary_202609.json",
                FinanceSummaryVO.class)));
        when(repo.getMonthlySummary("2026-10", FinanceConst.SCOPE_ALL)).thenReturn(Single.just(TestRules.mock(
                "summary_all.json", FinanceSummaryVO.class)));
        FinanceSummaryViewModel vm = new FinanceSummaryViewModel(repo, farm());
        vm.attachSavedState(new SavedStateHandle());
        assertEquals("10月", vm.getMonthLabel().getValue());
        assertFalse("当前月不能往后翻", vm.getCanNextMonth().getValue());
        assertEquals("截至 10月19日 · 已作废的不计入", vm.getPeriodNote().getValue());
        assertEquals("较上月同期 +32.1% · 上月同期 66,300 元", plain(vm.getBalanceNote().getValue()));
        assertEquals("本月 46 笔，其中自动生成 18 笔", vm.getRecordsSubtitle().getValue());
        assertTrue(vm.getShowScopeSwitch().getValue());
        assertEquals(6, vm.getShares().getValue().size());
        vm.selectShare(2);
        assertEquals(Integer.valueOf(2), vm.getSelectedShareIndex().getValue());
        vm.selectShare(2);
        assertEquals(Integer.valueOf(-1), vm.getSelectedShareIndex().getValue());
        vm.switchShareDirection(FinanceConst.INCOME);
        assertEquals("出栏销售", vm.getShares().getValue().get(0).getName());
        vm.openShare(0);
        assertEquals("201", vm.getOpenPage().getValue().get("category_ids"));
        vm.prevMonth();
        assertEquals("9月1日—30日 · 已作废的不计入", vm.getPeriodNote().getValue());
        assertTrue(vm.getCanNextMonth().getValue());
        vm.nextMonth();
        vm.switchScope(true);
        assertEquals("只合计你担任所有者或场长的养殖场。南坡羊场你是员工，不计入。", vm.getAllFarmsNotice().getValue());
        assertEquals("每条记录带养殖场标签", vm.getRecordsSubtitle().getValue());
    }

    @Test
    public void settlement_confirmSupplements() throws IOException {
        SettlementRepository repo = mock(SettlementRepository.class);
        when(repo.getSettlement(3009L)).thenReturn(Single.just(TestRules.mock("settlement_3009.json",
                BatchSettlementVO.class)));
        when(repo.confirmSupplements(anyLong(), any())).thenReturn(Single.just(TestRules.mock(
                "settlement_3009_confirmed.json", BatchSettlementVO.class)));
        BatchSettlementViewModel vm = new BatchSettlementViewModel(repo, farm());
        Map<String, Object> args = new HashMap<>();
        args.put("batch_id", 3009L);
        vm.attachSavedState(new SavedStateHandle(args));
        assertTrue(vm.isLocked());
        List<BatchSettlementVO.Supplement> list = vm.getSupplements().getValue();
        assertEquals("有 1 笔补录费用待确认。", BatchSettlementViewModel.supplementTitle(list));
        assertEquals("10月17日 运输费 600 元。核算已锁定，你确认后才会重新计算。",
                plain(BatchSettlementViewModel.supplementText(list)));
        vm.requestConfirm();
        String[] dialog = vm.getShowConfirmDialog().getValue();
        assertEquals("确认补录的运输费？", dialog[0]);
        assertEquals("10月17日 运输费 600 元将计入其他费用，批次盈亏变为 +2,560 元。", plain(dialog[1]));
        vm.confirmSupplements();
        ArgumentCaptor<SupplementConfirmRequest> req = ArgumentCaptor.forClass(SupplementConfirmRequest.class);
        verify(repo).confirmSupplements(org.mockito.ArgumentMatchers.eq(3009L), req.capture());
        assertEquals(Collections.singletonList(401L), req.getValue().getSupplementIds());
        assertEquals(2, req.getValue().getVersion());
        assertTrue(vm.getSupplements().getValue().isEmpty());
        assertEquals("已重新计算批次核算", vm.getMessage().getValue().getText());
        assertEquals("按出栏 98 头平摊；料肉比 = 饲料 25,930 千克 ÷ 净增重 9,260 千克",
                plain(BatchSettlementViewModel.perHeadNote(vm.getSettlement().getValue())));
    }

    @Test
    public void advice_pollsUntilDone() throws IOException {
        SaleOrderRepository sale = mock(SaleOrderRepository.class);
        MarketRepository market = mock(MarketRepository.class);
        DirectAdviceRepository direct = mock(DirectAdviceRepository.class);
        when(direct.isEnabled()).thenReturn(false);
        List<SaleBatchOptionVO> batches = TestRules.mock("batch_options.json",
                new TypeToken<List<SaleBatchOptionVO>>() {
                }.getType());
        when(sale.getBatchOptions()).thenReturn(Single.just(batches));
        when(market.getLatestAdvice(3001L)).thenReturn(Single.just(TestRules.mock("advice_3001.json", SaleAdviceVO.class)));
        when(market.getLatestAdvice(3002L)).thenReturn(Single.just(TestRules.mock("advice_3002_generating.json",
                SaleAdviceVO.class)));
        when(market.getAdvice(7302L)).thenReturn(Single.just(TestRules.mock("advice_3002_generating.json",
                SaleAdviceVO.class)), Single.just(TestRules.mock("advice_7302.json", SaleAdviceVO.class)));
        when(market.pollAdvice(7302L)).thenCallRealMethod();
        SaleAdviceViewModel vm = new SaleAdviceViewModel(sale, market, direct, farm());
        vm.attachSavedState(new SavedStateHandle());
        assertEquals(Long.valueOf(3001), vm.getSelectedBatchId().getValue());
        assertEquals(FinanceConst.NOT_SELLABLE, vm.getAdvice().getValue().getConclusion());
        assertFalse(vm.getGenerating().getValue());
        vm.selectBatch(3002L);
        assertTrue(vm.getGenerating().getValue());
        rules.time.advanceTimeBy(0, TimeUnit.SECONDS);
        assertTrue("第一次轮询仍在生成", vm.getGenerating().getValue());
        rules.time.advanceTimeBy(2, TimeUnit.SECONDS);
        assertFalse(vm.getGenerating().getValue());
        assertEquals(FinanceConst.SELL_SOON, vm.getAdvice().getValue().getConclusion());
        vm.openRegister();
        assertEquals(3002L, vm.getOpenPage().getValue().get("batch_id"));
    }

    @Test
    public void adviceComposer_ruleAndValidation() throws IOException {
        List<SaleBatchOptionVO> batches = TestRules.mock("batch_options.json",
                new TypeToken<List<SaleBatchOptionVO>>() {
                }.getType());
        MarketTrendVO trend = TestRules.mock("trend_30.json", MarketTrendVO.class);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 9, 30);
        JsonObject rule = AdviceComposer.rule(batches.get(0), trend, now);
        SaleAdviceVO r = GsonFactory.get().fromJson(rule, SaleAdviceVO.class);
        assertEquals(FinanceConst.NOT_SELLABLE, r.getConclusion());
        assertEquals("2026-10-29", r.getWindowStart());
        assertEquals("2026-11-05", r.getWindowEnd());
        assertEquals("氟苯尼考粉休药中，剩 10 天，10月29日起可出栏。", r.getReasons().getWithdrawal());
        assertTrue(r.getReasons().getPrice().startsWith("近 30 天每千克 14.40—15.60 元"));
        // 非休药期的 NOT_SELLABLE 纠正为 KEEP_FEEDING；起点不早于今天；跨度不超过 30 天；风险最多 3 条、每条 60 字
        JsonObject out = JsonParser.parseString("{\"conclusion\":\"NOT_SELLABLE\",\"window_start\":\"2026-10-01\","
                + "\"window_end\":\"2026-12-31\",\"risks\":[\"a\",\"b\",\"c\",\"d\"]}").getAsJsonObject();
        SaleAdviceVO v = GsonFactory.get().fromJson(AdviceComposer.fromModel(out, batches.get(1), trend, now),
                SaleAdviceVO.class);
        assertEquals(FinanceConst.KEEP_FEEDING, v.getConclusion());
        assertEquals("建议继续饲养", v.getConclusionText());
        assertEquals("2026-10-19", v.getWindowStart());
        assertEquals("2026-11-18", v.getWindowEnd());
        assertEquals(3, v.getRisks().size());
        StringBuilder longRisk = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            longRisk.append('长');
        }
        JsonObject out2 = JsonParser.parseString("{\"conclusion\":\"SELL_SOON\",\"window_start\":\"2026-10-20\","
                + "\"window_end\":\"2026-10-27\",\"risks\":[\"" + longRisk + "\"]}").getAsJsonObject();
        SaleAdviceVO v2 = GsonFactory.get().fromJson(AdviceComposer.fromModel(out2, batches.get(1), trend, now),
                SaleAdviceVO.class);
        assertEquals(60, v2.getRisks().get(0).length());
        assertNull(AdviceComposer.fromModel(JsonParser.parseString("{\"conclusion\":\"BUY\"}").getAsJsonObject(),
                batches.get(1), trend, now));
        assertTrue(AdviceComposer.user(batches.get(1), trend, now.toLocalDate()).contains("ZM2610-019"));
    }

    @Test
    public void report_periodsAndSummary() throws IOException {
        ReportRepository repo = mock(ReportRepository.class);
        SettlementRepository settlement = mock(SettlementRepository.class);
        BaseDataRepository base = mock(BaseDataRepository.class);
        ReportFileHelper files = mock(ReportFileHelper.class);
        when(settlement.getOptions(isNull(), anyInt(), anyInt())).thenReturn(Single.just(TestRules.mock(
                "settlement_options.json", SettlementOptionPageVO.class)));
        when(base.getCategories()).thenReturn(Single.just(Collections.emptyList()));
        when(repo.getReport(any())).thenReturn(Single.just(TestRules.mock("report_to_date.json", BusinessReportVO.class)));
        when(repo.getReportTrend(any())).thenReturn(Single.just(TestRules.mock("trend_stock.json", ReportTrendVO.class)));
        when(repo.getSummary(any())).thenReturn(Single.just(TestRules.mock("summary_to_date.json", ReportSummaryVO.class)));
        when(repo.pollSummary(any())).thenCallRealMethod();
        String emptyWeekly = "{\"periods\":[],\"next_generation_date\":\"2026-10-26\"}";
        when(repo.getPeriods(org.mockito.ArgumentMatchers.eq(FinanceConst.REPORT_WEEKLY), org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(Single.just(GsonFactory.get()
                .fromJson(emptyWeekly, ReportPeriodVO.class)));
        when(repo.getPeriods(org.mockito.ArgumentMatchers.eq(FinanceConst.REPORT_MONTHLY), org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(Single.just(TestRules.mock(
                "periods_monthly.json", ReportPeriodVO.class)));
        BusinessReportViewModel vm = new BusinessReportViewModel(repo, settlement, base, files, farm());
        vm.attachSavedState(new SavedStateHandle());
        assertNotNull(vm.getReport().getValue());
        rules.time.advanceTimeBy(0, TimeUnit.SECONDS);
        assertEquals(FinanceConst.GEN_SUCCESS, vm.getSummary().getValue().getStatus());
        assertEquals("东山猪场", vm.getFarmLabel().getValue());
        // 周报尚未生成
        vm.setReportType(FinanceConst.REPORT_WEEKLY);
        assertEquals("本周的周报将在 10月26日 周一生成，可以先查看本期至今。", vm.getNotGeneratedTip().getValue());
        // 月报默认选中最新一期
        vm.setReportType(FinanceConst.REPORT_MONTHLY);
        ReportQuery q = vm.getQuery().getValue();
        assertEquals("2026-09-01", q.getPeriodStart());
        assertEquals("9月", vm.periodLabel());
        vm.selectAllFarms();
        assertTrue(vm.getQuery().getValue().isAllFarms());
        assertEquals("全部养殖场合计", vm.getFarmLabel().getValue());
        assertEquals(2, vm.rangeOptions().size());
        // 重新生成：生成中 → 轮询到成功后提示
        when(repo.regenerateSummary(any())).thenReturn(Single.just(GsonFactory.get().fromJson(
                "{\"status\":\"GENERATING\"}", ReportSummaryVO.class)));
        vm.regenerateSummary();
        rules.time.advanceTimeBy(0, TimeUnit.SECONDS);
        assertEquals("已重新生成 AI 摘要", vm.getMessage().getValue().getText());
        verify(files, never()).enqueue(any());
    }
}
