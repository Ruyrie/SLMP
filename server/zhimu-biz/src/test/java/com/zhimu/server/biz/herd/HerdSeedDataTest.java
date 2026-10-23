package com.zhimu.server.biz.herd;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块04 示例数据静态校验（不启动 Spring、不连库）：V9_04 与 App mock 的 ID / 名称 / 数量 / 日期对齐，
 * 以及 V9_05 的消息页与登记人（mock messages_owner_p2.json、day_1019_owner.json）。
 */
class HerdSeedDataTest {

    private static final Path SAMPLE_DIR = sampleDir();

    @Test
    void v904SeedsPensBatchesAndCountersMatchingAppMock() {
        String sql = read("V9_04__sample_herd.sql");

        // 栏舍 2001–2007（1001）与 2201–2203（1002）
        for (String row : List.of(
                "(2001, 1001, 'BY-01', '1 号保育舍', 'NURSERY', 101, 360, 320, 'ENABLED'",
                "(2003, 1001, 'YF-03', '3 号育肥舍', 'FATTENING', 101, 220, 200, 'ENABLED'",
                "(2004, 1001, 'YF-04', '4 号育肥舍', 'FATTENING', 101, 220, 150, 'ENABLED'",
                "(2005, 1001, 'YF-05', '5 号育肥舍', 'FATTENING', 101, 400, 0, 'ENABLED'",
                "(2201, 1002, 'YC-01', '1 号育雏舍', 'BROODING', 201, 10000, 9300, 'ENABLED'",
                "(2202, 1002, 'RJ-01', '2 号鸡舍', 'BROILER', 201, 10000, 9300, 'ENABLED'")) {
            assertThat(sql).contains(row);
        }
        // 容量 360+400+220+220+400 = 1600（非种畜栏舍），存栏 320+200+200+150 = 870
        assertThat(sql).contains("存栏 870");
        assertThat(sql).contains("存栏 18600");

        // 在养批次：3001 205 入栏 / 5 死淘 / 200 存栏；3002–3004 与 mock 一致
        assertThat(sql).containsPattern("(?s)\\(3001, 1001, 'ZM2610-031', '2610', 31, 'k7Rm2xQ9pT'.*?"
                + "2003, '2026-10-01', 78, 205, 45\\.00, 500\\.00, 200, 5, 0, 'ACTIVE'");
        assertThat(sql).containsPattern("(?s)\\(3002, 1001, 'ZM2610-019'.*?"
                + "2004, '2026-10-05', 68, 152, 38\\.20, 480\\.00, 150, 2, 0, 'ACTIVE'");
        assertThat(sql).containsPattern("(?s)\\(3003, 1001, 'ZM2610-045'.*?"
                + "2001, '2026-10-12', 28, 326, 8\\.50, 420\\.00, 320, 6, 0, 'ACTIVE'");
        assertThat(sql).containsPattern("(?s)\\(3004, 1001, 'ZM2609-038'.*?"
                + "2002, '2026-09-20', 34, 215, 12\\.00, 430\\.00, 200, 5, 10, 'ACTIVE'");
        // 已结束 12 个（3010–3021），5 号育肥舍的最近结束批次 3010 于 2026-10-13 结束
        assertThat(sql).containsPattern("(?s)\\(3010, 1001, 'ZM2609-012'.*?'ALL_SOLD',\\s*'2026-10-13'");
        assertThat(count(sql, "(30")).isGreaterThanOrEqualTo(12);
        // 历史死淘 3010 的 1 头 → 全场批次死淘累计 19
        assertThat(sql).containsPattern("(?s)\\(910, 1001, 3010, 'DEATH'.*?1, 'OTHER'");
    }

    @Test
    void v905SeedsMessagesAndCompletedFeedersMatchingAppMock() {
        String sql = read("V9_05__sample_today.sql");

        // 消息 501–524 共 24 条（messages_owner_p1/p2 的 total = 24）
        assertThat(countMessages(sql)).isEqualTo(24);
        assertThat(sql).contains("(523, 10001, 1001, 'OWNER', 'OTHER', 'REPORT_READY'");
        assertThat(sql).contains("'东山猪场 8 月月报已生成', '8月1日–31日 · 经营报表'");
        assertThat(sql).contains("(524, 10001, 1001, 'OWNER', 'MEMBER', 'INVITE_ACCEPTED'");
        assertThat(sql).contains("'李明已加入东山猪场', '员工 · 负责 3 号育肥舍'");
        // 512 撤回的通知仍保留在消息中心（列表显示“已撤回”）
        assertThat(sql).containsPattern("(?s)\\(512, 10001, NULL, NULL, 'NOTICE'.*?'NOTICE_REVOKED'");
        // 90119 的登记人是李明（mock day_1019_owner.json 的“李明 07:20 登记”）
        assertThat(sql).containsPattern("(?s)\\(90119, 1001, 'FEEDING'.*?'DONE', 0, '2026-10-19 07:20:00', "
                + "10003, 'REGISTER'");
        assertThat(sql).containsPattern("(?s)\\(7802, 90119, 1001, 'COMPLETE', 'REGISTER', 10003,");
        // 固定“今天”= 2026-10-19
        assertThat(sql).contains("'2026-10-19'");
    }

    private static int countMessages(String sql) {
        Matcher matcher = Pattern.compile("(?m)^\\((5\\d\\d), 10001,").matcher(sql);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
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
            return Files.readString(SAMPLE_DIR.resolve(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path sampleDir() {
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
