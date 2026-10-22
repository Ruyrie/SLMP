package com.zhimu.slmp.breed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalDetailVO;
import com.zhimu.slmp.breed.model.vo.BreedingOverviewVO;
import com.zhimu.slmp.breed.model.vo.FarrowDueItemVO;
import com.zhimu.slmp.breed.model.vo.HatchDueItemVO;
import com.zhimu.slmp.breed.util.BreedTextFormatter;
import com.zhimu.slmp.network.model.PageVO;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Mock 数据自检：routes.json 引用的文件都存在且为完整 ApiResponse；关键响应与 UI 文档示例一致
 * （东山猪场 24 头母猪、待产 1 / 2 / 8、临产横幅；员工只读版标记为 false；西岭鸡场 2 批待出雏）。
 */
public class MockDataTest {

    @Rule
    public TestRules rules = new TestRules();

    @Test
    public void routesReferenceExistingResponses() throws IOException {
        File routes = new File(TestRules.mockDir(), "routes.json");
        JsonArray list = JsonParser.parseString(new String(Files.readAllBytes(routes.toPath()),
                StandardCharsets.UTF_8)).getAsJsonArray();
        assertTrue(list.size() > 100);
        for (JsonElement e : list) {
            JsonObject r = e.getAsJsonObject();
            JsonObject resp = TestRules.mock(r.get("file").getAsString());
            assertTrue(r.toString(), resp.has("code") && resp.has("message") && resp.has("trace_id"));
            String path = r.get("path").getAsString();
            // 不为其他模块的接口写无条件规则
            if (!path.startsWith("/api/v1/breeding") && !path.startsWith("/api/v1/matings")
                    && !path.startsWith("/api/v1/pregnancy-checks") && !path.startsWith("/api/v1/farrowings")
                    && !path.startsWith("/api/v1/weanings") && !path.startsWith("/api/v1/incubations")) {
                assertTrue("其他模块接口需带条件：" + r, r.has("when"));
            }
        }
    }

    @Test
    public void overviewAndBanner() throws IOException {
        BreedingOverviewVO o = TestRules.data("overview_owner.json", BreedingOverviewVO.class);
        assertEquals(24, o.getCounts().getAll());
        assertEquals(11, o.getFarrowDueCount());
        assertEquals("0131 今天预产。0136 预产期 10月22日 周四，还有 3 天。", BreedTextFormatter.dueBanner(o));
        assertEquals("东山猪场没有孵化记录。切到西岭鸡场可以查看待出雏，预计出雏日提前 1 天和当天各提醒一次。",
                BreedTextFormatter.switchBanner("东山猪场", o));
        BreedingOverviewVO staff = TestRules.data("overview_staff.json", BreedingOverviewVO.class);
        assertNull(staff.getCounts());
        assertTrue(staff.getDueSoon().isEmpty());
    }

    @Test
    public void farrowDue_groupsAndStaffFlags() throws IOException {
        PageVO<FarrowDueItemVO> owner = TestRules.data("farrow_due_owner.json",
                new TypeToken<PageVO<FarrowDueItemVO>>() { }.getType());
        assertEquals(Integer.valueOf(1), owner.getTabCounts().get("TODAY"));
        assertEquals(Integer.valueOf(2), owner.getTabCounts().get("WITHIN_7"));
        assertEquals(Integer.valueOf(8), owner.getTabCounts().get("LATER"));
        FarrowDueItemVO first = owner.getList().get(0);
        assertEquals("0131 · 产房 · 第 3 胎", BreedTextFormatter.farrowDueTitle(first));
        assertEquals("6月27日配种 · 预产期 10月19日", BreedTextFormatter.farrowDueLine2(first));
        assertEquals("已提醒 10月16日、今天", first.getReminderText());
        assertTrue(first.isCanRegister());
        assertEquals("今天已发提前 3 天提醒", owner.getList().get(1).getReminderText());
        assertEquals("6月30日配种 · 预产期 10月22日 周四", BreedTextFormatter.farrowDueLine2(owner.getList().get(1)));

        PageVO<FarrowDueItemVO> staff = TestRules.data("farrow_due_staff.json",
                new TypeToken<PageVO<FarrowDueItemVO>>() { }.getType());
        for (FarrowDueItemVO i : staff.getList()) {
            assertFalse(i.isCanRegister());
            assertFalse(i.isCanOpenArchive());
        }
    }

    @Test
    public void hatchDue_flockTextAndArchive() throws IOException {
        PageVO<HatchDueItemVO> hatch = TestRules.data("hatch_due_1002.json",
                new TypeToken<PageVO<HatchDueItemVO>>() { }.getType());
        assertEquals(2, hatch.getList().size());
        HatchDueItemVO first = hatch.getList().get(0);
        assertEquals("1 号种鸡舍 · ZM2603-015", first.getFlockText());
        assertEquals("9月28日入孵 · 入孵 9,600 枚 · 受精蛋 9,120 枚 · 预计出雏 10月19日",
                TestRules.plain(BreedTextFormatter.hatchDueLine2(first)));

        BreedingAnimalDetailVO d = TestRules.data("animal_0161.json", BreedingAnimalDetailVO.class);
        assertEquals("2025-01-10", d.getFilingDate());
        assertEquals(2, d.getStats().getFarrowingCount());
        assertEquals("10.5", d.getStats().getAvgLiveBorn().toPlainString());
    }
}
