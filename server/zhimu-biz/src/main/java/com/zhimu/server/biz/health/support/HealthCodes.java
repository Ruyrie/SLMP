package com.zhimu.server.biz.health.support;

import java.util.Map;

/**
 * 模块08 的枚举编码、中文名与单位映射。
 * <p>
 * 输出口径与 App 的 module_health mock 一致：状态另带 xxx_text；{@code dose_unit}、{@code usage_unit}、
 * {@code stock_unit} 输出中文（例如“克 / 升水”“头份 / 头”），库存里保存的仍是编码。
 * 字典 VACCINATE_METHOD、DISINFECT_METHOD 的名称由 DictQueryService 取，这里只做兜底。
 */
public final class HealthCodes {

    // ===== 免疫对象 / 计划方式 =====
    public static final String TARGET_BATCH = "BATCH";
    public static final String TARGET_BREEDING_PEN = "BREEDING_PEN";
    public static final String SOURCE_TEMPLATE = "TEMPLATE";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String PLAN_BY_DAY_AGE = "BY_DAY_AGE";
    public static final String PLAN_BY_DATE = "BY_DATE";

    // ===== 免疫计划状态 =====
    public static final String PLAN_PRE_ENTRY = "PRE_ENTRY";
    public static final String PLAN_PRE_ENTRY_DONE = "PRE_ENTRY_DONE";
    public static final String PLAN_PENDING = "PENDING";
    public static final String PLAN_POSTPONED = "POSTPONED";
    public static final String PLAN_MISSED = "MISSED";
    public static final String PLAN_DONE = "DONE";
    public static final String PLAN_CANCELLED = "CANCELLED";

    // ===== 取消方式 =====
    public static final String CANCEL_USER_DELETED = "USER_DELETED";
    public static final String CANCEL_BATCH_ENDED = "BATCH_ENDED";
    public static final String CANCEL_PEN_EMPTY = "PEN_EMPTY";

    // ===== 用药对象 / 消毒 =====
    public static final String MED_TARGET_BATCH = "BATCH";
    public static final String MED_TARGET_PEN = "PEN";

    public static final String DISINFECT_PLAN_ACTIVE = "ACTIVE";
    public static final String DISINFECT_PLAN_PAUSED = "PAUSED";
    public static final String DISINFECT_PLAN_ENDED = "ENDED";

    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_OVERDUE = "OVERDUE";
    public static final String TASK_DONE = "DONE";
    public static final String TASK_PAUSED = "PAUSED";
    public static final String TASK_CANCELLED = "CANCELLED";
    public static final String TASK_SOURCE_PLAN = "PLAN";
    public static final String TASK_SOURCE_ADMIN = "ADMIN_EMERGENCY";

    public static final String RECORD_PLAN_TASK = "PLAN_TASK";
    public static final String RECORD_EMPTY_PEN = "EMPTY_PEN";
    public static final String RECORD_EMERGENCY = "EMERGENCY";

    // ===== 休药状态 =====
    public static final String WITHDRAWAL_NONE = "NONE";
    public static final String WITHDRAWAL_IN = "IN_WITHDRAWAL";
    public static final String WITHDRAWAL_ENDED = "ENDED";

    // ===== 删除影响 =====
    public static final String IMPACT_ADVANCE = "ADVANCE";
    public static final String IMPACT_NO_CHANGE_TODAY = "NO_CHANGE_TODAY";
    public static final String IMPACT_NO_CHANGE_EARLIER = "NO_CHANGE_EARLIER";
    public static final String IMPACT_LOCKED = "LOCKED";

    // ===== 操作（RECORD_ACTION） =====
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

    // ===== 药品类别（t_drug.category / t_drug_catalog.drug_category） =====
    public static final String CATEGORY_VET_DRUG = "VET_DRUG";
    public static final String CATEGORY_VACCINE = "VACCINE";
    public static final String CATEGORY_DISINFECTANT = "DISINFECTANT";

    // ===== 库存用途（DrugStockPort.lotsFor） =====
    public static final String PURPOSE_VACCINATE = "VACCINATE";
    public static final String PURPOSE_MEDICATE = "MEDICATE";
    public static final String PURPOSE_DISINFECT = "DISINFECT";

    private static final Map<String, String> IMMUNE_STATUS_TEXT = Map.of(
            PLAN_PRE_ENTRY, "待确认",
            PLAN_PRE_ENTRY_DONE, "已确认",
            PLAN_PENDING, "待接种",
            PLAN_POSTPONED, "已延期",
            PLAN_MISSED, "漏免",
            PLAN_DONE, "已接种",
            PLAN_CANCELLED, "已取消");

    private static final Map<String, String> CANCEL_TEXT = Map.of(
            CANCEL_USER_DELETED, "手动删除",
            CANCEL_BATCH_ENDED, "批次已结束",
            CANCEL_PEN_EMPTY, "栏舍已无种畜");

    private static final Map<String, String> DISINFECT_PLAN_STATUS_TEXT = Map.of(
            DISINFECT_PLAN_ACTIVE, "启用中",
            DISINFECT_PLAN_PAUSED, "已暂停",
            DISINFECT_PLAN_ENDED, "已结束");

    private static final Map<String, String> TASK_STATUS_TEXT = Map.of(
            TASK_PENDING, "待执行",
            TASK_OVERDUE, "已逾期",
            TASK_DONE, "已执行",
            TASK_PAUSED, "已暂停",
            TASK_CANCELLED, "已取消");

