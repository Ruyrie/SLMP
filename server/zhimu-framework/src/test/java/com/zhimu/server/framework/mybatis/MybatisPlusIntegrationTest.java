package com.zhimu.server.framework.mybatis;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.fixture.FwTestFarmItem;
import com.zhimu.server.framework.mybatis.fixture.FwTestGlobalItem;
import com.zhimu.server.framework.mybatis.fixture.mapper.FwTestFarmItemMapper;
import com.zhimu.server.framework.mybatis.fixture.mapper.FwTestGlobalItemMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatis-Plus 配置集成测试：连本机 MySQL 的 zhimu_test 库，测试表 t_fwtest_* 在测试前建、测试后删。
 */
@SpringBootTest(classes = MybatisPlusIntegrationTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(locations = "classpath:fw-data-test.properties")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MybatisPlusIntegrationTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 10, 19, 9, 0, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 10, 19, 10, 30, 0);

    @SpringBootConfiguration
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
    @Import(MybatisPlusConfig.class)
    static class TestApp {
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FwTestFarmItemMapper farmItemMapper;
    @Autowired
    private FwTestGlobalItemMapper globalItemMapper;
    @Autowired
    private FarmTableRegistry farmTableRegistry;

    @BeforeAll
    void createTables() {
        dropTables();
        jdbc.execute("""
                CREATE TABLE t_fwtest_farm_item (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    farm_id BIGINT UNSIGNED NOT NULL,
                    name VARCHAR(50) NULL,
                    version INT NOT NULL DEFAULT 0,
                    deleted TINYINT(1) NOT NULL DEFAULT 0,
                    created_by BIGINT UNSIGNED NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci""");
        jdbc.execute("""
                CREATE TABLE t_fwtest_global_item (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(50) NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci""");
    }

    @AfterAll
    void dropTables() {
        jdbc.execute("DROP TABLE IF EXISTS t_fwtest_farm_item");
        jdbc.execute("DROP TABLE IF EXISTS t_fwtest_global_item");
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE t_fwtest_farm_item");
        jdbc.execute("TRUNCATE TABLE t_fwtest_global_item");
        setClock(T1);
    }

    @AfterEach
    void tearDown() {
        FarmContext.clear();
        UserContext.clear();
        AdminContext.clear();
        BeijingTime.setClock(null);
    }

    @Test
    void registryCollectsFarmScopedEntitiesAtStartup() {
        assertThat(farmTableRegistry.tables()).contains("t_fwtest_farm_item").doesNotContain("t_fwtest_global_item");
    }

    @Test
    void insertFillsFarmIdAndAuditFieldsFromContext() {
        enterFarm(1001L, 7L);
        FwTestFarmItem item = new FwTestFarmItem();
        item.setName("1 号保育舍");
        farmItemMapper.insert(item);

        Map<String, Object> row = row(item.getId());
        assertThat(row.get("farm_id")).hasToString("1001");
        assertThat(row.get("created_by")).hasToString("7");
        assertThat(row.get("created_at")).isEqualTo(T1);
        assertThat(row.get("updated_at")).isEqualTo(T1);
        assertThat(row.get("version")).hasToString("0");
        assertThat(item.getCreatedAt()).isEqualTo(T1);
    }

    @Test
    void insertKeepsExplicitFarmIdWithoutDuplicateColumn() {
        enterFarm(1001L, 7L);
        FwTestFarmItem item = new FwTestFarmItem();
        item.setFarmId(2002L);
        item.setName("显式养殖场");
        farmItemMapper.insert(item);

        assertThat(row(item.getId()).get("farm_id")).hasToString("2002");
    }

    @Test
    void insertKeepsExplicitAuditFields() {
        UserContext.set(new UserContext.LoginUser(7L, ClientType.APP_ANDROID, 1, "jti"));
        FwTestFarmItem item = new FwTestFarmItem();
        item.setFarmId(1001L);
        item.setCreatedBy(99L);
        item.setCreatedAt(T2);
        farmItemMapper.insert(item);

        Map<String, Object> row = row(item.getId());
        assertThat(row.get("created_by")).hasToString("99");
        assertThat(row.get("created_at")).isEqualTo(T2);
        assertThat(row.get("updated_at")).isEqualTo(T1);
    }

    @Test
    void createdByFallsBackToAdminThenSystem() {
        AdminContext.set(new AdminContext.LoginAdmin(3L, "吴昊", Set.of("AUDITOR"), Set.of(), 1, "session"));
        FwTestFarmItem byAdmin = newItem(1001L, "管理员新增");
        farmItemMapper.insert(byAdmin);
        AdminContext.clear();
        FwTestFarmItem bySystem = newItem(1001L, "系统新增");
        farmItemMapper.insert(bySystem);

        assertThat(row(byAdmin.getId()).get("created_by")).hasToString("3");
        assertThat(row(bySystem.getId()).get("created_by")).hasToString("0");
    }

    @Test
    void tenantConditionIsolatesFarms() {
        Long mine = insertRaw(1001L, "东山猪场");
        Long other = insertRaw(2002L, "西岭牧场");

        enterFarm(1001L, 7L);
        assertThat(farmItemMapper.selectList(null)).extracting(FwTestFarmItem::getId).containsExactly(mine);
        assertThat(farmItemMapper.selectById(other)).isNull();

        FwTestFarmItem update = new FwTestFarmItem();
        update.setId(other);
        update.setName("越权修改");
        assertThat(farmItemMapper.updateById(update)).isZero();
        assertThat(farmItemMapper.deleteById(other)).isZero();
        assertThat(row(other).get("name")).isEqualTo("西岭牧场");
    }

    @Test
    void tenantSkipAndNoContextQueryAllFarms() {
        insertRaw(1001L, "东山猪场");
        insertRaw(2002L, "西岭牧场");

        assertThat(farmItemMapper.selectCount(null)).isEqualTo(2L);
        enterFarm(1001L, 7L);
        assertThat(farmItemMapper.selectCount(null)).isEqualTo(1L);
        assertThat(TenantSkip.call(() -> farmItemMapper.selectCount(null))).isEqualTo(2L);
    }

    @Test
    void nonFarmTableIsNotFiltered() {
        enterFarm(1001L, 7L);
        FwTestGlobalItem item = new FwTestGlobalItem();
        item.setName("全局字典");
        globalItemMapper.insert(item);

        List<FwTestGlobalItem> list = globalItemMapper.selectList(null);
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().getCreatedAt()).isEqualTo(T1);
    }

    @Test
    void optimisticLockConflictUpdatesZeroRows() {
        enterFarm(1001L, 7L);
        FwTestFarmItem item = newItem(null, "原名称");
        farmItemMapper.insert(item);

        FwTestFarmItem first = farmItemMapper.selectById(item.getId());
        FwTestFarmItem second = farmItemMapper.selectById(item.getId());
        first.setName("第一次修改");
        assertThat(farmItemMapper.updateById(first)).isEqualTo(1);
        assertThat(first.getVersion()).isEqualTo(1);

        second.setName("基于旧版本的修改");
        assertThat(farmItemMapper.updateById(second)).isZero();
        assertThat(row(item.getId()).get("name")).isEqualTo("第一次修改");
    }

    @Test
    void updateAlwaysRefreshesUpdatedAt() {
        enterFarm(1001L, 7L);
        FwTestFarmItem item = newItem(null, "原名称");
        farmItemMapper.insert(item);

        setClock(T2);
        FwTestFarmItem loaded = farmItemMapper.selectById(item.getId());
        loaded.setName("新名称");
        farmItemMapper.updateById(loaded);

        Map<String, Object> row = row(item.getId());
        assertThat(row.get("created_at")).isEqualTo(T1);
        assertThat(row.get("updated_at")).isEqualTo(T2);
    }

    @Test
    void logicDeleteKeepsRow() {
        enterFarm(1001L, 7L);
        FwTestFarmItem item = newItem(null, "待删除");
        farmItemMapper.insert(item);

        assertThat(farmItemMapper.deleteById(item.getId())).isEqualTo(1);
        assertThat(farmItemMapper.selectById(item.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT deleted FROM t_fwtest_farm_item WHERE id = ?", Integer.class, item.getId()))
                .isEqualTo(1);
    }

    @Test
    void pageSizeIsCappedAt500() {
        insertRaw(1001L, "东山猪场");
        Page<FwTestFarmItem> page = farmItemMapper.selectPage(new Page<>(1, 1000), null);
        assertThat(page.getSize()).isEqualTo(MybatisPlusConfig.MAX_PAGE_SIZE);
        assertThat(page.getRecords()).hasSize(1);
    }

    private static FwTestFarmItem newItem(Long farmId, String name) {
        FwTestFarmItem item = new FwTestFarmItem();
        item.setFarmId(farmId);
        item.setName(name);
        return item;
    }

    private Long insertRaw(Long farmId, String name) {
        jdbc.update("INSERT INTO t_fwtest_farm_item (farm_id, name, created_at, updated_at) VALUES (?, ?, NOW(), NOW())",
                farmId, name);
        return jdbc.queryForObject("SELECT MAX(id) FROM t_fwtest_farm_item", Long.class);
    }

    private Map<String, Object> row(Long id) {
        return jdbc.queryForMap("SELECT * FROM t_fwtest_farm_item WHERE id = ?", id);
    }

    private static void enterFarm(Long farmId, Long userId) {
        UserContext.set(new UserContext.LoginUser(userId, ClientType.APP_ANDROID, 1, "jti"));
        FarmContext.set(new FarmContext.FarmMember(farmId, "东山猪场", userId, 70L, FarmRole.OWNER, Set.of(),
                FarmStatus.NORMAL));
    }

    private static void setClock(LocalDateTime time) {
        BeijingTime.setClock(Clock.fixed(time.atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
    }
}
