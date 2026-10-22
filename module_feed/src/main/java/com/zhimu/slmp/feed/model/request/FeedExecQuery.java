package com.zhimu.slmp.feed.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * API-G14–G16 共用查询参数：batch_id 与 all_batches 二选一；range 为 LAST_7 / LAST_30 / BATCH / CUSTOM，
 * CUSTOM 时必填 start_date、end_date（结束不晚于今天，跨度不超过 366 天）。
 */
public final class FeedExecQuery {

    public static final String RANGE_LAST_7 = "LAST_7";
    public static final String RANGE_LAST_30 = "LAST_30";
    public static final String RANGE_BATCH = "BATCH";
    public static final String RANGE_CUSTOM = "CUSTOM";

    @Nullable
    public final Long batchId;
    public final boolean allBatches;
    @NonNull
    public final String range;
    @Nullable
    public final String startDate;
    @Nullable
    public final String endDate;

    public FeedExecQuery(@Nullable Long batchId, @NonNull String range, @Nullable String startDate,
                         @Nullable String endDate) {
        this.batchId = batchId;
        this.allBatches = batchId == null;
        this.range = range;
        boolean custom = RANGE_CUSTOM.equals(range);
        this.startDate = custom ? startDate : null;
        this.endDate = custom ? endDate : null;
    }

    /** 查询参数（不含 page、size、date） */
    @NonNull
    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        if (batchId != null) {
            m.put("batch_id", String.valueOf(batchId));
        } else {
            m.put("all_batches", "true");
        }
        m.put("range", range);
        if (startDate != null) {
            m.put("start_date", startDate);
        }
        if (endDate != null) {
            m.put("end_date", endDate);
        }
        return m;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FeedExecQuery)) {
            return false;
        }
        FeedExecQuery q = (FeedExecQuery) o;
        return allBatches == q.allBatches && Objects.equals(batchId, q.batchId) && range.equals(q.range)
                && Objects.equals(startDate, q.startDate) && Objects.equals(endDate, q.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId, allBatches, range, startDate, endDate);
    }
}
