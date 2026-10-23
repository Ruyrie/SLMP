package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

/**
 * 支出子类（API-E11、E12）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class SubcategoryVO {

    private long id;
    private String name;
    @Nullable
    private String createdAt;

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }
}
