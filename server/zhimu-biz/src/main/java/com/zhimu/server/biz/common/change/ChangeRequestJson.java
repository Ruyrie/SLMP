package com.zhimu.server.biz.common.change;

import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.vo.OptionVO;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * t_change_request 的 before_json / after_json 统一结构（任务书第七节 11、管理端-01 2.3.2）：
 * <pre>
 * {
 *   "record_card": [{"label": "饲料", "value": "育肥全价料"}],
 *   "fields": [{"field_code": "actual_qty", "field_name": "实际投喂量", "value": "140",
 *               "value_unit": "千克", "input_type": "NUMBER", "options": [], "min": 0, "max": 1000}],
 *   "compare_keys": ["actual_qty"],
 *   "before": {"actual_qty": {"name": "实际投喂量", "value": "140", "display": "140 千克", "unit": "千克"}},
 *   "after":  {"actual_qty": {"name": "实际投喂量", "value": "160", "display": "160 千克", "unit": "千克"}}
 * }
 * </pre>
 * U03/U04 的 after_value 从 after 的主修改项取；before_json 的 after 为空对象。
 */
public final class ChangeRequestJson {

    private static final JsonMapper MAPPER = ZhimuJackson.newPlainMapper();

    private ChangeRequestJson() {
    }

    public static String beforeJson(ChangeRecord record, ChangeForm form, Map<String, ChangeValidation.FieldValue> beforeValues) {
        ObjectNode root = base(record, form, beforeValues);
        root.set("after", MAPPER.createObjectNode());
        return MAPPER.writeValueAsString(root);
    }

    public static String afterJson(ChangeRecord record, ChangeForm form, Map<String, ChangeValidation.FieldValue> beforeValues,
                                   ChangeValidation validation) {
        ObjectNode root = base(record, form, beforeValues);
        ObjectNode after = MAPPER.createObjectNode();
        if (validation != null && validation.hasField()) {
            after.set(validation.fieldCode(), fieldValue(validation.fieldName(), validation.afterValue(),
                    validation.afterDisplay(), validation.unit()));
        }
        root.set("after", after);
        return MAPPER.writeValueAsString(root);
    }

    /** 主修改项的新值（纯值）；没有时返回 null */
    public static String afterValue(String afterJson, String fieldCode) {
        if (afterJson == null || afterJson.isBlank() || fieldCode == null) {
            return null;
        }
        JsonNode after = readTree(afterJson).path("after").path(fieldCode);
        JsonNode value = after.path("value");
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    /** 影响快照 impact_json：重算项目、校验结果与生成时间 */
    public static String impactJson(List<ChangeImpact> impacts, List<String> recalcTags,
                                    ChangeApplyResult result, String validationLevel, String validationText) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode impactNodes = root.putArray("impacts");
        if (impacts != null) {
            for (ChangeImpact impact : impacts) {
                impactNodes.add(impact == null ? null : impact.name());
            }
        }
        ArrayNode tagNodes = root.putArray("recalc_tags");
        if (recalcTags != null) {
            recalcTags.forEach(tagNodes::add);
        }
        if (result != null) {
            if (result.recalcCount() != null) {
                root.put("recalc_count", result.recalcCount());
            }
            if (result.recalcSummary() != null) {
                root.put("recalc_summary", result.recalcSummary());
            }
        }
        ObjectNode validation = root.putObject("validation");
        validation.put("level", validationLevel == null ? "PASS" : validationLevel);
        if (validationText != null) {
            validation.put("text", validationText);
        }
        root.put("checked_at", com.zhimu.server.common.util.BeijingTime.now().format(
                com.zhimu.server.common.util.BeijingTime.DATETIME));
        return MAPPER.writeValueAsString(root);
    }

    /** 从 before/after JSON 取全部显示值（字段编码 -> 显示文本），用于管理端对比 */
    public static Map<String, String> displays(JsonNode node, String section) {
        Map<String, String> result = new LinkedHashMap<>();
        if (node == null) {
            return result;
        }
        JsonNode sectionNode = node.path(section);
        if (!sectionNode.isObject()) {
            return result;
        }
        java.util.Iterator<String> names = sectionNode.propertyNames().iterator();
        while (names.hasNext()) {
            String key = names.next();
            JsonNode display = sectionNode.get(key).path("display");
            if (!display.isMissingNode() && !display.isNull()) {
                result.put(key, display.asString());
            }
        }
        return result;
    }

