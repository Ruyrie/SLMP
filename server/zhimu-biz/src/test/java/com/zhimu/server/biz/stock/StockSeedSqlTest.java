package com.zhimu.server.biz.stock;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块09 示例数据（V9_10）与 App mock（module_stock/src/debug/assets/mock/stock）的静态对齐校验：
 * 饲料 / 药品 / 批号 / 单据 / 流水的 ID、结存、日期与 mock 一致，且不修改 V9_04 的栏舍与批次。
 * 同时校验收敛口径：t_batch_material_cost 的 cost_type 与模块10 读取的 FEED / DRUG 一致。
 */
class StockSeedSqlTest {

    private static final Path MIGRATION_DIR = dbDir("migration");
    private static final Path SAMPLE_DIR = dbDir("sample");

    @Test
    void v910SeedsFeedsAlignedWithMock() {
        String sql = read(SAMPLE_DIR, "V9_10__sample_stock.sql");

        // feed_5101.json / feed_overview.json：5101 结存 500 千克、1,600 元、均价 3.2、版本 12
        assertThat(sql).containsPattern("(?s)5101, 1001, '育肥全价料'.*?500\\.000, 1600\\.00, 3\\.2000");
        // 5102 结存 1,850 千克、7,770 元、均价 4.2、版本 8（stocktake_sheet.json 的 stock_version）
        assertThat(sql).containsPattern("(?s)5102, 1001, '保育料'.*?1850\\.000, 7770\\.00, 4\\.2000");
        // 5103 只在入库下拉里，结存 0
        assertThat(sql).containsPattern("(?s)5103, 1001, '哺乳仔猪教槽料'.*?0\\.000, 0\\.00, 0\\.0000");
        // 近 7 天领用与日均：5101 3,360 / 480.00、5102 1,050 / 150.00
        assertThat(sql).contains("3360.000, 7, 480.00").contains("1050.000, 7, 150.00");
    }

    @Test
    void v910SeedsDrugsAndLotsAlignedWithMock() {
        String sql = read(SAMPLE_DIR, "V9_10__sample_stock.sql");

        // drug_overview.json 的 lot_counts：all 7 / vet_drug 3 / vaccine 3 / disinfectant 1
        for (String lot : List.of("(5201, 1001, 5003, 7003, 'VACCINE'", "(6001, 1001, 5001, 7001, 'VACCINE'",
                "(6002, 1001, 5001, 7001, 'VACCINE'", "(6101, 1001, 5201, 7201, 'VET_DRUG'",
                "(6103, 1001, 5201, 7201, 'VET_DRUG'", "(6111, 1001, 5202, 7202, 'VET_DRUG'",
                "(6301, 1001, 5301, 7301, 'DISINFECTANT'")) {
            assertThat(sql).contains(lot);
        }
        // lot_5201.json：300 头份、840 元、2.8、版本 5、已停用（2026-10-18 16:20）
        assertThat(sql).containsPattern("(?s)5201, 1001, 5003, 7003, 'VACCINE', '2025047', '2027-04-20'.*?"
                + "300\\.000, 840\\.00, 2\\.8000, 9001, '2026-10-18 16:20:00'");
        // lot_6103.json：已过期（2026-10-12）、1,500 克、45 元、版本 5
        assertThat(sql).containsPattern("(?s)6103, 1001, 5201, 7201, 'VET_DRUG', '2024118', '2026-10-12'.*?"
                + "1500\\.000, 45\\.00, 0\\.0300");
        // lot_6301.json：临期（2026-11-08）、40 升、360 元、9.0
        assertThat(sql).containsPattern("(?s)6301, 1001, 5301, 7301, 'DISINFECTANT', '2025033', '2026-11-08'.*?"
                + "40\\.000, 360\\.00, 9\\.0000");
        // 药品档案 5 个，ID 与 mock 的 drug_*.json 一致
        for (long drugId : new long[]{5001, 5003, 5201, 5202, 5301}) {
            assertThat(sql).contains("(" + drugId + ", 1001,");
        }
        // 模块08 联调：health mock 的 vaccines.json / drug_options.json / disinfectants.json
        // 疫苗 5002（150 头份）/ 5004（300 毫升）、兽药 5204（500 毫升）/ 5205（1,000 克）、消毒剂 5302（12 升）
        assertThat(sql).containsPattern("(?s)5002, 1001, 'VACCINE', 7002, '猪伪狂犬病活疫苗'");
        assertThat(sql).containsPattern("(?s)5004, 1001, 'VACCINE', 7004, '猪细小病毒灭活疫苗'.*?'ML'");
        assertThat(sql).containsPattern("(?s)5204, 1001, 'VET_DRUG', 7204, '恩诺沙星注射液'.*?'ML', '支'");
        assertThat(sql).containsPattern("(?s)5205, 1001, 'VET_DRUG', 7205, '硫酸新霉素可溶性粉'");
        assertThat(sql).containsPattern("(?s)5302, 1001, 'DISINFECTANT', 7302, '戊二醛溶液'");
        // 对应批号与结存：6011 150 / 6041 300 / 6121 500 / 6131 1000 / 6311 12（与 health 的 lots_*.json 一致）
        assertThat(sql).containsPattern("(?s)6011, 1001, 5002, 7002, 'VACCINE', '2025066', '2026-11-30'.*?"
                + "150\\.000, 465\\.00, 3\\.1000");
        assertThat(sql).containsPattern("(?s)6041, 1001, 5004, 7004, 'VACCINE', '2025150', '2027-04-30'.*?"
                + "300\\.000, 1260\\.00, 4\\.2000");
        assertThat(sql).containsPattern("(?s)6121, 1001, 5204, 7204, 'VET_DRUG', '2025077', '2027-03-31'.*?"
                + "500\\.000, 450\\.00, 0\\.9000");
        assertThat(sql).containsPattern("(?s)6131, 1001, 5205, 7205, 'VET_DRUG', '2025080', '2027-04-30'.*?"
                + "1000\\.000, 150\\.00, 0\\.1500");
        assertThat(sql).containsPattern("(?s)6311, 1001, 5302, 7302, 'DISINFECTANT', '2025101', '2027-09-30'.*?"
                + "12\\.000, 336\\.00, 28\\.0000");
    }

