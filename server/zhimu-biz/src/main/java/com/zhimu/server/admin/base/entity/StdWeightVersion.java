package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 标准体重版本（t_std_weight_version）：草稿/待生效/线上/历史。 */
@Getter
@Setter
@TableName("t_std_weight_version")
public class StdWeightVersion extends BaseAdminEntity {

    private Long breedId;
    /** 草稿为空，品种内从 1 递增 */
    private Integer versionNo;
    /** VERSION_STATUS：DRAFT / SCHEDULED / PUBLISHED / SUPERSEDED */
    private String status;
    /** 草稿为 1，其余为空 */
    private Integer draftFlag;
    private BigDecimal toleranceRate;
    private Integer suggestedWeighTimes;
    /** EFFECTIVE_MODE：IMMEDIATE / ON_DATE */
    private String effectiveMode;
    private LocalDate effectiveDate;
    private LocalDateTime effectiveAt;
    private String updateNote;
    private Integer rollbackFromNo;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime recalculatedAt;
}
