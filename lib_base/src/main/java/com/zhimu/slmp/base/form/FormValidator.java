package com.zhimu.slmp.base.form;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 表单校验：按调用顺序检查，每个字段只保留第一条错误；返回“字段 → 提示文案”（字段名与请求体 JSON 字段一致，
 * 便于和服务端 40001 的 field_errors 合并显示）。
 * <pre>
 * Map&lt;String, String&gt; errors = new FormValidator()
 *         .phone("phone", phone)
 *         .password("password", password)
 *         .validate();
 * </pre>
 */
public class FormValidator {

    public static final String MSG_PHONE_EMPTY = "请输入手机号";
    public static final String MSG_PHONE_INVALID = "手机号格式不正确，请输入 11 位手机号";
    public static final String MSG_PASSWORD_EMPTY = "请输入密码";
    public static final String MSG_PASSWORD_INVALID = "密码需为 8–20 位，并同时包含字母和数字";
    public static final String MSG_SMS_CODE_INVALID = "请输入 6 位验证码";

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern SMS_CODE = Pattern.compile("^\\d{6}$");
    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");

    private final Map<String, String> errors = new LinkedHashMap<>();

    /** 必填：null 或去空格后为空 */
    public FormValidator required(@NonNull String field, @Nullable CharSequence value, @NonNull String message) {
        if (!has(field) && isBlank(value)) {
            errors.put(field, message);
        }
        return this;
    }

    /** 必填（非文本，例如选择项） */
    public FormValidator requiredValue(@NonNull String field, @Nullable Object value, @NonNull String message) {
        if (!has(field) && (value == null || (value instanceof CharSequence && isBlank((CharSequence) value)))) {
            errors.put(field, message);
        }
        return this;
    }

    /** 长度上限（按字符数，空值不检查） */
    public FormValidator maxLength(@NonNull String field, @Nullable CharSequence value, int max,
                                   @NonNull String message) {
        if (!has(field) && value != null && length(value) > max) {
            errors.put(field, message);
        }
        return this;
    }

    /** 长度范围（空值不检查，必填请另加 required） */
    public FormValidator length(@NonNull String field, @Nullable CharSequence value, int min, int max,
                                @NonNull String message) {
        if (!has(field) && !isBlank(value)) {
            int len = length(value);
            if (len < min || len > max) {
                errors.put(field, message);
            }
        }
        return this;
    }

    /** 数值范围（含两端；空值不检查；无法解析视为不合格） */
    public FormValidator range(@NonNull String field, @Nullable CharSequence value, @Nullable BigDecimal min,
                               @Nullable BigDecimal max, @NonNull String message) {
        if (has(field) || isBlank(value)) {
            return this;
        }
        BigDecimal v = parseDecimal(value);
        if (v == null || (min != null && v.compareTo(min) < 0) || (max != null && v.compareTo(max) > 0)) {
            errors.put(field, message);
        }
        return this;
    }

    public FormValidator range(@NonNull String field, @Nullable CharSequence value, long min, long max,
                               @NonNull String message) {
        return range(field, value, BigDecimal.valueOf(min), BigDecimal.valueOf(max), message);
    }

    /** 小数位数上限（例如金额 2 位） */
    public FormValidator maxScale(@NonNull String field, @Nullable CharSequence value, int scale,
                                  @NonNull String message) {
        if (has(field) || isBlank(value)) {
            return this;
        }
        BigDecimal v = parseDecimal(value);
        if (v == null || v.stripTrailingZeros().scale() > scale) {
            errors.put(field, message);
        }
        return this;
    }

    /** 手机号：必填 + 11 位且 1 开头（允许输入框中的空格） */
    public FormValidator phone(@NonNull String field, @Nullable CharSequence value) {
        required(field, value, MSG_PHONE_EMPTY);
        if (!has(field) && !isPhone(value)) {
            errors.put(field, MSG_PHONE_INVALID);
        }
        return this;
    }

    /** 密码：必填 + 8–20 位，同时包含字母和数字 */
    public FormValidator password(@NonNull String field, @Nullable CharSequence value) {
        required(field, value, MSG_PASSWORD_EMPTY);
        if (!has(field) && !isPassword(value)) {
            errors.put(field, MSG_PASSWORD_INVALID);
        }
        return this;
    }

    /** 6 位数字验证码 */
    public FormValidator smsCode(@NonNull String field, @Nullable CharSequence value) {
        if (!has(field) && (value == null || !SMS_CODE.matcher(value.toString().trim()).matches())) {
            errors.put(field, MSG_SMS_CODE_INVALID);
        }
        return this;
    }

    /** 自定义条件：condition 为 false 时记错 */
    public FormValidator check(@NonNull String field, boolean condition, @NonNull String message) {
        if (!has(field) && !condition) {
            errors.put(field, message);
        }
        return this;
    }

    /** 字段 → 提示文案（按校验顺序） */
    @NonNull
    public Map<String, String> validate() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /** 第一个出错的字段（用于滚动定位），没有返回 null */
    @Nullable
    public String firstErrorField() {
        return errors.isEmpty() ? null : errors.keySet().iterator().next();
    }

    // ===== 静态工具 =====

    public static boolean isPhone(@Nullable CharSequence value) {
        return value != null && PHONE.matcher(digitsOnly(value)).matches();
    }

    public static boolean isPassword(@Nullable CharSequence value) {
        if (value == null) {
            return false;
        }
        String s = value.toString();
        return s.length() >= 8 && s.length() <= 20 && HAS_LETTER.matcher(s).matches() && HAS_DIGIT.matcher(s).matches();
    }

    /** 去掉手机号输入中的空格与短横（3-4-4 显示） */
    @NonNull
    public static String digitsOnly(@NonNull CharSequence value) {
        return value.toString().replaceAll("[\\s-]", "");
    }

    @Nullable
    public static BigDecimal parseDecimal(@Nullable CharSequence value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean has(String field) {
        return errors.containsKey(field);
    }

    private static boolean isBlank(@Nullable CharSequence value) {
        return value == null || value.toString().trim().isEmpty();
    }

    private static int length(@NonNull CharSequence value) {
        String s = value.toString();
        return s.codePointCount(0, s.length());
    }
}
