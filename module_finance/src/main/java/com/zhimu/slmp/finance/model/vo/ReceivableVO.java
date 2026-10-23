package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.network.model.PageVO;

/**
 * API-E18 待收款分页（tab_counts 键 ALL、OVERDUE、DUE_7D）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class ReceivableVO extends PageVO<SaleOrderItemVO> {

    /** OWNER_AND_MANAGERS / YOU_OWNER_OTHER_MANAGERS */
    @Nullable
    private String reminderReceivers;
    @Nullable
    private String remindRuleText;
    @Nullable
    private ReceivableSummary summary;

    @Nullable
    public String getReminderReceivers() {
        return reminderReceivers;
    }

    @Nullable
    public String getRemindRuleText() {
        return remindRuleText;
    }

    @Nullable
    public ReceivableSummary getSummary() {
        return summary;
    }
}
