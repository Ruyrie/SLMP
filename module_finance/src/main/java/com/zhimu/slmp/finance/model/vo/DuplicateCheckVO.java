package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * API-E09 疑似重复检查
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class DuplicateCheckVO {

    private boolean hit;
    @Nullable
    private Long recordId;
    @Nullable
    private String categoryName;
    @Nullable
    private BigDecimal amount;
    @Nullable
    private String recordDate;
    @Nullable
    private String sourceLabel;

    public boolean isHit() {
        return hit;
    }

    @Nullable
    public Long getRecordId() {
        return recordId;
    }

    @Nullable
    public String getCategoryName() {
        return categoryName;
    }

    @Nullable
    public BigDecimal getAmount() {
        return amount;
    }

    @Nullable
    public String getRecordDate() {
        return recordDate;
    }

    @Nullable
    public String getSourceLabel() {
        return sourceLabel;
    }
}
