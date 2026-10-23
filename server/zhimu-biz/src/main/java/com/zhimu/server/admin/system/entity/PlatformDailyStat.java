package com.zhimu.server.admin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * 平台日统计（t_platform_daily_stat，管理端-04 2.3.7）。概览趋势的数据源，重算时覆盖。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_platform_daily_stat")
public class PlatformDailyStat extends BaseEntity {

    private LocalDate statDate;
    private Integer activeUserCount;
    private Integer newUserCount;
    private Integer productionRecordCount;
    private Integer activeFarmCount;
}