    @Test
    void v910SeedsDocumentsAndFlowsAlignedWithMock() {
        String sql = read(SAMPLE_DIR, "V9_10__sample_stock.sql");

        // 入库单 6201（RK26106201，2,000 千克 × 3.20 = 6,400）与 6211（批号 2025113）
        assertThat(sql).contains("RK26106201").contains("RK26106211");
        assertThat(sql).containsPattern("(?s)6201, 1001, 'RK26106201', 'FEED', 5101, NULL, NULL, NULL,\\s*"
                + "2000\\.000, 'MEASURE', 2000\\.000, 3\\.20, 3\\.2000, 6400\\.00");
        // 出库 6501 / 6511 / 6512（mock outbound_6501.json、6511、6512）
        assertThat(sql).contains("CK26106501").contains("CK26106511").contains("CK26106512");
        // 报损 6401（10 千克 × 3.20 = 32.00，2026-10-15）
        assertThat(sql).containsPattern("6401, 1001, 'FEED_LOSS', 5101, NULL, NULL, 10\\.000, 'MEASURE', 10\\.000");
        // 盘点 6301（2026-10-01，盘盈 25.20 / 盘亏 38.40）与 6290
        assertThat(sql).containsPattern("6301, 1001, 'PD2610010001', '2026-10-01', 2, 1, 1,\\s*"
                + "25\\.20, 38\\.40");
        assertThat(sql).containsPattern("6290, 1001, 'PD2609010001', '2026-09-01'");
        // 流水 ID 与 mock：feed 9301 / 9302（最近领用），drug 9500 / 9511 / 9512
        for (long flowId : new long[]{9240, 9260, 9270, 9288, 9290, 9301, 9302,
                9500, 9501, 9502, 9503, 9504, 9505, 9506, 9507, 9508, 9511, 9512}) {
            assertThat(sql).contains("(" + flowId + ", 1001,");
        }
        // 盘点流水 9201 / 9202
        assertThat(sql).contains("(9201, 1001,").contains("(9202, 1001,");
        // 模块08 联调：6212–6216 入库单 + 9520–9524 入库流水，9525 是批次 3001 的用药流水
        for (long inboundId : new long[]{6212, 6213, 6214, 6215, 6216}) {
            assertThat(sql).contains("(" + inboundId + ", 1001, 'RK");
        }
        assertThat(sql).containsPattern("(?s)\\(9525, 1001, 'DRUG', NULL, 5202, 6111, 'MEDICATION_USE', 'OUT', "
                + "800\\.000,\\s*0\\.1200, 96\\.00, 1000\\.000, 120\\.00, 'MEDICATION_RECORD', 5101, 3001,\\s*"
                + "2003, 10003, '咳嗽、采食下降', '2026-10-09 15:00:00', '2026-10-09', 1, 0");
    }

