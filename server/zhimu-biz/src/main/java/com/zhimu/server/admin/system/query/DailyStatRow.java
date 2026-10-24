package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 趋势日值（t_platform_daily_stat 或实时聚合）。 */
@Getter
@Setter
public class DailyStatRow {

    private LocalDate statDate;
    private Long value;
}
