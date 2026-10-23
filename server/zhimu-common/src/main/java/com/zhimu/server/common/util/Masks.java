package com.zhimu.server.common.util;

/**
 * 脱敏工具：手机号隐藏中间 4 位（138****6620）；日志中的令牌与验证码只保留首尾。
 */
public final class Masks {

    private Masks() {
    }

    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String secret(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 2);
    }

    /** 姓名首字，用于头像占位（name_initial） */
    public static String initial(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().substring(0, 1);
    }
}
