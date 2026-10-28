package com.zhimu.server.biz.today.support;

import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.common.enums.TodoCategory;
import com.zhimu.server.common.enums.TodoStatus;

import java.time.LocalDate;

/**
 * 待办归日规则（2.7.3）：日视图、周视图与首页“今日待办”数量共用，保证点进去的“待处理 N 项”与首页卡片一致。
 * 未关闭的预警类归今天；未关闭的休药期到期在到期日之后也归今天；其余按 due_date。
 */
public final class TodoBusinessDay {

    private TodoBusinessDay() {
    }

    public static LocalDate of(Todo todo, LocalDate today) {
        String type = todo.getTodoType() == null ? "" : todo.getTodoType();
        if (TodoCategory.WARNING.name().equals(todo.getCategory())) {
            if (isOpen(todo)) {
                return today;
            }
            return closedDay(todo);
        }
        if ("WITHDRAWAL_END".equals(type)) {
            if (isOpen(todo)) {
                return todo.getDueDate() != null && todo.getDueDate().isBefore(today)
                        ? today : todo.getDueDate();
            }
            LocalDate closed = closedDay(todo);
            if (closed == null) {
                return todo.getDueDate();
            }
            return todo.getDueDate() != null && todo.getDueDate().isAfter(closed) ? todo.getDueDate() : closed;
        }
        return todo.getDueDate();
    }

    /** 未完成且业务日就是今天（首页“今日待办”数量口径） */
    public static boolean isOpenToday(Todo todo, LocalDate today) {
        return isOpen(todo) && today.equals(of(todo, today));
    }

    private static LocalDate closedDay(Todo todo) {
        if (todo.getIgnoredAt() != null) {
            return todo.getIgnoredAt().toLocalDate();
        }
        if (todo.getCompletedAt() != null) {
            return todo.getCompletedAt().toLocalDate();
        }
        return todo.getDueDate();
    }

    private static boolean isOpen(Todo todo) {
        return TodoStatus.PENDING.name().equals(todo.getStatus());
    }
}
