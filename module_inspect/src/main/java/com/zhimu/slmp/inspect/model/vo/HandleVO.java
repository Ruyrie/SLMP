package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.Nullable;

/**
 * 处理结果（嵌入 API-I19 DiagnosisDetailVO.handle）：ai_match 为 MATCH / NOT_MATCH / NONE。
 */
public class HandleVO {

    public static final String MATCH = "MATCH";
    public static final String NOT_MATCH = "NOT_MATCH";
    public static final String NONE = "NONE";

    private long handleId;
    private String diagnostician;
    private String treatment;
    private boolean usedDrug;
    private boolean medicationConverted;
    private int curedCount;
    private int culledCount;
    private int totalCount;
    private String aiMatch;
    private String aiMatchText;
    private String handledByName;
    private String handledAt;
    private String updatedByName;
    private String updatedAt;
    private boolean hasRevisions;
    private boolean closed;
    private int version;

    public long getHandleId() { return handleId; }

    public String getDiagnostician() { return diagnostician; }

    public String getTreatment() { return treatment; }

    public boolean isUsedDrug() { return usedDrug; }

    public boolean isMedicationConverted() { return medicationConverted; }

    public int getCuredCount() { return curedCount; }

    public int getCulledCount() { return culledCount; }

    public int getTotalCount() { return totalCount; }

    public String getAiMatch() { return aiMatch; }

    public String getAiMatchText() { return aiMatchText; }

    @Nullable
    public String getHandledByName() { return handledByName; }

    @Nullable
    public String getHandledAt() { return handledAt; }

    @Nullable
    public String getUpdatedByName() { return updatedByName; }

    @Nullable
    public String getUpdatedAt() { return updatedAt; }

    public boolean isHasRevisions() { return hasRevisions; }

    public boolean isClosed() { return closed; }

    public int getVersion() { return version; }
}
