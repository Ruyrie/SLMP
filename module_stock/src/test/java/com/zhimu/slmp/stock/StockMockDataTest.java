package com.zhimu.slmp.stock;

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
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.stock.model.vo.DrugLotDetailVO;
import com.zhimu.slmp.stock.model.vo.DrugLotItemVO;
import com.zhimu.slmp.stock.model.vo.DrugLotOptionsVO;
import com.zhimu.slmp.stock.model.vo.DrugOptionListResult;
import com.zhimu.slmp.stock.model.vo.DrugStockOverviewVO;
import com.zhimu.slmp.stock.model.vo.FeedStockItemVO;
import com.zhimu.slmp.stock.model.vo.FeedStockOverviewVO;
import com.zhimu.slmp.stock.model.vo.StockOutboundVO;
import com.zhimu.slmp.stock.util.StockQtyFormatter;
import com.zhimu.slmp.stock.util.StockTexts;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mock 数据自检：routes.json 引用的文件都存在且为完整 ApiResponse；借用其他模块的接口（批号选项、字典、目录、修改记录、
 * 修改申请表单）一律带条件，批号选项只写本模块的 purpose（USE / RETURN），不遮住模块08 的规则；
 * 关键响应符合统一示例（育肥全价料 5101 结存 500 千克剩余 1 天、保育料 1,850 千克、口蹄疫 2025047 已停用 300 头份、
 * 药品与批号编号与模块08、模块03 的 Mock 一致）。
 */
public class StockMockDataTest {

    @Rule
    public TestRules rules = new TestRules();

    /** 本模块自己的接口路径前缀 */
    private static final List<String> OWN = Arrays.asList("/api/v1/feed-stock/", "/api/v1/feeds",
            "/api/v1/feed-inbounds", "/api/v1/stock-inbounds/", "/api/v1/stock-partners/", "/api/v1/feed-flows",
            "/api/v1/stocktakes", "/api/v1/feed-losses", "/api/v1/stock-losses/", "/api/v1/feed-other-outbounds",
            "/api/v1/stock-outbounds/", "/api/v1/drug-stock/", "/api/v1/drug-flows", "/api/v1/drug-inbounds",
            "/api/v1/drug-scraps", "/api/v1/drug-outbounds", "/api/v1/drugs", "/api/v1/stock-settings",
            "/api/v1/stock-requests/");

    private JsonArray routes() throws IOException {
        String text = new String(Files.readAllBytes(new File(TestRules.MOCK_DIR, "routes.json").toPath()),
                StandardCharsets.UTF_8);
        return JsonParser.parseString(text).getAsJsonArray();
    }

    @Test
    public void routesReferenceCompleteResponses() throws IOException {
        JsonArray routes = routes();
        assertTrue(routes.size() > 100);
        for (JsonElement e : routes) {
            JsonObject r = e.getAsJsonObject();
            String file = r.get("file").getAsString();
            JsonObject resp = TestRules.mockResponse(file);
            assertTrue(file, resp.has("code") && resp.has("message") && resp.has("trace_id") && resp.has("data"));
        }
    }

    @Test
    public void borrowedInterfacesAlwaysHaveConditions() throws IOException {
        for (JsonElement e : routes()) {
            JsonObject r = e.getAsJsonObject();
            String path = r.get("path").getAsString();
            boolean own = false;
            for (String p : OWN) {
                own |= path.startsWith(p);
            }
            // drug-lots 列表与详情是本模块的；批号选项与 D03 / D04 / D08 共用
            if (path.equals("/api/v1/drug-lots") || path.equals("/api/v1/drug-lots/{id}")) {
                own = true;
            }
            if (!own) {
                assertTrue(path + " 必须带 when 条件", r.has("when"));
            }
            if (path.equals("/api/v1/drug-lots/options")) {
                String purpose = r.getAsJsonObject("when").getAsJsonObject("query").get("purpose").getAsString();
                assertTrue(purpose, purpose.equals("USE") || purpose.equals("RETURN"));
            }
            if (path.equals("/api/v1/drug-lots/{id}")) {
                assertTrue("批号详情只写具体编号，避免吞掉 /drug-lots/options", r.has("when"));
            }
            if (path.equals("/api/v1/record-logs") || path.equals("/api/v1/change-requests/form")) {
                assertTrue(r.getAsJsonObject("when").getAsJsonObject("query").has("biz_type"));
            }
            assertFalse("不 Mock 模块02 / 04 的下拉", path.equals("/api/v1/pens") || path.equals("/api/v1/batches")
                    || path.startsWith("/api/v1/farms/"));
        }
    }

