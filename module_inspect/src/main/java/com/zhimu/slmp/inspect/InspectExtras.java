package com.zhimu.slmp.inspect;

/**
 * 本组件的页面参数键与业务类型（RouterPath 中还没有的，交付时列出，由主会话合并进 RouterPath）。
 * 与 JSON 字段同名（下划线小写）。
 */
public final class InspectExtras {

    private InspectExtras() {
    }

    /** 巡检记录 ID（long） */
    public static final String EXTRA_INSPECTION_ID = "inspection_id";
    /** 异常上报 ID（long，打开 I06、I07） */
    public static final String EXTRA_REPORT_ID = "report_id";
    /** 巡检日期 yyyy-MM-dd（String，I03 补录日期） */
    public static final String EXTRA_INSPECT_DATE = "inspect_date";
    /** I04 列表定位到的日期（String） */
    public static final String EXTRA_LOCATE_DATE = "locate_date";
    /** I03 补录日期锁定（boolean，从 I04 修改前 3 天内的记录进入） */
    public static final String EXTRA_DATE_LOCKED = "date_locked";
    /** I06 轮询间隔、最长轮询秒数（int，I05 提交后带入） */
    public static final String EXTRA_POLL_INTERVAL = "poll_interval_sec";
    public static final String EXTRA_POLL_MAX = "poll_max_sec";
    /** I05 提交后的 ai_state（String，DIAGNOSING 时 I06 先显示诊断中并轮询） */
    public static final String EXTRA_AI_STATE = "ai_state";
    /** D04 登记用药的带入（与模块08 对齐前的暂定键）：诊断 ID（long）、用药原因（String） */
    public static final String EXTRA_DIAGNOSIS_ID = "diagnosis_id";
    public static final String EXTRA_MEDICATION_REASON = "reason";

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
