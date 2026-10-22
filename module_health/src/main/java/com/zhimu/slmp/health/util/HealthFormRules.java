package com.zhimu.slmp.health.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 本模块与服务端一致的输入校验：每个方法通过时返回 null，否则返回错误文案（文案来自 UI 文档 2.5 节与服务端 40001）。
 */
public final class HealthFormRules {

    public static final BigDecimal MAX_DECIMAL = new BigDecimal("9999.999");
    public static final int MAX_DECIMAL_SCALE = 3;
    public static final int POSTPONE_REASON_MAX = 50;
    public static final int MEDICATION_REASON_MAX = 100;
    public static final int NOTE_MAX = 100;
    public static final int CHANGE_REASON_MAX = 200;
    public static final int PAUSE_REASON_MAX = 30;
    public static final int MAX_COURSE_DAYS = 90;
    public static final int MAX_CYCLE_DAYS = 365;
    public static final int MAX_PLAN_SPAN_DAYS = 366;

    public static final String MSG_DOSE = "剂量需大于 0";
    public static final String MSG_QUANTITY = "接种数量需大于 0";
    public static final String MSG_VACCINE = "请选择疫苗";
    public static final String MSG_METHOD = "请选择接种方式";
    public static final String MSG_DISINFECT_METHOD = "请选择消毒方式";
    public static final String MSG_LOT = "请选择库存批号";
    public static final String MSG_CONCENTRATION = "请按 1:500 的格式填写浓度";
    public static final String MSG_CONFIRM_CONCENTRATION = "请先确认本次浓度";

    private HealthFormRules() {
    }

    // ===== 数值 =====

