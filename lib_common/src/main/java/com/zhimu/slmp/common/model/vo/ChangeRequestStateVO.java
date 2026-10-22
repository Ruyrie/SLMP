package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 可修改记录的修改状态：是否还能直接修改、直接修改截止时间、待审核的修改申请。
 */
public class ChangeRequestStateVO {

    private boolean canEdit;
    @Nullable
    private String editDeadline;
    @Nullable
    private Long pendingRequestId;

    public boolean isCanEdit() {
        return canEdit;
    }

    @Nullable
    public String getEditDeadline() {
        return editDeadline;
    }

    @Nullable
    public Long getPendingRequestId() {
        return pendingRequestId;
    }
}
