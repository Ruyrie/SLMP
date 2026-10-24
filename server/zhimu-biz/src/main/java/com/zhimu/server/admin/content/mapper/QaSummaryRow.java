package com.zhimu.server.admin.content.mapper;

import lombok.Data;

import java.math.BigDecimal;

/** 问答指标汇总行。 */
@Data
public class QaSummaryRow {
    private Integer qaCount;
    private Integer askerCount;
    private Integer ratedCount;
    private Integer usefulCount;
    private Integer pendingUselessCount;
    private Integer noBasisCount;
    private BigDecimal usefulRate;
}
