package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.vo.QtyVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** API-M36 药品档案详情（App {@code DrugDetailVO}）。 */
@Data
public class DrugDetailVO {

    private long drugId;
    private String category;
    private Long catalogId;
    private String name;
    private String catalogSourceText;
    private String spec;
    private String unit;
    private String packUnit;
    private List<String> packUnitOptions = new ArrayList<>();
    private BigDecimal packSize;
    private boolean packSizeLocked;
    private BigDecimal minStock;
    private QtyVO usable;
    private boolean unitLocked;
    private boolean banned;
    private Integer nearDays;
    private Integer version;
    private String updatedAt;
    private String updatedByName;
}
