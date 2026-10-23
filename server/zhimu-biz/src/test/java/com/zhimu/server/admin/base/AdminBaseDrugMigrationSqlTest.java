package com.zhimu.server.admin.base;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3B 迁移脚本静态校验（不启动 Spring、不连库）：9 张表、初始化数据 ID 与 App Mock 对齐。
 * 实际迁移可执行性由验收时在 MySQL 空库顺序执行 V1_00→V2_08 验证，见联调报告。
 */
class AdminBaseDrugMigrationSqlTest {

    private static final Path MIGRATION_DIR = migrationDir();

    private static final List<String> DRUG_TABLES = List.of(
            "t_immune_template", "t_immune_template_version", "t_immune_template_item",
            "t_drug_catalog", "t_drug_catalog_usage", "t_banned_drug", "t_banned_drug_hit",
            "t_drug_lot_recall", "t_drug_lot_recall_farm");

    @Test
    void v1231CreatesNineTablesWithCharsetAndComments() {
        String sql = read("V1_23_1__init_base_data_drug.sql");

        assertThat(count(sql, "CREATE TABLE ")).isEqualTo(9);
        for (String table : DRUG_TABLES) {
            assertThat(sql).contains("CREATE TABLE " + table);
            assertThat(sql).contains("COMMENT ='");
        }
        assertThat(sql).doesNotContain("FOREIGN KEY");
        assertThat(count(sql, "utf8mb4_0900_ai_ci")).isGreaterThanOrEqualTo(9);
    }

    @Test
    void bannedDrugHitIsAppendOnlyWithoutUpdatedAtOrVersion() {
        String block = createTableBlock(read("V1_23_1__init_base_data_drug.sql"), "t_banned_drug_hit");

        assertThat(block).contains("banned_drug_id").contains("hit_source").contains("farm_id")
                .contains("user_id").contains("keyword").contains("ref_id").contains("hit_at")
                .contains("idx_banned_hit_drug_time").contains("idx_banned_hit_farm_time");
        assertThat(block).doesNotContain("updated_at");
        assertThat(block).doesNotContain("version");
        assertThat(block).doesNotContain("deleted");
    }

    @Test
    void v205SeedsCatalogIdsAndUnitsAlignedWithAppMock() {
        String sql = read("V2_05__init_drug_catalog.sql");

        for (String id : List.of("7001", "7002", "7003", "7004",
                "7201", "7202", "7203", "7204", "7205", "7206", "7301", "7302", "7303")) {
            assertThat(sql).containsPattern("\\(" + id + ", '");
        }
        // App Mock 的记账单位是代码，不是“克/毫升/头份”
        assertThat(sql).contains("'DOSE'").contains("'G'").contains("'ML'").contains("'L'");
        assertThat(sql).contains("'DOSE_PER_HEAD'").contains("'IM'");
        // 7203 停用，解释 catalog_vet.json 为什么没有 7203
        assertThat(sql).containsPattern("(?s)7203, 'VET_DRUG', '硫酸黏菌素预混剂'.*'DISABLED'");
        // 7205 不适用于猪（与 App drug_options.json 一致）
        assertThat(sql).containsPattern("7205, 'PIG', 0, NULL, NULL, NULL, NULL, 7");
        // 兽药/疫苗 4 个畜种各一行：10 个非消毒剂目录项 × 4 = 40
        assertThat(count(sql, "'PIG', ")).isEqualTo(10);
        assertThat(count(sql, "'POULTRY', ")).isEqualTo(10);
        assertThat(count(sql, "'CATTLE', ")).isEqualTo(10);
        assertThat(count(sql, "'SHEEP', ")).isEqualTo(10);
    }

    @Test
    void v206SeedsBannedDrug9901() {
        String sql = read("V2_06__init_banned_drugs.sql");

        assertThat(sql).contains("(9901, '盐酸克仑特罗', '克伦特罗、瘦肉精', 'BETA_AGONIST'");
        assertThat(sql).contains("'NITROFURAN'").contains("'HORMONE'");
    }

    @Test
    void v207SeedsPigTemplatesMatchingHealthMock() {
        String sql = read("V2_07__init_immune_templates.sql");

        assertThat(sql).contains("(21, 1101, '杜长大育肥猪', 1");
        assertThat(sql).contains("(22, 1201, '长大二元种猪', 1");
        assertThat(sql).contains("(23, 3101, '西门塔尔肉牛', 1");
        assertThat(sql).contains("(211, 21, 1, 'PUBLISHED'");
        assertThat(sql).contains("(221, 22, 1, 'PUBLISHED'");
        // App module_health mock 的日龄：入栏前 25/35/60，计划 85/89/96/110/117
        for (String dayAge : List.of("25", "35", "60", "85", "89", "96", "110", "117")) {
            assertThat(sql).contains(", " + dayAge + ", NULL, NULL, NULL,");
        }
        assertThat(sql).contains("'DOSE_PER_HEAD', 'IM'");
        assertThat(sql).contains("'BY_DATE'").contains("'BY_DAY_AGE'");
        // 9 + 4 + 3 = 16 个模板项目
        assertThat(count(sql, ", 'NONE', ")).isEqualTo(16);
    }

    private static String createTableBlock(String sql, String table) {
        int start = sql.indexOf("CREATE TABLE " + table);
        assertThat(start).as("表 %s 不存在", table).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf("ENGINE = InnoDB", start);
        assertThat(end).isGreaterThan(start);
        return sql.substring(start, end);
    }

    private static int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String read(String file) {
        try {
            return Files.readString(MIGRATION_DIR.resolve(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path migrationDir() {
        String userDir = System.getProperty("user.dir");
        List<Path> candidates = List.of(
                Path.of(userDir, "..", "zhimu-app", "src", "main", "resources", "db", "migration").normalize(),
                Path.of(userDir, "zhimu-app", "src", "main", "resources", "db", "migration").normalize());
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath();
            }
        }
        throw new IllegalStateException("找不到 db/migration，已尝试：" + candidates);
    }
}
