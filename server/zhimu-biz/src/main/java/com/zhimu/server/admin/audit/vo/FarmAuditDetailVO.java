package com.zhimu.server.admin.audit.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-R03 注册审核详情。 */
@Data
@Builder
public class FarmAuditDetailVO {

    private Long auditId;
    private Long farmId;
    private Integer submitNo;
    private String status;
    private String statusText;
    private Integer version;
    private LocalDateTime submittedAt;
    private Owner owner;
    private Info info;
    private LastReject lastReject;
    private String changeSummary;
    @Builder.Default
    private List<AdminImageVO> images = new ArrayList<>();
    private Location location;
    @Builder.Default
    private List<History> history = new ArrayList<>();
    private String approveTemplate;
    @Builder.Default
    private List<ReasonTemplateVO> rejectTemplates = new ArrayList<>();
    private ReviewVO review;

    @Data
    @Builder
    public static class Owner {
        private Long userId;
        private String name;
        private String phone;
        private LocalDateTime registeredAt;
        @Builder.Default
        private List<OtherFarm> otherFarms = new ArrayList<>();
    }

    @Data
    @Builder
    public static class OtherFarm {
        private Long farmId;
        private String name;
        private String auditStatus;
        private String auditStatusText;
        private String categoryName;
        private Boolean current;
    }

    @Data
    @Builder
    public static class Info {
        private String name;
        private String categoryName;
        private Integer scale;
        private String countUnitText;
        private String contactName;
        private String contactPhone;
        private String address;
        private String regionText;
        private String intro;
    }

    @Data
    @Builder
    public static class LastReject {
        private String reason;
        private LocalDateTime auditedAt;
        private String auditorName;
    }

    @Data
    @Builder
    public static class Location {
        private BigDecimal longitude;
        private BigDecimal latitude;
        private Integer locAccuracy;
        private String nearestFarmName;
        private BigDecimal distanceKm;
    }

    @Data
    @Builder
    public static class History {
        private String event;
        private LocalDateTime time;
        private String actor;
        private String desc;
        private String tag;
    }
}
