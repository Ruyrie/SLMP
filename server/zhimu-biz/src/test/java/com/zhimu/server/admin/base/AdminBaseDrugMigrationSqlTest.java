package com.zhimu.server.admin.base;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3B 迁移脚本静态校验（不启动 Spring、不连库）：9 张表、初始化数据 ID 与 App Mock 对齐。
 * 实际迁移可执行性由验收时在 MySQL 空库顺序执行 V1_00→V2_08 验证，见联调报告。
 * U09/U10/U11 的字段口径直接读 App 的 categories.json 与 module_stock/module_inspect mock 比对。
 */
class AdminBaseDrugMigrationSqlTest {

    private static final Path MIGRATION_DIR = migrationDir();

    private static final List<String> DRUG_TABLES = List.of(
            "t_immune_template", "t_immune_template_version", "t_immune_template_item",
            "t_drug_catalog", "t_drug_catalog_usage", "t_banned_drug", "t_banned_drug_hit",
            "t_drug_lot_recall", "t_drug_lot_recall_farm");

    private static final Pattern CATALOG_ROW = Pattern.compile(
            "\\((\\d{4}), '(VACCINE|VET_DRUG|DISINFECTANT)', '([^']+)', (?:NULL|'[^']*'), '[^']*', "
                    + "'([^']*)', '([^']*)', '([^']*)', ([0-9.]+),");

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

    /** U11：目录 ID、名称、规格、记账/包装单位与 module_stock 的 catalog_*.json 逐项一致。 */
    @Test
    void v205CatalogItemsMatchAppStockMocks() {
        Map<Long, CatalogRow> rows = catalogRows(read("V2_05__init_drug_catalog.sql"));
        assertThat(rows).hasSize(13);
        rows.values().forEach(row ->
                assertThat(row.measureUnit()).as("目录 %s 的记账单位", row.id())
                        .isIn("G", "ML", "L", "DOSE", "KG"));

        assertCatalogMock(rows, "catalog_vet.json", "VET_DRUG");
        assertCatalogMock(rows, "catalog_vaccine.json", "VACCINE");
        assertCatalogMock(rows, "catalog_dis.json", "DISINFECTANT");
    }

    @Test
    void v206SeedsBannedDrug9901() {
        String sql = read("V2_06__init_banned_drugs.sql");

        assertThat(sql).contains("(9901, '盐酸克仑特罗', '克伦特罗、瘦肉精', 'BETA_AGONIST'");
        assertThat(sql).contains("'NITROFURAN'").contains("'HORMONE'");

        JsonNode banned = AdminBaseTestMvc.json(readAppMock("module_stock", "stock", "catalog_banned.json"));
        JsonNode hit = banned.get("data").get("banned_hit");
        assertThat(hit.get("banned_drug_id").asLong()).isEqualTo(9901L);
        assertThat(hit.get("name").asText()).isEqualTo("盐酸克仑特罗");
        assertThat(sql).contains("'盐酸克仑特罗'");
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
        // 9 + 4 + 3 + 3 = 19 个模板项目
        assertThat(count(sql, ", 'NONE', ")).isEqualTo(19);
    }

    /** U09 的 has_immune_template 需与 lib_common categories.json 一致：长白 1102 也必须有已发布模板。 */
    @Test
    void v207AddsBreed1102TemplateForU09Mock() {
        String sql = read("V2_07__init_immune_templates.sql");

        assertThat(sql).contains("(24, 1102, '长白育肥猪', 1");
        assertThat(sql).contains("(241, 24, 1, 'PUBLISHED'");
        assertThat(sql).contains("(2401, 24, 241, NULL, 'NONE', 1, 7001, '首免', 'BY_DAY_AGE', 25");
        assertThat(sql).contains("(2402, 24, 241, NULL, 'NONE', 2, 7002, '首免', 'BY_DAY_AGE', 35");
        assertThat(sql).contains("(2403, 24, 241, NULL, 'NONE', 3, 7003, '首免', 'BY_DAY_AGE', 60");
    }

