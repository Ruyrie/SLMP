package com.zhimu.slmp.stock.model.vo;

import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import com.zhimu.slmp.common.model.vo.QtyVO;

/** 报损 / 报废（API-M18、M31 返回，API-M19 详情）；字段直接公开，由 Gson 按下划线命名读写。 */
public class StockLossVO {
    public long lossId;
    public String lossType;
    public String lossTypeText;
    public String materialName;
    public Long drugLotId;
    public String lotNo;
    public QtyVO qty;
    public QtyVO remaining;
    public String reason;
    public String reasonText;
    public String remark;
    public String lossDate;
    public String status;
    public BigDecimal unitPrice;
    public BigDecimal amount;
    public StockActionsVO actions;
    public UserBriefVO createdBy;
    public String createdAt;
    public Integer version;
}
