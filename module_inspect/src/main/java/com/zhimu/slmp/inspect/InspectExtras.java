package com.zhimu.slmp.inspect;

import com.zhimu.slmp.common.router.RouterPath;

/**
 * 本组件的页面参数键与业务类型（RouterPath 中还没有的，交付时列出，由主会话合并进 RouterPath）。
 * 与 JSON 字段同名（下划线小写）。
 */
public final class InspectExtras {

    private InspectExtras() {
    }


    /** I02 页面模式（RouterPath.EXTRA_MODE 的取值）：登记、修改、只读 */
    public static final String MODE_REGISTER = "REGISTER";
    public static final String MODE_MODIFY = "MODIFY";
    public static final String MODE_READONLY = "READONLY";

    /** 业务类型（待办变化、修改申请、修改记录） */
    public static final String BIZ_INSPECTION = "INSPECTION";
    public static final String BIZ_ABNORMAL_REPORT = "ABNORMAL_REPORT";
    public static final String BIZ_DIAGNOSIS_HANDLE = "DIAGNOSIS_HANDLE";

    /** 上传队列页面编号 */
    public static final String PAGE_I02 = "I02";
    public static final String PAGE_I03 = "I03";
    public static final String PAGE_I05 = "I05";
}
