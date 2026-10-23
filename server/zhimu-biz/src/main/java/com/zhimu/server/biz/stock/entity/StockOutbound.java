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

/** 手工出库单（t_stock_outbound，模块09 2.3.5）：饲料其他出库、药品领用与退货。 */
@Getter
@Setter
@FarmScoped
@TableName("t_stock_outbound")
public class StockOutbound extends AuditEntity {

    private Long farmId;
    private String outboundNo;
    /** FEED / DRUG */
    private String materialType;
    /** OTHER_OUT / USE / RETURN */
    private String outType;
    private Long feedId;
    private Long drugId;
    private Long drugLotId;
    private BigDecimal inputQty;
    private String inputUnit;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private BigDecimal maxQty;
    private String destination;
    private String targetType;
    private Long batchId;
    private Long penId;
    private Long receiverId;
    private String returnTo;
    private String returnReason;
    private String remark;
    private LocalDate outDate;
    private Long flowId;
    private String status;
    private LocalDateTime voidAt;
    private Long voidBy;
    private String clientRequestId;
    @Version
    private Integer version;
    private Long updatedBy;
}
