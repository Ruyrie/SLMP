package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-E39 指标逐日趋势
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class ReportTrendVO {

    private String metric;
    private List<TrendPoint> points;
    private List<StockEvent> events;
    @Nullable
    private StockEquation equation;

    public String getMetric() {
        return metric;
    }

    @NonNull
    public List<TrendPoint> getPoints() {
        return points == null ? java.util.Collections.emptyList() : points;
    }

    @NonNull
    public List<StockEvent> getEvents() {
        return events == null ? java.util.Collections.emptyList() : events;
    }

    @Nullable
    public StockEquation getEquation() {
        return equation;
    }

    /** 趋势点 */
    public static class TrendPoint {

        private String date;
        @Nullable
        private BigDecimal value;

        public String getDate() {
            return date;
        }

        @Nullable
        public BigDecimal getValue() {
            return value;
        }
    }

    /** 出入栏事件（只在 STOCK） */
    public static class StockEvent {

        private String date;
        /** IN / OUT */
        private String type;
        private int count;

        public String getDate() {
            return date;
        }

        public String getType() {
            return type;
        }

        public int getCount() {
            return count;
        }
    }

    /** 核对算式（只在 STOCK） */
    public static class StockEquation {

        private int begin;
        private int entry;
        private int death;
        private int sold;
        private int end;

        public int getBegin() {
            return begin;
        }

        public int getEntry() {
            return entry;
        }

        public int getDeath() {
            return death;
        }

        public int getSold() {
            return sold;
        }

        public int getEnd() {
            return end;
        }
    }
}
