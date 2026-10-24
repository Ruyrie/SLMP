package com.zhimu.server.admin.audit.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 单个审核队列的待审核数（ADM-R01 items）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueCountVO {

    /** FARM_AUDIT / FARM_CHANGE / CHANGE_REQUEST / ACCOUNT_RECOVERY */
    private String queue;
    private Integer count;
    private LocalDateTime earliestAt;
    private Integer overdueCount;
}
