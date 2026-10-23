package com.zhimu.slmp.finance;

import com.zhimu.slmp.common.router.RouterPath;

/**
 * 模块10 页面参数键。RouterPath 已有的直接引用；新键先在这里定义，交付时由主会话并入 RouterPath。
 */
public final class FinanceExtras {

    private FinanceExtras() {
    }

    // ===== RouterPath 已有 =====
    public static final String BATCH_ID = RouterPath.EXTRA_BATCH_ID;
    public static final String RECORD_ID = RouterPath.EXTRA_RECORD_ID;
    public static final String MODE = RouterPath.EXTRA_MODE;
    public static final String REPORT_ID = RouterPath.EXTRA_REPORT_ID;
    public static final String TODO_ID = RouterPath.EXTRA_TODO_ID;

    // ===== 待并入 RouterPath =====
    /** E07、E08、E06 编辑态：出栏单 ID（T01 / T03 待办链接 params 已使用 sale_order_id） */
    public static final String SALE_ORDER_ID = "sale_order_id";
    /** E04、E02：月份 yyyy-MM */
    public static final String MONTH = "month";
    /** E04：逗号分隔的一级分类 ID 与 Chip 名称 */
    public static final String CATEGORY_IDS = "category_ids";
    public static final String CATEGORY_NAME = "category_name";
    /** E04：批次筛选 Chip 显示的批次号 */
    public static final String BATCH_NO = "batch_no";
    /** E04：只看手工记录（E10“其他费用”行） */
    public static final String RECORD_FILTER_TYPE = "record_filter_type";
    /** E04：定位并高亮的记录 ID */
    public static final String HIGHLIGHT_ID = "highlight_id";
    /** E04：合计视角 scope=ALL */
    public static final String SCOPE = "scope";
    /** E03：默认收支类型 EXPENSE / INCOME */
    public static final String RECORD_TYPE = "record_type";
    /** E13：报表类型与期次（T06 报表生成消息带入，见待定事项 1） */
    public static final String REPORT_TYPE = "report_type";
    public static final String PERIOD_START = "period_start";
    /** E04“原单据”打开 M03 / M06 时附带（模块09 参数键待约定） */
    public static final String INBOUND_ID = "inbound_id";
    public static final String LOT_ID = "lot_id";
    /** 返回来源页的提示文字（setResult） */
    public static final String RESULT_MESSAGE = "finance_result_message";
    /** E06 → E14：新登记出栏单的 ID（返回时高亮） */
    public static final String RESULT_CREATED_ID = "finance_result_created_id";
}
