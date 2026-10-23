package com.zhimu.slmp.farm;

import com.zhimu.slmp.common.router.RouterPath;

/**
 * 本组件内的页面参数与结果键（RouterPath 中没有的，交付时列出，由主会话决定是否合并进 RouterPath）。
 * 通用键（farm_id、mode、message）直接用 RouterPath。
 */
public final class FarmExtras {

    private FarmExtras() {
    }

    /** F06 比较基准：F05 的 API-F09 data.registration（Gson 字符串） */
    public static final String EXTRA_REGISTRATION_JSON = "registration_json";
    /** F08 重新邀请的原邀请（InviteItemVO 的 Gson 字符串） */
    public static final String EXTRA_REINVITE_JSON = "reinvite_json";

    /** 子页返回 RESULT_OK 时带回的提示文字（String），来源页刷新后用 Snackbar 显示 */
    public static final String RESULT_MESSAGE = "result_message";
    /** F09 移出后没有负责员工的栏舍名（String[]），F07 显示警示横幅 */
    public static final String RESULT_UNASSIGNED_PENS = "result_unassigned_pens";

    /** F02 模式：新建 / 修改注册信息 */
    public static final String MODE_CREATE = "CREATE";
    public static final String MODE_EDIT = "EDIT";
    /** F06 模式：资料变更被驳回后“重新修改”，预填上次提交的内容 */
    public static final String MODE_REEDIT = "REEDIT";

    /** F06 滚动目标 */
    public static final String TARGET_NAME = "NAME";
    public static final String TARGET_ADDRESS = "ADDRESS";
    public static final String TARGET_QUALIFICATION = "QUALIFICATION";
}
