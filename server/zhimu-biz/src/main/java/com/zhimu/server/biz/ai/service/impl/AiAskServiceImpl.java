package com.zhimu.server.biz.ai.service.impl;

import com.zhimu.server.biz.ai.convert.AiMessageConvert;
import com.zhimu.server.biz.ai.entity.AiCitation;
import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.entity.AiSession;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.mapper.AiMessageMapper;
import com.zhimu.server.biz.ai.mapper.AiSessionMapper;
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.ai.request.AiAskRequest;
import com.zhimu.server.biz.ai.service.AiAskService;
import com.zhimu.server.biz.ai.support.AiBannedDrugDetector;
import com.zhimu.server.biz.ai.support.AiBatchContextService;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.support.AiErrors;
import com.zhimu.server.biz.ai.support.AiPromptBuilder;
import com.zhimu.server.biz.ai.support.AiRetrievalService;
import com.zhimu.server.biz.ai.support.AiRound;
import com.zhimu.server.biz.ai.support.AiRunningGuard;
import com.zhimu.server.biz.ai.support.AiStreamTextProcessor;
import com.zhimu.server.biz.ai.support.AiText;
import com.zhimu.server.biz.ai.support.RetrievalHit;
import com.zhimu.server.biz.ai.support.SseWriter;
import com.zhimu.server.biz.ai.vo.AiMessageVO;
import com.zhimu.server.biz.ai.vo.CitationBriefVO;
import com.zhimu.server.biz.ai.vo.LinkedBatchVO;
import com.zhimu.server.biz.ai.vo.StreamCitationVO;
import com.zhimu.server.biz.ai.vo.StreamDeltaVO;
import com.zhimu.server.biz.ai.vo.StreamDoneVO;
import com.zhimu.server.biz.ai.vo.StreamMetaVO;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.async.AsyncConfig;
import com.zhimu.server.integration.ai.AiChatRequest;
import com.zhimu.server.integration.ai.AiChatResult;
import com.zhimu.server.integration.ai.AiGateway;
import com.zhimu.server.integration.ai.AiStreamHandle;
import com.zhimu.server.integration.ai.AiStreamHandler;
import com.zhimu.server.integration.storage.ObjectStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * API-K06、K07：提问与重新生成（SSE）。
 * <p>
 * 开流前抛出的错误由 AiStreamController 统一写成 error 事件（HTTP 200）；开流后的错误只用 error 事件。
 * SSE 事件：meta → delta… → citation → done，或 meta → … → error；每 15 秒发送注释行心跳。
 * 客户端断开（点“停止”）时取消上游生成，已生成部分保留，消息记为 INTERRUPTED“已停止生成”；
 * 对仍在生成的消息重新生成时先取消旧生成再重来。
 */
@Slf4j
@Service
public class AiAskServiceImpl implements AiAskService {

    private static final int MAX_QUESTION = 500;
    private static final int MAX_IMAGES = 9;
    private static final long SSE_TIMEOUT_MS = 125_000L;
    /** 停止生成后消息的状态说明（App 历史记录按 error_message 显示） */
    static final String STOPPED_MESSAGE = "已停止生成";

    /** 本实例进行中的生成（message_id → 生成），用于停止与重新生成时取消上游 */
    private final Map<Long, Generation> running = new ConcurrentHashMap<>();

    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;
    private final CourseMapper courseMapper;
    private final AiMessageConvert messageConvert;
    private final AiRoundService roundService;
    private final AiRetrievalService retrievalService;
    private final AiBannedDrugDetector bannedDrugDetector;
    private final AiPromptBuilder promptBuilder;
    private final AiBatchContextService batchContextService;
    private final AiRunningGuard runningGuard;
    private final AiGateway aiGateway;
    private final com.zhimu.server.biz.common.api.BizMediaService bizMediaService;
    private final FileMapper fileMapper;
    private final ObjectStorage objectStorage;
    private final Executor aiExecutor;

