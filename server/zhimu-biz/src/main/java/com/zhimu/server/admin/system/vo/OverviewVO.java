package com.zhimu.server.admin.system.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-S06 平台概览。各块独立计算，失败的块名写入 failed_blocks，前端只替换该卡片。 */
@Data
@Builder
public class OverviewVO {

    private LocalDateTime asOf;
    private Pending pending;
    private FarmStat farmStat;
    private UserStat userStat;
    private ActiveStat activeStat;
    @Builder.Default
    private List<CategoryDist> categoryDist = new ArrayList<>();
    private MonthAudit monthAudit;
    @Builder.Default
    private List<RecentOp> recentOps = new ArrayList<>();
    @Builder.Default
    private List<String> failedBlocks = new ArrayList<>();

    @Data
    @Builder
    public static class Pending {
        private Integer total;
        private Integer overdueCount;
        @Builder.Default
        private List<PendingItem> items = new ArrayList<>();
    }

    @Data
    @Builder
    public static class PendingItem {
        /** FARM_AUDIT / FARM_CHANGE / CHANGE_REQUEST / ACCOUNT_RECOVERY */
        private String type;
        private String typeText;
        private Integer count;
        private LocalDateTime earliestAt;
        private Integer overdueCount;
        private Integer sellableDateCount;
        private String pageCode;
        private Boolean canHandle;
    }

    @Data
    @Builder
    public static class FarmStat {
        private Integer total;
        private Integer approved;
        private Integer normal;
        private Integer disabled;
        private Integer frozen;
        private Integer pending;
        private Integer rejected;
    }

    @Data
    @Builder
    public static class UserStat {
        private Integer total;
        private Integer normal;
        private Integer disabled;
        private Integer newThisMonth;
    }

    @Data
    @Builder
    public static class ActiveStat {
        private LocalDate rangeStart;
        private LocalDate rangeEnd;
        private Integer users7d;
        private BigDecimal users7dRatio;
        private Integer usersToday;
        private Integer farms7d;
        private BigDecimal farms7dRatio;
    }

    @Data
    @Builder
    public static class CategoryDist {
        private Long categoryId;
        private String categoryName;
        private String species;
        private String speciesText;
        private Integer count;
        private BigDecimal ratio;
    }

    @Data
    @Builder
    public static class MonthAudit {
        private LocalDate rangeStart;
        private LocalDate rangeEnd;
        @Builder.Default
        private List<MonthAuditItem> items = new ArrayList<>();
    }

    @Data
    @Builder
    public static class MonthAuditItem {
        private String type;
        private String typeText;
        private Integer approved;
        private Integer rejected;
        private BigDecimal avgHandleHours;
    }

    @Data
    @Builder
    public static class RecentOp {
        private Long logId;
        private String operatorName;
        private String opTypeText;
        private String opActionText;
        private String targetName;
        private String reason;
        private LocalDateTime createdAt;
    }
}
