package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 地图聚合行；POINT 层级时 farmId 有值。 */
@Getter
@Setter
public class FarmMapRow {

    private String regionCode;
    private String regionName;
    private BigDecimal lng;
    private BigDecimal lat;
    private Long approved;
    private Long pending;
    private Long frozen;
    private Long farmId;
    private String farmStatus;
}