    /** U09：has_std_weight / has_feed_std / has_immune_template 的数据来源与 App Mock 的品种集合一致。 */
    @Test
    void u09FlagSourcesMatchAppCategoriesMock() {
        String standards = read("V2_04__init_standards.sql");
        int feedStart = standards.indexOf("INSERT INTO t_feed_std_version");
        assertThat(feedStart).isGreaterThan(0);
        Set<Long> weightBreeds = publishedBreedIds(standards.substring(0, feedStart));
        Set<Long> feedBreeds = publishedBreedIds(standards.substring(feedStart));

        String templates = read("V2_07__init_immune_templates.sql");
        int versionStart = templates.indexOf("INSERT INTO t_immune_template_version");
        Set<Long> immuneBreeds = templateBreedIds(templates.substring(0, versionStart));

        // 1101/1102/2101/3101 有标准体重；1201 种猪不设标准体重（has_std_weight=false）
        assertThat(weightBreeds).containsExactlyInAnyOrder(1101L, 1102L, 2101L, 3101L);
        // 1201 种猪设饲喂标准；3101 肉牛不设（has_feed_std=false）
        assertThat(feedBreeds).containsExactlyInAnyOrder(1101L, 1102L, 1201L, 2101L);
        // 已发布免疫模板：1101（育肥猪）、1201（种猪）、1102（长白）、3101（肉牛）
        // 2101 白羽肉鸡的 mock has_immune_template=true 需禽用疫苗，见报告缺口
        assertThat(immuneBreeds).containsExactlyInAnyOrder(1101L, 1102L, 1201L, 3101L);
    }

    @Test
    void v208MatchesInspectTemplateMock() {
        String sql = read("V2_08__init_inspect_templates.sql");

        assertThat(sql).contains("(11, 101, '育肥猪巡检模板', 2, 0");
        assertThat(sql).contains("(1101, 11, 2, 'PUBLISHED', NULL,");
        assertThat(count(sql, ", 'NONE', ")).isEqualTo(3);

        JsonNode template = AdminBaseTestMvc.json(
                readAppMock("module_inspect", "inspect", "form_2001_registered.json"))
                .get("data").get("template");
        assertThat(template.get("template_id").asLong()).isEqualTo(11L);
        assertThat(template.get("template_version").asInt()).isEqualTo(2);

        for (JsonNode item : template.get("items")) {
            long itemId = item.get("item_id").asLong();
            String itemName = item.get("item_name").asText();
            int sortNo = item.get("sort_no").asInt();
            assertThat(sql).contains("(" + itemId + ", 11, 1101, NULL, 'NONE', " + sortNo + ", '" + itemName + "'");
            for (JsonNode option : item.get("options")) {
                long optionId = option.get("option_id").asLong();
                String optionName = option.get("option_name").asText();
                int optionSort = option.get("sort_no").asInt();
                int abnormal = option.get("is_abnormal").asBoolean() ? 1 : 0;
                assertThat(sql).contains("(" + optionId + ", " + itemId + ", " + optionSort + ", '"
                        + optionName + "', " + abnormal);
            }
        }
    }

