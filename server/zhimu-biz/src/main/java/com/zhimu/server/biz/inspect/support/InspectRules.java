package com.zhimu.server.biz.inspect.support;

import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/** 巡检状态、时间、异常文案等纯规则。 */
public final class InspectRules {

    public static final LocalTime DEFAULT_DEADLINE = LocalTime.of(18, 0);
    public static final LocalTime MIN_DEADLINE = LocalTime.of(6, 0);
    public static final LocalTime MAX_DEADLINE = LocalTime.of(23, 0);
    public static final int BACKFILL_DAYS = 3;
    public static final int EDIT_DAYS = 3;
    public static final int ON_SITE_METERS = 1000;

    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";
    public static final String MISSED = "MISSED";
    public static final String CANCELLED = "CANCELLED";

    private InspectRules() {
    }

    public static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim(), BeijingTime.DATE);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "日期格式不正确");
        }
    }

    public static LocalTime parseDeadline(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择巡检截止时间");
        }
        try {
            LocalTime time = LocalTime.parse(value.trim(), BeijingTime.TIME);
            if (!time.equals(time.withSecond(0).withNano(0))) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请选择 06:00–23:00 之间的时间");
            }
            if (time.isBefore(MIN_DEADLINE) || time.isAfter(MAX_DEADLINE)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "请选择 06:00–23:00 之间的时间");
            }
            return time;
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择 06:00–23:00 之间的时间");
        }
    }

    public static String formatTime(LocalTime time) {
        return time == null ? null : time.format(BeijingTime.TIME);
    }

    public static LocalTime remindTime(LocalTime deadline, Integer minutes) {
        LocalTime base = deadline == null ? DEFAULT_DEADLINE : deadline;
        int before = minutes == null ? 60 : minutes;
        return base.minusMinutes(before);
    }

    public static boolean deadlinePassed(LocalDate date, LocalTime deadline, LocalDateTime now) {
        LocalTime time = deadline == null ? DEFAULT_DEADLINE : deadline;
        return now.isAfter(date.atTime(time));
    }

    public static boolean withinEditWindow(LocalDate date) {
        return BeijingTime.now().isBefore(BeijingTime.editDeadline(date, EDIT_DAYS));
    }

    public static LocalDate backfillUntil(LocalDate inspectDate) {
        return inspectDate.plusDays(BACKFILL_DAYS);
    }

    public static boolean backfillExpired(LocalDate inspectDate) {
        return BeijingTime.today().isAfter(backfillUntil(inspectDate));
    }

    public static boolean inBackfillWindow(LocalDate date) {
        LocalDate today = BeijingTime.today();
        return date.isAfter(today.minusDays(BACKFILL_DAYS)) && date.isBefore(today);
    }

    public static String statusText(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case DONE -> "已巡";
            case MISSED -> "漏巡";
            case CANCELLED -> "已取消";
            default -> "未巡";
        };
    }

    public static String displayStatus(Inspection inspection) {
        if (inspection == null || inspection.getInspectStatus() == null) {
            return PENDING;
        }
        return switch (inspection.getInspectStatus()) {
            case DONE -> Boolean.TRUE.equals(inspection.getIsAbnormal()) ? "ABNORMAL" : DONE;
            case MISSED -> MISSED;
            default -> PENDING;
        };
    }

    public static String displayStatusText(String displayStatus) {
        return switch (displayStatus) {
            case "ABNORMAL" -> "异常";
            case MISSED -> "漏巡";
            case DONE -> "已巡";
            default -> "未巡";
        };
    }

    public static String locationText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "ON_SITE" -> "现场";
            case "OFF_SITE" -> "非现场";
            default -> "无位置";
        };
    }

    public static String issueTypeText(String type) {
        return switch (type) {
            case "OVERDUE" -> "超时";
            case "BACKFILL" -> "补录";
            case "MISSED" -> "漏巡";
            default -> type;
        };
    }

    public static BigDecimal sickRate(int sickCount, int stockSnapshot) {
        if (stockSnapshot <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(sickCount)
                .divide(BigDecimal.valueOf(stockSnapshot), 4, RoundingMode.HALF_UP);
    }

    public static String summaryOf(java.util.List<String> abnormalItems) {
        if (abnormalItems == null || abnormalItems.isEmpty()) {
            return null;
        }
        String text = String.join("、", abnormalItems);
        return text.length() > 100 ? text.substring(0, 100) : text;
    }

    public static void requirePenAccess(Long penId) {
        com.zhimu.server.common.context.FarmContext.checkPen(penId);
    }
}
