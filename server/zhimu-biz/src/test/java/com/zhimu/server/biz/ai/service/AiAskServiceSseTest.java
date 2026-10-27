package com.zhimu.server.biz.ai.service;

import com.zhimu.server.biz.ai.controller.AiMessageController;
import com.zhimu.server.biz.ai.convert.AiMessageConvert;
import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.entity.AiKnowledge;
import com.zhimu.server.biz.ai.entity.AiKnowledgeParagraph;
import com.zhimu.server.biz.ai.mapper.AiMessageMapper;
import com.zhimu.server.biz.ai.mapper.AiSessionMapper;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.ai.service.impl.AiAskServiceImpl;
import com.zhimu.server.biz.ai.service.impl.AiRoundService;
import com.zhimu.server.biz.ai.support.AiBannedDrugDetector;
import com.zhimu.server.biz.ai.support.AiBatchContextService;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.support.AiFixtures;
import com.zhimu.server.biz.ai.support.AiPromptBuilder;
import com.zhimu.server.biz.ai.support.AiRetrievalService;
import com.zhimu.server.biz.ai.support.AiRound;
import com.zhimu.server.biz.ai.support.AiRunningGuard;
import com.zhimu.server.biz.ai.support.RetrievalHit;
import com.zhimu.server.biz.ai.vo.AiMessageVO;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.integration.ai.AiChatRequest;
import com.zhimu.server.integration.ai.AiChatResult;
import com.zhimu.server.integration.ai.AiGateway;
import com.zhimu.server.integration.ai.AiStreamHandle;
import com.zhimu.server.integration.ai.AiUsage;
import com.zhimu.server.integration.storage.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSE 真实链路：AiAskServiceImpl + SseWriter + 顺序网关回调，经 standalone MockMvc 断言事件与 JSON。
 */
class AiAskServiceSseTest {

