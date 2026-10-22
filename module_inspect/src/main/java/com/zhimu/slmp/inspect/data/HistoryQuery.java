package com.zhimu.slmp.inspect.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * I04 巡检历史的筛选条件（不可变）：filter 为 ALL / ABNORMAL / OVERDUE / BACKFILL / OFF_SITE，locate_date 为定位日期。
 */
public final class HistoryQuery {

    public static final String ALL = "ALL";
    public static final String ABNORMAL = "ABNORMAL";
    public static final String OVERDUE = "OVERDUE";
    public static final String BACKFILL = "BACKFILL";
    public static final String OFF_SITE = "OFF_SITE";

    @NonNull
    public final String filter;
    @Nullable
    public final String startDate;
    @Nullable
    public final String endDate;
    @Nullable
    public final String locateDate;

    public HistoryQuery(@NonNull String filter, @Nullable String startDate, @Nullable String endDate,
                        @Nullable String locateDate) {
        this.filter = filter;
        this.startDate = startDate;
        this.endDate = endDate;
        this.locateDate = locateDate;
    }

    @NonNull
    public HistoryQuery withFilter(@NonNull String f) {
        return new HistoryQuery(f, startDate, endDate, locateDate);
    }

    @NonNull
    public HistoryQuery withLocateDate(@Nullable String date) {
        return new HistoryQuery(filter, startDate, endDate, date);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HistoryQuery)) {
            return false;
        }
        HistoryQuery q = (HistoryQuery) o;
        return filter.equals(q.filter) && Objects.equals(startDate, q.startDate) && Objects.equals(endDate, q.endDate)
                && Objects.equals(locateDate, q.locateDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filter, startDate, endDate, locateDate);
    }
}
