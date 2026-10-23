package com.zhimu.server.integration.support;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 外部接口报文用的 JSON 工具：请求体用树模型按外部接口的字段名手工组装，不受应用全局 Jackson 配置（SNAKE_CASE 等）影响。
 */
public final class Jsons {

    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Jsons() {
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static String write(JsonNode node) {
        return MAPPER.writeValueAsString(node);
    }

    /** 解析 JSON；为空或不是 JSON 时返回 null */
    public static JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(raw);
        } catch (JacksonException e) {
            return null;
        }
    }

    /** 字符串或数字字段的文本值；不存在、为 null、为数组或对象时返回 null */
    public static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isContainer()) {
            return null;
        }
        return v.asString();
    }

    /** 非空文本；高德等接口的空值可能是 [] 或空串，统一返回 null */
    public static String nonBlank(JsonNode node, String field) {
        String v = text(node, field);
        return v == null || v.isBlank() ? null : v;
    }

    public static Integer integer(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || !v.isNumber()) {
            return null;
        }
        return v.asInt();
    }

    /** 截断外部返回的报文用于日志，避免整段输出 */
    public static String abbreviate(String text, int max) {
        if (text == null) {
            return null;
        }
        String oneLine = text.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }
}
