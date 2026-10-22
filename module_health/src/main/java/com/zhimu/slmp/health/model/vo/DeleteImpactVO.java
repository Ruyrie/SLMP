package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-D25 删除影响预判：impact 为 ADVANCE / NO_CHANGE_TODAY / NO_CHANGE_EARLIER / LOCKED。
 */
public class DeleteImpactVO {

    @Nullable
    private String impact;
    @Nullable
    private String recordTitle;
    @Nullable
    private String notifyName;
    /** MANAGER / OWNER */
    @Nullable
    private String notifyRole;
    @Nullable
    private String releaseDateBefore;
    @Nullable
    private String releaseDateAfter;

    @Nullable
    public String getImpact() {
        return impact;
    }

    @Nullable
    public String getRecordTitle() {
        return recordTitle;
    }

    @Nullable
    public String getNotifyName() {
        return notifyName;
    }

    @Nullable
    public String getNotifyRole() {
        return notifyRole;
    }

    @Nullable
    public String getReleaseDateBefore() {
        return releaseDateBefore;
    }

    @Nullable
    public String getReleaseDateAfter() {
        return releaseDateAfter;
    }
}
