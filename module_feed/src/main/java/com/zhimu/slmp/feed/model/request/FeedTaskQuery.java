package com.zhimu.slmp.feed.model.request;

import androidx.annotation.Nullable;

import java.util.Objects;

/** API-G02 今日任务查询：status（PENDING / DONE / OVERDUE，空为全部）、pen_id（空为全部栏舍）；page、size 由分页源提供。 */
public final class FeedTaskQuery {

    @Nullable
    public final String status;
    @Nullable
    public final Long penId;

    public FeedTaskQuery(@Nullable String status, @Nullable Long penId) {
        this.status = status;
        this.penId = penId;
    }

    public boolean isFiltered() {
        return status != null || penId != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FeedTaskQuery)) {
            return false;
        }
        FeedTaskQuery q = (FeedTaskQuery) o;
        return Objects.equals(status, q.status) && Objects.equals(penId, q.penId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, penId);
    }
}
