package com.zhimu.server.biz.feeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 称重记录（t_weigh_record）：同批次同日只能一条，标准体重与偏离为快照。 */
@Getter
@Setter
@FarmScoped
@TableName("t_weigh_record")
public class WeighRecord extends AuditEntity {

    private Long farmId;
    private Long batchId;
    private LocalDate weighDate;
    private Integer dayAge;
    private Integer stockCount;
    private String mode;
    private Integer sampleCount;
    private BigDecimal totalWeight;
    private BigDecimal avgWeight;
    private Integer suggestedSample;
    private Boolean sampleLow;
    private Long stdVersionId;
    private BigDecimal stdWeight;
    private BigDecimal allowedDeviation;
    private BigDecimal deviationRatio;
    private String growthResult;
    private String gainBaseType;
    private LocalDate gainBaseDate;
    private BigDecimal gainBaseWeight;
    private BigDecimal dailyGain;
    private String requestId;
    private Long updatedBy;

    @Version
    private Integer version;
}
