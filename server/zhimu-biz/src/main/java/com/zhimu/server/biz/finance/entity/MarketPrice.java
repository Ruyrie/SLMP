package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 行情每日价格（t_market_price，全局表，只读 status = PUBLISHED）。 */
@Getter
@Setter
@TableName("t_market_price")
public class MarketPrice extends BaseEntity {

    private String provinceCode;
    private String provinceName;
    private Long categoryId;
    private Long breedId;
    private String priceType;
    private LocalDate priceDate;
    private BigDecimal price;
    private String priceUnit;
    private String priceUnitText;
    private Boolean corrected;
    private LocalDateTime correctedAt;
    private String status;
    private String source;
}
