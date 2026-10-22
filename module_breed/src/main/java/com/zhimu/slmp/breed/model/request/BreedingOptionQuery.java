package com.zhimu.slmp.breed.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 登记下拉候选查询（API-B04）：purpose 为 MATING / FARROWING / WEANING，size 默认 50、不分页。
 */
public final class BreedingOptionQuery {

    public static final int DEFAULT_SIZE = 50;

    @NonNull
    private final String purpose;
    @Nullable
    private final String keyword;
    private final int page;
    private final int size;

    public BreedingOptionQuery(@NonNull String purpose, @Nullable String keyword) {
        this.purpose = purpose;
        String k = keyword == null ? null : keyword.trim();
        this.keyword = k == null || k.isEmpty() ? null : k;
        this.page = 1;
        this.size = DEFAULT_SIZE;
    }

    @NonNull
    public String getPurpose() {
        return purpose;
    }

    @Nullable
    public String getKeyword() {
        return keyword;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
