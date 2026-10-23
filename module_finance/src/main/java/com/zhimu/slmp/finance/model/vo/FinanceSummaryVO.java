package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-E02 月度汇总（E02）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class FinanceSummaryVO {

    private String periodStart;
    private String periodEnd;
    private boolean isCurrentMonth;
    private BigDecimal income;
    private BigDecimal expenseAmount;
    private BigDecimal balanceAmount;
    private BigDecimal prevIncome;
    private BigDecimal prevExpenseAmount;
    private BigDecimal prevBalanceAmount;
    private boolean incomeComparable;
    private boolean expenseComparable;
    private boolean balanceComparable;
    @Nullable
    private BigDecimal incomeMomRate;
    @Nullable
    private BigDecimal expenseMomRate;
    @Nullable
    private BigDecimal balanceMomRate;
    private int recordCount;
    private int autoCount;
    private int manualCount;
    private int manageableFarmCount;
    /** scope=ALL 不输出 */
    private List<CategoryShareVO> expenseShares;
    private List<CategoryShareVO> incomeShares;
    /** 只在 ALL */
    private List<FarmFinanceVO> farms;
    /** 只在 ALL：本人担任员工、不计入的养殖场 */
    private List<String> staffFarmNames;

    public String getPeriodStart() {
        return periodStart;
    }

    public String getPeriodEnd() {
        return periodEnd;
    }

    public boolean isCurrentMonth() {
        return isCurrentMonth;
    }

    public BigDecimal getIncome() {
        return income;
    }

    public BigDecimal getExpenseAmount() {
        return expenseAmount;
    }

    public BigDecimal getBalanceAmount() {
        return balanceAmount;
    }

    public BigDecimal getPrevIncome() {
        return prevIncome;
    }

    public BigDecimal getPrevExpenseAmount() {
        return prevExpenseAmount;
    }

    public BigDecimal getPrevBalanceAmount() {
        return prevBalanceAmount;
    }

    public boolean isIncomeComparable() {
        return incomeComparable;
    }

    public boolean isExpenseComparable() {
        return expenseComparable;
    }

    public boolean isBalanceComparable() {
        return balanceComparable;
    }

    @Nullable
    public BigDecimal getIncomeMomRate() {
        return incomeMomRate;
    }

    @Nullable
    public BigDecimal getExpenseMomRate() {
        return expenseMomRate;
    }

    @Nullable
    public BigDecimal getBalanceMomRate() {
        return balanceMomRate;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public int getAutoCount() {
        return autoCount;
    }

    public int getManualCount() {
        return manualCount;
    }

    public int getManageableFarmCount() {
        return manageableFarmCount;
    }

    @NonNull
    public List<CategoryShareVO> getExpenseShares() {
        return expenseShares == null ? java.util.Collections.emptyList() : expenseShares;
    }

    @NonNull
    public List<CategoryShareVO> getIncomeShares() {
        return incomeShares == null ? java.util.Collections.emptyList() : incomeShares;
    }

    @NonNull
    public List<FarmFinanceVO> getFarms() {
        return farms == null ? java.util.Collections.emptyList() : farms;
    }

    @NonNull
    public List<String> getStaffFarmNames() {
        return staffFarmNames == null ? java.util.Collections.emptyList() : staffFarmNames;
    }
}
