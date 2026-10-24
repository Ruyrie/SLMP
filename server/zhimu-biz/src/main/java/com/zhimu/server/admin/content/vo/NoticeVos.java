package com.zhimu.server.admin.content.vo;

import com.zhimu.server.common.vo.FileVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** WA-C01 通知发布的响应模型（ADM-C01–C10）。 */
public final class NoticeVos {

    private NoticeVos() {
    }

    @Data
    public static class SummaryVO {
        private Integer monthSentCount = 0;
        private Integer monthImportantCount = 0;
        private Integer monthRevokedCount = 0;
        private Integer scheduledCount = 0;
        private Integer draftCount = 0;
        private BigDecimal avgReadRate30d;
        private LocalDateTime nextScheduledAt;
    }

    @Data
    public static class RowVO {
        private Long id;
        private String title;
        private Integer imageCount = 0;
        private Integer contentLength = 0;
        private String level;
        private String levelText;
        private String scopeType;
        private String scopeText;
        private String conditionText;
        private Integer accountCount = 0;
        private String status;
        private String statusText;
        private LocalDateTime scheduledAt;
        private LocalDateTime sentAt;
        private LocalDateTime revokedAt;
        private LocalDateTime canceledAt;
        private BigDecimal readRate;
        private Integer readBeforeRevoke;
        private String creatorName;
        private LocalDateTime updatedAt;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DetailVO extends RowVO {
        private String content;
        private FileVO image;
        private String sendMode;
        private String sendModeText;
        private List<String> roles;
        private List<String> provinceCodes;
        private List<Long> categoryIds;
        private List<FarmBrief> farms;
        private Integer estAccountCount = 0;
        private Integer estFarmCount = 0;
        private Integer deliveredCount;
        private Integer readCount;
        private Integer unreadCount;
        private Integer pushOpenCount;
        private LocalDateTime statAt;
        private List<RoleStat> roleStats = new ArrayList<>();
        private Actor creator;
        private Actor sender;
        private String revokedByName;
        private String revokeReason;
        private String canceledByName;
        private String cancelReason;
        private List<TimelineItem> timeline = new ArrayList<>();
        private Integer version;
    }

    @Data
    public static class FarmBrief {
        private Long farmId;
        private String name;
    }

    @Data
    public static class RoleStat {
        private String role;
        private String roleText;
        private Integer read;
        private Integer delivered;
        private BigDecimal rate;
    }

    @Data
    public static class Actor {
        private String name;
        private LocalDateTime at;
    }

    @Data
    public static class TimelineItem {
        private String action;
        private String actionText;
        private LocalDateTime time;
        private String adminName;
        private String desc;
    }

    @Data
    public static class AudienceVO {
        private Integer accountCount = 0;
        private Integer farmCount = 0;
    }

    @Data
    public static class NoticeVO {
        private Long id;
        private String status;
        private String statusText;
        private LocalDateTime scheduledAt;
        private LocalDateTime sentAt;
        private Integer estAccountCount;
        private Integer deliveredCount;
        private LocalDateTime revokedAt;
        private Integer readBeforeRevoke;
        private Integer version;
    }

    /** 导出结果（ADM-C10）：同步写入对象存储，返回可直接下载的临时地址。 */
    @Data
    public static class ExportVO {
        private Long fileId;
        private String fileName;
        private String status;
        private String url;
        private Integer rowCount;
    }
}
