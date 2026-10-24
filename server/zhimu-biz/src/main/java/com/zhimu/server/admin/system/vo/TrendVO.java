package com.zhimu.server.admin.system.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** ADM-S09 近 30 天趋势（截至昨天）。 */
@Data
@Builder
public class TrendVO {

    private String metric;
    private String metricText;
    private String unit;
    private LocalDate rangeStart;
    private LocalDate rangeEnd;
    @Builder.Default
    private List<Point> points = new ArrayList<>();
    private Summary summary;

    @Data
    @Builder
    public static class Point {
        private LocalDate date;
        private Integer value;
        private BigDecimal avg7;
    }

    @Data
    @Builder
    public static class Summary {
        private BigDecimal dailyAvg;
        private LocalDate maxDate;
        private Integer maxValue;
        private String holidayText;
        private BigDecimal holidayAvg;
    }
}
