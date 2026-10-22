package com.zhimu.slmp.account.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.form.FormValidator;

/**
 * 模块01 表单校验规则与文案（2.4 通用约定、UI 设计文档 2.5 节）。
 * 手机号、6 位验证码沿用 FormValidator；新密码分“长度”“字母和数字”两条文案。
 */
public final class AccountForm {

    public static final String MSG_PASSWORD_EMPTY = FormValidator.MSG_PASSWORD_EMPTY;
    public static final String MSG_PASSWORD_LENGTH = "密码需要 8–20 位";
    public static final String MSG_PASSWORD_MIX = "密码需同时包含字母和数字";
    public static final String MSG_PASSWORD_MISMATCH = "两次输入的密码不一致";
    public static final String MSG_PASSWORD_SAME_AS_CURRENT = "新密码不能和当前密码相同";
    public static final String MSG_AGREE_TERMS = "请先阅读并同意《用户协议》和《隐私政策》";
    public static final String MSG_RECOVERY_CODE = "恢复码应为 16 位字母和数字";
    public static final String MSG_SAME_PHONE = "新手机号不能和原手机号相同";
    public static final String MSG_DESCRIPTION = "请写明情况说明";
    public static final String MSG_NAME_EMPTY = "请输入姓名";
    public static final String MSG_NAME_TOO_LONG = "姓名不能超过 20 个字";
    public static final String MSG_CANCEL_CONFIRM = "请先勾选“我已了解注销后果”";
    public static final String MSG_PHONE_NOT_REGISTERED = "这个手机号还没有注册";
    public static final String MSG_NETWORK = "网络不可用，已填写的内容会保留，请检查网络后重试";
    public static final String MSG_TOO_FREQUENT = "获取验证码太频繁，请稍后再试";
    public static final String MSG_SMS_SEND_FAILED = "验证码发送失败，请稍后重试";

    /** 40001 的 data.reason：手机号未注册或已注销 */
    public static final String REASON_PHONE_NOT_REGISTERED = "PHONE_NOT_REGISTERED";

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 20;
    public static final int NAME_MAX = 20;
    public static final int DESCRIPTION_MAX = 200;

    private AccountForm() {
    }

    /** 新密码规则：必填，8–20 位，同时包含字母和数字 */
    @NonNull
    public static FormValidator newPassword(@NonNull FormValidator v, @NonNull String field, @Nullable String value) {
        v.required(field, value, MSG_PASSWORD_EMPTY);
        String s = value == null ? "" : value;
        v.check(field, s.length() >= PASSWORD_MIN && s.length() <= PASSWORD_MAX, MSG_PASSWORD_LENGTH);
        v.check(field, s.matches(".*[A-Za-z].*") && s.matches(".*\\d.*"), MSG_PASSWORD_MIX);
        return v;
    }

    /** 去空格后的手机号 */
    @NonNull
    public static String phone(@Nullable String value) {
        return PhoneFormatWatcher.digits(value);
    }

    @NonNull
    public static String text(@Nullable String value) {
        return value == null ? "" : value;
    }

    @NonNull
    public static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    public static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 按字符数计算长度（含中文与表情） */
    public static int length(@Nullable String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }
}
