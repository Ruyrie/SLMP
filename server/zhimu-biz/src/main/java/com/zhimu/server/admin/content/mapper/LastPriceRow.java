package com.zhimu.server.admin.content.mapper;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 某组合上一个有数据日期的已发布价。 */
@Data
public class LastPriceRow {
    private String provinceCode;
    private Long categoryId;
    private Long breedId;
    private String priceType;
    private LocalDate priceDate;
    private BigDecimal price;
    private String priceUnit;
    private String priceUnitText;
    private Boolean corrected;
}
