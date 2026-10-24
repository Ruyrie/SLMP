package com.zhimu.server.admin.base.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 批号停用列表行（只读，ADM-D35）。 */
@Getter
@Setter
public class DrugLotRecallRow {

    private Long id;
    private Long catalogId;
    private String catalogName;
    private String drugCategory;
    private String lotNo;
    private java.time.LocalDate expireDate;
    private String reason;
    private Integer farmCount;
    private java.math.BigDecimal totalQty;
    private String qtyUnit;
    private LocalDateTime recalledAt;
    private Long recalledBy;
    private Integer scrappedFarmCount;
}
