package com.zhimu.slmp.stock.model.vo;

import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import com.zhimu.slmp.common.model.vo.QtyVO;

/** 入库单（API-M08 / M30 返回，API-M09 详情；M03、M08 只读与修改）；字段直接公开，由 Gson 按下划线命名读写。 */
public class StockInboundVO {
    public long inboundId;
    public String inboundNo;
    public String materialType;
    public String materialName;
    public String category;
    public String categoryText;
    public Long feedId;
    public Long drugId;
    public Long drugLotId;
    public String lotNo;
    public BigDecimal inputQty;
    public String inputUnit;
    public QtyVO qty;
    public BigDecimal unitPrice;
    public BigDecimal amount;
    public String supplierName;
    public String productionDate;
    public String shelfLifeDate;
    public String expireDate;
    public String inboundDate;
    public boolean syncFinance;
    public Long financeRecordId;
    public String status;
    public String statusText;
    public boolean hasOutbound;
    public Integer nearExpiryDays;
    public StockActionsVO actions;
    public UserBriefVO createdBy;
    public String createdAt;
    public Integer version;
}
