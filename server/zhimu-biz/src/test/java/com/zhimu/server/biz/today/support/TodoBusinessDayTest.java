package com.zhimu.server.biz.today.support;

import com.zhimu.server.biz.today.entity.Todo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TodoBusinessDayTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private static Todo todo(String category, String type, String status, LocalDate due) {
        Todo todo = new Todo();
        todo.setCategory(category);
        todo.setTodoType(type);
        todo.setStatus(status);
        todo.setDueDate(due);
        return todo;
    }

    @Test
    void openWarningFromEarlierDayCountsAsToday() {
        Todo warning = todo("WARNING", "ABNORMAL_REPORT", "PENDING", TODAY.minusDays(5));
        assertThat(TodoBusinessDay.of(warning, TODAY)).isEqualTo(TODAY);
        assertThat(TodoBusinessDay.isOpenToday(warning, TODAY)).isTrue();
    }

    @Test
    void openWorkTodoFollowsDueDate() {
        assertThat(TodoBusinessDay.isOpenToday(todo("WORK", "FEEDING", "PENDING", TODAY), TODAY)).isTrue();
        assertThat(TodoBusinessDay.isOpenToday(todo("WORK", "FEEDING", "PENDING", TODAY.minusDays(1)), TODAY)).isFalse();
    }

    @Test
    void closedTodoIsNotOpenToday() {
        assertThat(TodoBusinessDay.isOpenToday(todo("WORK", "FEEDING", "DONE", TODAY), TODAY)).isFalse();
        assertThat(TodoBusinessDay.isOpenToday(todo("WARNING", "DRUG_LOW_STOCK", "IGNORED", TODAY), TODAY)).isFalse();
    }

    @Test
    void openWithdrawalEndRollsToToday() {
        Todo withdrawal = todo("WORK", "WITHDRAWAL_END", "PENDING", TODAY.minusDays(2));
        assertThat(TodoBusinessDay.isOpenToday(withdrawal, TODAY)).isTrue();
    }
}
