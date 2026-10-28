package com.zhimu.server.biz.inspect.vo;

import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** API-I19 诊断详情。status：PENDING / DIAGNOSED / HANDLED / CLOSED；ai_state：DIAGNOSING / DONE / UNAVAILABLE / FAILED。 */
@Data
public class DiagnosisDetailVO {

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

    @Data
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
        /** 数量单位文字：头 / 只（按批次品类；批次已结束时为空，客户端按“头”） */
        private String unitText;
        private BigDecimal sickRate;
        private List<String> symptomNames;
        private String description;
        private List<MediaItemVO> media;
        private int mediaCount;
    }

    @Data
    public static class DiagnosisStep {
        private String code;
        private String state;
        private String time;
        private String desc;
    }

    @Data
    public static class FailureInfo {
        private String failedAt;
        private String reasonText;
        private String triggerType;
    }

    @Data
    public static class MedicationPrefill {
        private long penId;
        private long batchId;
        private long reportId;
        private Long diagnosisId;
        private String reason;
    }

    @Data
    public static class DiagnosisPermissions {
        private boolean canHandle;
        private boolean canModifyHandle;
        private boolean canRediagnose;
        private boolean canViewInspection;
        private boolean canViewRevisions;
        private boolean canConvertMedication;
    }
}
