package com.zhimu.server.biz.stock.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 饲料档案与结存（t_feed，模块09 2.3.1）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_feed")
public class Feed extends AuditEntity {

    private Long farmId;
    private String name;
    private String feedTypeCode;
    private String spec;
    private String unit;
    private String packUnit;
    private BigDecimal packSize;
    private Long categoryId;
    private BigDecimal minStock;
    private Integer warnDays;
    /** 结存（千克） */
    private BigDecimal stockQty;
    private BigDecimal stockAmount;
    /** 移动加权平均成本（元/千克） */
    private BigDecimal avgPrice;
    @TableField("usage_7d_qty")
    private BigDecimal usage7dQty;
    private Integer usageDays;
    private BigDecimal dailyUseQty;
    private String remainingBasis;
    private Integer remainingDays;
    private LocalDate statDate;
    private Boolean isLowStock;
    private String lowStockReason;
    private LocalDateTime lowStockAt;
    private Boolean isUnitLocked;
    /** 结存版本，每次结存变化 +1 */
    @Version
    private Integer version;
    /** 档案编辑乐观锁 */
    private Integer editVersion;
    @TableLogic
    private Integer deleted;
    private Long updatedBy;
}
