package com.zhimu.server.admin.audit.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** ADM-R01 的队列统计行（UNION ALL 查询结果）。 */
@Getter
@Setter
public class QueueStatRow {

    private String queue;
    private Long count;
    private LocalDateTime earliestAt;
    private Long overdueCount;
}
