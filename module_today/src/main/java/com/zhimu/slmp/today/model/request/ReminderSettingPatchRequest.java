package com.zhimu.slmp.today.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 修改提醒设置（API-T11）：每次只带变化的一个字段，其余为 null 不序列化。
 */
public class ReminderSettingPatchRequest {

    public static final String FIELD_TODO_REMIND = "todo_remind_enabled";
    public static final String FIELD_DIGEST = "digest_enabled";
    public static final String FIELD_QUIET = "quiet_enabled";
    public static final String FIELD_DIGEST_TIME = "digest_time";
    public static final String FIELD_QUIET_START = "quiet_start";
    public static final String FIELD_QUIET_END = "quiet_end";

    @Nullable
    private Boolean todoRemindEnabled;
    @Nullable
    private Boolean digestEnabled;
    @Nullable
    private Boolean quietEnabled;
    @Nullable
    private String digestTime;
    @Nullable
    private String quietStart;
    @Nullable
    private String quietEnd;

    /** 开关字段 */
    @NonNull
    public static ReminderSettingPatchRequest ofSwitch(@NonNull String field, boolean value) {
        ReminderSettingPatchRequest r = new ReminderSettingPatchRequest();
        switch (field) {
            case FIELD_TODO_REMIND:
                r.todoRemindEnabled = value;
                break;
            case FIELD_DIGEST:
                r.digestEnabled = value;
                break;
            case FIELD_QUIET:
                r.quietEnabled = value;
                break;
            default:
                throw new IllegalArgumentException("不是开关字段：" + field);
        }
        return r;
    }

    /** 时间字段，hhmm 为 HH:mm */
    @NonNull
    public static ReminderSettingPatchRequest ofTime(@NonNull String field, @NonNull String hhmm) {
        ReminderSettingPatchRequest r = new ReminderSettingPatchRequest();
        switch (field) {
            case FIELD_DIGEST_TIME:
                r.digestTime = hhmm;
                break;
            case FIELD_QUIET_START:
                r.quietStart = hhmm;
                break;
            case FIELD_QUIET_END:
                r.quietEnd = hhmm;
                break;
            default:
                throw new IllegalArgumentException("不是时间字段：" + field);
        }
        return r;
    }

    @Nullable
    public Boolean getTodoRemindEnabled() {
        return todoRemindEnabled;
    }

    @Nullable
    public Boolean getDigestEnabled() {
        return digestEnabled;
    }

    @Nullable
    public Boolean getQuietEnabled() {
        return quietEnabled;
    }

    @Nullable
    public String getDigestTime() {
        return digestTime;
    }

    @Nullable
    public String getQuietStart() {
        return quietStart;
    }

    @Nullable
    public String getQuietEnd() {
        return quietEnd;
    }
}
