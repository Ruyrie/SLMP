package com.zhimu.server.biz.today.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/** 提醒设置（t_reminder_setting，每账号一行，覆盖全部养殖场）。 */
@Getter
@Setter
@TableName("t_reminder_setting")
public class ReminderSetting extends BaseEntity {

    private Long userId;
    private Boolean todoRemindEnabled;
    private Boolean digestEnabled;
    private LocalTime digestTime;
    private Boolean quietEnabled;
    private LocalTime quietStart;
    private LocalTime quietEnd;
}
