package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

/**
 * 删除消息结果（API-T17；客户端暂定类名）。
 */
public class MessageDeleteResult {

    @Nullable
    private String deletedAt;
    @Nullable
    private String undoDeadline;

    @Nullable
    public String getDeletedAt() {
        return deletedAt;
    }

    @Nullable
    public String getUndoDeadline() {
        return undoDeadline;
    }
}
