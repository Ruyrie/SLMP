package com.zhimu.slmp.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.feed.model.vo.FeedRecordDetailVO;
import com.zhimu.slmp.feed.model.vo.FeedTaskItemVO;
import com.zhimu.slmp.feed.model.vo.FeedTodaySummaryVO;
import com.zhimu.slmp.feed.model.vo.GrowthCurveVO;
import com.zhimu.slmp.network.model.PageVO;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Mock 数据自检：routes.json 引用的文件都存在且为完整 ApiResponse；路由不覆盖其他组件的接口；
 * 关键响应符合统一示例（每天 3 餐、每餐 160 千克、10月19日早餐 07:12 李明 158 千克、结存 500 千克、称重 60.0 / 68.6）。
 * 全部 VO 不含单价、金额字段。
 */
public class FeedMockDataTest {

    @Rule
    public TestRules rules = new TestRules();

    @Test
    public void routesReferenceExistingResponses_andStayInModule() throws IOException {
        String text = new String(Files.readAllBytes(new File(TestRules.MOCK_DIR, "routes.json").toPath()),
                StandardCharsets.UTF_8);
        JsonArray routes = JsonParser.parseString(text).getAsJsonArray();
        assertTrue(routes.size() > 80);
        for (JsonElement e : routes) {
            JsonObject r = e.getAsJsonObject();
            String path = r.get("path").getAsString();
            boolean own = path.startsWith("/api/v1/feed-") || path.startsWith("/api/v1/weigh-records")
                    || path.matches("^/api/v1/batches/\\{batch_id}/(feed-plan.*|weigh-.*|growth-.*)$")
                    || (path.equals("/api/v1/record-logs") && r.has("when"));
            assertTrue(path, own);
            JsonObject resp = TestRules.mockResponse(r.get("file").getAsString());
            assertTrue(r.get("file").getAsString(), resp.has("code") && resp.has("message") && resp.has("trace_id"));
        }
    }

    @Test
    public void noPriceOrAmountFields() throws IOException {
        for (File f : TestRules.MOCK_DIR.listFiles()) {
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            assertFalse(f.getName(), s.contains("price") || s.contains("amount") || s.contains("cost\""));
        }
    }

    @Test
    public void todayMatchesUnifiedExample() throws IOException {
        FeedTodaySummaryVO s = TestRules.mock("summary_owner.json", FeedTodaySummaryVO.class);
        assertEquals(9, s.getTaskTotal());
        assertEquals(0, s.getPlanTotalQty().compareTo(new java.math.BigDecimal("1050")));
        assertEquals("育肥全价料", s.getPlanShortage().get(0).getFeedName());
        assertEquals(0, s.getPlanShortage().get(0).getStockQty().compareTo(new java.math.BigDecimal("500")));
        FeedTodaySummaryVO staff = TestRules.mock("summary_staff.json", FeedTodaySummaryVO.class);
        assertFalse(staff.isCanManage());
        assertFalse(staff.isCanStockIn());

        PageVO<FeedTaskItemVO> page = TestRules.mock("tasks_owner.json",
                new TypeToken<PageVO<FeedTaskItemVO>>() {
                }.getType());
        FeedTaskItemVO breakfast3 = page.getList().get(1);
        assertEquals("3 号育肥舍", breakfast3.getPenName());
        assertEquals("早餐", breakfast3.getMealName());
        assertEquals(0, breakfast3.getActualQty().compareTo(new java.math.BigDecimal("158")));
        assertEquals("李明", breakfast3.getExecutorName());
        assertEquals("2026-10-19 07:12:00", breakfast3.getRegisteredAt());
        assertEquals(0, page.getList().get(3).getPlanQty().compareTo(new java.math.BigDecimal("160")));

        FeedRecordDetailVO r = TestRules.mock("record_7106.json", FeedRecordDetailVO.class);
        assertEquals(FeedRecordDetailVO.VIEW_EDIT, r.getViewMode());
        assertTrue(r.isCanVoid());
        FeedRecordDetailVO rs = TestRules.mock("record_7106_staff.json", FeedRecordDetailVO.class);
        assertEquals(FeedRecordDetailVO.READONLY_STAFF_CONFIRMED, rs.getReadonlyReason());
        assertFalse(rs.isCanVoid());
    }

    @Test
    public void growthMatchesUnifiedExample() throws IOException {
        GrowthCurveVO c = TestRules.mock("curve_3001.json", GrowthCurveVO.class);
        assertNotNull(c.getMetrics());
        assertEquals(0, c.getMetrics().getAvgWeight().compareTo(new java.math.BigDecimal("60")));
        assertEquals(0, c.getMetrics().getStdWeight().compareTo(new java.math.BigDecimal("68.6")));
        assertEquals(0, c.getActualPoints().get(0).getAvgWeight().compareTo(new java.math.BigDecimal("45")));
        assertTrue(c.getActualPoints().get(0).isEntry());
        assertEquals("2026-10-05", c.getActualPoints().get(1).getDate());
        assertNull(TestRules.mock("curve_3003.json", GrowthCurveVO.class).getMetrics());
    }
}
