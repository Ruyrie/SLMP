package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * API-E37 已生成期次
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class ReportPeriodVO {

    private List<ReportPeriod> periods;
    @Nullable
    private String nextGenerationDate;

    @NonNull
    public List<ReportPeriod> getPeriods() {
        return periods == null ? java.util.Collections.emptyList() : periods;
    }

    @Nullable
    public String getNextGenerationDate() {
        return nextGenerationDate;
    }

    /** 期次 */
    public static class ReportPeriod {

        private String periodStart;
        private String periodEnd;
        private String label;
        @Nullable
        private String generatedAt;

        public String getPeriodStart() {
            return periodStart;
        }

        public String getPeriodEnd() {
            return periodEnd;
        }

        public String getLabel() {
            return label;
        }

        @Nullable
        public String getGeneratedAt() {
            return generatedAt;
        }
    }
}
