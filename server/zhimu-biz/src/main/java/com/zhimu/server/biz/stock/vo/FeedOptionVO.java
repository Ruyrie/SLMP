package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.QtyVO;
import lombok.Data;

import java.math.BigDecimal;

/** API-M03 饲料下拉（App {@code FeedOptionVO}）：purpose INBOUND 全部可选，OTHER_OUT 结存为 0 不可选。 */
@Data
public class FeedOptionVO {

    private long feedId;
    private String name;
    private QtyVO stock;
    private Integer remainingDays;
    private boolean lowStock;
    private boolean selectable;
    private String disabledReason;
    @ManagerView
    private BigDecimal avgPrice;
    private Integer stockVersion;
}
