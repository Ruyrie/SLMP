package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-E01 经营概览（E01）：本月结余、待收款合计、出栏价卡、本月出栏与记账笔数、需要处理
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class BusinessOverviewVO {

    /** 服务端今天 yyyy-MM-dd */
    private String today;
    /** 待确认补录数 */
    private int pendingSupplementCount;
    private MonthBalance monthBalance;
    private ReceivableSummary receivable;
    /** 没有价格时不输出 */
    @Nullable
    private PriceCard priceCard;
    private MonthSales monthSales;
    private MonthRecords monthRecords;
    private Todos todos;

    public String getToday() {
        return today;
    }

    public int getPendingSupplementCount() {
        return pendingSupplementCount;
    }

    public MonthBalance getMonthBalance() {
        return monthBalance;
    }

    public ReceivableSummary getReceivable() {
        return receivable;
    }

    @Nullable
    public PriceCard getPriceCard() {
        return priceCard;
    }

    public MonthSales getMonthSales() {
        return monthSales;
    }

    public MonthRecords getMonthRecords() {
        return monthRecords;
    }

    public Todos getTodos() {
        return todos;
    }

    /** 本月结余 */
    public static class MonthBalance {

        private BigDecimal balanceAmount;
        private BigDecimal income;
        private BigDecimal expenseAmount;
        private BigDecimal prevBalanceAmount;
        /** 较上月同期（比例）；comparable 为假时不输出 */
        @Nullable
        private BigDecimal balanceMomRate;
        private boolean comparable;

        public BigDecimal getBalanceAmount() {
            return balanceAmount;
        }

        public BigDecimal getIncome() {
            return income;
        }

        public BigDecimal getExpenseAmount() {
            return expenseAmount;
        }

        public BigDecimal getPrevBalanceAmount() {
            return prevBalanceAmount;
        }

        @Nullable
        public BigDecimal getBalanceMomRate() {
            return balanceMomRate;
        }

        public boolean isComparable() {
            return comparable;
        }
    }

    /** 主要品类出栏价 */
    public static class PriceCard {

        private String categoryName;
        private String provinceName;
        private BigDecimal price;
        private String priceUnit;
        private String priceUnitText;
        @Nullable
        private BigDecimal changeRate;
        /** UP / DOWN / FLAT */
        @Nullable
        private String direction;
        private String priceDate;
        @Nullable
        private String compareDate;
        @Nullable
        private BigDecimal comparePrice;

        public String getCategoryName() {
            return categoryName;
        }

        public String getProvinceName() {
            return provinceName;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public String getPriceUnit() {
            return priceUnit;
        }

        public String getPriceUnitText() {
            return priceUnitText;
        }

        @Nullable
        public BigDecimal getChangeRate() {
            return changeRate;
        }

        @Nullable
        public String getDirection() {
            return direction;
        }

        public String getPriceDate() {
            return priceDate;
        }

        @Nullable
        public String getCompareDate() {
            return compareDate;
        }

        @Nullable
        public BigDecimal getComparePrice() {
            return comparePrice;
        }
    }

    /** 本月出栏 */
    public static class MonthSales {

        private int orderCount;
        @Nullable
        private BigDecimal saleAmount;

        public int getOrderCount() {
            return orderCount;
        }

        @Nullable
        public BigDecimal getSaleAmount() {
            return saleAmount;
        }
    }

    /** 本月记账 */
    public static class MonthRecords {

        private int total;
        private int autoCount;
        private int manualCount;

        public int getTotal() {
            return total;
        }

        public int getAutoCount() {
            return autoCount;
        }

        public int getManualCount() {
            return manualCount;
        }
    }

    /** 需要处理（最多 3 条） */
    public static class Todos {

        private int total;
        private List<TodoItem> items;

        public int getTotal() {
            return total;
        }

        @NonNull
        public List<TodoItem> getItems() {
            return items == null ? java.util.Collections.emptyList() : items;
        }
    }

    /** 经营类待办 */
    public static class TodoItem {

        private long todoId;
        private String todoType;
        private String title;
        private String subtitle;
        @Nullable
        private String dueDate;
        private boolean overdue;
        @Nullable
        private String tagText;
        /** SALE_ORDER / BATCH */
        @Nullable
        private String bizType;
        @Nullable
        private Long bizId;

        public long getTodoId() {
            return todoId;
        }

        public String getTodoType() {
            return todoType;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        @Nullable
        public String getDueDate() {
            return dueDate;
        }

        public boolean isOverdue() {
            return overdue;
        }

        @Nullable
        public String getTagText() {
            return tagText;
        }

        @Nullable
        public String getBizType() {
            return bizType;
        }

        @Nullable
        public Long getBizId() {
            return bizId;
        }
    }
}
