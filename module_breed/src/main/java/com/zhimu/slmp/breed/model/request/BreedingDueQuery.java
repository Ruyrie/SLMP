package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 待产 / 待出雏查询（API-B22、B23）：keyword 为耳号，或批次号、栏舍名（空串不传）；App 不传 pen_id。
 * reloadToken 只用于切换养殖场后强制重建分页（不提交）。
 */
public final class BreedingDueQuery {

    @Nullable
    private final String keyword;
    private final long reloadToken;

    public BreedingDueQuery(@Nullable String keyword, long reloadToken) {
        String k = keyword == null ? null : keyword.trim();
        this.keyword = k == null || k.isEmpty() ? null : k;
        this.reloadToken = reloadToken;
    }

    @Nullable
    public String getKeyword() {
        return keyword;
    }

    public long getReloadToken() {
        return reloadToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BreedingDueQuery)) {
            return false;
        }
        BreedingDueQuery q = (BreedingDueQuery) o;
        return reloadToken == q.reloadToken && Objects.equals(keyword, q.keyword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyword, reloadToken);
    }
}
