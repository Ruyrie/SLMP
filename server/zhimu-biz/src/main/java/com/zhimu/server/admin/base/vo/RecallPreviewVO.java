package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** ADM-D36 停用批号前预览：有效期、有库存的养殖场数与合计。 */
@Getter
@Setter
public class RecallPreviewVO {

    private LocalDate expireDate;
    private Integer farmCount;
    private BigDecimal totalQty;
    private String qtyUnit;
    private String qtyUnitText;
    private String sampleFarmName;
    private BigDecimal sampleFarmQty;
    /** 是否有养殖场入库过这个批号（结存为 0 也算入库过） */
    private Boolean hasStockRecord;
}
