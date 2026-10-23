package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.vo.QtyVO;
import lombok.Data;

import java.math.BigDecimal;

/** API-M34 药品档案列表项（App {@code DrugArchiveItemVO}）。 */
@Data
public class DrugArchiveItemVO {

    private long drugId;
    private Long catalogId;
    private String name;
    private String category;
    private QtyVO usable;
    private BigDecimal minStock;
    private boolean lowStock;
    private boolean hasStock;
    private boolean banned;
}
