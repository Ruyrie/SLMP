package com.zhimu.slmp.stock.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.TestRules;
import com.zhimu.slmp.stock.model.vo.DrugStockOverviewVO;
import com.zhimu.slmp.stock.model.vo.FeedStockItemVO;
import com.zhimu.slmp.stock.model.vo.FeedStockOverviewVO;
import com.zhimu.slmp.common.model.vo.QtyVO;
import com.zhimu.slmp.stock.model.vo.StockFlowItemVO;

import org.junit.Rule;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * 工具类：数量拼写（30 瓶（300 头份）、12 瓶 + 5 头份）、金额舍入、横幅三种 kind、临期说明、饲料卡说明行、
 * 流水跳转 FlowRouter、幂等键 SubmitGuard 沿用与换新、临期天数校验。
 */
public class StockUtilTest {

    @Rule
    public androidx.arch.core.executor.testing.InstantTaskExecutorRule instant =
            new androidx.arch.core.executor.testing.InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private static QtyVO q(String value, String unit, String unitText, String packUnit, String packSize) {
        QtyVO v = new QtyVO();
        v.value = new BigDecimal(value);
        v.unit = unit;
        v.unitText = unitText;
        v.packUnit = packUnit;
        v.packSize = packSize == null ? null : new BigDecimal(packSize);
        return v;
    }

    @Test
    public void qtyFormatter_vaccineDrugFeed() {
        assertEquals("30 瓶（300 头份）", TestRules.plain(StockQtyFormatter.format(q("300", "DOSE", "头份", "瓶", "10"))));
        assertEquals("12 瓶 + 5 头份", TestRules.plain(StockQtyFormatter.format(q("125", "DOSE", "头份", "瓶", "10"))));
        assertEquals("18 袋", TestRules.plain(StockQtyFormatter.format(q("1800", "G", "克", "袋", "100"))));
        assertEquals("12 袋 + 50 克", TestRules.plain(StockQtyFormatter.format(q("1250", "G", "克", "袋", "100"))));
        assertEquals("1,850 千克", TestRules.plain(StockQtyFormatter.format(q("1850", "KG", "千克", "袋", "20"))));
        assertEquals("30 瓶", TestRules.plain(StockQtyFormatter.packText(q("300", "DOSE", "头份", "瓶", "10"))));
        assertEquals("0 瓶", TestRules.plain(StockQtyFormatter.format(q("0", "DOSE", "头份", "瓶", "10"))));
        assertEquals("-100 千克", TestRules.plain(StockQtyFormatter.signed(q("100", "KG", "千克", null, null), "OUT")));
        assertEquals("+2,000 千克", TestRules.plain(StockQtyFormatter.signed(q("2000", "KG", "千克", null, null), "IN")));
        assertEquals("-10 瓶", TestRules.plain(StockQtyFormatter.signed(q("100", "DOSE", "头份", "瓶", "10"), "OUT")));
    }

    @Test
    public void calc_amountsRoundHalfUp() {
        assertEquals("3,200.00", TestRules.plain(StockCalc.money(StockCalc.amount(new BigDecimal("1000"),
                new BigDecimal("3.20")))));
        assertEquals("—", StockCalc.money(StockCalc.amount(null, BigDecimal.ONE)));
        assertEquals(0, StockCalc.amount(new BigDecimal("0.125"), BigDecimal.ONE).compareTo(new BigDecimal("0.13")));
        assertEquals(0, StockCalc.parse("1,000.5").compareTo(new BigDecimal("1000.5")));
        assertNull(StockCalc.parse("abc"));
        assertEquals(0, StockCalc.toPack(new BigDecimal("40"), new BigDecimal("5")).compareTo(new BigDecimal("8")));
        assertEquals("1000", StockCalc.plain(new BigDecimal("1000.00")));
    }

    @Test
    public void feedBanner_threeKinds() {
        FeedStockOverviewVO.Banner b = new FeedStockOverviewVO.Banner();
        b.kind = "DAYS";
        b.feedName = "育肥全价料";
        b.remainingDays = 1;
        b.warnDays = 7;
        assertEquals("育肥全价料库存不足，剩余可用 1 天，低于预警天数 7 天。", StockTexts.feedBanner(b));
        b.kind = "MIN";
        b.stock = q("500", "KG", "千克", null, null);
        b.minStock = new BigDecimal("800");
        assertEquals("育肥全价料库存不足，结存 500 千克，低于库存下限 800 千克。", TestRules.plain(StockTexts.feedBanner(b)));
        b.kind = "MULTI";
        b.feedNames = Arrays.asList("育肥全价料", "保育料");
        assertEquals("2 种饲料库存不足：育肥全价料、保育料。", StockTexts.feedBanner(b));
        assertNull(StockTexts.feedBanner(null));
    }

    @Test
    public void drugBanner_singleAndMulti() {
        DrugStockOverviewVO.LowStockBanner b = new DrugStockOverviewVO.LowStockBanner();
        DrugStockOverviewVO.LowDrug a = new DrugStockOverviewVO.LowDrug();
        a.name = "阿莫西林可溶性粉";
        a.usable = q("6000", "G", "克", "袋", "500");
        a.minStock = new BigDecimal("15");
        b.drugs = Arrays.asList(a);
        assertEquals("阿莫西林可溶性粉库存不足，可用结存 12 袋，低于库存下限 15 袋。", TestRules.plain(StockTexts.drugBanner(b)));
        DrugStockOverviewVO.LowDrug c = new DrugStockOverviewVO.LowDrug();
        c.name = "猪瘟活疫苗";
        b.drugs = Arrays.asList(a, c);
        assertEquals("2 种药品库存不足：阿莫西林可溶性粉、猪瘟活疫苗。", StockTexts.drugBanner(b));
    }

