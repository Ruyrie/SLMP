package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 标准体重日龄点（ADM-D07；草稿点另带线上值、差值与变化标记）。 */
@Data
public class StdWeightPointVO {

    private Integer dayAge;
    private BigDecimal stdWeight;
    private BigDecimal lower;
    private BigDecimal upper;
    /** 与前一点的日增重，首点为空 */
    private BigDecimal dailyGain;
    /** 草稿点：同一日龄的线上体重 */
    private BigDecimal onlineWeight;
    /** 草稿点：std_weight - online_weight */
    private BigDecimal diff;
    /** 草稿点：与线上值不同 */
    private Boolean changed;
}
