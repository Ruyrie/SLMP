package com.zhimu.slmp.finance.model.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.finance.model.FinanceConst;

import java.util.Objects;

/**
 * API-E03 收支流水查询参数（E04）：不可变，变化即重建分页。page、size 由分页数据源传入。
 */
public final class FinanceRecordQuery {

    @NonNull
    private final String month;
    @NonNull
    private final String type;
    /** 逗号分隔的一级分类 ID（E02 图例行带入、筛选面板多选） */
    @Nullable
    private final String categoryIds;
    /** 分类 Chip 显示的名称（只在客户端使用） */
    @Nullable
    private final String categoryLabel;
    @Nullable
    private final Long batchId;
    @Nullable
    private final String batchLabel;
    @Nullable
    private final Boolean hasVoucher;
    @Nullable
    private final String keyword;
    @Nullable
    private final String scope;

    public FinanceRecordQuery(@NonNull String month, @NonNull String type, @Nullable String categoryIds,
                              @Nullable String categoryLabel, @Nullable Long batchId, @Nullable String batchLabel,
                              @Nullable Boolean hasVoucher, @Nullable String keyword, @Nullable String scope) {
        this.month = month;
        this.type = type;
        this.categoryIds = categoryIds;
        this.categoryLabel = categoryLabel;
        this.batchId = batchId;
        this.batchLabel = batchLabel;
        this.hasVoucher = hasVoucher;
        this.keyword = keyword;
        this.scope = scope;
    }

    @NonNull
    public static FinanceRecordQuery of(@NonNull String month) {
        return new FinanceRecordQuery(month, FinanceConst.TYPE_ALL, null, null, null, null, null, null, null);
    }

    @NonNull
    public FinanceRecordQuery withMonth(@NonNull String m) {
        return new FinanceRecordQuery(m, type, categoryIds, categoryLabel, batchId, batchLabel, hasVoucher, keyword,
                scope);
    }

    @NonNull
    public FinanceRecordQuery withType(@NonNull String t) {
        return new FinanceRecordQuery(month, t, categoryIds, categoryLabel, batchId, batchLabel, hasVoucher, keyword,
                scope);
    }

    @NonNull
    public FinanceRecordQuery withCategories(@Nullable String ids, @Nullable String label) {
        return new FinanceRecordQuery(month, type, ids, label, batchId, batchLabel, hasVoucher, keyword, scope);
    }

    @NonNull
    public FinanceRecordQuery withBatch(@Nullable Long id, @Nullable String label) {
        return new FinanceRecordQuery(month, type, categoryIds, categoryLabel, id, label, hasVoucher, keyword, scope);
    }

    @NonNull
    public FinanceRecordQuery withHasVoucher(@Nullable Boolean v) {
        return new FinanceRecordQuery(month, type, categoryIds, categoryLabel, batchId, batchLabel, v, keyword, scope);
    }

    @NonNull
    public FinanceRecordQuery withKeyword(@Nullable String k) {
        String kw = k == null || k.trim().isEmpty() ? null : k.trim();
        return new FinanceRecordQuery(month, type, categoryIds, categoryLabel, batchId, batchLabel, hasVoucher, kw,
                scope);
    }

    @NonNull
    public FinanceRecordQuery withScope(@Nullable String s) {
        return new FinanceRecordQuery(month, type, categoryIds, categoryLabel, batchId, batchLabel, hasVoucher,
                keyword, s);
    }

    @NonNull
    public String getMonth() {
        return month;
    }

    @NonNull
    public String getType() {
        return type;
    }

    @Nullable
    public String getCategoryIds() {
        return categoryIds;
    }

    @Nullable
    public String getCategoryLabel() {
        return categoryLabel;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getBatchLabel() {
        return batchLabel;
    }

    @Nullable
    public Boolean getHasVoucher() {
        return hasVoucher;
    }

    @Nullable
    public String getKeyword() {
        return keyword;
    }

    @Nullable
    public String getScope() {
        return scope;
    }

    /** 是否带了类型以外的筛选（空状态文案区分“当月没有记录”与“筛选无结果”） */
    public boolean isFiltered() {
        return !FinanceConst.TYPE_ALL.equals(type) || categoryIds != null || batchId != null || hasVoucher != null
                || keyword != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FinanceRecordQuery)) {
            return false;
        }
        FinanceRecordQuery q = (FinanceRecordQuery) o;
        return month.equals(q.month) && type.equals(q.type) && Objects.equals(categoryIds, q.categoryIds)
                && Objects.equals(batchId, q.batchId) && Objects.equals(hasVoucher, q.hasVoucher)
                && Objects.equals(keyword, q.keyword) && Objects.equals(scope, q.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(month, type, categoryIds, batchId, hasVoucher, keyword, scope);
    }
}
