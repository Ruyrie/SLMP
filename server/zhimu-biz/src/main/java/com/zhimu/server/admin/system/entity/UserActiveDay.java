package com.zhimu.server.admin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * 账号日活跃（t_user_active_day，管理端-04 2.3.6）。已鉴权请求实时累加 op_count；
 * UserActivityStatJob 按真实登记记录重算 record_count，并从真实日志补齐历史活跃。
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
