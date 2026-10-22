package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.Nullable;

/**
 * API-I22 登记 / 修改处理结果的返回：死淘 &gt; 0 时带 death_prompt（提示去 L11）；to_medication 时带 medication_prefill（进入 D04）。
 */
public class HandleSaveResultVO {

    private long reportId;
    private long handleId;
    private String status;
    private boolean closed;
    private int culledCount;
    private DeathPrompt deathPrompt;
    private DiagnosisDetailVO.MedicationPrefill medicationPrefill;
    private int version;

    public long getReportId() { return reportId; }

    public long getHandleId() { return handleId; }

    public String getStatus() { return status; }

    public boolean isClosed() { return closed; }

    public int getCulledCount() { return culledCount; }

    @Nullable
    public DeathPrompt getDeathPrompt() { return deathPrompt; }

    @Nullable
    public DiagnosisDetailVO.MedicationPrefill getMedicationPrefill() { return medicationPrefill; }

    public int getVersion() { return version; }

    public static class DeathPrompt {
        private long penId;
        private long batchId;
        private int culledCount;

        public long getPenId() { return penId; }

        public long getBatchId() { return batchId; }

        public int getCulledCount() { return culledCount; }
    }
}
