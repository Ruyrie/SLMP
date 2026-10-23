package com.zhimu.server.framework.mybatis;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多租户 SQL 改写（只解析 SQL，不连数据库）。
 */
class FarmTenantLineSqlTest {

    private TenantLineInnerInterceptor interceptor;

    @BeforeEach
    void setUp() {
        FarmTableRegistry registry = new FarmTableRegistry();
        registry.register("t_pen");
        registry.register("t_batch");
        interceptor = new TenantLineInnerInterceptor(new FarmTenantLineHandler(registry));
    }

    @AfterEach
    void tearDown() {
        FarmContext.clear();
    }

    @Test
    void selectAddsFarmIdWhenFarmContextPresent() {
        enterFarm(1001L);
        String sql = interceptor.parserSingle("SELECT id, name FROM t_pen WHERE deleted = 0", null);
        assertThat(sql).isEqualTo("SELECT id, name FROM t_pen WHERE deleted = 0 AND farm_id = 1001");
    }

    @Test
    void joinAddsFarmIdToEveryFarmTableUsingAlias() {
        enterFarm(1001L);
        String sql = interceptor.parserSingle(
                "SELECT b.id FROM t_batch b LEFT JOIN t_pen p ON p.id = b.pen_id LEFT JOIN t_user u ON u.id = b.created_by",
                null);
        assertThat(sql).contains("p.farm_id = 1001").contains("b.farm_id = 1001").doesNotContain("u.farm_id");
    }

    @Test
    void updateAndDeleteAddFarmId() {
        enterFarm(1001L);
        assertThat(interceptor.parserSingle("UPDATE t_pen SET name = ? WHERE id = ?", null))
                .isEqualTo("UPDATE t_pen SET name = ? WHERE id = ? AND farm_id = 1001");
        assertThat(interceptor.parserSingle("DELETE FROM t_pen WHERE id = ?", null))
                .isEqualTo("DELETE FROM t_pen WHERE id = ? AND farm_id = 1001");
    }

    @Test
    void insertAppendsFarmIdWhenMissing() {
        enterFarm(1001L);
        String sql = interceptor.parserSingle("INSERT INTO t_pen (name, created_at) VALUES (?, ?)", null);
        assertThat(sql).isEqualTo("INSERT INTO t_pen (name, created_at, farm_id) VALUES (?, ?, 1001)");
    }

    @Test
    void insertKeepsExplicitFarmIdWithoutDuplicateColumn() {
        enterFarm(1001L);
        String sql = interceptor.parserSingle("INSERT INTO t_pen (farm_id, name) VALUES (?, ?)", null);
        assertThat(sql).isEqualTo("INSERT INTO t_pen (farm_id, name) VALUES (?, ?)");
        String quoted = interceptor.parserSingle("INSERT INTO t_pen (`farm_id`, name) VALUES (?, ?)", null);
        assertThat(quoted).isEqualTo("INSERT INTO t_pen (`farm_id`, name) VALUES (?, ?)");
    }

    @Test
    void noConditionWithoutFarmContext() {
        String sql = interceptor.parserSingle("SELECT id FROM t_pen WHERE id = ?", null);
        assertThat(sql).isEqualTo("SELECT id FROM t_pen WHERE id = ?");
    }

    @Test
    void noConditionInsideTenantSkip() {
        enterFarm(1001L);
        String sql = TenantSkip.call(() -> interceptor.parserSingle("SELECT id FROM t_pen WHERE farm_id IN (1001, 1002)", null));
        assertThat(sql).isEqualTo("SELECT id FROM t_pen WHERE farm_id IN (1001, 1002)");
        assertThat(TenantSkip.isSkipped()).isFalse();
    }

    @Test
    void tenantSkipSupportsNesting() {
        TenantSkip.run(() -> {
            TenantSkip.run(() -> assertThat(TenantSkip.isSkipped()).isTrue());
            assertThat(TenantSkip.isSkipped()).isTrue();
        });
        assertThat(TenantSkip.isSkipped()).isFalse();
    }

    @Test
    void noConditionForUnregisteredTable() {
        enterFarm(1001L);
        String sql = interceptor.parserSingle("SELECT id FROM t_user WHERE phone = ?", null);
        assertThat(sql).isEqualTo("SELECT id FROM t_user WHERE phone = ?");
        assertThat(interceptor.parserSingle("INSERT INTO t_user (phone) VALUES (?)", null))
                .isEqualTo("INSERT INTO t_user (phone) VALUES (?)");
    }

    private static void enterFarm(Long farmId) {
        FarmContext.set(new FarmContext.FarmMember(farmId, "东山猪场", 7L, 70L, FarmRole.OWNER, Set.of(),
                FarmStatus.NORMAL));
    }
}
