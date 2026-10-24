package com.zhimu.server.admin.audit.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GeoDistanceUtilTest {

    @Test
    void 赤峰附近两点距离保留一位小数() {
        BigDecimal distance = GeoDistanceUtil.distanceKm(
                new BigDecimal("42.3025000"), new BigDecimal("118.8516000"),
                new BigDecimal("42.3100000"), new BigDecimal("118.8700000"));
        assertThat(distance).isEqualByComparingTo("1.7");
    }

    @Test
    void 坐标为空返回null() {
        assertThat(GeoDistanceUtil.distanceKm(null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
                .isNull();
    }
}
