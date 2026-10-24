package com.zhimu.server.admin.audit.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** ADM-R07 资料变更详情（变更前后对比、两处地图、资质图前后对比、养殖场概况）。 */
@Data
@Builder
public class FarmChangeDetailVO {

    private Long changeId;
    private Long farmId;
    private String farmName;
    private Integer changeNo;
    private String status;
    private String statusText;
    private Integer version;
    private LocalDateTime submittedAt;
    private String ownerName;
    private String ownerPhone;
    private String remark;
    @Builder.Default
    private List<CompareRowVO> compare = new ArrayList<>();
    private Locations locations;
    @Builder.Default
    private List<AdminImageVO> imagesCurrent = new ArrayList<>();
    @Builder.Default
    private List<AdminImageVO> imagesAfter = new ArrayList<>();
    private FarmOverview farmOverview;
    private String approveTemplate;
    @Builder.Default
    private List<ReasonTemplateVO> rejectTemplates = new ArrayList<>();
    private ReviewVO review;

    @Data
    @Builder
    public static class Locations {
        private Point current;
        private Point applied;
        private BigDecimal distanceKm;
    }

    @Data
    @Builder
    public static class Point {
        private BigDecimal longitude;
        private BigDecimal latitude;
        private String address;
        private Integer locAccuracy;
        private String sourceText;
    }

    @Data
    @Builder
    public static class FarmOverview {
        private LocalDateTime approvedAt;
        private String categoryName;
        private Integer currentCount;
        private String countUnitText;
        private Integer memberCount;
        private String farmStatusText;
    }
}
