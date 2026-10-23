package com.zhimu.server.biz.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 批次物资成本明细（t_batch_material_cost，模块09 2.3.10）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_batch_material_cost")
public class BatchMaterialCost extends BaseEntity {

    private Long farmId;
    private Long batchId;
    private Long penId;
    private Long flowId;
    private String sourceType;
    private Long sourceId;
    /** FEED / DRUG */
    private String costType;
    private BigDecimal qty;
    private Integer headCount;
    /** 4 位小数，合计为 1 */
    private BigDecimal ratio;
    private BigDecimal amount;
    private LocalDate bizDate;
    private String status;
}
