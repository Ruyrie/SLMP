package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 标准体重点：API-G19 std_points 为 day_age、std_weight；API-G24 std_points 另带偏差带 lower、upper。
 */
public class StdPointVO {

    private Integer dayAge;
    private BigDecimal stdWeight;
    private BigDecimal lower;
    private BigDecimal upper;

    public StdPointVO() {
    }

    public StdPointVO(int dayAge, @Nullable BigDecimal stdWeight) {
        this.dayAge = dayAge;
        this.stdWeight = stdWeight;
    }

    public int getDayAge() {
        return dayAge == null ? 0 : dayAge;
    }

    @Nullable
    public BigDecimal getStdWeight() {
        return stdWeight;
    }

    @Nullable
    public BigDecimal getLower() {
        return lower;
    }

    @Nullable
    public BigDecimal getUpper() {
        return upper;
    }
}
