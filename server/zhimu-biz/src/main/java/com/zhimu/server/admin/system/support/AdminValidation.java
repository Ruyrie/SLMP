package com.zhimu.server.admin.system.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

/**
 * 管理员请求校验与提示文案（管理端-04 2.5.2、2.5.6）。密码规则逐字按文档；
 * 原因为空返回 40004，超长返回 40001。
 */
public final class AdminValidation {

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 20;
    public static final int NAME_MAX = 20;
    public static final int REASON_MAX = 100;

    public static final String MSG_PASSWORD_LENGTH = "新密码需为 8–20 位，同时包含字母和数字";
    public static final String MSG_PASSWORD_LETTER_DIGIT = "新密码需为 8–20 位，同时包含字母和数字";
    public static final String MSG_PASSWORD_MISMATCH = "两次输入的密码不一致";
    public static final String MSG_PASSWORD_SAME = "新密码不能与原密码相同";
    public static final String MSG_OLD_PASSWORD = "原密码不正确";
    public static final String MSG_REASON_TOO_LONG = "原因不能超过 100 个字";

    private AdminValidation() {
    }

    /** 新密码：8–20 位 ASCII 可见字符，同时含字母和数字；不满足返回提示文案 */
    public static String passwordProblem(String password) {
        if (password == null || !password.matches("[\\x21-\\x7E]{8,20}")) {
            return MSG_PASSWORD_LENGTH;
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            return MSG_PASSWORD_LETTER_DIGIT;
        }
        return null;
    }

    /** 原因：去首尾空白后 1–100 个字；空时抛出 40004，超长抛出 40001 */
    public static String reason(String raw, String emptyMessage) {
        String reason = raw == null ? "" : raw.trim();
        if (reason.isEmpty()) {
            throw new BizException(ErrorCode.REASON_EMPTY, emptyMessage);
        }
        if (reason.codePointCount(0, reason.length()) > REASON_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, MSG_REASON_TOO_LONG);
        }
        return reason;
    }

    public static String name(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
