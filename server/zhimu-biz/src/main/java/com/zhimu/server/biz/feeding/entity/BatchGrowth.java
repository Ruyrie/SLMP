package com.zhimu.server.biz.feeding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.BaseEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 批次生长汇总与称重设置（t_batch_growth）：每批次一行。 */
@Getter
@Setter
@FarmScoped
@TableName("t_batch_growth")
public class BatchGrowth extends BaseEntity {

    private Long farmId;
    private Long batchId;
    private Integer weighCyclePerMonth;
    private Boolean cycleCustomized;
    private Long cycleUpdatedBy;
    private LocalDateTime cycleUpdatedAt;
    private Long lastWeighId;
    private LocalDate lastWeighDate;
    private BigDecimal lastAvgWeight;
    private BigDecimal lastDeviation;
    private BigDecimal lastDailyGain;
    private String growthStatus;
    private LocalDateTime growthStatusAt;
    private LocalDate nextWeighDate;
    private Long weighTodoId;
    private LocalDateTime stdRecalcAt;

    @Version
    private Integer version;
}
