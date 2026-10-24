package com.zhimu.server.admin.content.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 管理端-02 内容运营的写请求。 */
public final class ContentRequests {

    private ContentRequests() {
    }

    /** ADM-C03 预计送达。 */
    @Data
    public static class AudienceRequest {
        private String scopeType;
        private List<String> roles = new ArrayList<>();
        private List<String> provinceCodes = new ArrayList<>();
        private List<Long> categoryIds = new ArrayList<>();
        private List<Long> farmIds = new ArrayList<>();
    }

    /** ADM-C04 新建、ADM-C05 编辑通知。 */
    @Data
    public static class NoticeSaveRequest {
        /** DRAFT / SCHEDULE / SEND_NOW */
        private String submitAction;
        private String level;
        private String title;
        private String content;
        private Long imageFileId;
        private String scopeType;
        private List<String> roles = new ArrayList<>();
        private List<String> provinceCodes = new ArrayList<>();
        private List<Long> categoryIds = new ArrayList<>();
        private List<Long> farmIds = new ArrayList<>();
        private LocalDateTime scheduledAt;
        private Long copiedFromId;
        private Integer version;
    }

    /** ADM-C07 取消、ADM-C08 撤回。 */
    @Data
    public static class NoticeReasonRequest {
        private String reason;
        private Integer version;
    }

    /** ADM-C14 批量发布。 */
    @Data
    public static class BatchPublishRequest {
        private LocalDate priceDate;
        private String provinceCode;
        private String dataSource;
        private String remark;
        private List<Item> items = new ArrayList<>();

        @Data
        public static class Item {
            private Long configId;
            private BigDecimal price;
            private String priceUnit;
        }
    }

    /** ADM-C17 复核。 */
    @Data
    public static class PriceReviewRequest {
        /** PASS / RETURN */
        private String result;
        private String reviewNote;
        private Long correctionId;
        private Integer version;
    }

    /** ADM-C18 更正历史价格。 */
    @Data
    public static class PriceCorrectionRequest {
        private BigDecimal newPrice;
        private String reason;
        private Integer version;
    }

    /** 当天撤销已发布价格（管理端补充操作）。 */
    @Data
    public static class MarketRevokeRequest {
        private String reason;
        private Integer version;
    }

    /** ADM-C21 保存发布配置（按省份整组保存）。 */
    @Data
    public static class MarketConfigSaveRequest {
        private String provinceCode;
        private List<ConfigItem> configs = new ArrayList<>();
        private String reason;

        @Data
        public static class ConfigItem {
            private Long configId;
            private Long categoryId;
            private Long breedId;
            private String priceType;
            private String defaultUnit;
            private Integer sortNo;
            private Boolean isEnabled;
        }
    }

    /** ADM-C60 发布更正说明。 */
    @Data
    public static class CorrectionPublishRequest {
        private String content;
        private Boolean createTask;
        private Long taskTargetKnowledgeId;
        private String taskTitle;
        private Long assigneeId;
    }

    /** ADM-C61 修改更正说明。 */
    @Data
    public static class CorrectionUpdateRequest {
        private String content;
        private Integer version;
    }

    /** ADM-C62 撤回更正说明。 */
    @Data
    public static class CorrectionWithdrawRequest {
        private String withdrawReason;
        private Integer version;
    }

    /** ADM-C63 标为无需处理（version 选填，用于并发保护）。 */
    @Data
    public static class NoActionRequest {
        private Integer version;
    }

    /** 转为知识补充任务（ADM-C55 的管理端-02 入口；任务表由管理端-02 下篇/知识库模块落地前先发管理端消息）。 */
    @Data
    public static class TaskCreateRequest {
        private String title;
        private String mode;
        private Long targetKnowledgeId;
        private Long categoryId;
        private Long assigneeId;
        private LocalDate dueDate;
        private String points;
        private Integer similarCount;
    }
}
