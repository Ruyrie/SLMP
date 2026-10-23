package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.QtyVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** API-M26 批号详情（App {@code DrugLotDetailVO}）：在列表项之上加库存金额、均价、首次入库、停用信息与 actions。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DrugLotDetailVO extends DrugLotItemVO {

    @ManagerView
    private BigDecimal stockAmount;
    @ManagerView
    private BigDecimal avgPrice;
    @ManagerView
    private BigDecimal packPrice;
    private FirstInbound firstInbound;
    private int inboundCount;
    private boolean hasOutbound;
    private boolean drugBanned;
    private StockActionsVO actions;
    private String recalledAt;
    private String recallOperatorText;
    private String recallReason;
    private Long recallMessageId;
    private Integer stockVersion;

    @Data
    public static class FirstInbound {
        private long inboundId;
        private String inboundDate;
        private QtyVO qty;
        @ManagerView
        private BigDecimal unitPrice;
    }
}
