package com.zhimu.server.admin.audit.support;

import com.zhimu.server.biz.farm.support.FarmSnapshot;
import com.zhimu.server.framework.jackson.CacheJson;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 兼容读取注册审核快照与资料变更 before_json。
 * 历史示例数据使用 camelCase（mainCategoryId、qualificationFileIds），现行 CacheJson 使用 snake_case；
 * 两种键都支持，避免审核详情缺少品类与资质图。
 */
public final class FarmSnapshotJson {

    private FarmSnapshotJson() {
    }

    public static FarmSnapshot read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = CacheJson.mapper().readTree(json);
            if (node == null || !node.isObject()) {
                return null;
            }
            return new FarmSnapshot(
                    text(node, "name"),
                    longValue(node, "mainCategoryId", "main_category_id"),
                    intValue(node, "scale"),
                    text(node, "contactName", "contact_name"),
                    text(node, "contactPhone", "contact_phone"),
                    text(node, "address"),
                    text(node, "province"),
                    text(node, "city"),
                    text(node, "district"),
                    text(node, "township"),
                    text(node, "adcode"),
                    decimal(node, "longitude"),
                    decimal(node, "latitude"),
                    intValue(node, "locAccuracy", "loc_accuracy"),
                    longList(node, "qualificationFileIds", "qualification_file_ids")
            );
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String... names) {
        JsonNode value = value(node, names);
        return value == null ? null : value.asString();
    }

    private static Long longValue(JsonNode node, String... names) {
        JsonNode value = value(node, names);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private static Integer intValue(JsonNode node, String... names) {
        JsonNode value = value(node, names);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static BigDecimal decimal(JsonNode node, String... names) {
        JsonNode value = value(node, names);
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    private static List<Long> longList(JsonNode node, String... names) {
        JsonNode value = value(node, names);
        List<Long> result = new ArrayList<>();
        if (value == null || !value.isArray()) {
            return result;
        }
        for (JsonNode item : value) {
            if (item != null && !item.isNull()) {
                result.add(item.asLong());
            }
        }
        return result;
    }

    private static JsonNode value(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }
}
