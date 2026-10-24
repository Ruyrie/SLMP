package com.zhimu.server.admin.audit.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** ADM-R06 指标卡聚合行。 */
@Getter
@Setter
public class FarmChangeStatsRow {

    private Long pendingCount;
    private LocalDateTime earliestAt;
    private Long approved30d;
    private Long approvedByName;
    private Long approvedByAddress;
    private Long approvedByQualification;
    private Long rejected30d;
}
