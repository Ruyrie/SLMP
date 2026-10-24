package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** ADM-D35 / D37 批号停用通知。 */
@Getter
@Setter
public class DrugLotRecallVO {

    private Long id;
    private Long catalogId;
    private String catalogName;
    private String drugCategory;
    private String drugCategoryText;
    private String lotNo;
    private LocalDate expireDate;
    private String reason;
    private Integer farmCount;
    private BigDecimal totalQty;
    private String qtyUnit;
    private String qtyUnitText;
    private Integer scrappedFarmCount;
    private LocalDateTime recalledAt;
    private String recalledByName;
    /** 仅 ADM-D37 返回：已通知养殖场数 */
    private Integer notifiedFarmCount;
}
