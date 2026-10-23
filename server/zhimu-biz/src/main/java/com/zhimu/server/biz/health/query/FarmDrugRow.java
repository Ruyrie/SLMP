package com.zhimu.server.biz.health.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 本场药品 / 疫苗 / 消毒剂档案（t_drug）：目录匹配、下拉与单位。 */
@Getter
@Setter
public class FarmDrugRow {
    private Long id;
    private Long catalogId;
    private String category;
    private String name;
    private String spec;
    private String unit;
    private String packUnit;
    private BigDecimal packSize;
    private String status;
}
