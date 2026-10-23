package com.zhimu.slmp.ai;

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
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.AiStreamEvent;
import com.zhimu.slmp.ai.model.vo.AiMessageSliceVO;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.ai.model.vo.AiSessionDetailVO;
import com.zhimu.slmp.ai.model.vo.AiSessionItemVO;
import com.zhimu.slmp.ai.model.vo.ContinueWatchingVO;
import com.zhimu.slmp.ai.model.vo.CoursePlayVO;
import com.zhimu.slmp.ai.model.vo.KnowledgeDetailVO;
import com.zhimu.slmp.ai.model.vo.LinkableBatchesVO;
import com.zhimu.slmp.ai.model.vo.LinkedBatchVO;
import com.zhimu.slmp.ai.model.vo.TopicDetailVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.common.ai.AiSseEvent;
import com.zhimu.slmp.common.ai.AiSseReader;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.network.model.PageVO;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Mock 数据自检：routes.json 只含本模块接口、引用的文件都存在；JSON 为完整 ApiResponse，SSE 可逐个解析且以 done 或 error 结束；
 * 关键数据符合统一示例（ZM2610-031 杜长大育肥猪 3 号育肥舍 96 日龄 200 头；员工只列 3 号、4 号育肥舍；
 * 仔猪腹泻防控 6 节 1318 秒已学 2；仔猪腹泻的日常防控 222 秒看到 84 秒）。
 */
public class AiMockDataTest {

    @Rule
    public AiTestRules rules = new AiTestRules();

    @Test
    public void routesStayInModule_andFilesValid() throws IOException {
        JsonArray routes = JsonParser.parseString(AiTestRules.read("routes.json")).getAsJsonArray();
        assertTrue(routes.size() > 50);
        for (JsonElement e : routes) {
            JsonObject r = e.getAsJsonObject();
            String path = r.get("path").getAsString();
            boolean own = path.startsWith("/api/v1/ai/") || path.startsWith("/api/v1/knowledge-items/")
                    || path.startsWith("/api/v1/course-") || path.startsWith("/api/v1/courses");
            assertTrue(path, own);
            String file = r.get("file").getAsString();
            assertTrue(file, new File(AiTestRules.MOCK_DIR, file).isFile());
            if (file.endsWith(".sse")) {
                List<AiSseEvent> events = AiSseReader.readAll(AiTestRules.read(file));
                AiStreamEvent first = AiStreamEvent.parse(GsonFactory.get(), events.get(0));
                assertEquals(file, AiStreamEvent.Type.META, first.getType());
                AiStreamEvent last = AiStreamEvent.parse(GsonFactory.get(), events.get(events.size() - 1));
                assertTrue(file, last.getType() == AiStreamEvent.Type.DONE || last.getType() == AiStreamEvent.Type.ERROR);
            } else {
                JsonObject resp = AiTestRules.mockResponse(file);
                assertTrue(file, resp.has("code") && resp.has("message") && resp.has("trace_id"));
            }
        }
    }

    @Test
    public void noPriceOrAmountFields() throws IOException {
        for (File f : AiTestRules.MOCK_DIR.listFiles()) {
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            assertFalse(f.getName(), s.contains("price") || s.contains("amount") || s.contains("cost\""));
        }
    }

    @Test
    public void batchesMatchUnifiedExample() throws IOException {
        LinkableBatchesVO owner = AiTestRules.mock("linkable_owner.json", LinkableBatchesVO.class);
        assertEquals(4, owner.getBatches().size());
        LinkedBatchVO b = owner.getBatches().get(0);
        assertEquals("ZM2610-031 · 育肥猪 · 96 日龄 · 存栏 200 头", AiText.batchChip(b));
        assertEquals("ZM2610-031 · 杜长大育肥猪", AiText.batchTitle(b));
        assertEquals("3 号育肥舍 · 96 日龄 · 存栏 200 头", AiText.batchSub(b));
        LinkableBatchesVO staff = AiTestRules.mock("linkable_staff.json", LinkableBatchesVO.class);
        assertEquals(2, staff.getBatches().size());
        assertEquals("4 号育肥舍", staff.getBatches().get(1).getPenName());
        assertTrue(staff.getPanelHint().startsWith("只能关联你负责栏舍"));
    }

