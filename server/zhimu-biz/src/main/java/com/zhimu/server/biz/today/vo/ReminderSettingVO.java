package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

/** 提醒设置（API-T10、T11）。 */
@Getter
@Setter
public class ReminderSettingVO {

    private Boolean todoRemindEnabled;
    private Boolean digestEnabled;
    private String digestTime;
    private Boolean quietEnabled;
    private String quietStart;
    private String quietEnd;
    private Boolean quietEndNextDay;
    private String summaryText;
}