    private final AiSessionMapper sessionMapper = mock(AiSessionMapper.class);
    private final AiMessageMapper messageMapper = mock(AiMessageMapper.class);
    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final AiMessageConvert messageConvert = mock(AiMessageConvert.class);
    private final AiRoundService roundService = mock(AiRoundService.class);
    private final AiRetrievalService retrievalService = mock(AiRetrievalService.class);
    private final AiBannedDrugDetector bannedDrugDetector = mock(AiBannedDrugDetector.class);
    private final AiPromptBuilder promptBuilder = mock(AiPromptBuilder.class);
    private final AiBatchContextService batchContextService = mock(AiBatchContextService.class);
    private final AiGateway aiGateway = mock(AiGateway.class);
    private final BizMediaService bizMediaService = mock(BizMediaService.class);
    private final FileMapper fileMapper = mock(FileMapper.class);
    private final ObjectStorage objectStorage = mock(ObjectStorage.class);
    private AiAskServiceImpl service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = new AiAskServiceImpl(sessionMapper, messageMapper, courseMapper, messageConvert, roundService,
                retrievalService, bannedDrugDetector, promptBuilder, batchContextService, AiRunningGuard.NOOP,
                aiGateway, bizMediaService, fileMapper, objectStorage, Runnable::run);
        mvc = TestMvc.builder(new AiMessageController(service, mock(AiMessageService.class),
                mock(AiSpeechService.class))).build();
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 10001L, 5001L, FarmRole.OWNER,
                Set.of(), FarmStatus.NORMAL));
        when(messageMapper.selectByClientMsgId(eq(10001L), anyString())).thenReturn(null);
        when(roundService.create(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(round());
        when(roundService.loadMessage(5501L)).thenReturn(AiFixtures.message(5501L, 1021L, 1));
        AiMessageVO vo = AiFixtures.messageVo();
        when(messageConvert.toVo(any(AiMessage.class))).thenReturn(vo);
        when(promptBuilder.build(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AiChatRequest.builder().build());
        when(aiGateway.isEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        FarmContext.clear();
    }

    @Test
    void streamsMetaDeltaCitationDoneAndPersistsNormalAnswer() throws Exception {
        RetrievalHit hit = hit();
        when(bannedDrugDetector.match(anyString())).thenReturn(null);
        when(retrievalService.search(anyString(), any())).thenReturn(List.of(hit));
        when(messageMapper.selectContextBefore(1021L, 1, 10)).thenReturn(List.of());
        AtomicReference<AiChatResult> usage = new AtomicReference<>(AiChatResult.builder()
                .provider("dashscope").model("qwen3.8-max").finishReason("STOP")
                .usage(new AiUsage(120, 80, 200)).costMs(1200).build());
        when(aiGateway.stream(any(AiChatRequest.class), any())).thenAnswer(invocation -> {
            com.zhimu.server.integration.ai.AiStreamHandler handler = invocation.getArgument(1);
            handler.onStart("dashscope", "qwen3.8-max", false);
            handler.onDelta("[[NORMAL]][[TOPIC:MEDICATION]]\n按平台");
            handler.onDelta("休药期标准，休药 20 天[^P1]。");
            handler.onResult(usage.get());
            handler.onComplete("STOP", "按平台休药期标准，休药 20 天[^P1]。");
            return handle();
        });

        MvcResult result = mvc.perform(post("/api/v1/ai/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "ask-key-1")
                        .content("{\"question_text\":\"氟苯尼考粉用于猪，多久可以出栏？\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult dispatched = mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        String body = dispatched.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("event:meta", "event:delta", "event:citation", "event:done",
                "data:{\"message_id\":5501", "\"round_no\":1", "\"session_created\":true",
                "answer_type\":\"NORMAL", "[^1]");
        assertThat(body).contains("event:progress", "\"stage\":\"CONTEXT\"", "\"stage\":\"RETRIEVAL\"",
                "\"stage\":\"GENERATING\"", "event:analysis_summary", "检索到 1 段相关平台资料");
        assertThat(body.indexOf("CONTEXT")).isLessThan(body.indexOf("RETRIEVAL"));
        assertThat(body.indexOf("RETRIEVAL")).isLessThan(body.indexOf("GENERATING"));
        assertThat(body.indexOf("GENERATING")).isLessThan(body.indexOf("event:delta"));
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> answer = ArgumentCaptor.forClass(String.class);
        verify(roundService).finish(any(AiRound.class), type.capture(), answer.capture(), any(), eq("qwen3.8-max"),
                eq(120), eq(80), eq(1200), eq(AiCodes.TOPIC_MEDICATION));
        assertThat(type.getValue()).isEqualTo(AiCodes.ANSWER_NORMAL);
        assertThat(answer.getValue()).isEqualTo("按平台休药期标准，休药 20 天[^1]。");
    }

    @Test
    void bannedDrugQuestionUsesFixedAnswerWithoutModel() throws Exception {
        when(bannedDrugDetector.match(anyString())).thenReturn("克伦特罗");
        when(retrievalService.bannedDrugHit("克伦特罗")).thenReturn(hit());

        MvcResult result = mvc.perform(post("/api/v1/ai/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "ask-key-2")
                        .content("{\"question_text\":\"克伦特罗能用吗\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult dispatched = mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        String body = dispatched.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("event:done", "\"answer_type\":\"BANNED_DRUG\"",
                "克伦特罗是食品动物禁用药物", "任何阶段都不能添加");
        verify(aiGateway, never()).stream(any(), any());
        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        verify(roundService).finish(any(AiRound.class), type.capture(), any(), any(), isNull(), isNull(),
                isNull(), isNull(), eq(AiCodes.TOPIC_MEDICATION));
        assertThat(type.getValue()).isEqualTo(AiCodes.ANSWER_BANNED_DRUG);
    }

    @Test
    void answeredIdempotencyKeyReplaysStoredRound() throws Exception {
        AiMessage stored = AiFixtures.message(5501L, 1021L, 1);
        when(messageMapper.selectByClientMsgId(eq(10001L), eq("ask-key-answered"))).thenReturn(stored);
        when(sessionMapper.selectById(1021L)).thenReturn(AiFixtures.session(1021L, 10001L));

        MvcResult result = mvc.perform(post("/api/v1/ai/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "ask-key-answered")
                        .content("{\"question_text\":\"氟苯尼考粉用于猪，多久可以出栏？\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult dispatched = mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn();
        String body = dispatched.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("event:meta", "event:done", "\"session_created\":false",
                "\"answer_type\":\"NORMAL\"");
        verify(roundService, never()).create(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void blankIdempotencyKeyIsRejectedBeforeStream() throws Exception {
        mvc.perform(post("/api/v1/ai/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question_text\":\"你好\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void noMatchingKnowledgeDoesNotPrefixTheAnswerOrInventCitations() throws Exception {
        when(retrievalService.search(anyString(), any())).thenReturn(List.of());
        when(messageMapper.selectContextBefore(1021L, 1, 10)).thenReturn(List.of());
        when(aiGateway.stream(any(AiChatRequest.class), any())).thenAnswer(invocation -> {
            com.zhimu.server.integration.ai.AiStreamHandler handler = invocation.getArgument(1);
            handler.onDelta("[[NO_BASIS]]\n" + AiCodes.NO_BASIS_LEAD + "先检查饮水[^P99]。");
            handler.onComplete("STOP", "");
            return handle();
        });
        MvcResult result = mvc.perform(post("/api/v1/ai/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "ask-no-basis")
                        .content("{\"question_text\":\"饮水管理建议\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        String body = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("本次未检索到匹配的平台资料", "event:done").doesNotContain("event:citation");
        verify(roundService).finish(any(AiRound.class), eq(AiCodes.ANSWER_NO_BASIS), eq("先检查饮水。"),
                eq(List.of()), isNull(), isNull(), isNull(), isNull(), eq(AiCodes.TOPIC_OTHER));
    }

    private static AiRound round() {
        return new AiRound(1001L, 10001L, 1021L, 5501L, 1, true, AiCodes.LINK_NONE,
                "氟苯尼考粉用于猪，多久可以出栏？", List.of(), null, null, null, null, null);
    }

    private static RetrievalHit hit() {
        AiKnowledge knowledge = new AiKnowledge();
        knowledge.setId(8102L);
        knowledge.setTitle("兽药休药期标准 · 氟苯尼考粉");
        knowledge.setCategoryName("用药规范");
        knowledge.setSourceType(AiCodes.SOURCE_DRUG_CATALOG);
        knowledge.setDisplayType(AiCodes.ICON_STANDARD);
        knowledge.setVersionNo(4);
        knowledge.setIsMajorEpidemic(0);
        AiKnowledgeParagraph paragraph = new AiKnowledgeParagraph();
        paragraph.setKnowledgeId(8102L);
        paragraph.setParaKey("p2");
        paragraph.setContentText("氟苯尼考粉用于猪的休药期为 20 天。");
        return new RetrievalHit(knowledge, paragraph);
    }

    private static AiStreamHandle handle() {
        return new AiStreamHandle() {
            @Override
            public void cancel() {
            }

            @Override
            public boolean isDone() {
                return true;
            }
        };
    }
}
