package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 批号停用通知（t_drug_lot_recall）：无状态字段，停用后不能恢复。 */
@Getter
@Setter
@TableName("t_drug_lot_recall")
public class DrugLotRecall extends BaseEntity {

    private Long catalogId;
    /** 批号，去首尾空格后保存 */
    private String lotNo;
    private LocalDate expireDate;
    /** 停用原因，原样显示在用户端 */
    private String reason;
    private Integer farmCount;
    private BigDecimal totalQty;
    private String qtyUnit;
    private LocalDateTime recalledAt;
    private Long recalledBy;
}
