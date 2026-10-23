package com.zhimu.server.biz.finance.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** 模块10 口径：金额比例、环比方向、期间标签与文案映射（服务端文档 2.5/2.7 节）。 */
class FinanceCalcTest {

    @Test
    void rateUsesPreviousAbsoluteValueAndNullWhenNoBase() {
        assertThat(FinanceSupport.rate(new BigDecimal("120"), new BigDecimal("100"))).isEqualByComparingTo("0.2000");
        assertThat(FinanceSupport.rate(new BigDecimal("80"), new BigDecimal("100"))).isEqualByComparingTo("-0.2000");
        assertThat(FinanceSupport.rate(new BigDecimal("100"), new BigDecimal("-100"))).isEqualByComparingTo("2.0000");
        assertThat(FinanceSupport.rate(new BigDecimal("120"), BigDecimal.ZERO)).isNull();
        assertThat(FinanceSupport.rate(new BigDecimal("120"), null)).isNull();
    }

    @Test
    void trendOfDirection() {
        assertThat(FinanceSupport.trendOf(new BigDecimal("0.2000"))).isEqualTo("UP");
        assertThat(FinanceSupport.trendOf(new BigDecimal("-0.2000"))).isEqualTo("DOWN");
        assertThat(FinanceSupport.trendOf(BigDecimal.ZERO)).isEqualTo("FLAT");
        assertThat(FinanceSupport.trendOf(null)).isNull();
    }

    @Test
    void scaleAndNullZero() {
        assertThat(FinanceSupport.nz(null)).isEqualByComparingTo("0");
        assertThat(FinanceSupport.scale2(new BigDecimal("3200.005"))).isEqualByComparingTo("3200.01");
        assertThat(FinanceSupport.scale4(new BigDecimal("0.65217391"))).isEqualByComparingTo("0.6522");
        assertThat(FinanceSupport.scale2(null)).isEqualByComparingTo("0.00");
    }

    @Test
    void daysBetweenCountyMayBeNegative() {
        assertThat(FinanceSupport.daysBetween(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 19))).isEqualTo(6);
        assertThat(FinanceSupport.daysBetween(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 19))).isEqualTo(-2);
    }

    @Test
    void periodLabelSameMonthAndCrossMonth() {
        assertThat(FinanceSupport.periodLabel(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)))
                .isEqualTo("10月12日—18日");
        assertThat(FinanceSupport.periodLabel(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)))
                .isEqualTo("9月28日—10月4日");
        assertThat(FinanceSupport.periodLabel(null, LocalDate.of(2026, 10, 4))).isNull();
    }

    @Test
    void formatDateAndMonth() {
        assertThat(FinanceSupport.format(LocalDate.of(2026, 10, 19))).isEqualTo("2026-10-19");
        assertThat(FinanceSupport.formatMonth(YearMonth.of(2026, 10))).isEqualTo("2026-10");
        assertThat(FinanceSupport.format((LocalDate) null)).isNull();
    }

    @Test
    void recordTypeAndStatusText() {
        assertThat(FinanceConst.recordTypeText("INCOME")).isEqualTo("收入");
        assertThat(FinanceConst.recordTypeText("EXPENSE")).isEqualTo("支出");
        assertThat(FinanceConst.recordTypeText("AUTO")).isNull();
        assertThat(FinanceConst.statusText("NORMAL")).isEqualTo("正常");
        assertThat(FinanceConst.statusText("VOID")).isEqualTo("已作废");
    }

    @Test
    void sourceMappingToAppCodes() {
        assertThat(FinanceConst.sourceTypeApp("FEED_INBOUND")).isEqualTo("INBOUND");
        assertThat(FinanceConst.sourceTypeApp("DRUG_INBOUND")).isEqualTo("DRUG_LOT");
        assertThat(FinanceConst.sourceTypeApp("MANUAL")).isEqualTo("MANUAL");
        assertThat(FinanceConst.sourceLabel("FEED_INBOUND")).isEqualTo("来自饲料入库单");
        assertThat(FinanceConst.sourceLabel("DRUG_INBOUND")).isEqualTo("来自兽药疫苗入库单");
        assertThat(FinanceConst.sourceLabel("SALE_RECEIPT")).isEqualTo("来自出栏收款");
        assertThat(FinanceConst.sourceLabel("MANUAL")).isNull();
        assertThat(FinanceConst.sourceTargetBizType("SALE_RECEIPT")).isEqualTo("SALE_ORDER");
        assertThat(FinanceConst.sourceTargetBizType("FEED_INBOUND")).isEqualTo("INBOUND");
    }

    @Test
    void paymentPricingCostAndConclusionText() {
        assertThat(FinanceConst.paymentStatusText("UNPAID")).isEqualTo("未收款");
        assertThat(FinanceConst.paymentStatusText("PARTIAL")).isEqualTo("部分收款");
        assertThat(FinanceConst.paymentStatusText("PAID")).isEqualTo("已收款");
        assertThat(FinanceConst.paymentStatusText("VOIDED")).isEqualTo("已作废");
        assertThat(FinanceConst.pricingMethodText("BY_WEIGHT")).isEqualTo("按重量");
        assertThat(FinanceConst.pricingMethodText("BY_HEAD")).isEqualTo("按头");
        assertThat(FinanceConst.paymentMethodText("BANK_TRANSFER")).isEqualTo("银行转账");
        assertThat(FinanceConst.paymentMethodText("WECHAT")).isEqualTo("微信");
        // 成本项编码按 V1_10 建表口径：FEED / INTRO / DRUG / OTHER
        assertThat(FinanceConst.costGroupText("FEED")).isEqualTo("饲料");
        assertThat(FinanceConst.costGroupText("INTRO")).isEqualTo("引种");
        assertThat(FinanceConst.costGroupText("DRUG")).isEqualTo("兽药疫苗");
        assertThat(FinanceConst.costGroupText("OTHER")).isEqualTo("其他费用");
        assertThat(FinanceConst.conclusionText("SELL_SOON")).isEqualTo("建议近期出栏");
        assertThat(FinanceConst.conclusionText("KEEP_FEEDING")).isEqualTo("建议继续饲养");
        assertThat(FinanceConst.conclusionText("NOT_SELLABLE")).isEqualTo("暂不可出栏");
    }

    @Test
    void marketPriceUnitTextByPriceType() {
        assertThat(FinanceConst.marketPriceUnitText("SLAUGHTER")).isEqualTo("元/千克");
        assertThat(FinanceConst.marketPriceUnitText("SEEDLING")).isEqualTo("元/头");
    }

    @Test
    void reminderRuleTextMentionsThirdDayAndSevenDayFallback() {
        String text = FinanceConst.reminderRuleText();
        assertThat(text).contains("约定收款日当天").contains("第 3 天").contains("第 7 天");
    }
}
