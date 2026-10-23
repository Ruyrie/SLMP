package com.zhimu.server.biz.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 入库单（t_stock_inbound，模块09 2.3.4）：饲料与药品共用，药品带批号。 */
@Getter
@Setter
@FarmScoped
@TableName("t_stock_inbound")
public class StockInbound extends AuditEntity {

    private Long farmId;
    private String inboundNo;
    /** FEED / DRUG */
    private String materialType;
    private Long feedId;
    private Long drugId;
    private Long drugLotId;
    private String lotNo;
    private BigDecimal inputQty;
    /** MEASURE / PACK */
    private String inputUnit;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal measurePrice;
    private BigDecimal amount;
    private LocalDate productionDate;
    private LocalDate shelfLifeDate;
    private LocalDate expireDate;
    private String supplierName;
    private LocalDate inboundDate;
    private Boolean isSyncFinance;
    private Long financeRecordId;
    private Long flowId;
    /** NORMAL / VOID */
    private String status;
    private LocalDateTime voidAt;
    private Long voidBy;
    private String clientRequestId;
    @Version
    private Integer version;
    private Long updatedBy;
}
