package com.zhimu.server.biz.feeding.support;

import com.zhimu.server.biz.feeding.entity.FeedTask;
import com.zhimu.server.biz.herd.api.BatchInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 模块06 计算与展示口径（技术准备文档 2.7）。 */
public final class FeedCalc {

    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_OVERDUE = "OVERDUE";
    public static final String TASK_DONE = "DONE";
    public static final String TASK_MANUAL_DONE = "MANUAL_DONE";
    public static final String TASK_IGNORED = "IGNORED";
    public static final String TASK_MISSED = "MISSED";
    public static final String TASK_CANCELLED = "CANCELLED";

    public static final String MEAL_BREAKFAST = "BREAKFAST";
    public static final String MEAL_LUNCH = "LUNCH";
    public static final String MEAL_DINNER = "DINNER";
    public static final String MEAL_EXTRA = "EXTRA";

    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private FeedCalc() {
    }

    public static BigDecimal round2(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal round4(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal round1(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
    }

    /** 日龄 = 入栏日龄 +（日期 − 入栏日期） */
    public static int dayAge(BatchInfo batch, LocalDate date) {
        if (batch == null || batch.entryDate() == null) {
            return 0;
        }
        int entryAge = batch.entryAgeDays() == null ? 0 : batch.entryAgeDays();
        return entryAge + (int) ChronoUnit.DAYS.between(batch.entryDate(), date);
    }

    /** 任务日期 D 使用 D 前一天的日龄；入栏当天用入栏日龄 */
    public static int generationDayAge(BatchInfo batch, LocalDate taskDate) {
        LocalDate useDate = taskDate.minusDays(1);
        if (batch.entryDate() != null && useDate.isBefore(batch.entryDate())) {
            useDate = batch.entryDate();
        }
        return dayAge(batch, useDate);
    }

    /** 每餐计划量 = 每头日投喂量 × 在栏数 ÷ 餐次，四舍五入 2 位 */
    public static BigDecimal planQty(BigDecimal dailyQtyPerHead, Integer headCount, Integer mealsPerDay) {
        if (dailyQtyPerHead == null || headCount == null || mealsPerDay == null || mealsPerDay <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return dailyQtyPerHead.multiply(BigDecimal.valueOf(headCount))
                .divide(BigDecimal.valueOf(mealsPerDay), 2, RoundingMode.HALF_UP);
    }

    /** 偏差 =（实投 − 计划）÷ 计划，保留 4 位 */
    public static BigDecimal deviation(BigDecimal actualQty, BigDecimal planQty) {
        if (actualQty == null || planQty == null || planQty.signum() == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return actualQty.subtract(planQty).divide(planQty, 4, RoundingMode.HALF_UP);
    }

    /** 偏差绝对值大于阈值才算异常；等于阈值正常 */
    public static String abnormalType(BigDecimal deviationRatio, BigDecimal threshold) {
        if (deviationRatio == null || threshold == null) {
            return "NONE";
        }
        if (deviationRatio.abs().compareTo(threshold) <= 0) {
            return "NONE";
        }
        return deviationRatio.signum() < 0 ? "UNDER" : "OVER";
    }

    public static String abnormalTypeText(String abnormalType) {
        return switch (abnormalType == null ? "" : abnormalType) {
            case "UNDER" -> "少投";
            case "OVER" -> "多投";
            default -> "正常";
        };
    }

    /** 按投喂时间命名：10:00 前早餐、10:00–14:59 午餐、15:00 起晚餐；同阶段重名时后一餐为加餐 */
    public static List<MealSlot> mealSlots(List<String> times) {
        if (times == null || times.isEmpty()) {
            return List.of();
        }
        List<LocalTime> parsed = new ArrayList<>();
        for (String time : times) {
            LocalTime value = parseTime(time);
            if (value != null) {
                parsed.add(value);
            }
        }
        parsed.sort(Comparator.naturalOrder());
        List<MealSlot> slots = new ArrayList<>();
        int breakfast = 0;
        int lunch = 0;
        int dinner = 0;
        for (int i = 0; i < parsed.size(); i++) {
            LocalTime time = parsed.get(i);
            String code;
            String name;
            if (time.isBefore(LocalTime.of(10, 0))) {
                code = MEAL_BREAKFAST;
                name = "早餐";
                breakfast++;
                if (breakfast > 1) {
                    code = MEAL_EXTRA;
                    name = "加餐";
                }
            } else if (time.isBefore(LocalTime.of(15, 0))) {
                code = MEAL_LUNCH;
                name = "午餐";
                lunch++;
                if (lunch > 1) {
                    code = MEAL_EXTRA;
                    name = "加餐";
                }
            } else {
                code = MEAL_DINNER;
                name = "晚餐";
                dinner++;
                if (dinner > 1) {
                    code = MEAL_EXTRA;
                    name = "加餐";
                }
            }
            slots.add(new MealSlot(i + 1, code, name, time));
        }
        return slots;
    }

    public static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            String[] parts = text.split(":");
            if (parts.length < 2) {
                return null;
            }
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            return LocalTime.of(hour, minute);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 任务当前应显示的状态：PENDING 且已过 deadline_at 按 OVERDUE 返回 */
    public static String effectiveStatus(FeedTask task, LocalDateTime now) {
        if (task == null) {
            return null;
        }
        String status = task.getStatus();
        if (TASK_PENDING.equals(status) && task.getDeadlineAt() != null && !now.isBefore(task.getDeadlineAt())) {
            return TASK_OVERDUE;
        }
        return status;
    }

    public static String statusText(String status) {
        return switch (status == null ? "" : status) {
            case TASK_PENDING -> "待投喂";
            case TASK_OVERDUE -> "已逾期";
            case TASK_DONE -> "已投喂";
            case TASK_MANUAL_DONE -> "手动完成";
            case TASK_IGNORED -> "已忽略";
            case TASK_MISSED -> "未执行";
            case TASK_CANCELLED -> "已取消";
            default -> "";
        };
    }

    public static String weekdayText(LocalDate date) {
        return date == null ? null : WEEKDAYS[date.getDayOfWeek().getValue() - 1];
    }

    public static int overdueMinutes(FeedTask task, LocalDateTime now) {
        if (task == null || task.getFeedAt() == null) {
            return 0;
        }
        long minutes = ChronoUnit.MINUTES.between(task.getFeedAt(), now);
        return minutes <= 0 ? 0 : (int) minutes;
    }

    public static String growthStatusText(String status) {
        return switch (status == null ? "" : status) {
            case "SLOW" -> "生长偏慢";
            case "FAST" -> "生长偏快";
            case "NORMAL" -> "正常";
            case "NO_STD" -> "无标准";
            default -> "未称重";
        };
    }

    public static String growthResultText(String result) {
        return switch (result == null ? "" : result) {
            case "SLOW" -> "生长偏慢";
            case "FAST" -> "生长偏快";
            case "NORMAL" -> "正常";
            default -> "无标准";
        };
    }

    public static String lower(String text) {
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    /** 计量单位显示 */
    public static String unit(String unit) {
        return unit == null || unit.isBlank() ? "KG" : unit;
    }

    /** 阶段餐次 */
    public record MealSlot(int mealNo, String mealCode, String mealName, LocalTime feedTime) {
    }
}