    public static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        return MAPPER.readTree(json);
    }

    /** "、" 分隔的 recalc_tags 存 VARCHAR(200)，返回时拆数组 */
    public static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined = String.join("、", tags.stream().filter(t -> t != null && !t.isBlank()).toList());
        if (joined.isEmpty()) {
            return null;
        }
        return joined.length() > 200 ? joined.substring(0, 200) : joined;
    }

    public static List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String tag : raw.split("、")) {
            if (!tag.isBlank()) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }

    private static ObjectNode base(ChangeRecord record, ChangeForm form,
                                   Map<String, ChangeValidation.FieldValue> beforeValues) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode card = root.putArray("record_card");
        if (form != null) {
            for (ChangeCardItem item : form.recordCard()) {
                ObjectNode row = card.addObject();
                row.put("label", item.label());
                row.put("value", item.value());
            }
        }
        ArrayNode fields = root.putArray("fields");
        ArrayNode compareKeys = root.putArray("compare_keys");
        Set<String> keys = new LinkedHashSet<>();
        if (form != null) {
            for (ChangeField field : form.fields()) {
                ObjectNode node = fields.addObject();
                node.put("field_code", field.fieldCode());
                node.put("field_name", field.fieldName());
                put(node, "value", field.value());
                put(node, "value_unit", field.valueUnit());
                node.put("input_type", field.inputType());
                ArrayNode options = node.putArray("options");
                for (OptionVO option : field.options()) {
                    ObjectNode optionNode = options.addObject();
                    put(optionNode, "value", option.getValue());
                    put(optionNode, "label", option.getLabel());
                }
                putDecimal(node, "min", field.min());
                putDecimal(node, "max", field.max());
                keys.add(field.fieldCode());
            }
        }
        keys.forEach(compareKeys::add);

        ObjectNode before = root.putObject("before");
        Map<String, ChangeValidation.FieldValue> values = beforeValues == null ? Map.of() : beforeValues;
        for (Map.Entry<String, ChangeValidation.FieldValue> entry : values.entrySet()) {
            ChangeValidation.FieldValue value = entry.getValue();
            before.set(entry.getKey(), fieldValue(value.name(), value.value(), value.display(), value.unit()));
        }
        return root;
    }

    private static ObjectNode fieldValue(String name, String value, String display, String unit) {
        ObjectNode node = MAPPER.createObjectNode();
        put(node, "name", name);
        put(node, "value", value);
        put(node, "display", display);
        put(node, "unit", unit);
        return node;
    }

    private static void put(ObjectNode node, String key, String value) {
        if (value != null) {
            node.put(key, value);
        }
    }

    private static void putDecimal(ObjectNode node, String key, BigDecimal value) {
        if (value != null) {
            node.put(key, value.stripTrailingZeros());
        }
    }

    /** 修改前字段值：编码 -> 名称、纯值与显示文本 */
    public static Map<String, ChangeValidation.FieldValue> fieldValues(ChangeForm form) {
        Map<String, ChangeValidation.FieldValue> values = new LinkedHashMap<>();
        if (form == null) {
            return values;
        }
        for (ChangeField field : form.fields()) {
            String display = display(field.value(), field.valueUnit());
            values.put(field.fieldCode(), new ChangeValidation.FieldValue(field.fieldName(), field.value(), display,
                    field.valueUnit()));
        }
        return values;
    }

    public static String display(String value, String unit) {
        if (value == null) {
            return null;
        }
        return unit == null || unit.isBlank() ? value : value + " " + unit;
    }

    /** 校验字段编码属于表单；不属于时抛 40001（field_code） */
    public static void requireField(ChangeForm form, String fieldCode) {
        if (form.fieldOf(fieldCode) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要修改的项目")
                    .with("field_errors", List.of(new com.zhimu.server.common.error.FieldErrorVO(
                            "field_code", "请选择要修改的项目")));
        }
    }

    /** 申请类型只接受 UPDATE / VOID / DELETE */
    public static ChangeType parseChangeType(String code) {
        ChangeType type = ChangeType.UPDATE.name().equals(code) ? ChangeType.UPDATE
                : ChangeType.VOID.name().equals(code) ? ChangeType.VOID
                : ChangeType.DELETE.name().equals(code) ? ChangeType.DELETE : null;
        return type;
    }
}
