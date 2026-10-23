package com.zhimu.server.framework.jackson;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis 缓存值的 JSON 读写：独立的 JsonMapper（规则同 HTTP，但不做员工视图裁剪）。
 */
public final class CacheJson {

    private static final JsonMapper MAPPER = ZhimuJackson.newPlainMapper();

    private CacheJson() {
    }

    public static JsonMapper mapper() {
        return MAPPER;
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    /** json 为 null 时返回 null */
    public static <T> T read(String json, Class<T> type) {
        return json == null ? null : MAPPER.readValue(json, type);
    }

    public static <T> T read(String json, TypeReference<T> type) {
        return json == null ? null : MAPPER.readValue(json, type);
    }
}