    @Test
    void v910SeedsBatchMaterialCostWithMockCostTypes() {
        String sql = read(SAMPLE_DIR, "V9_10__sample_stock.sql");

        // 批次 3001 饲料 448.00 + 505.60（mock feed_overview.json 的流水），3003 药品 280.00
        assertThat(sql).contains("(8001, 1001, 3001, 2003, 9288, 'FEED_RECORD', 7001, 'FEED', 140.000");
        assertThat(sql).contains("(8002, 1001, 3001, 2003, 9301, 'FEED_RECORD', 7106, 'FEED', 158.000");
        assertThat(sql).contains("(8003, 1001, 3003, 2001, 9302, 'FEED_RECORD', 7120, 'FEED', 150.000");
        assertThat(sql).contains("(8004, 1001, 3003, 2001, 9501, 'IMMUNE_RECORD', 8812, 'DRUG', 100.000");
        // 模块08 / 模块10 联调：批次 3001 的用药成本 96.00（流水 9525）
        assertThat(sql).contains("(8007, 1001, 3001, 2003, 9525, 'MEDICATION_RECORD', 5101, 'DRUG', 800.000");
        assertThat(sql).containsPattern("800\\.000,\\s*200, 1\\.0000, 96\\.00, '2026-10-09'");
        // 成本类型只允许 FEED / DRUG（模块10 通过 StockCostQueryService 汇总）
        assertThat(sql).contains(", 'FEED', ").contains(", 'DRUG', ");
        assertThat(sql).doesNotContain(", 'INTRO', ");
    }

    @Test
    void v910DoesNotTouchV904PensOrBatches() {
        String sql = read(SAMPLE_DIR, "V9_10__sample_stock.sql");

        assertThat(sql).doesNotContain("INSERT INTO t_pen").doesNotContain("INSERT INTO t_batch ");
        assertThat(sql).doesNotContain("UPDATE t_pen").doesNotContain("UPDATE t_batch ");
        assertThat(sql).doesNotContain("INSERT INTO t_batch_pen");
        // 只通过 ALTER TABLE 调整自增起点
        assertThat(sql).contains("ALTER TABLE t_feed AUTO_INCREMENT = 5300");
        assertThat(sql).contains("ALTER TABLE t_drug AUTO_INCREMENT = 5400");
        assertThat(sql).contains("ALTER TABLE t_drug_lot AUTO_INCREMENT = 7001");
    }

    @Test
    void v109DefinesStockTablesUsedByCostQuery() {
        String sql = read(MIGRATION_DIR, "V1_09__init_stock.sql");

        assertThat(sql).contains("CREATE TABLE t_batch_material_cost");
        assertThat(sql).contains("cost_type").contains("cost_type   VARCHAR(20)     NOT NULL");
        assertThat(sql).contains("idx_bmc_batch (batch_id, cost_type, status)");
        assertThat(sql).contains("status      VARCHAR(20)     NOT NULL DEFAULT 'NORMAL'");
    }

    private static String read(Path dir, String file) {
        try {
            return Files.readString(dir.resolve(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path dbDir(String name) {
        String userDir = System.getProperty("user.dir");
        List<Path> candidates = List.of(
                Path.of(userDir, "..", "zhimu-app", "src", "main", "resources", "db", name).normalize(),
                Path.of(userDir, "zhimu-app", "src", "main", "resources", "db", name).normalize());
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath();
            }
        }
        throw new IllegalStateException("找不到 db/" + name + "，已尝试：" + candidates);
    }
}
