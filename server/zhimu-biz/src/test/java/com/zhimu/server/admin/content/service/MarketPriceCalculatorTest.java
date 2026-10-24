package com.zhimu.server.admin.content.service;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketPriceCalculatorTest {

    private final MarketPriceCalculator calculator = new MarketPriceCalculator();

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:22:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void needReviewOnlyWhenAbsoluteRateGreaterThan20Percent() {
        assertThat(calculator.rate(new BigDecimal("130.00"), new BigDecimal("100.00")))
                .isEqualByComparingTo("0.3000");
        assertThat(calculator.needReview(new BigDecimal("0.2000"))).isFalse();
        assertThat(calculator.needReview(new BigDecimal("-0.2000"))).isFalse();
        assertThat(calculator.needReview(new BigDecimal("0.2001"))).isTrue();
        assertThat(calculator.needReview(new BigDecimal("-0.2001"))).isTrue();
        assertThat(calculator.needReview(null)).isFalse();
    }

    @Test
    void noPreviousPriceMeansNoRate() {
        assertThat(calculator.rate(new BigDecimal("15.60"), null)).isNull();
        assertThat(calculator.rate(new BigDecimal("15.60"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void validatesPricePrecision() {
        assertThat(calculator.validPrice(new BigDecimal("15.60"))).isTrue();
        assertThat(calculator.validPrice(new BigDecimal("15.600"))).isTrue();
        assertThat(calculator.validPrice(new BigDecimal("15.123"))).isFalse();
        assertThat(calculator.validPrice(BigDecimal.ZERO)).isFalse();
        assertThat(calculator.validPrice(new BigDecimal("-1"))).isFalse();
    }

    @Test
    void publishWindowIsTodayAndPreviousThreeDays() {
        calculator.requirePublishableDate(LocalDate.of(2026, 10, 19));
        calculator.requirePublishableDate(LocalDate.of(2026, 10, 16));
        assertThatThrownBy(() -> calculator.requirePublishableDate(LocalDate.of(2026, 10, 20)))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> calculator.requirePublishableDate(LocalDate.of(2026, 10, 15)))
                .isInstanceOf(BizException.class);
    }
}
