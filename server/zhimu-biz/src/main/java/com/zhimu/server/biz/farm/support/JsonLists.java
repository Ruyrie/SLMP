package com.zhimu.server.biz.farm.support;

import com.zhimu.server.framework.jackson.CacheJson;
import tools.jackson.core.type.TypeReference;

import java.util.List;

/** JSON 列中的 Long 数组（reshoot_file_ids 等）读写；null / 非法内容返回空列表 */
public final class JsonLists {

    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
    };

    private JsonLists() {
    }

    public static List<Long> readLongs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Long> list = CacheJson.read(json, LONG_LIST);
            return list == null ? List.of() : list;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public static String writeLongs(List<Long> values) {
        return CacheJson.write(values == null ? List.of() : values);
    }
}
