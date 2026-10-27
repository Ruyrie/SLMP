package com.zhimu.server.biz.account.mapper;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountPersonalCleanupMapperTest {
    @Test
    void everyPersonalCleanupStatementIsScopedAndCompatibleWithTheSqlInterceptor() throws Exception {
        for (var method : AccountPersonalCleanupMapper.class.getDeclaredMethods()) {
            Delete delete = method.getAnnotation(Delete.class);
            Update update = method.getAnnotation(Update.class);
            Select select = method.getAnnotation(Select.class);
            String sql = String.join(" ", delete != null ? delete.value()
                    : update != null ? update.value() : select.value());
            assertThat(sql).as(method.getName()).contains("#{userId}", "WHERE");
            if (delete != null || update != null) {
                assertThat(sql).as(method.getName()).contains("status='CANCELLED'");
            }
            assertThat(CCJSqlParserUtil.parse(sql.replaceAll("#\\{[^}]+}", "?"))).isNotNull();
        }
    }

    @Test
    void dueScanIncludesWaitingPhasesAndIsBoundedWhileProgressClearsNullFields() throws Exception {
        String scan = String.join(" ", AccountCancelMapper.class
                .getMethod("selectDueUserIds", java.time.LocalDateTime.class).getAnnotation(Select.class).value());
        assertThat(scan).contains("'WAITING'", "'FAILED'", "next_run_at <=", "LIMIT 100")
                .doesNotContain("'DONE'");
        assertThat(CCJSqlParserUtil.parse(scan.replaceAll("#\\{[^}]+}", "?"))).isNotNull();
        String update = String.join(" ", AccountCancelMapper.class.getMethod("updateCleanupProgress",
                com.zhimu.server.biz.account.entity.AccountCancel.class, java.time.LocalDateTime.class)
                .getAnnotation(Update.class).value());
        assertThat(update).contains("last_error=#{row.lastError}", "next_run_at=#{row.nextRunAt}",
                "WHERE id=#{row.id} AND user_id=#{row.userId}");
        assertThat(CCJSqlParserUtil.parse(update.replaceAll("#\\{[^}]+}", "?"))).isNotNull();
    }
}
