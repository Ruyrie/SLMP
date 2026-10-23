package com.zhimu.slmp.herd;

import com.zhimu.slmp.common.router.RouterPath;

/**
 * 本组件新增的页面参数键与结果键（RouterPath 中没有的，交付时由主会话合并进 RouterPath）。
 * 通用参数（batch_id、pen_id、farm_id、biz_type、biz_id 等）直接用 RouterPath。
 */
public final class HerdExtras {

    private HerdExtras() {
    }

    /** L09 入栏结果（String，BatchEntryResultVO 的 JSON） */
    public static final String EXTRA_RESULT_JSON = "result_json";

    /** 本组件内页面返回结果：成功提示文案（String） */
    public static final String RESULT_MESSAGE = "herd_result_message";

    /** L03 视图 */
    public static final String VIEW_PEN = "PEN";
    public static final String VIEW_CATEGORY = "CATEGORY";
    public static final String VIEW_BATCH = "BATCH";

    /** 业务记录类型（U01 修改申请、TodoChange） */
    public static final String BIZ_PEN = "PEN";
    public static final String BIZ_BATCH_ENTRY = "BATCH_ENTRY";
    public static final String BIZ_BATCH_TRANSFER = "BATCH_TRANSFER";
    public static final String BIZ_BATCH_DEATH = "BATCH_DEATH";
}
