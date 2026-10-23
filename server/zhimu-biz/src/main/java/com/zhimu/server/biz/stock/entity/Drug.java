package com.zhimu.server.biz.stock.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 兽药疫苗档案（t_drug，模块09 2.3.2）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_drug")
public class Drug extends AuditEntity {

    private Long farmId;
    /** DRUG_CATEGORY：VET_DRUG / VACCINE / DISINFECTANT */
    private String category;
    private Long catalogId;
    private String name;
    private String spec;
    /** 记账单位：DOSE / G / ML / L */
    private String unit;
    private String packUnit;
    private BigDecimal packSize;
    /** 库存下限（记账单位） */
    private BigDecimal minStock;
    /** 下限录入单位：PACK / MEASURE */
    private String minStockUnit;
    private Boolean isLowStock;
    private LocalDateTime lowStockAt;
    private Boolean isUnitLocked;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
    private Long updatedBy;
}
