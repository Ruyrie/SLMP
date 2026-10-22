package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.MediaItemVO;

import java.util.Collections;
import java.util.List;

/**
 * API-I14 异常上报表单上下文（I05）：source 为 INSPECTION（巡检转上报）/ DIRECT（直接上报）。
 */
public class ReportFormVO {

    public static final String SOURCE_INSPECTION = "INSPECTION";
    public static final String SOURCE_DIRECT = "DIRECT";

    private String source;
    private String sourceText;
    private Long inspectionId;
    private List<ReportPen> pens;
    private ReportPrefill prefill;
    private List<SymptomVO> symptoms;
    private List<String> notifyNames;
    private int maxMediaCount;
    private int maxVideoCount;

    public String getSource() { return source; }

    @Nullable
    public String getSourceText() { return sourceText; }

    @Nullable
    public Long getInspectionId() { return inspectionId; }

    @NonNull
    public List<ReportPen> getPens() { return pens == null ? Collections.emptyList() : pens; }

    @Nullable
    public ReportPrefill getPrefill() { return prefill; }

    @NonNull
    public List<SymptomVO> getSymptoms() { return symptoms == null ? Collections.emptyList() : symptoms; }

    @NonNull
    public List<String> getNotifyNames() { return notifyNames == null ? Collections.emptyList() : notifyNames; }

    public int getMaxMediaCount() { return maxMediaCount > 0 ? maxMediaCount : 6; }

    public int getMaxVideoCount() { return maxVideoCount > 0 ? maxVideoCount : 1; }

    @Nullable
    public ReportPen findPen(@Nullable Long penId) {
        if (penId != null) {
            for (ReportPen p : getPens()) {
                if (p.penId == penId) {
                    return p;
                }
            }
        }
        return null;
    }

    public static class ReportPen {
        private long penId;
        private String penName;
        private List<ReportBatch> batches;

        public long getPenId() { return penId; }

        public String getPenName() { return penName; }

        @NonNull
        public List<ReportBatch> getBatches() { return batches == null ? Collections.emptyList() : batches; }

        @Nullable
        public ReportBatch findBatch(@Nullable Long batchId) {
            if (batchId != null) {
                for (ReportBatch b : getBatches()) {
                    if (b.batchId == batchId) {
                        return b;
                    }
                }
            }
            return null;
        }
    }

    public static class ReportBatch {
        private long batchId;
        private String batchNo;
        private long categoryId;
        private int stockCount;
        /** 品种与日龄（客户端补充，用于 AI 诊断提示词，服务端可不返回） */
        private String breedTitle;
        private Integer dayAge;

        public long getBatchId() { return batchId; }

        public String getBatchNo() { return batchNo; }

        public long getCategoryId() { return categoryId; }

        public int getStockCount() { return stockCount; }

        @Nullable
        public String getBreedTitle() { return breedTitle; }

        @Nullable
        public Integer getDayAge() { return dayAge; }
    }

    public static class ReportPrefill {
        private Long penId;
        private Long batchId;
        private String description;
        private List<MediaItemVO> media;

        @Nullable
        public Long getPenId() { return penId; }

        @Nullable
        public Long getBatchId() { return batchId; }

        @Nullable
        public String getDescription() { return description; }

        @NonNull
        public List<MediaItemVO> getMedia() { return media == null ? Collections.emptyList() : media; }
    }
}
