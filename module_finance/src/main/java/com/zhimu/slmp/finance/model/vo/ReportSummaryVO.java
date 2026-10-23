package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

/**
 * API-E40、E41 AI 分析摘要
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class ReportSummaryVO {

    /** GENERATING / SUCCESS / FAILED */
    private String status;
    @Nullable
    private String highlight;
    @Nullable
    private String problem;
    @Nullable
    private String suggestion;
    @Nullable
    private String generatedAt;
    private boolean outdated;

    public String getStatus() {
        return status;
    }

    @Nullable
    public String getHighlight() {
        return highlight;
    }

    @Nullable
    public String getProblem() {
        return problem;
    }

    @Nullable
    public String getSuggestion() {
        return suggestion;
    }

    @Nullable
    public String getGeneratedAt() {
        return generatedAt;
    }

    public boolean isOutdated() {
        return outdated;
    }
}
