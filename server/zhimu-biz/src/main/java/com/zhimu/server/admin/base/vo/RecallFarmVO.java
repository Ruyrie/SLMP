package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** ADM-D38 批号停用处理进度行。 */
@Getter
@Setter
public class RecallFarmVO {

    private Long farmId;
    private String farmName;
    private BigDecimal qtyAtRecall;
    private String qtyUnit;
    private String qtyUnitText;
    /** RECALL_SCRAP_STATUS：UNSCRAPPED / PARTIAL / SCRAPPED */
    private String scrapStatus;
    private String scrapStatusText;
    private BigDecimal scrappedQty;
    private LocalDate scrappedAt;
}
