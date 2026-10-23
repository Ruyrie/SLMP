package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.QtyVO;
import lombok.Data;

import java.math.BigDecimal;

/** 出入库流水（App {@code StockFlowItemVO}，API-M13 / M28，M01 最近出入库）。 */
@Data
public class StockFlowItemVO {

    private long flowId;
    private String flowType;
    private String flowTypeText;
    private String direction;
    private String materialType;
    private String materialName;
    private String lotNo;
    private QtyVO qty;
    private BigDecimal stockQtyAfter;
    private String relatedDesc;
    private Long batchId;
    private String batchNo;
    private String penName;
    private String operatorName;
    private String occurAt;
    private String bizDate;
    private String status;
    private String statusText;
    private String bizType;
    private Long bizId;
    @ManagerView
    private BigDecimal unitPrice;
    @ManagerView
    private BigDecimal amount;
}
