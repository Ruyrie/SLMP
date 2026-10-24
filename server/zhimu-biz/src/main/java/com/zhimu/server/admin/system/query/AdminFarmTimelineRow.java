package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** ADM-S16 平台记录时间线（注册审核、资料变更、冻结解冻日志的合并）。 */
@Getter
@Setter
public class AdminFarmTimelineRow {

    private String event;
    private LocalDateTime time;
    private String actor;
    private String tag;
    private String description;
}
