package com.zhimu.slmp.stock.model.vo;

import java.math.BigDecimal;
import com.zhimu.slmp.common.model.vo.QtyVO;

/** API-M35 药品下拉（M08 INBOUND、M11 OUTBOUND）；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugOptionVO {
    public long drugId;
    public String name;
    public String category;
    public String spec;
    public String unit;
    public String packUnit;
    public BigDecimal packSize;
    public QtyVO usable;
}
