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

    public static Diff diff() {
        return new Diff();
    }

    /**
     * “修改”类留痕：只收显示文本有变化的字段；外键传名称、编码传中文，空值记“未填写”。
     * before() / after() 没有变化时返回 null。
     */
    public static final class Diff {

        private static final String EMPTY = "未填写";

        private final Map<String, Object> before = new LinkedHashMap<>();
        private final Map<String, Object> after = new LinkedHashMap<>();

        private Diff() {
        }

        public Diff field(String code, String name, Object beforeDisplay, Object afterDisplay) {
            String b = text(beforeDisplay);
            String a = text(afterDisplay);
            if (!b.equals(a)) {
                put(before, code, name, b);
                put(after, code, name, a);
            }
            return this;
        }

        public boolean isEmpty() {
            return after.isEmpty();
        }

        public Map<String, Object> before() {
            return before.isEmpty() ? null : before;
        }

        public Map<String, Object> after() {
            return after.isEmpty() ? null : after;
        }

        private static String text(Object value) {
            if (value == null) {
                return EMPTY;
            }
            if (value instanceof Boolean flag) {
                return flag ? "是" : "否";
            }
            if (value instanceof java.math.BigDecimal decimal) {
                return decimal.stripTrailingZeros().toPlainString();
            }
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? EMPTY : text;
        }
    }
}