    public AiAskServiceImpl(AiSessionMapper sessionMapper, AiMessageMapper messageMapper, CourseMapper courseMapper,
                            AiMessageConvert messageConvert, AiRoundService roundService,
                            AiRetrievalService retrievalService, AiBannedDrugDetector bannedDrugDetector,
                            AiPromptBuilder promptBuilder, AiBatchContextService batchContextService,
                            AiRunningGuard runningGuard, AiGateway aiGateway,
                            com.zhimu.server.biz.common.api.BizMediaService bizMediaService, FileMapper fileMapper,
                            ObjectStorage objectStorage,
                            @Qualifier(AsyncConfig.AI_EXECUTOR) Executor aiExecutor) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.courseMapper = courseMapper;
        this.messageConvert = messageConvert;
        this.roundService = roundService;
        this.retrievalService = retrievalService;
        this.bannedDrugDetector = bannedDrugDetector;
        this.promptBuilder = promptBuilder;
        this.batchContextService = batchContextService;
        this.runningGuard = runningGuard;
        this.aiGateway = aiGateway;
        this.bizMediaService = bizMediaService;
        this.fileMapper = fileMapper;
        this.objectStorage = objectStorage;
        this.aiExecutor = aiExecutor;
    }

    @Override
    public SseEmitter ask(Long userId, String idempotencyKey, AiAskRequest request) {
        FarmContext.FarmMember member = FarmContext.require();
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 64) {
            throw AiErrors.fieldError("Idempotency-Key", "缺少请求标识，请重试");
        }
        String question = request.getQuestionText() == null ? null : request.getQuestionText().trim();
        if (question != null && question.length() > MAX_QUESTION) {
            throw AiErrors.fieldError("question_text", "问题不能超过 500 字");
        }
        List<Long> imageIds = distinct(request.getImageFileIds());
        if (imageIds.size() > MAX_IMAGES) {
            throw AiErrors.fieldError("image_file_ids", "最多附 9 张图片");
        }
        if ((question == null || question.isEmpty()) && imageIds.isEmpty()) {
            throw AiErrors.fieldError("question_text", "请输入问题或添加图片");
        }
        AiSession session = null;
        if (request.getSessionId() != null) {
            session = sessionMapper.selectById(request.getSessionId());
            if (session == null || !userId.equals(session.getUserId())
                    || AiCodes.LINK_DELETED.equals(session.getBatchLinkState())) {
                throw AiErrors.notFound();
            }
        }
        AiMessage existing = messageMapper.selectByClientMsgId(userId, key);
        if (existing != null) {
            if (AiCodes.STATUS_DELETED.equals(existing.getStatus())) {
                throw AiErrors.notFound();
            }
            if (AiCodes.STATUS_ANSWERING.equals(existing.getStatus())) {
                throw AiErrors.stateChanged("请求正在处理，请勿重复提交");
            }
            return replay(existing);
        }
        if (!imageIds.isEmpty()) {
            bizMediaService.validateForBind(imageIds, FileScene.AI_IMAGE, userId, "image_file_ids");
        }
        LinkedBatchVO batch = null;
        String batchState = AiCodes.LINK_NONE;
        String snapshot = null;
        if (request.getBatchId() != null) {
            batch = batchContextService.resolve(member.farmId(), request.getBatchId(), member.isStaff(), member.penIds());
            if (batch != null) {
                batchState = AiCodes.LINK_VALID;
                snapshot = batchContextService.toSnapshot(batch);
            } else {
                batch = batchContextService.resolve(member.farmId(), request.getBatchId(), false, member.penIds());
                batchState = batch == null ? AiCodes.LINK_NOT_IN_STOCK : AiCodes.LINK_NO_PERMISSION;
                snapshot = batch == null ? null : batchContextService.toSnapshot(batch);
                batch = null;
            }
        }
        Course course = null;
        if (request.getContextCourseId() != null) {
            course = courseMapper.selectById(request.getContextCourseId());
            if (course != null && !"ON_SHELF".equals(course.getStatus())) {
                course = null;
            }
        }
        if (!runningGuard.tryAcquire(userId)) {
            throw new BizException(ErrorCode.TOO_FREQUENT, "上一个问题还在回答中，请稍后再问");
        }
        AiRound round;
        try {
            round = roundService.create(userId, member.farmId(), request.getSessionId(),
                    AiText.sessionTitle(question), question, imageIds, request.getBatchId(), batchState, snapshot,
                    course == null ? null : course.getId(), course == null ? null : course.getTitle(),
                    course == null ? null : course.getKnowledgeId(), key, clientType());
        } catch (RuntimeException e) {
            runningGuard.release(userId);
            throw e;
        }
        LinkedBatchVO finalBatch = batch;
        String finalCourseTitle = course == null ? null : course.getTitle();
        return start(userId, round, member, finalBatch, finalCourseTitle);
    }

    /** 开流：登记进行中的生成，客户端断开（停止）时取消上游并保留已生成部分 */
    private SseEmitter start(Long userId, AiRound round, FarmContext.FarmMember member, LinkedBatchVO batch,
                             String courseTitle) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseWriter writer = new SseWriter(emitter);
        Generation generation = new Generation(round, writer);
        Generation previous = running.put(round.messageId(), generation);
        if (previous != null) {
            stop(previous);
        }
        writer.onDisconnect(() -> stop(generation));
        emitter.onCompletion(() -> {
            runningGuard.release(userId);
            stop(generation);
        });
        emitter.onTimeout(() -> {
            runningGuard.release(userId);
            timeout(generation);
        });
        emitter.onError(e -> {
            runningGuard.release(userId);
            writer.markDisconnected();
        });
        writer.startHeartbeat();
        try {
            aiExecutor.execute(() -> generate(generation, member, batch, courseTitle));
        } catch (RuntimeException e) {
            runningGuard.release(userId);
            failOnce(generation, AiCodes.STATUS_FAILED, ErrorCode.AI_UNAVAILABLE.getCode(),
                    "AI 助手暂时不可用，点按重新生成", null);
        }
        return emitter;
    }

    /**
     * 停止生成（客户端点“停止”断开、或对同一条消息重新生成）：取消上游请求，保留已生成部分，消息记为“已停止生成”。
     * 已正常结束或已失败的生成不受影响。
     */
    private void stop(Generation generation) {
        if (!generation.finished.compareAndSet(false, true)) {
            return;
        }
        generation.cancelUpstream();
        String text = generation.text();
        try {
            roundService.fail(generation.round, AiCodes.STATUS_INTERRUPTED, null, STOPPED_MESSAGE, text);
        } catch (RuntimeException e) {
            log.warn("保存已停止的回答失败 messageId={}", generation.round.messageId(), e);
        }
        generation.writer.error(ErrorCode.STATE_CHANGED.getCode(), STOPPED_MESSAGE, AiCodes.STATUS_INTERRUPTED);
        running.remove(generation.round.messageId(), generation);
        log.info("AI 回答已停止 messageId={} chars={}", generation.round.messageId(), text == null ? 0 : text.length());
    }

    /** SSE 连接到时仍未结束：按超时处理（有部分回答记为中断） */
    private void timeout(Generation generation) {
        generation.cancelUpstream();
        String text = generation.text();
        boolean partial = text != null;
        failOnce(generation, partial ? AiCodes.STATUS_INTERRUPTED : AiCodes.STATUS_TIMEOUT,
                ErrorCode.AI_TIMEOUT.getCode(), partial ? "回答中断，点按重新生成" : "回答超时，点按重新生成", text);
    }

    /** 失败收尾只执行一次：已停止、已完成的生成不再改写 */
    private void failOnce(Generation generation, String status, int code, String message, String partialText) {
        if (!generation.finished.compareAndSet(false, true)) {
            return;
        }
        generation.cancelUpstream();
        try {
            roundService.fail(generation.round, status, code, message, partialText);
        } finally {
            generation.writer.error(code, message, status);
            running.remove(generation.round.messageId(), generation);
        }
    }

    @Override
    public SseEmitter retry(Long userId, Long messageId) {
        FarmContext.FarmMember member = FarmContext.require();
        AiMessage message = messageMapper.selectById(messageId);
        if (message == null || !userId.equals(message.getUserId())
                || AiCodes.STATUS_DELETED.equals(message.getStatus())) {
            throw AiErrors.notFound();
        }
        AiSession session = sessionMapper.selectById(message.getSessionId());
        if (session == null || AiCodes.LINK_DELETED.equals(session.getBatchLinkState())) {
            throw AiErrors.notFound();
        }
        if (AiCodes.STATUS_ANSWERED.equals(message.getStatus())) {
            return replay(message);
        }
        if (AiCodes.STATUS_ANSWERING.equals(message.getStatus())) {
            // 停止后立即重新生成：旧的生成可能还没察觉客户端断开，先取消它（保留的部分随后被重置）再重来
            Generation previous = running.get(messageId);
            if (previous != null) {
                stop(previous);
            }
            runningGuard.release(userId);
        }
        if (!runningGuard.tryAcquire(userId)) {
            throw new BizException(ErrorCode.TOO_FREQUENT, "上一个问题还在回答中，请稍后再问");
        }
        AiRound round;
        try {
            round = roundService.reset(userId, messageId, message.getClientMsgId());
        } catch (RuntimeException e) {
            runningGuard.release(userId);
            throw e;
        }
        LinkedBatchVO batch = null;
        if (round.batchId() != null && AiCodes.LINK_VALID.equals(round.batchLinkState())) {
            batch = batchContextService.resolve(member.farmId(), round.batchId(), member.isStaff(), member.penIds());
        }
        return start(userId, round, member, batch, round.contextCourseTitle());
    }

    private void generate(Generation generation, FarmContext.FarmMember member, LinkedBatchVO batch,
                          String courseTitle) {
        AiRound round = generation.round;
        SseWriter writer = generation.writer;
        try {
            if (generation.finished.get()) {
                return;
            }
            StreamMetaVO meta = new StreamMetaVO();
            meta.setMessageId(round.messageId());
            meta.setSessionId(round.sessionId());
            meta.setRoundNo(round.roundNo());
            meta.setSessionCreated(round.sessionCreated());
            meta.setBatchLinkState(round.batchLinkState());
            writer.send(AiCodes.SSE_META, meta);

            String banned = bannedDrugDetector.match(round.questionText());
            if (banned != null) {
                if (generation.finished.compareAndSet(false, true)) {
                    answerBanned(writer, round, banned);
                    running.remove(round.messageId(), generation);
                }
                return;
            }
            writer.send(AiCodes.SSE_PROGRESS, java.util.Map.of("stage", "CONTEXT", "message",
                    batch == null ? "正在读取本轮问题和对话上下文" : "正在整理关联批次和对话上下文"));
            List<AiMessage> history = new ArrayList<>(messageMapper.selectContextBefore(round.sessionId(),
                    round.roundNo(), 10));
            java.util.Collections.reverse(history);
            List<String> imageUrls = imageUrls(round.imageFileIds());
            writer.send(AiCodes.SSE_PROGRESS, java.util.Map.of("stage", "RETRIEVAL", "message", "正在检索平台资料"));
            List<RetrievalHit> hits = retrievalService.search(round.questionText(), courseTitle);
            String summary = hits.isEmpty() ? "已读取问题上下文；本次未检索到匹配的平台资料。"
                    : "已读取问题上下文，检索到 " + hits.size() + " 段相关平台资料供本次回答参考。";
            writer.send(AiCodes.SSE_ANALYSIS_SUMMARY, new StreamDeltaVO(summary));
            AiChatRequest prompt = promptBuilder.build(round.questionText(), member, batch, courseTitle, hits,
                    history, imageUrls);
            if (!aiGateway.isEnabled()) {
                failOnce(generation, AiCodes.STATUS_FAILED, ErrorCode.AI_UNAVAILABLE.getCode(),
                        "AI 助手暂时不可用，点按重新生成", null);
                return;
            }
            if (generation.finished.get()) {
                // 客户端已停止：不再调用模型
                return;
            }
            writer.send(AiCodes.SSE_PROGRESS, java.util.Map.of("stage", "GENERATING", "message", "正在生成回答"));
            stream(generation, hits, prompt);
        } catch (BizException e) {
            boolean timeout = e.getCode() == ErrorCode.AI_TIMEOUT.getCode();
            String message = timeout ? "回答超时，点按重新生成" : "AI 助手暂时不可用，点按重新生成";
            failOnce(generation, timeout ? AiCodes.STATUS_TIMEOUT : AiCodes.STATUS_FAILED, e.getCode(), message,
                    null);
        } catch (RuntimeException e) {
            log.warn("AI 生成失败 messageId={}", round.messageId(), e);
            failOnce(generation, AiCodes.STATUS_FAILED, ErrorCode.SERVER_ERROR.getCode(),
                    "回答生成失败，点按重新生成", null);
        } finally {
            runningGuard.release(round.userId());
        }
    }

    private void stream(Generation generation, List<RetrievalHit> hits, AiChatRequest prompt) {
        AiRound round = generation.round;
        SseWriter writer = generation.writer;
        AiStreamTextProcessor processor = new AiStreamTextProcessor(hits);
        AtomicReference<AiChatResult> result = new AtomicReference<>();

        AiStreamHandler handler = new AiStreamHandler() {
            @Override
            public void onStart(String provider, String modelName, boolean fallback) {
                result.set(AiChatResult.builder().provider(provider).model(modelName).fallback(fallback).build());
            }

            @Override
            public void onDelta(String text) {
                if (generation.finished.get()) {
                    return;
                }
                String visible = processor.feed(text);
                if (visible.isEmpty()) {
                    return;
                }
                generation.append(visible);
                writer.send(AiCodes.SSE_DELTA, new StreamDeltaVO(visible));
            }

            @Override
            public void onResult(AiChatResult value) {
                result.set(value);
            }

            @Override
            public void onComplete(String finishReason, String fullText) {
                if (!generation.finished.compareAndSet(false, true)) {
                    return;
                }
                try {
                    String tail = processor.flush();
                    if (!tail.isEmpty()) {
                        generation.append(tail);
                        writer.send(AiCodes.SSE_DELTA, new StreamDeltaVO(tail));
                    }
                    String answer = generation.text();
                    completeAnswer(writer, round, processor, hits, answer == null ? "" : answer, finishReason,
                            result.get());
                } finally {
                    running.remove(round.messageId(), generation);
                }
            }

            @Override
            public void onError(BizException error) {
                String text = generation.text();
                boolean partial = text != null;
                boolean timeout = error.getCode() == ErrorCode.AI_TIMEOUT.getCode();
                String status = partial ? AiCodes.STATUS_INTERRUPTED
                        : (timeout ? AiCodes.STATUS_TIMEOUT : AiCodes.STATUS_FAILED);
                String message = partial ? "回答中断，点按重新生成"
                        : (timeout ? "回答超时，点按重新生成" : "AI 助手暂时不可用，点按重新生成");
                failOnce(generation, status, error.getCode(), message, text);
            }
        };
        try {
            AiStreamHandle current = aiGateway.stream(prompt, handler);
            generation.attach(current);
        } catch (BizException e) {
            boolean timeout = e.getCode() == ErrorCode.AI_TIMEOUT.getCode();
            String message = timeout ? "回答超时，点按重新生成" : "AI 助手暂时不可用，点按重新生成";
            failOnce(generation, timeout ? AiCodes.STATUS_TIMEOUT : AiCodes.STATUS_FAILED, e.getCode(), message,
                    null);
        }
    }

    private void completeAnswer(SseWriter writer, AiRound round, AiStreamTextProcessor processor,
                                List<RetrievalHit> hits, String rawAnswer, String finishReason,
                                AiChatResult result) {
        String answerType = processor.getAnswerType();
        String answerText = rawAnswer == null ? "" : com.zhimu.server.biz.ai.support.AiText.answerBody(rawAnswer).trim();
        List<AiCitation> citations = new ArrayList<>(processor.getCitations());
        if (AiCodes.ANSWER_OFF_TOPIC.equals(answerType)) {
            answerText = AiCodes.OFF_TOPIC_ANSWER;
            citations = new ArrayList<>();
        } else {
            String bannedInAnswer = bannedDrugDetector.match(answerText);
            if (bannedInAnswer != null) {
                answerType = AiCodes.ANSWER_BANNED_DRUG;
                answerText = bannedAnswer(bannedInAnswer, citations);
            } else if (hasEpidemic(citations, hits)) {
                answerType = AiCodes.ANSWER_EPIDEMIC;
            } else if (citations.isEmpty()) {
                answerType = AiCodes.ANSWER_NO_BASIS;
            }
            if (answerText.isBlank()) {
                roundService.fail(round, AiCodes.STATUS_FAILED, ErrorCode.AI_UNAVAILABLE.getCode(),
                        "未生成有效回答，请重新提问", null);
                writer.error(ErrorCode.AI_UNAVAILABLE.getCode(), "未生成有效回答，请重新提问", AiCodes.STATUS_FAILED);
                return;
            }
        }
        String model = result == null ? null : result.getModel();
        Integer promptTokens = result == null || result.getUsage() == null ? null
                : result.getUsage().promptTokens();
        Integer completionTokens = result == null || result.getUsage() == null ? null
                : result.getUsage().completionTokens();
        Integer latency = result == null ? null : (int) result.getCostMs();
        roundService.finish(round, answerType, answerText, citations, model, promptTokens, completionTokens, latency,
                processor.getTopic());
        AiMessage message = roundService.loadMessage(round.messageId());
        AiMessageVO vo = messageConvert.toVo(message);
        if (!citations.isEmpty()) {
            StreamCitationVO citationVO = new StreamCitationVO();
            citationVO.setCitations(vo == null ? List.of() : vo.getCitations());
            writer.send(AiCodes.SSE_CITATION, citationVO);
        }
        StreamDoneVO done = new StreamDoneVO();
        done.setAnswerType(answerType);
        done.setFinishReason(finishReason == null ? AiCodes.FINISH_STOP : finishReason);
        done.setMessage(vo);
        writer.send(AiCodes.SSE_DONE, done);
        writer.complete();
    }

    private void answerBanned(SseWriter writer, AiRound round, String drug) {
        writer.send(AiCodes.SSE_PROGRESS, java.util.Map.of("stage", "RETRIEVAL", "message", "正在核对平台禁用药资料"));
        RetrievalHit hit = retrievalService.bannedDrugHit(drug);
        List<AiCitation> citations = new ArrayList<>();
        String text = bannedAnswer(drug, citations, hit);
        String answerType = AiCodes.ANSWER_BANNED_DRUG;
        roundService.finish(round, answerType, text, citations, null, null, null, null, AiCodes.TOPIC_MEDICATION);
        writer.send(AiCodes.SSE_DELTA, new StreamDeltaVO(text));
        AiMessage message = roundService.loadMessage(round.messageId());
        AiMessageVO vo = messageConvert.toVo(message);
        if (!citations.isEmpty()) {
            StreamCitationVO citationVO = new StreamCitationVO();
            citationVO.setCitations(vo == null ? List.of() : vo.getCitations());
            writer.send(AiCodes.SSE_CITATION, citationVO);
        }
        StreamDoneVO done = new StreamDoneVO();
        done.setAnswerType(answerType);
        done.setFinishReason(AiCodes.FINISH_FIXED);
        done.setMessage(vo);
        writer.send(AiCodes.SSE_DONE, done);
        writer.complete();
    }

    private String bannedAnswer(String drug, List<AiCitation> citations) {
        return bannedAnswer(drug, citations, retrievalService.bannedDrugHit(drug));
    }

    private String bannedAnswer(String drug, List<AiCitation> citations, RetrievalHit hit) {
        String base = drug + "是食品动物禁用药物";
        if (hit != null) {
            citations.clear();
            citations.add(toCitation(hit, 1));
            return base + "[^1]，任何阶段都不能添加到饲料或饮水中。请遵守兽药使用规定，如已使用请立即停止并咨询执业兽医。";
        }
        return base + "，任何阶段都不能添加到饲料或饮水中。请遵守兽药使用规定，如已使用请立即停止并咨询执业兽医。";
    }

    private static AiCitation toCitation(RetrievalHit hit, int seq) {
        AiCitation citation = new AiCitation();
        citation.setSeqNo(seq);
        citation.setKnowledgeId(hit.knowledge().getId());
        citation.setKnowledgeVersionNo(hit.knowledge().getVersionNo() == null ? 1 : hit.knowledge().getVersionNo());
        citation.setParaKey(hit.paragraph().getParaKey() == null ? "" : hit.paragraph().getParaKey());
        citation.setSourceType(hit.knowledge().getSourceType() == null ? AiCodes.SOURCE_PLATFORM
                : hit.knowledge().getSourceType());
        citation.setIconType(hit.knowledge().getDisplayType() == null ? AiCodes.ICON_STANDARD
                : hit.knowledge().getDisplayType());
        citation.setTitleSnapshot(hit.knowledge().getTitle() == null ? "" : hit.knowledge().getTitle());
        citation.setCategoryNameSnapshot(hit.knowledge().getCategoryName() == null ? ""
                : hit.knowledge().getCategoryName());
        String text = hit.paragraph().getContentText();
        citation.setQuoteText(text != null && text.length() > 1000 ? text.substring(0, 1000)
                : (text == null ? "" : text));
        return citation;
    }

    private static boolean hasEpidemic(List<AiCitation> citations, List<RetrievalHit> hits) {
        for (AiCitation citation : citations) {
            for (RetrievalHit hit : hits) {
                if (hit.knowledge().getId().equals(citation.getKnowledgeId())
                        && hit.knowledge().getIsMajorEpidemic() != null
                        && hit.knowledge().getIsMajorEpidemic() == 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private SseEmitter replay(AiMessage message) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseWriter writer = new SseWriter(emitter);
        AiSession session = sessionMapper.selectById(message.getSessionId());
        StreamMetaVO meta = new StreamMetaVO();
        meta.setMessageId(message.getId());
        meta.setSessionId(message.getSessionId());
        meta.setRoundNo(message.getRoundNo());
        meta.setSessionCreated(false);
        meta.setBatchLinkState(session == null || session.getBatchLinkState() == null
                ? AiCodes.LINK_NONE : session.getBatchLinkState());
        writer.send(AiCodes.SSE_META, meta);
        if (AiCodes.STATUS_ANSWERED.equals(message.getStatus())) {
            AiMessageVO vo = messageConvert.toVo(message);
            if (vo != null && !vo.getCitations().isEmpty()) {
                StreamCitationVO citationVO = new StreamCitationVO();
                citationVO.setCitations(vo.getCitations());
                writer.send(AiCodes.SSE_CITATION, citationVO);
            }
            StreamDoneVO done = new StreamDoneVO();
            done.setAnswerType(message.getAnswerType());
            done.setFinishReason(AiCodes.FINISH_STOP);
            done.setMessage(vo);
            writer.send(AiCodes.SSE_DONE, done);
            writer.complete();
            return emitter;
        }
        int code = message.getErrorCode() == null ? ErrorCode.AI_UNAVAILABLE.getCode() : message.getErrorCode();
        String status = message.getStatus();
        String text = message.getErrorMessage() != null ? message.getErrorMessage()
                : (AiCodes.STATUS_TIMEOUT.equals(status) ? "回答超时，点按重新生成" : "AI 助手暂时不可用，点按重新生成");
        writer.error(code, text, status);
        return emitter;
    }

    private List<String> imageUrls(List<Long> fileIds) {
        List<String> urls = new ArrayList<>();
        if (fileIds == null || fileIds.isEmpty()) {
            return urls;
        }
        for (Long fileId : fileIds) {
            FileEntity file = fileMapper.selectById(fileId);
            if (file == null || file.getObjectKey() == null) {
                continue;
            }
            try {
                urls.add(objectStorage.presignedGetUrl(file.getBucket(), file.getObjectKey()));
            } catch (RuntimeException ignored) {
                // 单个附图取地址失败不影响提问
            }
        }
        return urls;
    }

    private static List<Long> distinct(List<Long> ids) {
        if (ids == null) {
            return new ArrayList<>();
        }
        Set<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                set.add(id);
            }
        }
        return new ArrayList<>(set);
    }

    /** 一次进行中的生成：上游句柄、已推送给客户端的正文、只收尾一次的标记 */
    private static final class Generation {
        final AiRound round;
        final SseWriter writer;
        final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicReference<AiStreamHandle> handle = new AtomicReference<>();
        private final StringBuilder streamed = new StringBuilder();

        Generation(AiRound round, SseWriter writer) {
            this.round = round;
            this.writer = writer;
        }

        void attach(AiStreamHandle current) {
            handle.set(current);
            if (finished.get() && current != null && !current.isDone()) {
                // 开始调用模型前客户端已停止
                current.cancel();
            }
        }

        void cancelUpstream() {
            AiStreamHandle current = handle.get();
            if (current != null && !current.isDone()) {
                current.cancel();
            }
        }

        synchronized void append(String text) {
            streamed.append(text);
        }

        /** 已推送的正文；没有时为 null */
        synchronized String text() {
            return streamed.isEmpty() ? null : streamed.toString();
        }
    }

    private static String clientType() {
        ClientType type = RequestContext.clientType();
        if (type == null) {
            type = UserContext.get() != null ? UserContext.get().clientType() : null;
        }
        return type == null || type == ClientType.ADMIN_WEB ? ClientType.APP_ANDROID.name() : type.name();
    }
}
