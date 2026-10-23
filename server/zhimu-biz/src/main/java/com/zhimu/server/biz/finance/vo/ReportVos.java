package com.zhimu.server.biz.finance.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.FarmBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 经营报表（API-E37–E42）响应结构。 */
public final class ReportVos {

    private ReportVos() {
    }

    @Data
    public static class ReportPeriodVO {
        private List<ReportPeriod> periods = new ArrayList<>();
        private String nextGenerationDate;
    }

    @Data
    public static class ReportPeriod {
        private String periodStart;
        private String periodEnd;
        private String label;
        private String generatedAt;
    }

    @Data
    public static class BusinessReportVO {
        private ReportPeriodInfo period;
        private String scope;
        private List<FarmBriefVO> includedFarms = new ArrayList<>();
        private List<String> staffFarmNames = new ArrayList<>();
        private Boolean empty;
        private DataCheck dataCheck;
        private ReportMetrics metrics;
        private BatchProfits batchProfits;
        private List<RunningBatch> runningBatches = new ArrayList<>();
    }

    @Data
    public static class ReportPeriodInfo {
        private String start;
        private String end;
        private String label;
        private String toDate;
    }

    @Data
    public static class DataCheck {
        private Boolean hasIssue;
        private Integer batchCount;
        private Integer moreCount;
        private List<CheckItem> items = new ArrayList<>();
    }

    @Data
    public static class CheckItem {
        private Long batchId;
        private String batchNo;
        private String penName;
        private String farmName;
        private Integer expected;
        private Integer actual;
        private Integer diff;
    }

    @Data
    public static class ReportMetrics {
        private List<StockMetric> stock = new ArrayList<>();
        private Mortality mortality;
        private Feed feed;
        private Balance balance;
    }

    @Data
    public static class StockMetric {
        private String countUnit;
        private String countUnitText;
        private Integer begin;
        private Integer end;
        private Integer change;
    }

    @Data
    public static class Mortality {
        private BigDecimal rate;
        private BigDecimal changePoint;
        private Integer deathCount;
        private Boolean comparable;
    }

    @Data
    public static class Feed {
        @ManagerView
        private BigDecimal kg;
        private BigDecimal changeRate;
        private Boolean comparable;
    }

    @Data
    public static class Balance {
        @ManagerView
        private BigDecimal amount;
        private BigDecimal changeRate;
        private Boolean comparable;
    }

    @Data
    public static class BatchProfits {
        @ManagerView
        private BigDecimal profitTotal;
        private List<ProfitItem> items = new ArrayList<>();
    }

    @Data
    public static class ProfitItem {
        private Long batchId;
        private String batchNo;
        private String farmName;
        @ManagerView
        private BigDecimal profit;
    }

    @Data
    public static class RunningBatch {
        private Long batchId;
        private String batchNo;
        private String penName;
        @ManagerView
        private BigDecimal costTotal;
    }

    @Data
    public static class ReportSummaryVO {
        private String status;
        private String highlight;
        private String problem;
        private String suggestion;
        private String generatedAt;
        private Boolean outdated;
    }

    @Data
    public static class ReportTrendVO {
        private String metric;
        private List<TrendPoint> points = new ArrayList<>();
        private List<StockEvent> events = new ArrayList<>();
        private StockEquation equation;
    }

    @Data
    public static class TrendPoint {
        private String date;
        @ManagerView
        private BigDecimal value;
    }

    @Data
    public static class StockEvent {
        private String date;
        private String type;
        private Integer count;
    }

    @Data
    public static class StockEquation {
        private Integer begin;
        private Integer entry;
        private Integer death;
        private Integer sold;
        private Integer end;
    }

    @Data
    public static class ReportExportVO {
        private Long exportId;
        private Long fileId;
        private String fileName;
        private String downloadUrl;
        private String expireAt;
    }
}
