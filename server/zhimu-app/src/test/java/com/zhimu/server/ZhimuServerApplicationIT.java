package com.zhimu.server;

import com.zhimu.server.framework.mybatis.FarmTableRegistry;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动冒烟：Spring 上下文能启动，Flyway 在 zhimu_test 库执行 V1_00，5 张公共表存在且字符集、注释符合约定。
 * 每次先 clean 再 migrate，保证脚本从空库可重复执行。
 */
@SpringBootTest(classes = ZhimuServerApplication.class)
@ActiveProfiles("it")
class ZhimuServerApplicationIT {

    private static final List<String> COMMON_TABLES =
            List.of("t_file", "t_file_variant", "t_biz_media", "t_change_request", "t_operation_log");

    @TestConfiguration
    static class CleanMigrateConfig {

        @Bean
        FlywayMigrationStrategy cleanMigrateStrategy() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Flyway flyway;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private TaskScheduler taskScheduler;
    @Autowired
    private FarmTableRegistry farmTableRegistry;

    @Test
    void flywayCreatesCommonTables() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("zhimu_test");

        MigrationInfo[] applied = flyway.info().applied();
        assertThat(Arrays.stream(applied).map(info -> info.getVersion().getVersion()))
                .containsExactly("1.00");
        assertThat(applied[0].getState().isFailed()).isFalse();

        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);
        assertThat(tables).containsAll(COMMON_TABLES);

        String inClause = String.join(",", COMMON_TABLES.stream().map(t -> "'" + t + "'").toList());
        assertThat(jdbc.queryForList("SELECT DISTINCT table_collation FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name IN (" + inClause + ")", String.class))
                .containsExactly("utf8mb4_0900_ai_ci");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_name IN (" + inClause + ") AND (table_comment IS NULL OR table_comment = '')", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name IN (" + inClause + ") AND (column_comment IS NULL OR column_comment = '')", Integer.class))
                .isZero();
    }

    @Test
    void infrastructureBeansAreConfigured() {
        assertThat(redisTemplate.getValueSerializer()).isInstanceOf(GenericJacksonJsonRedisSerializer.class);
        assertThat(taskScheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
        assertThat(((ThreadPoolTaskScheduler) taskScheduler).getThreadNamePrefix()).isEqualTo("zmt-job-");
        assertThat(farmTableRegistry).isNotNull();
    }
}
