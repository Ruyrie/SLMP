package com.zhimu.server.biz.demo.job;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** M18：平移后示例行的“已发生”时刻不晚于现在，计划类时刻（截止、逾期、提醒）不动。 */
class DemoShiftClampTest {

    @Test
    void scheduleColumnsAreNotClamped() {
        assertThat(DemoShiftPlan.pastOnlyColumn("t_sale_advice", "generated_at")).isTrue();
        assertThat(DemoShiftPlan.pastOnlyColumn("t_stock_flow", "created_at")).isTrue();
        assertThat(DemoShiftPlan.pastOnlyColumn("t_message", "sort_time")).isTrue();
        assertThat(DemoShiftPlan.pastOnlyColumn("t_todo", "sort_time")).isFalse();
        assertThat(DemoShiftPlan.pastOnlyColumn("t_todo", "overdue_at")).isFalse();
        assertThat(DemoShiftPlan.pastOnlyColumn("t_feed_task", "deadline_at")).isFalse();
        assertThat(DemoShiftPlan.pastOnlyColumn("t_feed_task", "backfill_end_at")).isFalse();
        assertThat(DemoShiftPlan.pastOnlyColumn("t_todo_remind", "remind_at")).isFalse();
    }

    @Test
    void clampSqlOnlyTouchesRegisteredSampleRowsLaterThanNow() {
        String sql = DemoDataShifter.clampSql("t_sale_advice", List.of("generated_at", "started_at"));

        assertThat(sql).isEqualTo("UPDATE `t_sale_advice` t JOIN t_demo_sample_row r ON r.table_name = ? "
                + "AND r.row_id = t.id SET t.`generated_at` = IF(t.`generated_at` > ?, ?, t.`generated_at`), "
                + "t.`started_at` = IF(t.`started_at` > ?, ?, t.`started_at`) "
                + "WHERE (t.`generated_at` > ? OR t.`started_at` > ?)");
        assertThat(sql.chars().filter(c -> c == '?').count()).isEqualTo(1 + 2 * 3);
    }
}
