package com.zhimu.server.biz.inspect.query;

import lombok.Data;

import java.math.BigDecimal;

/** 养殖场坐标（只读；模块02 的 FarmBriefVO 没有坐标）。 */
@Data
public class FarmGeoRow {
    private Long farmId;
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