    /** U10：8 类字典都有启用项；ID 分段与 App Mock 的差异按服务端任务书口径（PAYMENT 3301–）。 */
    @Test
    void v203SeedsEightDictTypes() {
        String sql = read("V2_03__init_dicts.sql");

        assertThat(count(sql, "'PEN_TYPE'")).isEqualTo(16);
        assertThat(count(sql, "'DEATH_REASON'")).isEqualTo(4);
        assertThat(count(sql, "'SYMPTOM'")).isEqualTo(38);
        assertThat(count(sql, "'FEED_TYPE'")).isEqualTo(9);
        assertThat(count(sql, "'VACCINATE_METHOD'")).isEqualTo(7);
        assertThat(count(sql, "'DISINFECT_METHOD'")).isEqualTo(4);
        assertThat(count(sql, "'SCRAP_REASON'")).isEqualTo(4);
        assertThat(count(sql, "'PAYMENT_METHOD'")).isEqualTo(4);

        // App Mock 固定的 ID 全部沿用
        assertThat(sql).contains("(501, 'PEN_TYPE', 'NURSERY', '保育舍'");
        assertThat(sql).contains("(601, 'DEATH_REASON', 'SICK', '病死'");
        assertThat(sql).contains("(401, 'SYMPTOM', 'DIARRHEA', '腹泻'");
        assertThat(sql).contains("(3101, 'FEED_TYPE', 'COMPLETE', '全价料'");
        assertThat(sql).contains("(721, 'DISINFECT_METHOD', 'SPRAY', '喷雾'");
        assertThat(sql).contains("(3201, 'SCRAP_REASON', 'PLATFORM_RECALL', '平台停用'");
        // 饮水用 WATER（App Mock），PAYMENT_METHOD 是第 8 类且 ID 用 3301–3304（不沿用 mock 的 701–704）
        assertThat(sql).contains("(706, 'VACCINATE_METHOD', 'WATER', '饮水'");
        assertThat(sql).contains("(3301, 'PAYMENT_METHOD', 'BANK_TRANSFER', '银行转账'");
        assertThat(sql).contains("(3304, 'PAYMENT_METHOD', 'CASH', '现金'");
    }

    private static void assertCatalogMock(Map<Long, CatalogRow> rows, String mockFile, String category) {
        JsonNode data = AdminBaseTestMvc.json(readAppMock("module_stock", "stock", mockFile)).get("data");
        assertThat(data.get("banned_hit").isNull()).isTrue();
        List<Long> mockIds = new ArrayList<>();
        for (JsonNode item : data.get("items")) {
            long id = item.get("id").asLong();
            mockIds.add(id);
            CatalogRow row = rows.get(id);
            assertThat(row).as("%s 缺少目录项 %s", mockFile, id).isNotNull();
            assertThat(row.category()).isEqualTo(category);
            assertThat(row.name()).isEqualTo(item.get("name").asText());
            assertThat(row.spec()).isEqualTo(item.get("spec").asText());
            assertThat(row.measureUnit()).isEqualTo(item.get("measure_unit").asText());
            assertThat(row.packUnit()).isEqualTo(item.get("pack_unit").asText());
            assertThat(new BigDecimal(row.packSize()))
                    .isEqualByComparingTo(item.get("pack_size").asText());
        }
        // 7203 为停用项，App catalog_vet.json 不返回；其余同类目录项与 mock 顺序/ID 一致
        assertThat(rows.values().stream()
                .filter(row -> row.category().equals(category) && !"硫酸黏菌素预混剂".equals(row.name()))
                .map(CatalogRow::id).toList())
                .containsExactlyElementsOf(mockIds);
    }

    private static Map<Long, CatalogRow> catalogRows(String sql) {
        Map<Long, CatalogRow> rows = new LinkedHashMap<>();
        Matcher matcher = CATALOG_ROW.matcher(sql);
        while (matcher.find()) {
            long id = Long.parseLong(matcher.group(1));
            rows.put(id, new CatalogRow(id, matcher.group(2), matcher.group(3), matcher.group(4),
                    matcher.group(5), matcher.group(6), matcher.group(7)));
        }
        return rows;
    }

    private static Set<Long> publishedBreedIds(String sql) {
        Set<Long> ids = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\\(\\d+, (\\d+), 1, 'PUBLISHED'").matcher(sql);
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return ids;
    }

    private static Set<Long> templateBreedIds(String sql) {
        Set<Long> ids = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\\(\\d+, (\\d+), '[^']+', 1,").matcher(sql);
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group(1)));
        }
        return ids;
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
        return read(MIGRATION_DIR.resolve(file));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readAppMock(String module, String dir, String file) {
        Path repoRoot = MIGRATION_DIR;
        for (int i = 0; i < 7; i++) {
            repoRoot = repoRoot.getParent();
        }
        return read(repoRoot.resolve(module).resolve("src").resolve("debug").resolve("assets")
                .resolve("mock").resolve(dir).resolve(file));
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

    private record CatalogRow(long id, String category, String name, String spec, String measureUnit,
                              String packUnit, String packSize) {
    }
}
