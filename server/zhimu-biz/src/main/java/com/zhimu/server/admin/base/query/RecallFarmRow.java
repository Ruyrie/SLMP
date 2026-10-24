package com.zhimu.server.admin.base.query;

import lombok.Getter;
import lombok.Setter;

/** 批号停用处理进度行（只读，供 ADM-D38 组装）。 */
@Getter
@Setter
public class RecallFarmRow {

    private Long farmId;
    private String farmName;
    private java.math.BigDecimal qtyAtRecall;
    private String qtyUnit;
    private String scrapStatus;
    private java.math.BigDecimal scrappedQty;
    private java.time.LocalDate scrappedAt;
}
