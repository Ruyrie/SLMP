package com.zhimu.server.admin.system.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** ADM-S07 养殖场分布聚合（PROVINCE / CITY / DISTRICT / POINT）。 */
@Data
@Builder
public class FarmMapVO {

    private String level;
    private String parentCode;
    private Legend legend;
    @Builder.Default
    private List<Cluster> clusters = new ArrayList<>();
    @Builder.Default
    private List<Point> points = new ArrayList<>();

    @Data
    @Builder
    public static class Legend {
        private Integer approved;
        private Integer pending;
        private Integer frozen;
    }

    @Data
    @Builder
    public static class Cluster {
        private String regionCode;
        private String regionName;
        private BigDecimal lng;
        private BigDecimal lat;
        private Integer approved;
        private Integer pending;
        private Integer frozen;
        private Integer total;
    }

    @Data
    @Builder
    public static class Point {
        private Long farmId;
        private String name;
        private BigDecimal lng;
        private BigDecimal lat;
        private String farmStatus;
    }
}
