package com.zhimu.slmp.stock.model.vo;

import java.math.BigDecimal;

/** 出入库流水（API-M13 / M28，M01 最近出入库）；字段直接公开，由 Gson 按下划线命名读写。 */
public class StockFlowItemVO {
    public long flowId;
    public String flowType;
    public String flowTypeText;
    public String direction;
    public String materialType;
    public String materialName;
    public String lotNo;
    public QtyVO qty;
    public BigDecimal stockQtyAfter;
    public String relatedDesc;
    public Long batchId;
    public String batchNo;
    public String penName;
    public String operatorName;
    public String occurAt;
    public String bizDate;
    public String status;
    public String statusText;
    public String bizType;
    public Long bizId;
    public BigDecimal unitPrice;
    public BigDecimal amount;
}
