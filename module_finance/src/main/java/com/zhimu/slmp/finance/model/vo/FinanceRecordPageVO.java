package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.network.model.PageVO;

import java.math.BigDecimal;

/**
 * API-E03 收支流水分页（客户端类）：另带不含作废的合计
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class FinanceRecordPageVO extends PageVO<FinanceRecordVO> {

    @Nullable
    private Totals totals;

    @Nullable
    public Totals getTotals() {
        return totals;
    }

    /** 当前筛选结果合计（不含作废） */
    public static class Totals {

        private BigDecimal income;
        private BigDecimal expenseAmount;

        public BigDecimal getIncome() {
            return income;
        }

        public BigDecimal getExpenseAmount() {
            return expenseAmount;
        }
    }
}
