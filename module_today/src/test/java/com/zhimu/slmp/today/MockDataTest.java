package com.zhimu.slmp.today;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.today.model.vo.HomeVO;
import com.zhimu.slmp.today.model.vo.MessageDetailVO;
import com.zhimu.slmp.today.model.vo.MessagePageVO;
import com.zhimu.slmp.today.model.vo.OpenResultVO;
import com.zhimu.slmp.today.model.vo.TodoCalendarResult;
import com.zhimu.slmp.today.model.vo.TodoDetailVO;
import com.zhimu.slmp.today.model.vo.TodoPageVO;
import com.zhimu.slmp.today.model.vo.TodoWeeklyResult;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Mock 数据自检：routes.json 引用的文件都存在且为完整 ApiResponse；关键响应能解析为本组件的 VO 并符合 4.4 节示例数据。
 */
public class MockDataTest {

    private static final File DIR = new File("src/debug/assets/mock/today");

    private static JsonObject read(String name) throws IOException {
        String text = new String(Files.readAllBytes(new File(DIR, name).toPath()), StandardCharsets.UTF_8);
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static <T> T data(String name, Class<T> cls) throws IOException {
        return GsonFactory.get().fromJson(read(name).get("data"), cls);
    }

    @Test
    public void routesReferenceExistingResponses() throws IOException {
        String text = new String(Files.readAllBytes(new File(DIR, "routes.json").toPath()), StandardCharsets.UTF_8);
        JsonArray routes = JsonParser.parseString(text).getAsJsonArray();
        assertTrue(routes.size() > 50);
        for (JsonElement e : routes) {
            String file = e.getAsJsonObject().get("file").getAsString();
            JsonObject resp = read(file);
            assertTrue(file, resp.has("code") && resp.has("message") && resp.has("trace_id"));
        }
    }

    @Test
    public void homeAndTodayMatchPrototype() throws IOException {
        HomeVO owner = data("home_owner.json", HomeVO.class);
        assertEquals(870, owner.getOwnerMetrics().getStockCount());
        assertEquals(16, owner.getOwnerMetrics().getTodayTodoCount());
        assertEquals(3, owner.getUnreadCount());
        TodoPageVO today = data("today_owner.json", TodoPageVO.class);
        assertEquals(16, today.getList().size());
        assertEquals("G02", today.getList().get(0).getListLink().getRoute());
        assertEquals("2001", today.getList().get(0).getListLink().getParams().get("pen_id"));

        HomeVO staff = data("home_staff.json", HomeVO.class);
        assertEquals(2, staff.getMyPens().size());
        assertEquals("ZM2610-031", staff.getMyPens().get(0).getBatches().get(0).getBatchNo());
        assertEquals(7, data("today_staff.json", TodoPageVO.class).getList().size());
    }

    @Test
    public void calendarAndWeekly() throws IOException {
        TodoCalendarResult month = data("calendar_month_owner.json", TodoCalendarResult.class);
        assertEquals(35, month.getDays().size());
        TodoWeeklyResult week = data("weekly_all.json", TodoWeeklyResult.class);
        assertEquals(24, week.getDays().get(0).getTotal());
        assertEquals(5, week.getDays().get(0).getItems().size());
        assertEquals(17, data("day_1019_owner.json", TodoPageVO.class).getList().size());
    }

    @Test
    public void detailsAndMessages() throws IOException {
        TodoDetailVO d = data("todo_90112.json", TodoDetailVO.class);
        assertTrue(d.isCanComplete());
        assertEquals(3, d.getVersion());
        assertNotNull(d.getRelatedBatch());
        assertEquals(41001, read("error_41001.json").get("code").getAsInt());

        MessagePageVO p1 = data("messages_owner_p1.json", MessagePageVO.class);
        assertTrue(p1.isHasMore());
        assertEquals(3, p1.getFilteredUnread());
        long categories = p1.getList().stream().map(m -> m.getCategory()).distinct().count();
        assertEquals(7, categories);
        assertFalse(data("messages_owner_p2.json", MessagePageVO.class).isHasMore());
        assertNotNull(p1.getList().get(3).getAction());

        MessageDetailVO revoked = data("message_512.json", MessageDetailVO.class);
        assertTrue(revoked.getPayload().get("revoked").getAsBoolean());
        OpenResultVO deleted = data("open_515.json", OpenResultVO.class);
        assertEquals("DELETED", deleted.getTargetStatus());
        assertEquals("批次档案", deleted.getPageTitle());
    }
}
