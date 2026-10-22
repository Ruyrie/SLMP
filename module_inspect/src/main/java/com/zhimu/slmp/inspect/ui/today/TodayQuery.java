package com.zhimu.slmp.inspect.ui.today;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.util.DateFormatter;

import java.util.Objects;

/**
 * I01 清单条件（不可变）：日期与状态（ALL / PENDING / ABNORMAL / DONE）。
 */
public final class TodayQuery {

    public static final String ALL = "ALL";
    public static final String PENDING = "PENDING";
    public static final String ABNORMAL = "ABNORMAL";
    public static final String DONE = "DONE";

    @NonNull
    public final String date;
    @NonNull
    public final String status;

    public TodayQuery(@NonNull String date, @NonNull String status) {
        this.date = date;
        this.status = status;
    }

    public boolean isToday() {
        return date.equals(DateFormatter.todayString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TodayQuery)) {
            return false;
        }
        TodayQuery q = (TodayQuery) o;
        return date.equals(q.date) && status.equals(q.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, status);
    }
}
