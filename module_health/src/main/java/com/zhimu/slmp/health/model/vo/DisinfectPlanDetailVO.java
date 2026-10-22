package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-D29 消毒计划详情（D07 修改回填）。
 */
public class DisinfectPlanDetailVO extends DisinfectPlanVO {

    private long catalogId;
    @Nullable
    private Long drugId;
    @Nullable
    private List<Pen> pens;

    public long getCatalogId() {
        return catalogId;
    }

    @Nullable
    public Long getDrugId() {
        return drugId;
    }

    @NonNull
    public List<Pen> getPens() {
        return pens == null ? Collections.emptyList() : pens;
    }

    /** 覆盖的栏舍与执行人 */
    public static class Pen {

        private long penId;
        @Nullable
        private String penName;
        @Nullable
        private String executorText;

        public long getPenId() {
            return penId;
        }

        @Nullable
        public String getPenName() {
            return penName;
        }

        @Nullable
        public String getExecutorText() {
            return executorText;
        }
    }
}
