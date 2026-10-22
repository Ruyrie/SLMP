package com.zhimu.slmp.feed.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** API-G24 查询参数：compare_batch_ids 最多 3 个，逗号拼接；为空时不传。 */
public final class GrowthCurveQuery {

    public static final int MAX_COMPARE = 3;

    @NonNull
    public final List<Long> compareBatchIds;

    public GrowthCurveQuery(@Nullable List<Long> compareBatchIds) {
        List<Long> ids = compareBatchIds == null ? Collections.emptyList() : compareBatchIds;
        this.compareBatchIds = Collections.unmodifiableList(
                new ArrayList<>(ids.subList(0, Math.min(ids.size(), MAX_COMPARE))));
    }

    /** “3009,3002”；没有对比批次时为 null（Retrofit 不传该参数） */
    @Nullable
    public String joined() {
        if (compareBatchIds.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Long id : compareBatchIds) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }
}
