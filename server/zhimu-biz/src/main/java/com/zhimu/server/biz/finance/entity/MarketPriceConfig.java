package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 行情筛选发布配置（t_market_price_config，全局表，管理端-02 维护）。 */
@Getter
@Setter
@TableName("t_market_price_config")
public class MarketPriceConfig extends BaseEntity {

    private String provinceCode;
    private String provinceName;
    private Long categoryId;
    private Long breedId;
    private String priceType;
    private String status;
    private Integer sortNo;
}
