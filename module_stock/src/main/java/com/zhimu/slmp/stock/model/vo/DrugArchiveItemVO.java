package com.zhimu.slmp.stock.model.vo;

import java.math.BigDecimal;
import com.zhimu.slmp.common.model.vo.QtyVO;

/** API-M34 药品档案列表项（M05“药品档案”面板、M09 已建档比对）；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugArchiveItemVO {
    public long drugId;
    public Long catalogId;
    public String name;
    public String category;
    public QtyVO usable;
    public BigDecimal minStock;
    public boolean lowStock;
    public boolean hasStock;
    public boolean banned;
}
