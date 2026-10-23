package com.zhimu.server.admin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * 账号日活跃（t_user_active_day，管理端-04 2.3.6）。由 ActiveStatFlushJob 以绝对值覆盖写入，
 * 可重复执行；保留 400 天。第 2 批 4A 只建表，写入任务随 4B/统计模块。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_user_active_day")
public class UserActiveDay extends BaseEntity {

    private LocalDate statDate;
    private Long userId;
    private Long farmId;
    private Integer opCount;
    private Integer recordCount;
}
