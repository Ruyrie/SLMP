package com.zhimu.server.admin.audit.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-R11 修改申请审核详情（原记录、修改前后、影响范围、系统校验、历史申请）。 */
@Data
@Builder
public class ChangeRequestAdminDetailVO {

    private Long id;
    private String requestNo;
    private String status;
    private String statusText;
    private Integer version;
    private String changeType;
    private String changeTypeText;
    private Header header;
    @Builder.Default
    private List<Kv> original = new ArrayList<>();
    @Builder.Default
    private List<CompareRowVO> compare = new ArrayList<>();
    private String reason;
    private Impact impact;
    private Validation validation;
    @Builder.Default
    private List<RecordHistory> recordHistory = new ArrayList<>();
    private Long batchId;
    private String batchNo;
    private String notifyText;
    private ReviewVO review;

    @Data
    @Builder
    public static class Header {
        private String title;
        private String farmName;
        private String submitterName;
        private String submitterRoleText;
        private LocalDateTime submittedAt;
        private String submitChannelText;
    }

    @Data
    @Builder
    public static class Kv {
        private String label;
        private String value;
        private Integer span;
    }

    @Data
    @Builder
    public static class Impact {
        private String note;
        @Builder.Default
        private List<ImpactRow> rows = new ArrayList<>();
        @Builder.Default
        private List<String> recalcTags = new ArrayList<>();
    }

    @Data
    @Builder
    public static class ImpactRow {
        private String item;
        private String current;
        private String after;
        private Boolean isLoss;
    }

    @Data
    @Builder
    public static class Validation {
        private String level;
        private String text;
        private String errorCode;
    }

    @Data
    @Builder
    public static class RecordHistory {
        private String fieldName;
        private String beforeDisplay;
        private String afterDisplay;
        private String reviewerName;
        private LocalDateTime reviewedAt;
        private String statusText;
        private String recalcSummary;
    }
}
