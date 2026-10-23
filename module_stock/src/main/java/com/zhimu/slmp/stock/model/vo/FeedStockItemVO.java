package com.zhimu.slmp.stock.model.vo;

import java.math.BigDecimal;
import com.zhimu.slmp.common.model.vo.QtyVO;

/** API-M02 饲料结存（M01 饲料卡）；字段直接公开，由 Gson 按下划线命名读写。 */
public class FeedStockItemVO {
    public long feedId;
    public String name;
    public String spec;
    public String feedTypeCode;
    public String feedTypeText;
    public String categoryText;
    public QtyVO stock;
    public Integer remainingDays;
    public String remainingBasis;
    public BigDecimal dailyUseQty;
    public BigDecimal usageQty;
    public Integer usageDays;
    public BigDecimal avgPrice;
    public BigDecimal stockAmount;
    public BigDecimal minStock;
    public Integer warnDays;
    public boolean lowStock;
    public String lowStockReason;
    public String lowStockReasonText;
}
