package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.MediaItemVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * API-I19 诊断详情（I06、I07）。status：PENDING / DIAGNOSED / HANDLED / CLOSED；
 * ai_state：DIAGNOSING / DONE / UNAVAILABLE / FAILED。测试直连时由客户端用 AI 回答替换 diagnosis 与状态（见 setter）。
 */
public class DiagnosisDetailVO {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DIAGNOSED = "DIAGNOSED";
    public static final String STATUS_HANDLED = "HANDLED";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String AI_DIAGNOSING = "DIAGNOSING";
    public static final String AI_DONE = "DONE";
    public static final String AI_UNAVAILABLE = "UNAVAILABLE";
    public static final String AI_FAILED = "FAILED";

    private long reportId;
    private int version;
    private String status;
    private String statusText;
    private String aiState;
    private String aiStateText;
    private DiagnosisReport report;
    private List<DiagnosisStep> steps;
    private DiagnosisResultVO diagnosis;
    private HandleVO handle;
    private FailureInfo lastFailure;
    private String noDiagnosisText;
    private List<String> notifiedNames;
    private MedicationPrefill medicationPrefill;
    private DiagnosisPermissions permissions;

    public long getReportId() { return reportId; }

    public int getVersion() { return version; }

    public String getStatus() { return status; }

    public String getStatusText() { return statusText; }

    public String getAiState() { return aiState; }

    public String getAiStateText() { return aiStateText; }

    public DiagnosisReport getReport() { return report; }

    @NonNull
    public List<DiagnosisStep> getSteps() { return steps == null ? Collections.emptyList() : steps; }

    @Nullable
    public DiagnosisResultVO getDiagnosis() { return diagnosis; }

    @Nullable
    public HandleVO getHandle() { return handle; }

    @Nullable
    public FailureInfo getLastFailure() { return lastFailure; }

    @Nullable
    public String getNoDiagnosisText() { return noDiagnosisText; }

    @NonNull
    public List<String> getNotifiedNames() { return notifiedNames == null ? Collections.emptyList() : notifiedNames; }

    @Nullable
    public MedicationPrefill getMedicationPrefill() { return medicationPrefill; }

    @NonNull
    public DiagnosisPermissions getPermissions() {
        return permissions == null ? new DiagnosisPermissions() : permissions;
    }

    public boolean isClosed() { return STATUS_CLOSED.equals(status); }

    public boolean isDiagnosing() { return AI_DIAGNOSING.equals(aiState); }

    // ===== 测试直连：用 AI 回答替换诊断结果 =====

    public void applyDirectDiagnosis(@NonNull DiagnosisResultVO result, @NonNull String time) {
        this.diagnosis = result;
        this.aiState = AI_DONE;
        this.aiStateText = "已完成";
        if (STATUS_PENDING.equals(status) || status == null) {
            this.status = STATUS_DIAGNOSED;
            this.statusText = "已诊断";
        }
        this.lastFailure = null;
        this.noDiagnosisText = null;
        List<DiagnosisStep> list = new ArrayList<>();
        list.add(DiagnosisStep.of(STATUS_PENDING, DiagnosisStep.DONE, report == null ? null : report.reportedAt));
        list.add(DiagnosisStep.of(STATUS_DIAGNOSED, STATUS_DIAGNOSED.equals(this.status)
                ? DiagnosisStep.CURRENT : DiagnosisStep.DONE, time));
        list.add(DiagnosisStep.of(STATUS_HANDLED, STATUS_HANDLED.equals(this.status) ? DiagnosisStep.CURRENT
                : STATUS_CLOSED.equals(this.status) ? DiagnosisStep.DONE : DiagnosisStep.TODO, null));
        list.add(DiagnosisStep.of(STATUS_CLOSED, STATUS_CLOSED.equals(this.status) ? DiagnosisStep.DONE
                : DiagnosisStep.TODO, null));
        this.steps = list;
    }

    /** 测试直连：用上报页填写的内容替换上报信息（Mock 的上报信息是固定的） */
    public void setReport(@NonNull DiagnosisReport report) {
        this.report = report;
    }

    public static class DiagnosisReport {
        private long penId;
        private String penName;
        private long batchId;
        private String batchNo;
        private UserBriefVO reporter;
        private String reportedAt;
        private String source;
        private Long inspectionId;
        private String inspectionRegisteredAt;
        private int sickCount;
        private int stockSnapshot;
        private BigDecimal sickRate;
        private List<String> symptomNames;
        private String description;
        private List<MediaItemVO> media;
        private int mediaCount;

        public long getPenId() { return penId; }

        public String getPenName() { return penName; }

        public long getBatchId() { return batchId; }

        public String getBatchNo() { return batchNo; }

        @Nullable
        public UserBriefVO getReporter() { return reporter; }

        public String getReportedAt() { return reportedAt; }

        public String getSource() { return source; }

        @Nullable
        public Long getInspectionId() { return inspectionId; }

        @Nullable
        public String getInspectionRegisteredAt() { return inspectionRegisteredAt; }

        public int getSickCount() { return sickCount; }

        public int getStockSnapshot() { return stockSnapshot; }

        @Nullable
        public BigDecimal getSickRate() { return sickRate; }

        @NonNull
        public List<String> getSymptomNames() { return symptomNames == null ? Collections.emptyList() : symptomNames; }

        @Nullable
        public String getDescription() { return description; }

        @NonNull
        public List<MediaItemVO> getMedia() { return media == null ? Collections.emptyList() : media; }

        public int getMediaCount() { return mediaCount; }
    }

    public static class DiagnosisStep {
        public static final String DONE = "DONE";
        public static final String CURRENT = "CURRENT";
        public static final String TODO = "TODO";

        private String code;
        private String state;
        private String time;
        private String desc;

        @NonNull
        static DiagnosisStep of(@NonNull String code, @NonNull String state, @Nullable String time) {
            DiagnosisStep s = new DiagnosisStep();
            s.code = code;
            s.state = state;
            s.time = time;
            return s;
        }

        public String getCode() { return code; }

        public String getState() { return state; }

        @Nullable
        public String getTime() { return time; }

        @Nullable
        public String getDesc() { return desc; }
    }

    public static class FailureInfo {
        private String failedAt;
        private String reasonText;
        private String triggerType;

        public String getFailedAt() { return failedAt; }

        public String getReasonText() { return reasonText; }

        public String getTriggerType() { return triggerType; }
    }

    public static class MedicationPrefill {
        private long penId;
        private long batchId;
        private long reportId;
        private Long diagnosisId;
        private String reason;

        public long getPenId() { return penId; }

        public long getBatchId() { return batchId; }

        public long getReportId() { return reportId; }

        @Nullable
        public Long getDiagnosisId() { return diagnosisId; }

        public String getReason() { return reason; }
    }

    public static class DiagnosisPermissions {
        private boolean canHandle;
        private boolean canModifyHandle;
        private boolean canRediagnose;
        private boolean canViewInspection;
        private boolean canViewRevisions;
        private boolean canConvertMedication;

        public boolean isCanHandle() { return canHandle; }

        public boolean isCanModifyHandle() { return canModifyHandle; }

        public boolean isCanRediagnose() { return canRediagnose; }

        public boolean isCanViewInspection() { return canViewInspection; }

        public boolean isCanViewRevisions() { return canViewRevisions; }

        public boolean isCanConvertMedication() { return canConvertMedication; }
    }
}