    private static final Map<String, String> RECORD_TYPE_TEXT = Map.of(
            RECORD_PLAN_TASK, "计划任务",
            RECORD_EMPTY_PEN, "空栏消毒",
            RECORD_EMERGENCY, "紧急消毒");

    private static final Map<String, String> WITHDRAWAL_STATUS_TEXT = Map.of(
            WITHDRAWAL_NONE, "无休药",
            WITHDRAWAL_IN, "休药中",
            WITHDRAWAL_ENDED, "已结束");

    private static final Map<String, String> WITHDRAWAL_TAG_TEXT = Map.of(
            "LATEST", "最晚",
            "ENDED", "已到期",
            WITHDRAWAL_NONE, "无");

    /** 记账单位 MEASURE_UNIT（库存里保存的编码） */
    private static final Map<String, String> MEASURE_UNIT_TEXT = Map.of(
            "DOSE", "头份",
            "G", "克",
            "ML", "毫升",
            "L", "升",
            "KG", "千克");

    /** 疫苗剂量单位 VACCINE_DOSE_UNIT */
    private static final Map<String, String> VACCINE_DOSE_UNIT_TEXT = Map.of(
            "DOSE_PER_HEAD", "头份 / 头",
            "ML_PER_HEAD", "毫升 / 头",
            "DOSE_PER_BIRD", "羽份 / 只");

    /** 兽药剂量单位 DRUG_DOSE_UNIT */
    private static final Map<String, String> DRUG_DOSE_UNIT_TEXT = Map.of(
            "G_PER_T_FEED", "克 / 吨料",
            "G_PER_L_WATER", "克 / 升水",
            "ML_PER_10KG", "毫升 / 10千克体重",
            "MG_PER_KG", "毫克 / 千克体重",
            "ML_PER_HEAD", "毫升 / 头",
            "G_PER_HEAD", "克 / 头");

    /** 给药途径 ADMIN_ROUTE */
    private static final Map<String, String> ROUTE_TEXT = Map.of(
            "MIX_FEED", "混饲",
            "MIX_WATER", "混饮",
            "IM", "肌肉注射",
            "INJECTION", "肌肉注射",
            "SC", "皮下注射",
            "IV", "静脉注射",
            "ORAL", "口服",
            "EXTERNAL", "外用");

    private static final Map<String, String> METHOD_TEXT = Map.of(
            "IM", "肌肉注射",
            "SC", "皮下注射",
            "NASAL", "滴鼻",
            "ORAL", "口服",
            "EYE_NASAL", "滴鼻点眼",
            "WATER", "饮水",
            "SPRAY", "喷雾",
            "WIPE", "擦拭",
            "SOAK", "浸泡",
            "FUMIGATE", "熏蒸");

    private static final Map<String, String> LOT_STATUS_TEXT = Map.of(
            "NORMAL", "正常",
            "NEAR_EXPIRY", "临期",
            "EXPIRED", "已过期",
            "RECALLED", "已停用",
            "BANNED", "禁用药");

    private HealthCodes() {
    }

    public static String immuneStatusText(String status) {
        return status == null ? null : IMMUNE_STATUS_TEXT.get(status);
    }

    public static String cancelTypeText(String cancelType) {
        return cancelType == null ? null : CANCEL_TEXT.get(cancelType);
    }

    public static String disinfectPlanStatusText(String status) {
        return status == null ? null : DISINFECT_PLAN_STATUS_TEXT.get(status);
    }

    public static String taskStatusText(String status) {
        return status == null ? null : TASK_STATUS_TEXT.get(status);
    }

    public static String recordTypeText(String recordType) {
        return recordType == null ? null : RECORD_TYPE_TEXT.get(recordType);
    }

    public static String withdrawalStatusText(String status) {
        return status == null ? null : WITHDRAWAL_STATUS_TEXT.get(status);
    }

    public static String withdrawalTagText(String tag) {
        return tag == null ? null : WITHDRAWAL_TAG_TEXT.get(tag);
    }

    public static String unitText(String unit) {
        return unit == null ? null : MEASURE_UNIT_TEXT.getOrDefault(unit, unit);
    }

    public static String vaccineDoseUnitText(String unit) {
        return unit == null ? null : VACCINE_DOSE_UNIT_TEXT.getOrDefault(unit, DRUG_DOSE_UNIT_TEXT.getOrDefault(unit, unit));
    }

    public static String drugDoseUnitText(String unit) {
        return unit == null ? null : DRUG_DOSE_UNIT_TEXT.getOrDefault(unit, VACCINE_DOSE_UNIT_TEXT.getOrDefault(unit, unit));
    }

    /** 疫苗、兽药都可能出现的剂量单位：先查两类映射 */
    public static String doseUnitText(String unit) {
        if (unit == null) {
            return null;
        }
        String text = VACCINE_DOSE_UNIT_TEXT.get(unit);
        if (text == null) {
            text = DRUG_DOSE_UNIT_TEXT.get(unit);
        }
        return text == null ? unit : text;
    }

    public static String routeText(String route) {
        return route == null ? null : ROUTE_TEXT.getOrDefault(route, route);
    }

    public static String methodText(String methodCode) {
        return methodCode == null ? null : METHOD_TEXT.getOrDefault(methodCode, methodCode);
    }

    public static String lotStatusText(String status) {
        return status == null ? null : LOT_STATUS_TEXT.getOrDefault(status, status);
    }

    /** 员工登记提示：没有可用批号时的后缀（2.5.7） */
    public static final String STAFF_NO_LOT_SUFFIX = "请联系所有者或场长先登记入库。";
}
