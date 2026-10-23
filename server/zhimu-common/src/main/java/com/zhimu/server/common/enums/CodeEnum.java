package com.zhimu.server.common.enums;

/**
 * 带中文名的枚举：数据库与接口存大写编码（name()），VO 另带 xxx_text 中文名（getText()）。
 */
public interface CodeEnum {

    String name();

    String getText();

    default String getCode() {
        return name();
    }

    /** 按编码取中文名；编码为空或不存在时返回 null */
    static <E extends Enum<E> & CodeEnum> String textOf(Class<E> type, String code) {
        E e = of(type, code);
        return e == null ? null : e.getText();
    }

    /** 按编码取枚举；编码为空或不存在时返回 null */
    static <E extends Enum<E> & CodeEnum> E of(Class<E> type, String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
