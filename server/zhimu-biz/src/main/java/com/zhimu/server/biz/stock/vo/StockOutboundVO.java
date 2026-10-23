package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.BatchBriefVO;
import com.zhimu.server.common.vo.PenBriefVO;
import com.zhimu.server.common.vo.QtyVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 出库单（App {@code StockOutboundVO}）：API-M20 / M32 返回、API-M21 详情。 */
@Data
public class StockOutboundVO {

    private long outboundId;
    private String outboundNo;
    private String outType;
    private String outTypeText;
    private String materialType;
    private String materialName;
    private String category;
    private Long feedId;
    private Long drugId;
    private Long drugLotId;
    private String lotNo;
    private String expireDate;
    private BigDecimal inputQty;
    private String inputUnit;
    private QtyVO qty;
    private BigDecimal maxQty;
    private String destination;
    private String destinationText;
    private String targetType;
    private BatchBriefVO batch;
    private PenBriefVO pen;
    private UserBriefVO receiver;
    private String returnTo;
    private String returnReason;
    private String returnReasonText;
    private String remark;
    private String outDate;
    private String status;
    private String statusText;
    @ManagerView
    private BigDecimal unitPrice;
    @ManagerView
    private BigDecimal amount;
    private List<CostAllocationVO.Row> allocations = new ArrayList<>();
    private StockActionsVO actions;
    private UserBriefVO createdBy;
    private String createdAt;
    private Integer version;
}
