package com.zhimu.server.biz.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 盘点明细（t_stocktake_item，模块09 2.3.8）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_stocktake_item")
public class StocktakeItem extends BaseEntity {

    private Long stocktakeId;
    private Long farmId;
    private Long feedId;
    private BigDecimal bookQty;
    private BigDecimal actualQty;
    private BigDecimal diffQty;
    /** GAIN / LOSS / MATCH */
    private String result;
    private BigDecimal avgPrice;
    private BigDecimal diffAmount;
    private Long flowId;
}
