package com.zhimu.server.biz.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

/** 库存设置（t_stock_setting，模块09 2.3.12）：每场一行，无记录时按默认 30 天返回。 */
@Getter
@Setter
@FarmScoped
@TableName("t_stock_setting")
public class StockSetting extends AuditEntity {

    private Long farmId;
    private Integer nearDaysVet;
    private Integer nearDaysVaccine;
    private Integer nearDaysDisinfectant;
    @Version
    private Integer version;
    private Long updatedBy;
}
