package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.vo.QtyVO;
import lombok.Data;

import java.math.BigDecimal;

/** API-M35 药品下拉项（App {@code DrugOptionVO}）。 */
@Data
public class DrugOptionVO {

    private long drugId;
    private String name;
    private String category;
    private String spec;
    private String unit;
    private String packUnit;
    private BigDecimal packSize;
    private QtyVO usable;
}
