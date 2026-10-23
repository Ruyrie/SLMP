package com.zhimu.server.biz.today.request;

import lombok.Getter;
import lombok.Setter;

/** 修改提醒设置（API-T11）：每次只带变化的一个字段，其余为 null。 */
@Getter
@Setter
public class ReminderSettingPatchRequest {

    private Boolean todoRemindEnabled;
    private Boolean digestEnabled;
    private Boolean quietEnabled;
    private String digestTime;
    private String quietStart;
    private String quietEnd;

    /** 是否至少带了一个字段 */
    public boolean isEmpty() {
        return todoRemindEnabled == null && digestEnabled == null && quietEnabled == null
                && digestTime == null && quietStart == null && quietEnd == null;
    }
}
