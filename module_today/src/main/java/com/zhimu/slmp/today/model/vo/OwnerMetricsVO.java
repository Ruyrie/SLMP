package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * 养殖户版首页指标（API-T01 owner_metrics，员工不返回）。
 */
public class OwnerMetricsVO {

    /** 存栏数 */
    private int stockCount;
    private int batchCount;
    /** 今天到期和已逾期且未完成的待办数 */
    private int todayTodoCount;
    private int overdueCount;
    /** 未解除的预警类待办数 */
    private int warningCount;
    private int receivableOverdueCount;
    private int receivableMaxOverdueDays;
    /** HEAD / BIRD */
    @Nullable
    private String countUnit;
    /** 头 / 只 */
    @Nullable
    private String countUnitText;
    @Nullable
    private BigDecimal penOccupancyRate;
    @Nullable
    private List<String> warningNames;
    /** 管理视图 */
    @Nullable
    private BigDecimal receivableAmount;

    public int getStockCount() {
        return stockCount;
    }

    public int getBatchCount() {
        return batchCount;
    }

    public int getTodayTodoCount() {
        return todayTodoCount;
    }

    public int getOverdueCount() {
        return overdueCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public int getReceivableOverdueCount() {
        return receivableOverdueCount;
    }

    public int getReceivableMaxOverdueDays() {
        return receivableMaxOverdueDays;
    }

    @Nullable
    public String getCountUnit() {
        return countUnit;
    }

    @Nullable
    public String getCountUnitText() {
        return countUnitText;
    }

    @Nullable
    public BigDecimal getPenOccupancyRate() {
        return penOccupancyRate;
    }

    @Nullable
    public List<String> getWarningNames() {
        return warningNames;
    }

    @Nullable
    public BigDecimal getReceivableAmount() {
        return receivableAmount;
    }

    /** 登记回流：今日待办减 1（预警类同时减预警数，已逾期同时减逾期数），返回新对象 */
    @androidx.annotation.NonNull
    public OwnerMetricsVO minusTodo(boolean overdue, boolean warning) {
        OwnerMetricsVO c = new OwnerMetricsVO();
        c.stockCount = stockCount;
        c.batchCount = batchCount;
        c.todayTodoCount = Math.max(0, todayTodoCount - 1);
        c.overdueCount = overdue ? Math.max(0, overdueCount - 1) : overdueCount;
        c.warningCount = warning ? Math.max(0, warningCount - 1) : warningCount;
        c.receivableOverdueCount = receivableOverdueCount;
        c.receivableMaxOverdueDays = receivableMaxOverdueDays;
        c.countUnit = countUnit;
        c.countUnitText = countUnitText;
        c.penOccupancyRate = penOccupancyRate;
        c.warningNames = warningNames;
        c.receivableAmount = receivableAmount;
        return c;
    }
}
