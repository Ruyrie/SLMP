package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.QtyVO;
import lombok.Data;

import java.math.BigDecimal;

/** API-M02 饲料结存列表项（App {@code FeedStockItemVO}）。 */
@Data
public class FeedStockItemVO {

    private long feedId;
    private String name;
    private String spec;
    private String feedTypeCode;
    private String feedTypeText;
    private String categoryText;
    private QtyVO stock;
    private Integer remainingDays;
    private String remainingBasis;
    private BigDecimal dailyUseQty;
    private BigDecimal usageQty;
    private Integer usageDays;
    @ManagerView
    private BigDecimal avgPrice;
    @ManagerView
    private BigDecimal stockAmount;
    private BigDecimal minStock;
    private Integer warnDays;
    private boolean lowStock;
    private String lowStockReason;
    private String lowStockReasonText;
}
