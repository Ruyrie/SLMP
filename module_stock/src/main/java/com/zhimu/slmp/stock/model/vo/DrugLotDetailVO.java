package com.zhimu.slmp.stock.model.vo;

import java.math.BigDecimal;
import com.zhimu.slmp.common.model.vo.QtyVO;

/**
 * API-M26 批号详情（M06、M07）：在 DrugLotItemVO 之上加库存金额、均价、首次入库、停用信息与 actions；
 * stock_version 为技术准备补充字段（待定事项 1，服务端未列出时为 null）。字段直接公开，由 Gson 按下划线命名读写。
 */
public class DrugLotDetailVO extends DrugLotItemVO {
    public BigDecimal stockAmount;
    public BigDecimal avgPrice;
    public BigDecimal packPrice;
    public FirstInbound firstInbound;
    public int inboundCount;
    public boolean hasOutbound;
    public boolean drugBanned;
    public StockActionsVO actions;
    public String recalledAt;
    public String recallOperatorText;
    public String recallReason;
    public Long recallMessageId;
    public Integer stockVersion;

    public static class FirstInbound {
        public long inboundId;
        public String inboundDate;
        public QtyVO qty;
        public BigDecimal unitPrice;
    }
}
