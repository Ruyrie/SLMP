package com.zhimu.slmp.health;

/**
 * 模块08 页面参数键与固定取值。RouterPath 中已有 EXTRA_BATCH_ID、EXTRA_PEN_ID、EXTRA_TASK_ID、EXTRA_MODE 等，
 * 这里只定义 RouterPath 中还没有的键（交付时列出，由主会话合并进 RouterPath）；键名与 JSON 字段同名。
 */
public final class HealthExtras {

    private HealthExtras() {
    }

    // ===== 新参数键（RouterPath 中暂无） =====
    /** 免疫计划项目 ID（long，D02 修改、D03 登记） */
    public static final String EXTRA_PLAN_ID = "plan_id";
    /** 模块03 待办 register_link 中免疫计划项目 ID 的写法（long，与 plan_id 同义） */
    public static final String EXTRA_PLAN_ITEM_ID = "plan_item_id";
    /** 是否补录（boolean，D03 漏免补免、D08 逾期补录） */
    public static final String EXTRA_BACKFILL = "backfill";
    /** 记录 ID（long，D03 接种记录、D04 用药记录、D08 消毒记录的查看与修改） */
    public static final String EXTRA_RECORD_ID = "record_id";
    /** 异常上报 ID（long，I07 转为用药记录） */
    public static final String EXTRA_REPORT_ID = "report_id";
    /** 诊断 ID（long，I07 转为用药记录） */
    public static final String EXTRA_DIAGNOSIS_ID = "diagnosis_id";
    /** 消毒登记类型（String：PLAN_TASK / EMPTY_PEN / EMERGENCY） */
    public static final String EXTRA_RECORD_TYPE = "record_type";
    /** 版本号（int，D02 修改项目） */
    public static final String EXTRA_VERSION = "version";
    /** 剂量（String，D02 修改项目预填） */
    public static final String EXTRA_DOSE = "dose";
    /** 接种方式编码（String，D02 修改项目预填） */
    public static final String EXTRA_METHOD_CODE = "method_code";

    // ===== 页面返回结果 =====
    /** D02 保存成功后回传的计划 ID（long），D01 据此高亮 */
    public static final String RESULT_PLAN_ID = "result_plan_id";

    // ===== 消毒登记类型 =====
    public static final String RECORD_PLAN_TASK = "PLAN_TASK";
    public static final String RECORD_EMPTY_PEN = "EMPTY_PEN";
    public static final String RECORD_EMERGENCY = "EMERGENCY";

    // ===== 修改申请与修改记录的业务类型（BIZ_TYPE） =====
    public static final String BIZ_IMMUNE_PLAN = "IMMUNE_PLAN";
    public static final String BIZ_IMMUNE_RECORD = "IMMUNE_RECORD";
    public static final String BIZ_MEDICATION_RECORD = "MEDICATION_RECORD";
    public static final String BIZ_DISINFECT_RECORD = "DISINFECT_RECORD";
    public static final String BIZ_DISINFECT_PLAN = "DISINFECT_PLAN";

    // ===== API-M27 purpose（待定事项 1：暂按本模块服务端文档） =====
    public static final String PURPOSE_VACCINATE = "VACCINATE";
    public static final String PURPOSE_MEDICATE = "MEDICATE";
    public static final String PURPOSE_DISINFECT = "DISINFECT";

    // ===== RECORD_ACTION =====
    public static final String ACTION_REGISTER = "REGISTER";
    public static final String ACTION_BACKFILL = "BACKFILL";
    public static final String ACTION_POSTPONE = "POSTPONE";
    public static final String ACTION_EDIT = "EDIT";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_CHANGE_REQUEST = "CHANGE_REQUEST";
    public static final String ACTION_VIEW_REQUEST = "VIEW_REQUEST";
    public static final String ACTION_VIEW_LOG = "VIEW_LOG";
    public static final String ACTION_NOTIFY = "NOTIFY";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_RESUME = "RESUME";
}