    @Test
    public void feedStockMatchesUnifiedExample() throws IOException {
        FeedStockOverviewVO o = TestRules.mock("feed_overview.json", FeedStockOverviewVO.class);
        assertEquals("育肥全价料库存不足，剩余可用 1 天，低于预警天数 7 天。", StockTexts.feedBanner(o.banner));
        assertEquals(0, o.stockAmount.compareTo(new BigDecimal("9370")));
        assertEquals(0, o.usage7dTotal.compareTo(new BigDecimal("4410")));
        assertEquals("FEED_RECORD", o.recentFlows.get(0).bizType);
        assertEquals(Long.valueOf(7106), o.recentFlows.get(0).bizId);
        PageVO<FeedStockItemVO> feeds = TestRules.mock("feeds_page.json",
                new TypeToken<PageVO<FeedStockItemVO>>() {
                }.getType());
        FeedStockItemVO f = feeds.getList().get(0);
        assertEquals(5101, f.feedId);
        assertEquals("500 千克", TestRules.plain(StockQtyFormatter.format(f.stock)));
        assertEquals("近 7 天领用 3,360 千克 · 均价每千克 3.20 元", TestRules.plain(StockTexts.feedUsage(f)));
        assertEquals("1,850 千克", TestRules.plain(StockQtyFormatter.format(feeds.getList().get(1).stock)));
    }

    @Test
    public void drugStockMatchesUnifiedExample() throws IOException {
        DrugStockOverviewVO o = TestRules.mock("drug_overview.json", DrugStockOverviewVO.class);
        assertEquals("阿莫西林可溶性粉库存不足，可用结存 12 袋，低于库存下限 15 袋。",
                TestRules.plain(StockTexts.drugBanner(o.lowStockBanner)));
        PageVO<DrugLotItemVO> att = TestRules.mock("lots_attention.json",
                new TypeToken<PageVO<DrugLotItemVO>>() {
                }.getType());
        assertEquals(3, att.getList().size());
        assertEquals(Integer.valueOf(3), att.getTabCounts().get("ATTENTION"));
        DrugLotItemVO recalled = att.getList().get(0);
        assertEquals("30 瓶（300 头份）", TestRules.plain(StockQtyFormatter.format(recalled.stock)));
        assertEquals("已停用", StockTexts.lotTag(recalled));
        assertEquals("临期 20 天", StockTexts.lotTag(att.getList().get(2)));
        assertEquals("8 桶", TestRules.plain(StockQtyFormatter.format(att.getList().get(2).stock)));

        DrugLotDetailVO lot = TestRules.mock("lot_5201.json", DrugLotDetailVO.class);
        assertEquals("口蹄疫灭活疫苗", lot.drugName);
        assertEquals(Long.valueOf(506), lot.recallMessageId);
        assertEquals(0, lot.stockAmount.compareTo(new BigDecimal("840")));
        assertNotNull(lot.stockVersion);

        DrugLotOptionsVO use = TestRules.mock("lotopt_5301_use.json", DrugLotOptionsVO.class);
        assertEquals(Long.valueOf(6301), use.defaultLotId);
        assertNull(TestRules.mock("lotopt_5301_return.json", DrugLotOptionsVO.class).defaultLotId);
        assertTrue(TestRules.mock("lotopt_5201_use.json", DrugLotOptionsVO.class).unselectable.get(0).canGoScrap);
    }

    @Test
    public void idsAlignWithHealthAndTodayMocks() throws IOException {
        DrugOptionListResult out = TestRules.mock("drug_options_outbound.json", DrugOptionListResult.class);
        Set<Long> ids = new HashSet<>();
        for (com.zhimu.slmp.stock.model.vo.DrugOptionVO d : out.list) {
            ids.add(d.drugId);
        }
        // 模块08 Mock：5001 猪瘟活疫苗、5201 阿莫西林可溶性粉、5202 氟苯尼考粉、5301 聚维酮碘溶液
        assertTrue(ids.containsAll(Arrays.asList(5001L, 5201L, 5202L, 5301L)));
        // 模块03 消息：lot_id 5201 口蹄疫 2025047 停用、5188 猪瘟 2024112 停用
        assertEquals("2025047", TestRules.mock("lot_5201.json", DrugLotDetailVO.class).lotNo);
        assertEquals("2024112", TestRules.mock("lot_5188.json", DrugLotDetailVO.class).lotNo);
        StockOutboundVO use = TestRules.mock("outbound_6511.json", StockOutboundVO.class);
        assertEquals(2, use.allocations.size());
        assertEquals(0, use.allocations.get(0).amount.compareTo(new BigDecimal("56.25")));
    }
}
