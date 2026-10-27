package com.zhimu.server.biz.herd.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 批次（t_batch）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_batch")
public class Batch extends AuditEntity {

    private Long farmId;
    private String batchNo;
    /** 生成时的北京年月，例如 2610 */
    private String noMonth;
    private Integer noSeq;
    private String qrToken;
    private Boolean traceEnabled;
    private Long categoryId;
    private Long breedId;
    /** BATCH_PURPOSE：COMMERCIAL / BREEDING */
    private String purpose;
    /** BATCH_SOURCE：PURCHASED / SELF_BRED */
    private String source;
    private String sourceRecordType;
    private Long sourceRecordId;
    private Long entryPenId;
    private LocalDate entryDate;
    private Integer entryAgeDays;
    private Integer entryCount;
    private BigDecimal entryAvgWeight;
    private BigDecimal introUnitPrice;
    private Integer currentCount;
    private Integer deathCount;
    private Integer soldCount;
    /** BATCH_STATUS：ACTIVE / ENDED */
    private String status;
    /** BATCH_END_TYPE：ALL_SOLD / ALL_DEAD */
    private String endType;
    private LocalDate endDate;
    private LocalDateTime endedAt;
    private String clientRequestId;

    @Version
    private Integer version;

    @TableLogic
    private Boolean deleted;

    private Long updatedBy;
}
