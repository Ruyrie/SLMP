package com.zhimu.server.biz.account.support;

/** 手机号、短信验证码、恢复码、密码、姓名与情况说明的规范化与校验（模块01 文档 2.7.1）。 */
public final class AccountValidation {

    public static final int CODE_LENGTH = 6;
    public static final int RECOVERY_CODE_LENGTH = 16;
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 20;
    public static final int NAME_MAX = 20;
    public static final int DESCRIPTION_MAX = 200;

    public static final String MSG_PHONE_EMPTY = "请输入手机号";
    public static final String MSG_PHONE_FORMAT = "手机号格式不正确，请输入 11 位手机号";
    public static final String MSG_CODE_FORMAT = "请输入 6 位验证码";
    public static final String MSG_PASSWORD_LENGTH = "密码需要 8–20 位";
    public static final String MSG_PASSWORD_LETTER_DIGIT = "密码需同时包含字母和数字";
    public static final String MSG_PASSWORD_MISMATCH = "两次输入的密码不一致";
    public static final String MSG_RECOVERY_CODE = "恢复码应为 16 位字母和数字";
    public static final String MSG_NAME_EMPTY = "请输入姓名";
    public static final String MSG_NAME_TOO_LONG = "姓名不能超过 20 个字";
    public static final String MSG_DESCRIPTION = "请写明情况说明";
    public static final String MSG_AGREE_TERMS = "请先阅读并同意《用户协议》和《隐私政策》";
    public static final String MSG_AGREEMENT_UPDATED = "协议已更新，请重新阅读并同意";
    public static final String MSG_SAME_PHONE = "新手机号不能和原手机号相同";
    public static final String MSG_PHONE_NOT_REGISTERED = "这个手机号还没有注册";
    public static final String MSG_PHONE_REGISTERED = "这个手机号已经注册，可以直接登录";
    public static final String MSG_PHONE_TAKEN = "这个手机号已注册其他账号，请换一个手机号";
    public static final String MSG_PASSWORD_SAME = "新密码不能和当前密码相同";
    public static final String MSG_TEMP_PASSWORD_SAME = "不能使用短信中的临时密码";

    private AccountValidation() {
    }

    /** 去空格（含全角空格）后 1 开头的 11 位数字 */
    public static String phone(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace(" ", "").replace("\u3000", "").replace("\t", "");
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && phone.matches("1\\d{10}");
    }

    public static String trim(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /** 去空格、短横线后转大写 */
    public static String recoveryCode(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[\\s\\u3000-]", "").toUpperCase();
    }

    public static boolean isValidRecoveryCode(String code) {
        return code != null && code.matches("[A-Z0-9]{16}");
    }

    public static boolean isValidSmsCode(String code) {
        return code != null && code.matches("\\d{6}");
    }

    /** 8–20 位，至少 1 个字母和 1 个数字，只含 ASCII 可见字符且不含空格 */
    public static String passwordProblem(String password) {
        if (password == null || password.isEmpty()) {
            return MSG_PASSWORD_LENGTH;
        }
        if (!password.matches("[\\x21-\\x7E]{8,20}")) {
            return MSG_PASSWORD_LENGTH;
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            return MSG_PASSWORD_LETTER_DIGIT;
        }
        return null;
    }

    /** Unicode 码点计数（emoji 等按 1 个字） */
    public static int length(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    /** 姓名：去首尾空白后 1–20 个字 */
    public static String nameProblem(String name) {
        String trimmed = trim(name);
        if (trimmed.isEmpty()) {
            return MSG_NAME_EMPTY;
        }
        if (length(trimmed) > NAME_MAX) {
            return MSG_NAME_TOO_LONG;
        }
        return null;
    }

    /** 情况说明：去首尾空白后 1–200 个字 */
    public static String descriptionProblem(String description) {
        String trimmed = trim(description);
        return trimmed.isEmpty() || length(trimmed) > DESCRIPTION_MAX ? MSG_DESCRIPTION : null;
    }
}
