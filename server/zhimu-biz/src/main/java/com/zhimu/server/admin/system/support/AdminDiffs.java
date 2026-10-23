package com.zhimu.server.admin.system.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作留痕的 before / after 结构：字段编码 → {name 中文名, display 显示文本}，
 * 供管理端日志详情显示“角色 审核员 → 内容运营”。手机号等敏感信息写入前先打码。
 */
public final class AdminDiffs {

    private AdminDiffs() {
    }

    public static Map<String, Object> put(Map<String, Object> target, String code, String name, Object display) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("display", display);
        target.put(code, field);
        return target;
    }

    public static Map<String, Object> of(String code, String name, Object display) {
        return put(new LinkedHashMap<>(), code, name, display);
    }
}
