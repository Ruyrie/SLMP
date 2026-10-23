package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.QtyVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.math.BigDecimal;

/** 报损 / 报废（App {@code StockLossVO}）：API-M18、M31 返回，API-M19 详情。 */
@Data
public class StockLossVO {

    private long lossId;
    private String lossType;
    private String lossTypeText;
    private String materialName;
    private Long drugLotId;
    private String lotNo;
    private QtyVO qty;
    private QtyVO remaining;
    private String reason;
    private String reasonText;
    private String remark;
    private String lossDate;
    private String status;
    @ManagerView
    private BigDecimal unitPrice;
    @ManagerView
    private BigDecimal amount;
    private StockActionsVO actions;
    private UserBriefVO createdBy;
    private String createdAt;
    private Integer version;
}
