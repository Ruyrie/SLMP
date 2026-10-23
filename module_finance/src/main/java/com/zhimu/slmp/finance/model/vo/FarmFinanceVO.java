package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 合计视角的养殖场卡（E02）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class FarmFinanceVO {

    private long farmId;
    private String farmName;
    private String role;
    @Nullable
    private String roleText;
    @Nullable
    private String mainCategoryName;
    private BigDecimal income;
    private BigDecimal expenseAmount;
    private BigDecimal balanceAmount;
    @Nullable
    private BigDecimal incomeMomRate;
    @Nullable
    private BigDecimal expenseMomRate;
    @Nullable
    private BigDecimal balanceMomRate;
    private boolean incomeComparable;
    private boolean expenseComparable;
    private boolean balanceComparable;
    @Nullable
    private BigDecimal incomeShareRate;

    public long getFarmId() {
        return farmId;
    }

    public String getFarmName() {
        return farmName;
    }

    public String getRole() {
        return role;
    }

    @Nullable
    public String getRoleText() {
        return roleText;
    }

    @Nullable
    public String getMainCategoryName() {
        return mainCategoryName;
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
    public BigDecimal getIncomeShareRate() {
        return incomeShareRate;
    }
}
