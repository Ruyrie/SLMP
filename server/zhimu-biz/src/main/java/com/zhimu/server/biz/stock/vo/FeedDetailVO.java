package com.zhimu.server.biz.stock.vo;

import lombok.Data;

import java.math.BigDecimal;

/** API-M04 饲料档案详情（App {@code FeedDetailVO}）。 */
@Data
public class FeedDetailVO {

    private long feedId;
    private String name;
    private String feedTypeCode;
    private String feedTypeText;
    private String spec;
    private String unit;
    private String unitText;
    private String packUnit;
    private BigDecimal packSize;
    private Long categoryId;
    private String categoryText;
    private BigDecimal minStock;
    private Integer warnDays;
    private boolean unitLocked;
    private Integer editVersion;
    private String updatedAt;
    private String updatedByName;
}
