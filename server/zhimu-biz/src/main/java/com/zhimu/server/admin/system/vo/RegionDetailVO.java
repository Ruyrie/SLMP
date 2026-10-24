package com.zhimu.server.admin.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** ADM-S08 地区明细（地图聚合点浮层）。 */
@Data
@Builder
public class RegionDetailVO {

    private String regionCode;
    private String regionName;
    private Integer total;
    private Integer approved;
    private Integer pending;
    private Integer frozen;
    @Builder.Default
    private List<PendingFarm> pendingFarms = new ArrayList<>();

    @Data
    @Builder
    public static class PendingFarm {
        private Long farmId;
        private Long auditId;
        private String farmName;
        private String categoryName;
        private String waitText;
        private Boolean overdue;
        private Integer submitNo;
    }
}
