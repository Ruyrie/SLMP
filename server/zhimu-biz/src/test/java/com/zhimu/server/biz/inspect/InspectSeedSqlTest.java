package com.zhimu.server.biz.inspect;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块07 示例数据（V9_09）与 App mock（module_inspect/src/debug/assets/mock/inspect）的静态对齐校验：
 * 巡检 50060–50124、上报 5988/6001/6002/6003/6005、诊断 9901–9905、处理结果 7702 的 ID 与日期。
 * 不修改 V9_04 的栏舍与批次。
 */
class InspectSeedSqlTest {

    private static final Path SAMPLE_DIR = dbDir();

    @Test
    void v909SeedsInspectionsAlignedWithMock() {
        String sql = read("V9_09__sample_inspection.sql");

        // 巡检设置 18:00（setting.json / setting_1700.json 的默认值）
        assertThat(sql).containsPattern("1601, 1001, '18:00:00', 60");
        // 历史巡检与当天应巡行：mock detail_50101/50121/50124/50071 与 history_2003.json
        assertThat(sql).containsPattern("(?s)\\(50071, 1001, 2003, 3001, '2026-10-14', 'DONE'");
        assertThat(sql).containsPattern("(?s)\\(50101, 1001, 2003, 3001, '2026-10-18', 'DONE'");
        assertThat(sql).containsPattern("(?s)\\(50121, 1001, 2003, 3001, '2026-10-19', 'DONE'");
        assertThat(sql).containsPattern("(?s)\\(50124, 1001, 2001, 3003, '2026-10-19', 'DONE'");
        // 当天未巡占位行 50122（2004）/ 50123（2002）、昨天漏巡 50103（2002）
        assertThat(sql).containsPattern("(?s)\\(50122, 1001, 2004, 3002, '2026-10-19', 'PENDING'");
        assertThat(sql).containsPattern("(?s)\\(50123, 1001, 2002, 3004, '2026-10-19', 'PENDING'");
        assertThat(sql).containsPattern("(?s)\\(50103, 1001, 2002, 3004, '2026-10-18', 'MISSED'");
        // 50121 是异常巡检（粪便 水样 → report 6001）
        assertThat(sql).containsPattern("(?s)\\(50121, 1001, 2003, 3001, '2026-10-19', 'DONE'.*?"
                + "'CHECK', '粪便 水样'.*?6001, 1");
        // 50101 超时（is_overdue = 1）、50081 补录 + 非现场
        assertThat(sql).containsPattern("(?s)\\(50101, 1001, 2003, 3001, '2026-10-18', 'DONE'.*?"
                + "'ON_SITE'.*?1, 0,");
        assertThat(sql).containsPattern("(?s)\\(50081, 1001, 2003, 3001, '2026-10-16', 'DONE'.*?'OFF_SITE'");
    }

    @Test
    void v909SeedsReportsAndDiagnosesAlignedWithMock() {
        String sql = read("V9_09__sample_inspection.sql");

        // mock diag_5988 / 6001 / 6002 / 6003 / 6005
        assertThat(sql).containsPattern("(?s)\\(5988, 1001, 2003, 3001, NULL, 'DIRECT'.*?'DIAGNOSED', 'DONE'");
        assertThat(sql).containsPattern("(?s)\\(6001, 1001, 2003, 3001, 50121, 'INSPECTION'.*?"
                + "12, 200, 0\\.0600.*?'DIAGNOSED', 'DONE'");
        assertThat(sql).containsPattern("(?s)\\(6002, 1001, 2001, 3003, NULL, 'DIRECT'.*?'URGENT'");
        assertThat(sql).containsPattern("(?s)\\(6003, 1001, 2003, 3001, NULL, 'DIRECT'.*?'UNAVAILABLE'");
        assertThat(sql).containsPattern("(?s)\\(6005, 1001, 2003, 3001, NULL, 'DIRECT'.*?'HANDLED'");
        // 诊断尝试 9901–9905，6003 为超时（state_6003_unavailable.json 的 last_failure_text）
        for (long diagnosisId : new long[]{9901, 9902, 9903, 9904, 9905}) {
            assertThat(sql).contains("(" + diagnosisId + ", 1001,");
        }
        assertThat(sql).containsPattern("(?s)\\(9903, 1001, 6003, 1, 'SUBMIT', 'TIMEOUT'.*?'TIMEOUT'");
        // 处理结果 7702（mock handle_saved.json 的 handle_id）
        assertThat(sql).containsPattern("7702, 1001, 6005, '赵磊（执业兽医）'");
        // 症状字典 401–408 与 mock symptoms_pig.json 一致
        assertThat(sql).contains("(1001, 6001, 401, '腹泻', 1,");
    }

    @Test
    void v909DoesNotTouchV904PensOrBatches() {
        String sql = read("V9_09__sample_inspection.sql");

        assertThat(sql).doesNotContain("INSERT INTO t_pen").doesNotContain("INSERT INTO t_batch ");
        assertThat(sql).doesNotContain("UPDATE t_pen").doesNotContain("UPDATE t_batch ");
    }

    private static String read(String file) {
        try {
            return Files.readString(SAMPLE_DIR.resolve(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path dbDir() {
        String userDir = System.getProperty("user.dir");
        List<Path> candidates = List.of(
                Path.of(userDir, "..", "zhimu-app", "src", "main", "resources", "db", "sample").normalize(),
                Path.of(userDir, "zhimu-app", "src", "main", "resources", "db", "sample").normalize());
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath();
            }
        }
        throw new IllegalStateException("找不到 db/sample，已尝试：" + candidates);
    }
}
