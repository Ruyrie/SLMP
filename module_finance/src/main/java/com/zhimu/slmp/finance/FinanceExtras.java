package com.zhimu.slmp.finance;

import com.zhimu.slmp.common.router.RouterPath;

/**
 * 模块10 页面参数键。RouterPath 已有的直接引用；新键先在这里定义，交付时由主会话并入 RouterPath。
 */
public final class FinanceExtras {

    private FinanceExtras() {
    }

    // ===== RouterPath 已有 =====

    // ===== 待并入 RouterPath =====
    /** 返回来源页的提示文字（setResult） */
    public static final String RESULT_MESSAGE = "finance_result_message";
    /** E06 → E14：新登记出栏单的 ID（返回时高亮） */
    public static final String RESULT_CREATED_ID = "finance_result_created_id";
}
