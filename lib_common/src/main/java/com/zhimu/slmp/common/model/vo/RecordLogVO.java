package com.zhimu.slmp.common.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 记录的修改记录（API-U14）；由修改申请回写的修改带申请编号。
 */
public class RecordLogVO {

    private long id;
    @Nullable
    private UserBriefVO operator;
    @Nullable
    private String opAction;
    @Nullable
    private String opActionText;
    @Nullable
    private List<Change> changes;
    @Nullable
    private String reason;
    @Nullable
    private String changeRequestNo;
    @Nullable
    private String createdAt;

    public long getId() {
        return id;
    }

    @Nullable
    public UserBriefVO getOperator() {
        return operator;
    }

    @Nullable
    public String getOpAction() {
        return opAction;
    }

    @Nullable
    public String getOpActionText() {
        return opActionText;
    }

    @NonNull
    public List<Change> getChanges() {
        return changes == null ? Collections.emptyList() : changes;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    @Nullable
    public String getChangeRequestNo() {
        return changeRequestNo;
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }

    /** 一项字段变化 */
    public static class Change {

        @Nullable
        private String fieldName;
        @Nullable
        private String before;
        @Nullable
        private String after;

        @Nullable
        public String getFieldName() {
            return fieldName;
        }

        @Nullable
        public String getBefore() {
            return before;
        }

        @Nullable
        public String getAfter() {
            return after;
        }
    }
}
