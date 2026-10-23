package com.zhimu.server.biz.today.support;

import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.common.enums.TodoCategory;
import com.zhimu.server.common.enums.TodoStatus;
import com.zhimu.server.common.enums.TodoType;
import com.zhimu.server.common.util.BeijingTime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 待办与消息的中文显示文字（2.7.6、2.7.7）：全部由后端生成，员工视图不拼负责人与金额。
 * 时间一律北京时间，不依赖手机时区。
 */
public final class TodayText {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M月d日");
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private TodayText() {
    }

    /** 问候语：05:00–10:59 早上好，11:00–12:59 中午好，13:00–17:59 下午好，其余晚上好 */
    public static String greeting(LocalDateTime now) {
        int hour = now.getHour();
        if (hour >= 5 && hour < 11) {
            return "早上好";
        }
        if (hour >= 11 && hour < 13) {
            return "中午好";
        }
        if (hour >= 13 && hour < 18) {
            return "下午好";
        }
        return "晚上好";
    }

    public static String monthDay(LocalDate date) {
        return date == null ? null : date.format(MONTH_DAY);
    }

    public static String weekday(LocalDate date) {
        return date == null ? null : WEEKDAYS[date.getDayOfWeek().getValue() - 1];
    }

    /** 10月19日 周一 */
    public static String dateWithWeekday(LocalDate date) {
        return date == null ? null : monthDay(date) + " " + weekday(date);
    }

    public static String hm(LocalDateTime time) {
        return time == null ? null : time.format(HOUR_MINUTE);
    }

    /** yyyy-MM-dd HH:mm:ss（接口中的 datetime） */
    public static String dateTime(LocalDateTime time) {
        return time == null ? null : time.format(BeijingTime.DATETIME);
    }

    public static String hm(LocalTime time) {
        return time == null ? null : time.format(HOUR_MINUTE);
    }

    /** 日视图时间轴：全天 / 07:00 */
    public static String timeLabel(Todo todo) {
        if ("ALL_DAY".equals(todo.getTimeType()) || todo.getDueTime() == null) {
            return "全天";
        }
        return hm(todo.getDueTime());
    }

    /** 展示状态：DONE / IGNORED 落库；PENDING 且已过 overdue_at 为 OVERDUE */
    public static TodoStatus displayStatus(Todo todo, LocalDateTime now) {
        if (TodoStatus.DONE.name().equals(todo.getStatus())) {
            return TodoStatus.DONE;
        }
        if (TodoStatus.IGNORED.name().equals(todo.getStatus())) {
            return TodoStatus.IGNORED;
        }
        if (todo.getOverdueAt() != null && now.isAfter(todo.getOverdueAt())) {
            return TodoStatus.OVERDUE;
        }
        return TodoStatus.PENDING;
    }

    /** 已逾期天数：今天减 due_date；当天逾期（定时已过）返回 null */
    public static Integer overdueDays(Todo todo, LocalDate today) {
        if (todo.getDueDate() == null || !todo.getDueDate().isBefore(today)) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(todo.getDueDate(), today);
    }

    /** 尾部标签：已逾期 / 预警 / 今天 / 11:30 / 17:00 前 / 待处理 / 已完成 / 已忽略 */
    public static String tailText(Todo todo, LocalDate today, LocalDateTime now) {
        TodoStatus status = displayStatus(todo, now);
        if (status == TodoStatus.DONE) {
            return "已完成";
        }
        if (status == TodoStatus.IGNORED) {
            return "已忽略";
        }
        if (status == TodoStatus.OVERDUE) {
            return "已逾期";
        }
        if (TodoCategory.WARNING.name().equals(todo.getCategory())) {
            return "预警";
        }
        if (todo.getDueDate() != null && todo.getDueDate().equals(today)) {
            if ("ALL_DAY".equals(todo.getTimeType()) || todo.getDueTime() == null) {
                return "今天";
            }
            if ("BEFORE_TIME".equals(todo.getTimeType())) {
                return hm(todo.getDueTime()) + " 前";
            }
            return hm(todo.getDueTime());
        }
        return "待处理";
    }

    public static String todoTypeText(Todo todo) {
        TodoType type = com.zhimu.server.common.enums.CodeEnum.of(TodoType.class, todo.getTodoType());
        return type == null ? todo.getTodoType() : type.getText();
    }

    public static String categoryText(Todo todo) {
        TodoCategory category = com.zhimu.server.common.enums.CodeEnum.of(TodoCategory.class, todo.getCategory());
        return category == null ? todo.getCategory() : category.getText();
    }

    public static String displayStatusText(Todo probe, LocalDateTime now) {
        return displayStatus(probe, now).getText();
    }

