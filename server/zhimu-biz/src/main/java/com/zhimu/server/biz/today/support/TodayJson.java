package com.zhimu.server.biz.today.support;

import com.zhimu.server.framework.jackson.ZhimuJackson;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 消息 extra_json 的读写（键为 snake_case，使用与 HTTP 相同的 Jackson 规则）。 */
public final class TodayJson {

    private TodayJson() {
    }

    /** 解析为 Map；空或格式错误时返回空 Map */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Object value = ZhimuJackson.newPlainMapper().readValue(json, Object.class);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((k, v) -> result.put(String.valueOf(k), v));
                return result;
            }
        } catch (RuntimeException ignored) {
            // 历史数据格式异常时按没有附加字段处理
        }
        return Map.of();
    }

    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        return ZhimuJackson.newPlainMapper().writeValueAsString(value);
    }

    public static String str(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static Long lng(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        String s = value == null ? null : String.valueOf(value);
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer integer(Map<String, Object> map, String key) {
        Long value = lng(map, key);
        return value == null ? null : value.intValue();
    }

    public static boolean bool(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    public static Map<String, Object> copy(Map<String, Object> map) {
        return map == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
