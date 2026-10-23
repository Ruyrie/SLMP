package com.zhimu.slmp.stock.model.vo;

import com.zhimu.slmp.common.model.vo.BatchBriefVO;
import com.zhimu.slmp.common.model.vo.PenBriefVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import java.util.List;

/** 出库单（M10 其他出库、M11 领用与退货；API-M20 / M32 返回，API-M21 详情）；字段直接公开，由 Gson 按下划线命名读写。 */
public class StockOutboundVO {
    public long outboundId;
    public String outboundNo;
    public String outType;
    public String outTypeText;
    public String materialType;
    public String materialName;
    public String category;
    public Long feedId;
    public Long drugId;
    public Long drugLotId;
    public String lotNo;
    public String expireDate;
    public BigDecimal inputQty;
    public String inputUnit;
    public QtyVO qty;
    public BigDecimal maxQty;
    public String destination;
    public String destinationText;
    public String targetType;
    public BatchBriefVO batch;
    public PenBriefVO pen;
    public UserBriefVO receiver;
    public String returnTo;
    public String returnReason;
    public String returnReasonText;
    public String remark;
    public String outDate;
    public String status;
    public String statusText;
    public BigDecimal unitPrice;
    public BigDecimal amount;
    public List<CostAllocationVO.Row> allocations;
    public StockActionsVO actions;
    public UserBriefVO createdBy;
    public String createdAt;
    public Integer version;
}