    /** 来源模块名（2.4）；模块未接入时按类型给出约定名称 */
    public static String sourceModuleName(String todoType) {
        if (todoType == null) {
            return null;
        }
        return switch (todoType) {
            case "FEEDING", "WEIGHING" -> "饲喂与称重";
            case "INSPECTION" -> "巡检管理";
            case "IMMUNIZATION", "DISINFECTION" -> "免疫与消毒";
            case "FARROWING", "HATCHING" -> "繁育管理";
            case "FEED_LOW_STOCK", "DRUG_LOW_STOCK" -> "物资库存";
            case "DRUG_NEAR_EXPIRY", "DRUG_EXPIRED" -> "药品批号";
            case "WITHDRAWAL_END" -> "用药与休药期管理";
            case "RECEIVABLE" -> "出栏与收款";
            default -> "待办";
        };
    }

    /** 完成条件（T04） */
    public static String completeCondition(String todoType) {
        if (todoType == null) {
            return "登记对应记录后自动完成";
        }
        return switch (todoType) {
            case "FEEDING" -> "登记投喂后自动完成";
            case "INSPECTION" -> "登记巡检后自动完成";
            case "IMMUNIZATION" -> "登记接种后自动完成";
            case "DISINFECTION" -> "登记消毒后自动完成";
            case "WEIGHING" -> "登记称重后自动完成";
            case "FARROWING" -> "登记产仔后自动完成";
            case "HATCHING" -> "登记出雏后自动完成";
            case "FEED_LOW_STOCK", "DRUG_LOW_STOCK" -> "补货恢复正常后自动完成";
            case "DRUG_NEAR_EXPIRY", "DRUG_EXPIRED" -> "批号结存清零或报废后自动完成";
            case "WITHDRAWAL_END" -> "登记出栏后自动完成";
            case "RECEIVABLE" -> "收款合计等于销售金额后自动完成";
            default -> "登记对应记录后自动完成";
        };
    }

    /** 标记完成对话框正文（只用于作业类） */
    public static String markDoneTip(String todoType) {
        if (todoType == null) {
            return null;
        }
        return switch (todoType) {
            case "FEEDING" -> "手动标记完成不会自动生成投喂记录，饲料库存也不会扣减。建议去登记饲喂。";
            case "DISINFECTION" -> "手动标记完成不会自动生成消毒记录，消毒剂库存也不会扣减。建议去登记消毒。";
            case "IMMUNIZATION" -> "手动标记完成不会自动生成接种记录，疫苗库存也不会扣减。建议去登记接种。";
            case "INSPECTION" -> "手动标记完成不会自动生成巡检记录。建议去登记巡检。";
            case "WEIGHING" -> "手动标记完成不会自动生成称重记录。建议去登记称重。";
            case "FARROWING" -> "手动标记完成不会自动生成产仔记录。建议去登记产仔。";
            case "HATCHING" -> "手动标记完成不会自动生成出雏记录。建议去登记出雏。";
            default -> null;
        };
    }

    /** 说明横幅（2.7.7） */
    public static String noticeText(Todo todo, boolean staff, LocalDateTime now) {
        if (staff) {
            return "登记对应记录后，待办自动完成。需要调整计划时，请联系所有者或场长。";
        }
        if (TodoCategory.WARNING.name().equals(todo.getCategory())
                || TodoCategory.BUSINESS.name().equals(todo.getCategory())) {
            return "经营类和预警类待办只能在登记对应记录后自动完成，不能手动标记完成。不需要处理时可以忽略。";
        }
        if (displayStatus(todo, now) == TodoStatus.OVERDUE) {
            return "现在登记" + actionName(todo.getTodoType()) + "，这条待办算作逾期完成。";
        }
        return null;
    }

    /** 工人看到的动作名：饲喂、消毒、接种…… */
    public static String actionName(String todoType) {
        return switch (todoType == null ? "" : todoType) {
            case "FEEDING" -> "饲喂";
            case "INSPECTION" -> "巡检";
            case "IMMUNIZATION" -> "接种";
            case "DISINFECTION" -> "消毒";
            case "WEIGHING" -> "称重";
            case "FARROWING" -> "产仔";
            case "HATCHING" -> "出雏";
            case "WITHDRAWAL_END" -> "出栏";
            case "RECEIVABLE" -> "收款";
            default -> "记录";
        };
    }

    /** Unicode 码点长度（忽略首尾空格后） */
    public static int codePointLength(String text) {
        if (text == null) {
            return 0;
        }
        String trimmed = text.trim();
        return trimmed.codePointCount(0, trimmed.length());
    }

    public static String trim(String text) {
        return text == null ? null : text.trim();
    }

    /** 按当前北京时间格式化为 yyyy-MM-dd HH:mm:ss */
    public static String nowText() {
        return BeijingTime.format(BeijingTime.now());
    }
}
