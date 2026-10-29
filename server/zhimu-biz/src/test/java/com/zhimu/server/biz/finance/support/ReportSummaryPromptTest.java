package com.zhimu.server.biz.finance.support;

import com.zhimu.server.biz.finance.vo.ReportVos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 复测 N5：经营报表 AI 摘要的输入按展示格式写好再交给模型。 */
class ReportSummaryPromptTest {

    @Test
    void formatsRatesAsPercentAndAmountsWithGroupingAndUnits() {
        ReportVos.ReportMetrics metrics = new ReportVos.ReportMetrics();
        ReportVos.StockMetric stock = new ReportVos.StockMetric();
        stock.setCountUnitText("只");
        stock.setBegin(27600);
        stock.setEnd(27200);
        stock.setChange(-400);
        metrics.setStock(List.of(stock));
        ReportVos.Mortality mortality = new ReportVos.Mortality();
        mortality.setRate(new BigDecimal("0.0181"));
        mortality.setDeathCount(400);
        mortality.setComparable(true);
        mortality.setChangePoint(new BigDecimal("0.0050"));
        metrics.setMortality(mortality);
        ReportVos.Feed feed = new ReportVos.Feed();
        feed.setKg(new BigDecimal("12345.60"));
        feed.setComparable(true);
        feed.setChangeRate(new BigDecimal("-0.0320"));
        metrics.setFeed(feed);
        ReportVos.Balance balance = new ReportVos.Balance();
        balance.setAmount(new BigDecimal("99380.00"));
        balance.setComparable(false);
        metrics.setBalance(balance);
        ReportVos.BatchProfits profits = new ReportVos.BatchProfits();
        profits.setProfitTotal(new BigDecimal("2560.00"));
        ReportVos.ProfitItem item = new ReportVos.ProfitItem();
        item.setBatchNo("ZM2609-012");
        item.setProfit(new BigDecimal("2560.00"));
        profits.setItems(List.of(item));

        String prompt = ReportSummaryPrompt.build(metrics, profits);

        assertThat(prompt)
                .contains("存栏：期初 27,600 只，期末 27,200 只，较期初 -400 只")
                .contains("死亡率：1.81%（死淘 400 只），较上期上升 0.50 个百分点")
                .contains("饲料消耗：12,345.6 千克，较上期 -3.2%")
                .contains("资金余额：99,380.00 元，较上期无法比较")
                .contains("批次盈亏：合计盈利 2,560.00 元；ZM2609-012 盈利 2,560.00 元")
                .contains("照上面的格式书写");
        // 指标部分不再出现原始数字（要求里举的反例除外）
        String data = prompt.substring(0, prompt.indexOf("请输出"));
        assertThat(data).doesNotContain("0.0181").doesNotContain("99380").doesNotContain("12345.6");
    }

    @Test
    void missingValuesAreWrittenAsNoData() {
        String prompt = ReportSummaryPrompt.build(new ReportVos.ReportMetrics(), new ReportVos.BatchProfits());
        assertThat(prompt)
                .contains("死亡率：暂无数据")
                .contains("饲料消耗：暂无数据，较上期无法比较")
                .contains("资金余额：暂无数据")
                .contains("批次盈亏：本期没有已结算批次");
    }

    @Test
    void lossAndMixedUnitsAreReadable() {
        ReportVos.Mortality mortality = new ReportVos.Mortality();
        mortality.setRate(new BigDecimal("0.0250"));
        mortality.setDeathCount(1230);
        mortality.setComparable(true);
        mortality.setChangePoint(new BigDecimal("-0.0040"));
        assertThat(ReportSummaryPrompt.mortalityText(mortality, "头/只"))
                .isEqualTo("2.50%（死淘 1,230 头/只），较上期下降 0.40 个百分点");

        ReportVos.BatchProfits profits = new ReportVos.BatchProfits();
        ReportVos.ProfitItem item = new ReportVos.ProfitItem();
        item.setBatchNo("ZM2610-072");
        item.setFarmName("西岭鸡场");
        item.setProfit(new BigDecimal("-1234.5"));
        profits.setItems(List.of(item));
        assertThat(ReportSummaryPrompt.profitText(profits)).isEqualTo("ZM2610-072（西岭鸡场）亏损 1,234.50 元");
    }
}
