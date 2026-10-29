package com.zhimu.server.biz.finance.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 给客户端展示的金额文字带千分位（经营概览“未收 88,160 元”）。 */
class FinanceMoneyTextTest {

    @Test
    void groupsThousandsAndKeepsAtMostTwoDecimals() {
        assertThat(FinanceSupport.moneyText(new BigDecimal("88160.00"))).isEqualTo("88,160");
        assertThat(FinanceSupport.moneyText(new BigDecimal("1234.5"))).isEqualTo("1,234.5");
        assertThat(FinanceSupport.moneyText(new BigDecimal("600"))).isEqualTo("600");
        assertThat(FinanceSupport.moneyText(new BigDecimal("0.456"))).isEqualTo("0.46");
        assertThat(FinanceSupport.moneyText(null)).isEqualTo("0");
    }
}
