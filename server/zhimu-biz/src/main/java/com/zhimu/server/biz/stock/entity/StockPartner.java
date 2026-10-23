package com.zhimu.server.biz.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 库存往来单位（t_stock_partner）：最近供应商 / 退回对象。 */
@Getter
@Setter
@FarmScoped
@TableName("t_stock_partner")
public class StockPartner extends BaseEntity {

    private Long farmId;
    /** SUPPLIER / RETURN_TO */
    private String kind;
    private String name;
    private Integer useCount;
    private LocalDateTime lastUsedAt;
}
