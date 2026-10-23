package com.zhimu.slmp.finance.model.request;

import androidx.annotation.Nullable;

/**
 * API-E11 添加子类 / API-E12 改名（改名时 category_id 置 null）
 * <p>Gson 不序列化 null：可空字段置 null 即不提交。
 */
public class SubcategoryRequest {

    @Nullable
    private final Long categoryId;
    private final String name;

    public SubcategoryRequest(@Nullable Long categoryId, String name) {
        this.categoryId = categoryId;
        this.name = name;
    }

    @Nullable
    public Long getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }
}
