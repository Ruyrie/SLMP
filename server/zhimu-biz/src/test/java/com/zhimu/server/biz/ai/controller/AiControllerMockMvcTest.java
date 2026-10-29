package com.zhimu.server.biz.ai.controller;

import com.zhimu.server.biz.ai.request.AiAskRequest;
import com.zhimu.server.biz.ai.service.AiAskService;
import com.zhimu.server.biz.ai.service.AiMessageService;
import com.zhimu.server.biz.ai.service.AiSessionService;
import com.zhimu.server.biz.ai.service.AiSpeechService;
import com.zhimu.server.biz.ai.service.CourseService;
import com.zhimu.server.biz.ai.service.KnowledgeViewService;
import com.zhimu.server.biz.ai.support.AiFixtures;
import com.zhimu.server.biz.ai.vo.AiMessageSliceVO;
import com.zhimu.server.biz.ai.vo.AiMessageVO;
import com.zhimu.server.biz.ai.vo.LinkableBatchesVO;
import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 19 个映射的 standalone MockMvc 测试：路径/字段以 App 契约与 mock 为准；SSE 断言 content-type 与关键事件。
 */
class AiControllerMockMvcTest {

    private final AiSessionService sessionService = mock(AiSessionService.class);
    private final AiMessageService messageService = mock(AiMessageService.class);
    private final AiSpeechService speechService = mock(AiSpeechService.class);
    private final AiAskService askService = mock(AiAskService.class);
    private final KnowledgeViewService knowledgeService = mock(KnowledgeViewService.class);
    private final CourseService courseService = mock(CourseService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestMvc.builder(
                new AiSessionController(sessionService),
                new AiMessageController(messageService, speechService),
                new AiStreamController(askService),
                new KnowledgeController(knowledgeService),
                new CourseController(courseService)).build();
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        FarmContext.clear();
    }

    @Test
    void k01ListsSessions() throws Exception {
        when(sessionService.listSessions(eq(10001L), any())).thenReturn(AiFixtures.sessionPage());
        mvc.perform(get("/api/v1/ai/sessions").param("keyword", "休药").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(1021))
                .andExpect(jsonPath("$.data.list[0].batch_no").value("ZM2610-031"))
                .andExpect(jsonPath("$.data.list[0].date_group").value("TODAY"))
                .andExpect(jsonPath("$.data.list[0].date_group_text").value("今天"))
                .andExpect(jsonPath("$.data.list[0].has_correction").value(true));
    }

    @Test
    void k02ReturnsSessionDetail() throws Exception {
        when(sessionService.detail(10001L, 1015L)).thenReturn(AiFixtures.sessionDetail());
        mvc.perform(get("/api/v1/ai/sessions/1015"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1015))
                .andExpect(jsonPath("$.data.batch_link_state").value("VALID"))
                .andExpect(jsonPath("$.data.last_round_no").value(3))
                .andExpect(jsonPath("$.data.batch.batch_id").value(3003))
                .andExpect(jsonPath("$.data.batch.pen_name").value("1 号保育舍"));
    }

