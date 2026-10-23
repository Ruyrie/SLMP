package com.zhimu.server.biz.breeding.support;

import com.zhimu.server.common.error.BizException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** 模块05 的表单字段校验（文案与模块05 文档 2.5 节、App Mock 一致）。 */
public final class BreedingValidations {

    private static final Pattern EAR_TAG = Pattern.compile("^[A-Za-z0-9-]{1,20}$");

    private BreedingValidations() {
    }

    /** 按 Unicode 码点计数（中文、emoji 都算 1 个字） */
    public static int length(String value) {
        if (value == null) {
            return 0;
        }
        return value.codePointCount(0, value.length());
    }

    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static String requireEarTag(String earTag) {
        String value = trim(earTag);
        if (value.isEmpty()) {
            throw BreedingErrors.invalid("ear_tag", "请填写耳号");
        }
        if (!EAR_TAG.matcher(value).matches()) {
            throw BreedingErrors.invalid("ear_tag", "耳号最多 20 个字，只能包含字母、数字和短横线");
        }
        return value;
    }

    public static String requireText(String field, String value, String emptyMessage, int max, String tooLongMessage) {
        String text = trim(value);
        if (text.isEmpty()) {
            throw BreedingErrors.invalid(field, emptyMessage);
        }
        if (length(text) > max) {
            throw BreedingErrors.invalid(field, tooLongMessage);
        }
        return text;
    }

    public static String optionalText(String field, String value, int max, String tooLongMessage) {
        String text = trim(value);
        if (text.isEmpty()) {
            return null;
        }
        if (length(text) > max) {
            throw BreedingErrors.invalid(field, tooLongMessage);
        }
        return text;
    }

    public static LocalDate date(String field, String value, String emptyMessage) {
        String text = trim(value);
        if (text.isEmpty()) {
            throw BreedingErrors.invalid(field, emptyMessage);
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw BreedingErrors.invalid(field, emptyMessage);
        }
    }

    public static LocalDate optionalDate(String field, String value, String invalidMessage) {
        String text = trim(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw BreedingErrors.invalid(field, invalidMessage);
        }
    }

    public static void notFuture(String field, LocalDate date, String message) {
        if (date != null && date.isAfter(BreedingDates.today())) {
            throw BreedingErrors.invalid(field, message);
        }
    }

    /** 修改类请求的 version 必填 */
    public static int requireVersion(Integer version) {
        if (version == null) {
            throw BreedingErrors.versionConflict();
        }
        return version;
    }

    public static void checkVersion(Integer requestVersion, Integer actualVersion) {
        requireVersion(requestVersion);
        if (!requestVersion.equals(actualVersion)) {
            throw BreedingErrors.versionConflict();
        }
    }

    public static void intRange(String field, Integer value, int min, int max, String message) {
        if (value == null || value < min || value > max) {
            throw BreedingErrors.invalid(field, message);
        }
    }

    public static BigDecimal optionalWeight(String field, BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0
                || value.compareTo(new BigDecimal("9999.9")) > 0
                || value.stripTrailingZeros().scale() > 1) {
            throw BreedingErrors.invalid(field, "请输入大于 0 的重量，最多 1 位小数");
        }
        return value;
    }

    public static BizException invalid(String field, String message) {
        return BreedingErrors.invalid(field, message);
    }
}
