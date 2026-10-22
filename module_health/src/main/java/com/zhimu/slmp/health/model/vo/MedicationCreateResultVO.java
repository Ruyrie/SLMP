package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-D22 登记用药结果：按栏舍登记时每个在栏批次各一条记录（共用 group_no）。
 */
public class MedicationCreateResultVO {

    @Nullable
    private List<Long> recordIds;
    @Nullable
    private String groupNo;
    @Nullable
    private String toast;
    @Nullable
    private List<PerBatch> perBatch;

    @NonNull
    public List<Long> getRecordIds() {
        return recordIds == null ? Collections.emptyList() : recordIds;
    }

    @Nullable
    public String getGroupNo() {
        return groupNo;
    }

    @Nullable
    public String getToast() {
        return toast;
    }

    @NonNull
    public List<PerBatch> getPerBatch() {
        return perBatch == null ? Collections.emptyList() : perBatch;
    }

    /** 各批次新的可出栏日期 */
    public static class PerBatch {

        @Nullable
        private String batchNo;
        @Nullable
        private String releaseDate;

        @Nullable
        public String getBatchNo() {
            return batchNo;
        }

        @Nullable
        public String getReleaseDate() {
            return releaseDate;
        }
    }
}