    @Test
    void k02OtherSessionReturns404() throws Exception {
        when(sessionService.detail(10001L, 9999L)).thenThrow(new BizException(ErrorCode.NOT_FOUND));
        mvc.perform(get("/api/v1/ai/sessions/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void k03ReturnsMessageSlice() throws Exception {
        AiMessageSliceVO slice = new AiMessageSliceVO();
        slice.setList(List.of(AiFixtures.messageVo()));
        slice.setHasMore(true);
        when(sessionService.messages(eq(10001L), eq(1015L), any())).thenReturn(slice);
        mvc.perform(get("/api/v1/ai/sessions/1015/messages")
                        .param("before_round_no", "4").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.has_more").value(true))
                .andExpect(jsonPath("$.data.list[0].id").value(5501))
                .andExpect(jsonPath("$.data.list[0].status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.list[0].can_rate").value(true));
    }

    @Test
    void k04ChangesBatchWithNullBodyValue() throws Exception {
        when(sessionService.update(eq(10001L), eq(1021L), any())).thenReturn(AiFixtures.sessionDetail());
        mvc.perform(patch("/api/v1/ai/sessions/1021")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"batch_id\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1015));
    }

    @Test
    void createsEmptySession() throws Exception {
        when(sessionService.create(eq(10001L), any())).thenReturn(AiFixtures.sessionDetail());
        mvc.perform(post("/api/v1/ai/sessions")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"新的会话\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1015));
    }

    @Test
    void deletesOwnSession() throws Exception {
        mvc.perform(delete("/api/v1/ai/sessions/1021"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void k05ReturnsLinkableBatches() throws Exception {
        LinkableBatchesVO vo = new LinkableBatchesVO();
        vo.setBatches(List.of(AiFixtures.batch()));
        vo.setPanelHint("只能关联东山猪场的在栏批次。");
        when(sessionService.linkableBatches(10001L)).thenReturn(vo);
        mvc.perform(get("/api/v1/ai/linkable-batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batches[0].batch_id").value(3003))
                .andExpect(jsonPath("$.data.batches[0].count_unit_text").value("头"))
                .andExpect(jsonPath("$.data.panel_hint").value("只能关联东山猪场的在栏批次。"));
    }

    @Test
    void k08ReturnsSingleMessage() throws Exception {
        when(messageService.getMessage(10001L, 5501L)).thenReturn(AiFixtures.messageVo());
        mvc.perform(get("/api/v1/ai/messages/5501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5501))
                .andExpect(jsonPath("$.data.answer_type").value("NORMAL"))
                .andExpect(jsonPath("$.data.in_context").value(true));
    }

    @Test
    void k09RatesAndCancelsRating() throws Exception {
        when(messageService.rate(10001L, 5501L, "USEFUL")).thenReturn(AiFixtures.rating());
        mvc.perform(put("/api/v1/ai/messages/5501/rating")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"USEFUL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value("USEFUL"))
                .andExpect(jsonPath("$.data.rated_at").value("2026-10-19 09:41:00"));
    }

    @Test
    void k09NullRatingCancels() throws Exception {
        when(messageService.rate(10001L, 5501L, null)).thenReturn(new com.zhimu.server.biz.ai.vo.RatingResultVO());
        mvc.perform(put("/api/v1/ai/messages/5501/rating")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").doesNotExist());
    }

    @Test
    void k10RecognizesSpeech() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("audio", "voice.wav", "audio/wav",
                "RIFF".getBytes(StandardCharsets.UTF_8));
        when(speechService.recognize(eq(10001L), any(), eq(3200))).thenReturn(AiFixtures.speech());
        mvc.perform(multipart("/api/v1/ai/speech-recognitions").file(audio).param("duration_ms", "3200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recognized").value(true))
                .andExpect(jsonPath("$.data.text").value("保育猪换料后拉稀，要停料吗？"))
                .andExpect(jsonPath("$.data.duration_ms").value(3200));
    }

    @Test
    void k06AsksWithSseEvents() throws Exception {
        when(askService.ask(eq(10001L), eq("ask-key-1"), any(AiAskRequest.class))).thenReturn(sseEmitter());
        MvcResult result = mvc.perform(post("/api/v1/ai/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "ask-key-1")
                        .content("{\"question_text\":\"氟苯尼考粉用于猪，多久可以出栏？\","
                                + "\"batch_id\":3001,\"image_file_ids\":[]}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult dispatched = mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        String body = dispatched.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("event:meta", "event:delta", "event:citation", "event:done",
                "\"message_id\":5501", "\"answer_type\":\"NORMAL\"");
    }

    @Test
    void k07RetriesWithSseError() throws Exception {
        when(askService.retry(10001L, 5590L)).thenReturn(sseErrorEmitter());
        MvcResult result = mvc.perform(post("/api/v1/ai/messages/5590/retry"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult dispatched = mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        String body = dispatched.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("event:meta", "event:error", "\"code\":50401", "\"status\":\"TIMEOUT\"");
    }

    @Test
    void k12ReturnsKnowledgeDetail() throws Exception {
        when(knowledgeService.detail(10001L, 8101L, 9902L)).thenReturn(AiFixtures.knowledge());
        mvc.perform(get("/api/v1/knowledge-items/8101").param("citation_id", "9902"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8101))
                .andExpect(jsonPath("$.data.source_type_text").value("平台知识库"))
                .andExpect(jsonPath("$.data.highlight_para_key").value("p3"))
                .andExpect(jsonPath("$.data.updated_after_answer").value(false));
    }

    @Test
    void k14ReturnsCategories() throws Exception {
        when(courseService.categories()).thenReturn(AiFixtures.categories());
        mvc.perform(get("/api/v1/course-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(5101))
                .andExpect(jsonPath("$.data[0].sort_no").value(1));
    }

    @Test
    void k15ReturnsContinueWatching() throws Exception {
        when(courseService.continueWatching(10001L)).thenReturn(AiFixtures.continueWatching());
        mvc.perform(get("/api/v1/courses/continue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.course_id").value(6102))
                .andExpect(jsonPath("$.data.remaining_sec").value(138))
                .andExpect(jsonPath("$.data.section_no").value(2));
    }

    @Test
    void k16ReturnsTopicsPage() throws Exception {
        when(courseService.topics(eq(10001L), any())).thenReturn(AiFixtures.topicPage());
        mvc.perform(get("/api/v1/course-topics").param("sort", "DEFAULT").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(7001))
                .andExpect(jsonPath("$.data.list[0].learned_count").value(2))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void k17ReturnsTopicDetail() throws Exception {
        when(courseService.topicDetail(10001L, 7001L)).thenReturn(AiFixtures.topicDetail());
        mvc.perform(get("/api/v1/course-topics/7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("CONTINUE"))
                .andExpect(jsonPath("$.data.resume.course_id").value(6102))
                .andExpect(jsonPath("$.data.resume.position_sec").value(84));
    }

    @Test
    void k18ReturnsCoursesPage() throws Exception {
        when(courseService.courses(eq(10001L), any())).thenReturn(AiFixtures.coursePage());
        mvc.perform(get("/api/v1/courses").param("category_id", "5102")
                        .param("learn_status", "LEARNING").param("sort", "LATEST")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(6102))
                .andExpect(jsonPath("$.data.list[0].learn_status").value("LEARNING"))
                .andExpect(jsonPath("$.data.list[0].progress_ratio").value(0.3784))
                .andExpect(jsonPath("$.data.list[0].topic_name").value("仔猪腹泻防控"));
    }

    @Test
    void k19ReturnsCoursePlay() throws Exception {
        when(courseService.coursePlay(10001L, 6101L)).thenReturn(AiFixtures.coursePlay());
        mvc.perform(get("/api/v1/courses/6101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(6101))
                .andExpect(jsonPath("$.data.next_course_id").value(6102))
                .andExpect(jsonPath("$.data.progress.completed").value(true))
                .andExpect(jsonPath("$.data.knowledge_id").value(8104));
    }

    @Test
    void k20ReportsProgress() throws Exception {
        when(courseService.reportProgress(eq(10001L), eq(6102L), any())).thenReturn(AiFixtures.progressResult());
        mvc.perform(post("/api/v1/courses/6102/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"play_token\":\"pt-1\",\"event\":\"TICK\",\"position_sec\":94,"
                                + "\"watched_sec\":10,\"quality\":\"P720\",\"network_type\":\"WIFI\","
                                + "\"entry_type\":\"COURSE\",\"client_time\":\"2026-10-19 08:59:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position_sec").value(94))
                .andExpect(jsonPath("$.data.max_position_sec").value(94))
                .andExpect(jsonPath("$.data.learn_status").value("LEARNING"))
                .andExpect(jsonPath("$.data.topic_learned_count").value(2));
    }

    @Test
    void k21ReturnsWatchHistory() throws Exception {
        when(courseService.history(10001L, 1, 20)).thenReturn(AiFixtures.historyPage());
        mvc.perform(get("/api/v1/course-progress").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].id").value(6102))
                .andExpect(jsonPath("$.data.list[0].last_watched_at").value("2026-10-18 20:15:00"));
    }

    private static SseEmitter sseEmitter() throws Exception {
        SseEmitter emitter = new SseEmitter();
        emitter.send(SseEmitter.event().name("meta")
                .data("{\"message_id\":5501,\"session_id\":1021,\"round_no\":1,\"session_created\":false,"
                        + "\"batch_link_state\":\"VALID\"}", MediaType.TEXT_PLAIN));
        emitter.send(SseEmitter.event().name("delta").data("{\"text\":\"按平台休药期标准\"}",
                MediaType.TEXT_PLAIN));
        emitter.send(SseEmitter.event().name("citation").data("{\"citations\":[]}", MediaType.TEXT_PLAIN));
        emitter.send(SseEmitter.event().name("done")
                .data("{\"answer_type\":\"NORMAL\",\"finish_reason\":\"STOP\"}", MediaType.TEXT_PLAIN));
        emitter.complete();
        return emitter;
    }

    private static SseEmitter sseErrorEmitter() throws Exception {
        SseEmitter emitter = new SseEmitter();
        emitter.send(SseEmitter.event().name("meta")
                .data("{\"message_id\":5590,\"session_id\":1021,\"round_no\":1,\"session_created\":false,"
                        + "\"batch_link_state\":\"NONE\"}", MediaType.TEXT_PLAIN));
        emitter.send(SseEmitter.event().name("error")
                .data("{\"code\":50401,\"message\":\"回答超时，点按重新生成\",\"status\":\"TIMEOUT\"}",
                        MediaType.TEXT_PLAIN));
        emitter.complete();
        return emitter;
    }
}
