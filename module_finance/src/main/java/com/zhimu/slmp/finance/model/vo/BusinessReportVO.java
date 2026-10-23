package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.FarmBriefVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-E38 经营报表
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class BusinessReportVO {

    private ReportPeriodInfo period;
    @Nullable
    private String scope;
    private List<FarmBriefVO> includedFarms;
    private List<String> staffFarmNames;
    private boolean empty;
    @Nullable
    private DataCheck dataCheck;
    @Nullable
    private ReportMetrics metrics;
    @Nullable
    private BatchProfits batchProfits;
    private List<RunningBatch> runningBatches;

    public ReportPeriodInfo getPeriod() {
        return period;
    }

    @Nullable
    public String getScope() {
        return scope;
    }

    @NonNull
    public List<FarmBriefVO> getIncludedFarms() {
        return includedFarms == null ? java.util.Collections.emptyList() : includedFarms;
    }

    @NonNull
    public List<String> getStaffFarmNames() {
        return staffFarmNames == null ? java.util.Collections.emptyList() : staffFarmNames;
    }

    public boolean isEmpty() {
        return empty;
    }

    @Nullable
    public DataCheck getDataCheck() {
        return dataCheck;
    }

    @Nullable
    public ReportMetrics getMetrics() {
        return metrics;
    }

    @Nullable
    public BatchProfits getBatchProfits() {
        return batchProfits;
    }

    @NonNull
    public List<RunningBatch> getRunningBatches() {
        return runningBatches == null ? java.util.Collections.emptyList() : runningBatches;
    }

    /** 统计期 */
    public static class ReportPeriodInfo {

        private String start;
        private String end;
        private String label;
        @Nullable
        private String toDate;

        public String getStart() {
            return start;
        }

        public String getEnd() {
            return end;
        }

        public String getLabel() {
            return label;
        }

        @Nullable
        public String getToDate() {
            return toDate;
        }
    }

    /** 数据待核对（最多 3 项） */
    public static class DataCheck {

        private boolean hasIssue;
        private int batchCount;
        private int moreCount;
        private List<CheckItem> items;

        public boolean isHasIssue() {
            return hasIssue;
        }

        public int getBatchCount() {
            return batchCount;
        }

        public int getMoreCount() {
            return moreCount;
        }

        @NonNull
        public List<CheckItem> getItems() {
            return items == null ? java.util.Collections.emptyList() : items;
        }
    }

    /** 差异批次 */
    public static class CheckItem {

        private long batchId;
        private String batchNo;
        @Nullable
        private String penName;
        @Nullable
        private String farmName;
        private int expected;
        private int actual;
        private int diff;

        public long getBatchId() {
            return batchId;
        }

        public String getBatchNo() {
            return batchNo;
        }

        @Nullable
        public String getPenName() {
            return penName;
        }

        @Nullable
        public String getFarmName() {
            return farmName;
        }

        public int getExpected() {
            return expected;
        }

        public int getActual() {
            return actual;
        }

        public int getDiff() {
            return diff;
        }
    }

    /** 四项指标 */
    public static class ReportMetrics {

        private List<StockMetric> stock;
        @Nullable
        private Mortality mortality;
        @Nullable
        private Feed feed;
        @Nullable
        private Balance balance;

        @NonNull
        public List<StockMetric> getStock() {
            return stock == null ? java.util.Collections.emptyList() : stock;
        }

        @Nullable
        public Mortality getMortality() {
            return mortality;
        }

        @Nullable
        public Feed getFeed() {
            return feed;
        }

        @Nullable
        public Balance getBalance() {
            return balance;
        }
    }

    /** 期初、期末存栏（按计数单位） */
    public static class StockMetric {

        private String countUnit;
        private String countUnitText;
        private int begin;
        private int end;
        private int change;

        public String getCountUnit() {
            return countUnit;
        }

        public String getCountUnitText() {
            return countUnitText;
        }

        public int getBegin() {
            return begin;
        }

        public int getEnd() {
            return end;
        }

        public int getChange() {
            return change;
        }
    }

    /** 死亡率 */
    public static class Mortality {

        @Nullable
        private BigDecimal rate;
        /** 较上期变化（比例差，0.004 为 0.4 个百分点） */
        @Nullable
        private BigDecimal changePoint;
        private int deathCount;
        private boolean comparable;

        @Nullable
        public BigDecimal getRate() {
            return rate;
        }

        @Nullable
        public BigDecimal getChangePoint() {
            return changePoint;
        }

        public int getDeathCount() {
            return deathCount;
        }

        public boolean isComparable() {
            return comparable;
        }
    }

    /** 饲料用量 */
    public static class Feed {

        @Nullable
        private BigDecimal kg;
        @Nullable
        private BigDecimal changeRate;
        private boolean comparable;

        @Nullable
        public BigDecimal getKg() {
            return kg;
        }

        @Nullable
        public BigDecimal getChangeRate() {
            return changeRate;
        }

        public boolean isComparable() {
            return comparable;
        }
    }

    /** 收支结余 */
    public static class Balance {

        @Nullable
        private BigDecimal amount;
        @Nullable
        private BigDecimal changeRate;
        private boolean comparable;

        @Nullable
        public BigDecimal getAmount() {
            return amount;
        }

        @Nullable
        public BigDecimal getChangeRate() {
            return changeRate;
        }

        public boolean isComparable() {
            return comparable;
        }
    }

    /** 批次盈亏对比 */
    public static class BatchProfits {

        @Nullable
        private BigDecimal profitTotal;
        private List<ProfitItem> items;

        @Nullable
        public BigDecimal getProfitTotal() {
            return profitTotal;
        }

        @NonNull
        public List<ProfitItem> getItems() {
            return items == null ? java.util.Collections.emptyList() : items;
        }
    }

    /** 已结束批次盈亏 */
    public static class ProfitItem {

        private long batchId;
        private String batchNo;
        @Nullable
        private String farmName;
        private BigDecimal profit;

        public long getBatchId() {
            return batchId;
        }

        public String getBatchNo() {
            return batchNo;
        }

        @Nullable
        public String getFarmName() {
            return farmName;
        }

        public BigDecimal getProfit() {
            return profit;
        }
    }

    /** 在养批次累计成本 */
    public static class RunningBatch {

        private long batchId;
        private String batchNo;
        @Nullable
        private String penName;
        private BigDecimal costTotal;

        public long getBatchId() {
            return batchId;
        }

        public String getBatchNo() {
            return batchNo;
        }

        @Nullable
        public String getPenName() {
            return penName;
        }

        public BigDecimal getCostTotal() {
            return costTotal;
        }
    }
}
