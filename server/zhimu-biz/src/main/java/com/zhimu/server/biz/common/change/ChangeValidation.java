package com.zhimu.server.biz.common.change;

import com.zhimu.server.common.enums.ChangeImpact;

import java.util.List;

/**
 * U02 校验通过后的主修改项信息（DELETE / VOID 时除 impacts、recalcTags 外均为空）。
 *
 * @param beforeValue 原值（纯值）
 * @param beforeDisplay 原显示值（带单位，例如 "140 千克"）
 * @param afterValue  新值（纯值）
 * @param afterDisplay 新显示值（带单位）
 */
public record ChangeValidation(
        String fieldCode,
        String fieldName,
        String unit,
        String beforeValue,
        String beforeDisplay,
        String afterValue,
        String afterDisplay,
        List<ChangeImpact> impacts,
        List<String> recalcTags) {

    public ChangeValidation {
        impacts = impacts == null ? List.of() : List.copyOf(impacts);
        recalcTags = recalcTags == null ? List.of() : List.copyOf(recalcTags);
    }

    public boolean hasField() {
        return fieldCode != null && !fieldCode.isBlank();
    }

    public static ChangeValidation of(String fieldCode, String fieldName, String unit,
                                      String beforeValue, String beforeDisplay,
                                      String afterValue, String afterDisplay,
                                      List<ChangeImpact> impacts, List<String> recalcTags) {
        return new ChangeValidation(fieldCode, fieldName, unit, beforeValue, beforeDisplay,
                afterValue, afterDisplay, impacts, recalcTags);
    }

    /** 作废、删除申请：没有主修改项 */
    public static ChangeValidation withoutField(List<ChangeImpact> impacts, List<String> recalcTags) {
        return new ChangeValidation(null, null, null, null, null, null, null, impacts, recalcTags);
    }

    /** JSON 快照中的一个字段值（name 中文名、value 纯值、display 带单位显示文字） */
    public record FieldValue(String name, String value, String display, String unit) {
    }
}