    @Nullable
    public static BigDecimal parseDecimal(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        String s = text.toString().trim().replace(",", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    public static Integer parseInt(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        String s = text.toString().trim().replace(",", "");
        if (!s.matches("^\\d{1,9}$")) {
            return null;
        }
        return Integer.parseInt(s);
    }

    /** 大于 0、最多 3 位小数、不大于 9999.999 */
    @Nullable
    public static String positiveDecimal(@Nullable CharSequence text, @NonNull String message) {
        BigDecimal v = parseDecimal(text);
        if (v == null || v.signum() <= 0) {
            return message;
        }
        if (v.stripTrailingZeros().scale() > MAX_DECIMAL_SCALE) {
            return "最多 3 位小数";
        }
        if (v.compareTo(MAX_DECIMAL) > 0) {
            return "不能超过 9,999.999";
        }
        return null;
    }

    /** 剂量：“剂量需大于 0” */
    @Nullable
    public static String dose(@Nullable CharSequence text) {
        return positiveDecimal(text, MSG_DOSE);
    }

    /** 接种数量：正整数且不超过存栏（“不能超过存栏 200 头”） */
    @Nullable
    public static String vaccinateQuantity(@Nullable CharSequence text, int currentCount, @Nullable String unit) {
        Integer q = parseInt(text);
        if (q == null || q <= 0) {
            return MSG_QUANTITY;
        }
        if (q > currentCount) {
            return "不能超过存栏 " + NumberFormatter.formatQuantity(currentCount, unit);
        }
        return null;
    }

    /** 接种日期：不晚于今天（date_max）；不早于 date_min（补免时为计划日期） */
    @Nullable
    public static String vaccinateDate(@Nullable String date, @Nullable String min, @Nullable String max) {
        LocalDate d = DateFormatter.parseDate(date);
        if (d == null) {
            return "请选择接种日期";
        }
        LocalDate upper = DateFormatter.parseDate(max);
        LocalDate today = DateFormatter.today();
        if (d.isAfter(today) || (upper != null && d.isAfter(upper))) {
            return "接种日期不能晚于今天";
        }
        LocalDate lower = DateFormatter.parseDate(min);
        if (lower != null && d.isBefore(lower)) {
            return "接种日期不能早于 " + HealthFormat.date(min);
        }
        return null;
    }

    /** 接种日龄：不小于批次当前日龄，不大于 3650 */
    @Nullable
    public static String planAgeDay(@Nullable CharSequence text, int dayAge) {
        Integer v = parseInt(text);
        if (v == null) {
            return "请填写接种日龄";
        }
        if (v < dayAge) {
            return "不能早于批次当前日龄 " + dayAge + " 日龄";
        }
        if (v > ImmuneRules.MAX_AGE_DAY) {
            return "接种日龄不能超过 " + ImmuneRules.MAX_AGE_DAY;
        }
        return null;
    }

    /** 按日期的计划日期：不早于今天 */
    @Nullable
    public static String planDate(@Nullable String date) {
        LocalDate d = DateFormatter.parseDate(date);
        if (d == null) {
            return "请选择计划日期";
        }
        return d.isBefore(DateFormatter.today()) ? "计划日期不能早于今天" : null;
    }

    /** 调整计划日期：不早于今天、不等于原计划日期 */
    @Nullable
    public static String postponeDate(@Nullable String newDate, @Nullable String oldDate) {
        LocalDate d = DateFormatter.parseDate(newDate);
        if (d == null) {
            return "请选择新日期";
        }
        if (d.isBefore(DateFormatter.today())) {
            return "新日期不能早于今天";
        }
        LocalDate old = DateFormatter.parseDate(oldDate);
        if (d.equals(old)) {
            return "新日期与原计划日期相同";
        }
        return null;
    }

    /** 延期原因 1–50 字 */
    @Nullable
    public static String postponeReason(@Nullable String reason) {
        return requiredText(reason, "请填写延期原因", POSTPONE_REASON_MAX, "延期原因最多 50 字");
    }

    // ===== 用药 =====

    /** 疗程 1–90 天 */
    @Nullable
    public static String courseDays(@Nullable CharSequence text) {
        Integer v = parseInt(text);
        if (v == null || v < 1) {
            return "疗程至少 1 天";
        }
        return v > MAX_COURSE_DAYS ? "疗程不能超过 90 天" : null;
    }

    /** 首次用药日期不晚于今天 */
    @Nullable
    public static String firstDate(@Nullable String date) {
        LocalDate d = DateFormatter.parseDate(date);
        if (d == null) {
            return "请选择首次用药日期";
        }
        return d.isAfter(DateFormatter.today()) ? "首次用药日期不能晚于今天" : null;
    }

    /** 用药原因 1–100 字 */
    @Nullable
    public static String medicationReason(@Nullable String reason) {
        return requiredText(reason, "请填写用药原因", MEDICATION_REASON_MAX, "用药原因最多 100 字");
    }

    /** 超量说明 / 用药说明：需要时 1–100 字 */
    @Nullable
    public static String note(@Nullable String note, boolean required, @NonNull String emptyMessage) {
        if (!required) {
            return length(note) > NOTE_MAX ? "最多 100 字" : null;
        }
        return requiredText(note, emptyMessage, NOTE_MAX, "最多 100 字");
    }

    /** 修改申请原因 1–200 字（D05 删除用药记录） */
    @Nullable
    public static String changeReason(@Nullable String reason) {
        return requiredText(reason, "请填写申请原因", CHANGE_REASON_MAX, "申请原因最多 200 字");
    }

    /** 剂量超过推荐剂量（推荐值为空时不判断） */
    public static boolean isOverDose(@Nullable CharSequence dose, @Nullable BigDecimal recommended) {
        BigDecimal d = parseDecimal(dose);
        return d != null && recommended != null && recommended.signum() > 0 && d.compareTo(recommended) > 0;
    }

    // ===== 消毒 =====

    /** 周期 1–365 天 */
    @Nullable
    public static String cycleDays(@Nullable CharSequence text) {
        Integer v = parseInt(text);
        return v == null || v < 1 || v > MAX_CYCLE_DAYS ? "周期天数需为大于 0 的整数" : null;
    }

    /** 开始日期不早于今天（修改且原开始日期已过时不校验） */
    @Nullable
    public static String startDate(@Nullable String date, boolean skipPastCheck) {
        LocalDate d = DateFormatter.parseDate(date);
        if (d == null) {
            return "请选择开始日期";
        }
        return !skipPastCheck && d.isBefore(DateFormatter.today()) ? "开始日期不能早于今天" : null;
    }

    /** 结束日期不早于开始日期，跨度不超过 366 天 */
    @Nullable
    public static String endDate(@Nullable String start, @Nullable String end) {
        LocalDate e = DateFormatter.parseDate(end);
        if (e == null) {
            return "请选择结束日期";
        }
        LocalDate s = DateFormatter.parseDate(start);
        if (s == null) {
            return null;
        }
        if (e.isBefore(s)) {
            return "结束日期不能早于开始日期";
        }
        return ChronoUnit.DAYS.between(s, e) > MAX_PLAN_SPAN_DAYS ? "起止日期跨度不能超过 366 天" : null;
    }

    /** 浓度格式 1:正整数 */
    @Nullable
    public static String concentration(@Nullable String text) {
        return ConcentrationUtil.isValid(text) ? null : MSG_CONCENTRATION;
    }

    /** 消毒用量：大于 0，不超过所选批号结存（“不能超过批号 2025033 结存 2.0 升”） */
    @Nullable
    public static String usageQty(@Nullable CharSequence text, @Nullable BigDecimal stock, @Nullable String lotNo,
                                  @Nullable String unit) {
        String e = positiveDecimal(text, "用量需大于 0");
        if (e != null) {
            return e;
        }
        BigDecimal v = parseDecimal(text);
        if (stock != null && v != null && v.compareTo(stock) > 0) {
            return "不能超过批号 " + HealthFormat.nonNull(lotNo) + " 结存 " + NumberFormatter.formatQuantity(stock, unit);
        }
        return null;
    }

    /** 实际时间：不晚于现在；补录时不早于任务日期 00:00 */
    @Nullable
    public static String actualTime(@Nullable String datetime, @Nullable String minDate, @NonNull LocalDateTime now) {
        LocalDateTime t = DateFormatter.parseDateTime(datetime);
        if (t == null) {
            return "请选择实际时间";
        }
        if (t.isAfter(now)) {
            return "实际时间不能晚于现在";
        }
        LocalDate min = DateFormatter.parseDate(minDate);
        if (min != null && t.toLocalDate().isBefore(min)) {
            return "实际时间不能早于任务日期 " + HealthFormat.date(minDate);
        }
        return null;
    }

    // ===== 通用 =====

    @Nullable
    public static String requiredText(@Nullable String text, @NonNull String emptyMessage, int max,
                                      @NonNull String tooLongMessage) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return emptyMessage;
        }
        return length(t) > max ? tooLongMessage : null;
    }

    /** 按字符（码点）计数，汉字算 1 个 */
    public static int length(@Nullable String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    @Nullable
    public static String trimToNull(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        return t.isEmpty() ? null : t;
    }
}