    @Test
    public void nearRule_sameOrRange() {
        DrugStockOverviewVO.NearDays d = new DrugStockOverviewVO.NearDays();
        d.vet = 30;
        d.vaccine = 30;
        d.disinfectant = 30;
        assertEquals("按“物资 + 批号”分别结存 · 到期前 30 天为临期", StockTexts.nearRule(d));
        d.vaccine = 15;
        assertEquals("按“物资 + 批号”分别结存 · 到期前 15–30 天为临期", StockTexts.nearRule(d));
    }

    @Test
    public void feedUsage_noUsageAndShortPeriod() {
        FeedStockItemVO f = new FeedStockItemVO();
        f.stock = q("1850", "KG", "千克", null, null);
        f.avgPrice = new BigDecimal("4.2");
        f.usageQty = BigDecimal.ZERO;
        assertEquals("暂无消耗", StockTexts.remaining(f));
        assertEquals("近 7 天没有领用 · 均价每千克 4.20 元", TestRules.plain(StockTexts.feedUsage(f)));
        f.usageQty = new BigDecimal("300");
        f.usageDays = 3;
        assertEquals("近 3 天领用 300 千克 · 均价每千克 4.20 元", TestRules.plain(StockTexts.feedUsage(f)));
    }

    @Test
    public void flowTexts_feedAndLotStyle() {
        StockFlowItemVO f = new StockFlowItemVO();
        f.flowTypeText = "饲喂领用";
        f.materialName = "育肥全价料";
        f.batchNo = "ZM2610-031";
        f.occurAt = "2026-10-19 07:40:00";
        assertEquals("饲喂领用 · 育肥全价料", StockTexts.flowTitle(f, true));
        assertEquals("ZM2610-031 · 今天 07:40", StockTexts.flowSubtitle(f, false));
        StockFlowItemVO v = new StockFlowItemVO();
        v.flowTypeText = "接种领用";
        v.bizDate = "2026-10-12";
        v.penName = "1 号保育舍";
        v.operatorName = "张伟";
        assertEquals("接种领用", StockTexts.flowTitle(v, false));
        assertEquals("10月12日 · 1 号保育舍 · 张伟", StockTexts.flowSubtitle(v, true));
        assertEquals("10月19日 周一 · 今天", StockTexts.flowDateHeader("2026-10-19"));
    }

    @Test
    public void flowSubtitle_scrapDoesNotRepeatLotNo() {
        StockFlowItemVO f = new StockFlowItemVO();
        f.materialType = "DRUG";
        f.lotNo = "2024118";
        f.relatedDesc = "批号 2024118";
        f.occurAt = "2026-10-19 07:40:00";
        assertEquals("批号 2024118 · 今天 07:40", TestRules.plain(StockTexts.flowSubtitle(f, false)));
        f.relatedDesc = "松山区康牧兽药店";
        assertEquals("批号 2024118 · 松山区康牧兽药店 · 今天 07:40", TestRules.plain(StockTexts.flowSubtitle(f, false)));
    }

    @Test
    public void flowRouter_mapsBizTypes() {
        StockFlowItemVO f = new StockFlowItemVO();
        f.bizType = "FEED_RECORD";
        f.bizId = 7106L;
        f.batchId = 3001L;
        FlowRouter.Target t = FlowRouter.route(f);
        assertEquals("L06", t.page);
        assertEquals(3001L, t.args.get(RouterPath.EXTRA_BATCH_ID));
        f.bizType = "INBOUND";
        f.materialType = "FEED";
        assertEquals("M03", FlowRouter.route(f).page);
        f.materialType = "DRUG";
        assertEquals("M08", FlowRouter.route(f).page);
        f.bizType = "OUTBOUND";
        f.flowType = "OTHER_OUT";
        assertEquals("M10", FlowRouter.route(f).page);
        f.flowType = "RETURN";
        assertEquals("M11", FlowRouter.route(f).page);
        f.bizType = "LOSS";
        FlowRouter.Target loss = FlowRouter.route(f);
        assertEquals(FlowRouter.PAGE_RECORD, loss.page);
        assertEquals(StockExtras.KIND_LOSS, loss.args.get(RouterPath.EXTRA_RECORD_KIND));
        f.bizType = "IMMUNE_RECORD";
        assertEquals("D03", FlowRouter.route(f).page);
        f.bizType = "CHANGE_REQUEST";
        assertNull(FlowRouter.route(f));
    }

    @Test
    public void submitGuard_reusesKeyForSameContent() {
        SubmitGuard g = new SubmitGuard();
        g.attach(new SavedStateHandle());
        String k1 = g.keyFor(Arrays.asList("a", "1"));
        assertEquals(k1, g.keyFor(Arrays.asList("a", "1")));
        String k2 = g.keyFor(Arrays.asList("a", "2"));
        assertNotEquals(k1, k2);
        g.clear();
        assertNull(g.current());
        assertTrue(SubmitGuard.isTimeout(com.zhimu.slmp.base.error.ApiException.network(
                new java.net.SocketTimeoutException("timeout"))));
    }

    @Test
    public void settingDelegate_validatesRange() {
        StockSettingDelegate d = new StockSettingDelegate();
        d.apply(null);
        d.getNearVaccine().setValue("0");
        assertNull(d.buildRequest());
        assertEquals(StockSettingDelegate.MSG_RANGE, d.getErrors().getValue().get(StockSettingDelegate.F_VACCINE));
        d.getNearVaccine().setValue("20");
        assertEquals(Integer.valueOf(20), d.buildRequest().nearDaysVaccine);
        assertEquals(Integer.valueOf(0), d.buildRequest().version);
    }
}
