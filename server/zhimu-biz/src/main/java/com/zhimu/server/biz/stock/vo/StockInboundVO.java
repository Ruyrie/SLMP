package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.QtyVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.math.BigDecimal;

/** 入库单（App {@code StockInboundVO}）：API-M08 / M30 返回、API-M09 详情。 */
@Data
public class StockInboundVO {

    private long inboundId;
    private String inboundNo;
    private String materialType;
    private String materialName;
    private String category;
    private String categoryText;
    private Long feedId;
    private Long drugId;
    private Long drugLotId;
    private String lotNo;
    private BigDecimal inputQty;
    private String inputUnit;
    private QtyVO qty;
    @ManagerView
    private BigDecimal unitPrice;
    @ManagerView
    private BigDecimal amount;
    private String supplierName;
    private String productionDate;
    private String shelfLifeDate;
    private String expireDate;
    private String inboundDate;
    private boolean syncFinance;
    private Long financeRecordId;
    private String status;
    private String statusText;
    private boolean hasOutbound;
    private Integer nearExpiryDays;
    private StockActionsVO actions;
    private UserBriefVO createdBy;
    private String createdAt;
    private Integer version;
}
