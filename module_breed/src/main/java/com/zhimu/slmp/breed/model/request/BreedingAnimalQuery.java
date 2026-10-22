package com.zhimu.slmp.breed.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.breed.model.BreedCodes;

import java.util.Objects;

/**
 * 种畜列表查询（API-B02）：status 为 ANIMAL_FILTER（默认 ALL），keyword 为耳号（空串不传）；App 不传 pen_id。
 * 不可变，作为 B01 列表的筛选条件。
 */
public final class BreedingAnimalQuery {

    @NonNull
    private final String status;
    @Nullable
    private final String keyword;

    public BreedingAnimalQuery(@Nullable String status, @Nullable String keyword) {
        this.status = status == null || status.isEmpty() ? BreedCodes.FILTER_ALL : status;
        String k = keyword == null ? null : keyword.trim();
        this.keyword = k == null || k.isEmpty() ? null : k;
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getKeyword() {
        return keyword;
    }

    @NonNull
    public BreedingAnimalQuery withStatus(@Nullable String s) {
        return new BreedingAnimalQuery(s, keyword);
    }

    @NonNull
    public BreedingAnimalQuery withKeyword(@Nullable String k) {
        return new BreedingAnimalQuery(status, k);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BreedingAnimalQuery)) {
            return false;
        }
        BreedingAnimalQuery q = (BreedingAnimalQuery) o;
        return status.equals(q.status) && Objects.equals(keyword, q.keyword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, keyword);
    }
}
