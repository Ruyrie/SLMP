package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * API-E10 一级分类与本场子类
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class FinanceCategoryVO {

    private long id;
    /** FEED、DRUG、SALE 时查疑似重复 */
    private String code;
    private String name;
    @Nullable
    private String icon;
    private String direction;
    private int sortNo;
    private boolean autoSource;
    private boolean markOnly;
    private boolean isFallback;
    private List<SubcategoryVO> subcategories;

    public long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getIcon() {
        return icon;
    }

    public String getDirection() {
        return direction;
    }

    public int getSortNo() {
        return sortNo;
    }

    public boolean isAutoSource() {
        return autoSource;
    }

    public boolean isMarkOnly() {
        return markOnly;
    }

    public boolean isFallback() {
        return isFallback;
    }

    @NonNull
    public List<SubcategoryVO> getSubcategories() {
        return subcategories == null ? java.util.Collections.emptyList() : subcategories;
    }
}
