package com.zhimu.slmp.common.service;

import androidx.annotation.Nullable;

/**
 * 待办变化通知：登记完成、忽略待办后由业务组件发出，今日首页与日历据此局部刷新。
 * todoId 未知时为 null（例如直接从登记页提交，没有经过待办）。
 */
public final class TodoChange {

    @Nullable
    private final Long todoId;
    @Nullable
    private final String bizType;
    @Nullable
    private final Long bizId;
    private final long farmId;

    public TodoChange(@Nullable Long todoId, @Nullable String bizType, @Nullable Long bizId, long farmId) {
        this.todoId = todoId;
        this.bizType = bizType;
        this.bizId = bizId;
        this.farmId = farmId;
    }

    @Nullable
    public Long getTodoId() {
        return todoId;
    }

    /** BIZ_TYPE，例如 FEED_RECORD、BATCH_DEATH */
    @Nullable
    public String getBizType() {
        return bizType;
    }

    @Nullable
    public Long getBizId() {
        return bizId;
    }

    public long getFarmId() {
        return farmId;
    }
}
