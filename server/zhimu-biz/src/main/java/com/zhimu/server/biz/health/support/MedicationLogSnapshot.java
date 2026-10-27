package com.zhimu.server.biz.health.support;

import com.zhimu.server.biz.health.entity.MedicationRecord;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** 用药留痕保存当时的中文名称、单位和完整可编辑值，避免把缺失快照误报为“无”。 */
public final class MedicationLogSnapshot {

    private MedicationLogSnapshot() {
    }

    public static Map<String, Object> of(MedicationRecord record, String executorName) {
        Map<String, Object> fields = new LinkedHashMap<>();
        put(fields, "drug_name", "药品名称", record.getDrugName());
        put(fields, "lot_no", "药品批号", record.getLotNo());
        put(fields, "dose", "用药剂量", quantity(record.getDose(), HealthCodes.drugDoseUnitText(record.getDoseUnit())));
        put(fields, "used_qty", "实际用量", quantity(record.getUsedQty(), HealthCodes.unitText(record.getUsedUnit())));
        put(fields, "route_code", "给药途径", HealthCodes.routeText(record.getRouteCode()));
        put(fields, "course_days", "疗程", days(record.getCourseDays()));
        put(fields, "first_date", "首次用药日期", record.getFirstDate());
        put(fields, "last_date", "末次用药日期", record.getLastDate());
        put(fields, "withdrawal_days", "休药天数", days(record.getWithdrawalDays()));
        put(fields, "withdrawal_end_date", "休药结束日期", record.getWithdrawalEndDate());
        put(fields, "reason", "用药原因", record.getReason());
        put(fields, "over_dose_note", "超剂量说明", record.getOverDoseNote());
        put(fields, "usage_note", "适用品类说明", record.getUsageNote());
        put(fields, "executor_id", "执行人", executorName);
        put(fields, "report_id", "关联异常上报", record.getReportId());
        return fields;
    }

    private static void put(Map<String, Object> fields, String key, String name, Object value) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("display", value == null ? null : value.toString());
        fields.put(key, field);
    }

    private static String quantity(BigDecimal value, String unit) {
        return value == null ? null : HealthFormat.numberText(value)
                + (unit == null || unit.isBlank() ? "" : " " + unit);
    }

    private static String days(Integer value) {
        return value == null ? null : value + " 天";
    }
}
