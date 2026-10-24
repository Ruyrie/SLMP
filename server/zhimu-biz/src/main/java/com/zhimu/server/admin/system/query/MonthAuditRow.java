package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 本月审核统计的一行（四类队列 UNION ALL）。 */
@Getter
@Setter
public class MonthAuditRow {

    /** FARM_AUDIT / FARM_CHANGE / CHANGE_REQUEST / ACCOUNT_RECOVERY */
    private String type;
    private Long approved;
    private Long rejected;
    private BigDecimal avgHandleHours;
}
