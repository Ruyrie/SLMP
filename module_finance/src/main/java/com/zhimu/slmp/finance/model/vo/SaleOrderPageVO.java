package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.network.model.PageVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-E17 出栏单列表分页（客户端类）：tab_counts 为五个状态，另带月份分组
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class SaleOrderPageVO extends PageVO<SaleOrderItemVO> {

    private List<MonthGroup> monthGroups;

    @NonNull
    public List<MonthGroup> getMonthGroups() {
        return monthGroups == null ? java.util.Collections.emptyList() : monthGroups;
    }

    /** 月份分组合计（张数含已作废，金额不含） */
    public static class MonthGroup {

        private String month;
        private String label;
        private int count;
        @Nullable
        private BigDecimal saleAmount;

        public String getMonth() {
            return month;
        }

        public String getLabel() {
            return label;
        }

        public int getCount() {
            return count;
        }

        @Nullable
        public BigDecimal getSaleAmount() {
            return saleAmount;
        }
    }
}