    @Test
    public void sessionsAndMessages() throws IOException {
        PageVO<AiSessionItemVO> page = AiTestRules.mock("sessions_owner.json",
                new TypeToken<PageVO<AiSessionItemVO>>() {
                }.getType());
        assertEquals(6, page.getList().size());
        assertEquals("ZM2610-031 · 1 轮 · 09:12", AiText.sessionSub(page.getList().get(0)));
        assertEquals("未关联批次 · 4 轮 · 10月13日", AiText.sessionSub(page.getList().get(4)));
        AiSessionDetailVO s = AiTestRules.mock("session_1021.json", AiSessionDetailVO.class);
        assertEquals(AiCodes.LINK_VALID, s.getBatchLinkState());
        assertEquals(3001, s.getBatch().getBatchId());
        AiMessageSliceVO slice = AiTestRules.mock("messages_1021.json", AiMessageSliceVO.class);
        AiMessageVO m = slice.getList().get(0);
        assertEquals(2, m.getCitations().size());
        assertTrue(m.getAnswerText().contains("[^1]"));
        assertNotNull(m.getCorrection());
        AiMessageSliceVO s1018 = AiTestRules.mock("messages_1018.json", AiMessageSliceVO.class);
        assertEquals(14, s1018.getList().size());
        assertFalse(s1018.getList().get(3).isInContext());
        assertTrue(s1018.getList().get(4).isInContext());
        AiMessageSliceVO s1015 = AiTestRules.mock("messages_1015.json", AiMessageSliceVO.class);
        assertEquals(AiCodes.STATUS_TIMEOUT, s1015.getList().get(2).getStatus());
    }

    @Test
    public void knowledgeAndCourses() throws IOException {
        KnowledgeDetailVO k = AiTestRules.mock("knowledge_8101_cited.json", KnowledgeDetailVO.class);
        assertEquals("p3", k.getHighlightParaKey());
        assertEquals(6301, k.getEmbeddedVideo().getCourseId());
        KnowledgeDetailVO k2 = AiTestRules.mock("knowledge_8102_cited.json", KnowledgeDetailVO.class);
        assertTrue(k2.isUpdatedAfterAnswer());
        assertNull(AiTestRules.mock("knowledge_8101.json", KnowledgeDetailVO.class).getHighlightParaKey());

        TopicDetailVO t = AiTestRules.mock("topic_7001.json", TopicDetailVO.class);
        assertEquals(6, t.getCourseCount());
        assertEquals(1318, t.getTotalDurationSec());
        assertEquals(2, t.getLearnedCount());
        assertEquals("6 节 · 约 22 分钟", AiText.topicMeta(t));
        assertEquals(6102, t.getResume().getCourseId());
        assertEquals(84, t.getResume().getPositionSec());

        CoursePlayVO p = AiTestRules.mock("course_6102.json", CoursePlayVO.class);
        assertEquals(222, p.getDurationSec());
        assertEquals(84, p.getProgress().getResumePositionSec());
        assertEquals(Long.valueOf(6103), p.getNextCourseId());
        assertEquals(2, p.getSources().size());
        assertTrue(p.sourceOf(AiCodes.QUALITY_720).getHlsUrl().endsWith(".m3u8"));
        assertEquals(28, AiText.sizeMb(p.sourceOf(AiCodes.QUALITY_720).getSizeBytes()));

        ContinueWatchingVO c = AiTestRules.mock("continue.json", ContinueWatchingVO.class);
        assertEquals("看到 1:24 · 还剩 2:18", AiText.continueText(c));
    }
}
