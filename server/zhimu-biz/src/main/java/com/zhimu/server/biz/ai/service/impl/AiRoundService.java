package com.zhimu.server.biz.ai.service.impl;

import com.zhimu.server.biz.ai.entity.AiCitation;
import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.entity.AiSession;
import com.zhimu.server.biz.ai.mapper.AiCitationMapper;
import com.zhimu.server.biz.ai.mapper.AiMessageMapper;
import com.zhimu.server.biz.ai.mapper.AiSessionMapper;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.support.AiErrors;
import com.zhimu.server.biz.ai.support.AiRound;
import com.zhimu.server.biz.ai.support.AiText;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 轮次的短事务：建轮次、收尾、失败、重置。流式生成与模型调用都在事务外。
 */
@Service
@RequiredArgsConstructor
public class AiRoundService {

    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;
    private final AiCitationMapper citationMapper;
    private final com.zhimu.server.biz.common.api.BizMediaService bizMediaService;

    /**
     * 事务一：新会话写入；已有会话加行锁（更新 round_count 即行锁）后分配轮次，写入 ANSWERING 轮次并绑定附图。
     */
    @Transactional
    public AiRound create(Long userId, Long farmId, Long sessionId, String title, String questionText,
                          List<Long> imageFileIds, Long batchId, String batchLinkState, String batchSnapshotJson,
                          Long contextCourseId, String contextCourseTitle, Long contextKnowledgeId,
                          String clientMsgId, String clientType) {
        LocalDateTime now = BeijingTime.now();
        AiSession session;
        boolean created = false;
        if (sessionId == null) {
            session = new AiSession();
            session.setFarmId(farmId);
            session.setUserId(userId);
            session.setTitle(title);
            session.setTopic(AiCodes.TOPIC_OTHER);
            session.setBatchId(AiCodes.LINK_VALID.equals(batchLinkState) ? batchId : null);
            session.setBatchSnapshotJson(batchLinkState == null || AiCodes.LINK_NONE.equals(batchLinkState)
                    ? null : batchSnapshotJson);
            session.setBatchLinkState(batchLinkState == null ? AiCodes.LINK_NONE : batchLinkState);
            session.setRoundCount(0);
            session.setContextTruncated(0);
            session.setLastAskedAt(now);
            session.setHasCorrection(0);
            session.setHasEpidemicAlert(0);
            sessionMapper.insert(session);
            created = true;
        } else {
            session = sessionMapper.selectById(sessionId);
            if (session == null || !userId.equals(session.getUserId())) {
                throw AiErrors.notFound();
            }
            if (batchId != null) {
                session.setBatchLinkState(batchLinkState);
                if (AiCodes.LINK_VALID.equals(batchLinkState)) {
                    session.setBatchId(batchId);
                    session.setBatchSnapshotJson(batchSnapshotJson);
                }
            }
        }
        int roundNo = messageMapper.maxRoundNo(session.getId()) + 1;
        session.setRoundCount(roundNo);
        session.setLastAskedAt(now);
        session.setContextTruncated(roundNo > 10 ? 1 : 0);
        sessionMapper.updateById(session);

        AiMessage message = new AiMessage();
        message.setSessionId(session.getId());
        message.setFarmId(farmId);
        message.setUserId(userId);
        message.setRoundNo(roundNo);
        message.setClientMsgId(clientMsgId);
        message.setClientType(clientType);
        message.setQuestionText(questionText);
        message.setImageFileIds(com.zhimu.server.biz.ai.convert.AiMessageConvert.writeFileIds(imageFileIds));
        message.setBatchId(AiCodes.LINK_VALID.equals(batchLinkState) ? batchId : null);
        message.setBatchNo(batchNoOf(batchLinkState, batchSnapshotJson));
        message.setContextCourseId(contextCourseId);
        message.setContextCourseTitle(contextCourseTitle);
        message.setContextKnowledgeId(contextKnowledgeId);
        message.setStatus(AiCodes.STATUS_ANSWERING);
        message.setRetryCount(0);
        message.setAskedAt(now);
        message.setStartedAt(now);
        messageMapper.insert(message);
        if (imageFileIds != null && !imageFileIds.isEmpty()) {
            bizMediaService.replace(BizType.AI_MESSAGE, message.getId(), farmId, imageFileIds);
        }
        return new AiRound(farmId, userId, session.getId(), message.getId(), roundNo, created,
                batchLinkState == null ? AiCodes.LINK_NONE : batchLinkState, questionText, imageFileIds,
                message.getBatchId(), message.getBatchNo(), contextCourseId, contextCourseTitle, contextKnowledgeId);
    }

    private static String batchNoOf(String state, String snapshotJson) {
        if (snapshotJson == null || AiCodes.LINK_NONE.equals(state)) {
            return null;
        }
        int index = snapshotJson.indexOf("\"batch_no\":\"");
        if (index < 0) {
            return null;
        }
        int start = index + "\"batch_no\":\"".length();
        int end = snapshotJson.indexOf('"', start);
        return end < 0 ? null : snapshotJson.substring(start, end);
    }

    /** 事务二：正常收尾，写入回答、引用与会话标记 */
    @Transactional
    public void finish(AiRound round, String answerType, String answerText, List<AiCitation> citations,
                       String modelName, Integer promptTokens, Integer completionTokens, Integer latencyMs,
                       String topic) {
        LocalDateTime now = BeijingTime.now();
        AiMessage message = messageMapper.selectById(round.messageId());
        if (message == null) {
            return;
        }
        message.setStatus(AiCodes.STATUS_ANSWERED);
        message.setAnswerType(answerType);
        message.setAnswerText(answerText);
        message.setErrorCode(null);
        message.setErrorMessage(null);
        message.setAnsweredAt(now);
        message.setModelName(modelName);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setLatencyMs(latencyMs);
        if (AiCodes.ANSWER_NO_BASIS.equals(answerType)) {
            message.setHandleStatus("PENDING");
        }
        messageMapper.updateById(message);

        citationMapper.deleteByMessageId(message.getId());
        if (citations != null) {
            for (AiCitation citation : citations) {
                citation.setId(null);
                citation.setMessageId(message.getId());
                citation.setSessionId(round.sessionId());
                citationMapper.insert(citation);
            }
        }

        AiSession session = sessionMapper.selectById(round.sessionId());
        if (session != null) {
            if (round.roundNo() == 1) {
                session.setTopic(topic == null || topic.isBlank() ? AiText.topicOf(round.questionText()) : topic);
            }
            if (AiCodes.ANSWER_EPIDEMIC.equals(answerType)) {
                session.setHasEpidemicAlert(1);
            }
            session.setLastAskedAt(now);
            sessionMapper.updateById(session);
        }
    }

    /** 失败/中断/超时：保留已生成部分 */
    @Transactional
    public void fail(AiRound round, String status, Integer errorCode, String errorMessage, String partialText) {
        AiMessage message = messageMapper.selectById(round.messageId());
        if (message == null) {
            return;
        }
        message.setStatus(status);
        message.setAnswerText(partialText);
        message.setErrorCode(errorCode);
        message.setErrorMessage(errorMessage);
        if (AiCodes.STATUS_ANSWERED.equals(status)) {
            message.setAnsweredAt(BeijingTime.now());
        }
        messageMapper.updateById(message);
    }

    /** 重新生成：条件更新状态并清空回答与引用 */
    @Transactional
    public AiRound reset(Long userId, Long messageId, String clientMsgId) {
        AiMessage message = messageMapper.selectById(messageId);
        if (message == null || !userId.equals(message.getUserId())) {
            throw AiErrors.notFound();
        }
        if (AiCodes.STATUS_ANSWERING.equals(message.getStatus())) {
            throw AiErrors.stateChanged("回答还在生成中，请稍后");
        }
        message.setStatus(AiCodes.STATUS_ANSWERING);
        message.setAnswerType(null);
        message.setAnswerText(null);
        message.setErrorCode(null);
        message.setErrorMessage(null);
        message.setAnsweredAt(null);
        message.setStartedAt(BeijingTime.now());
        message.setRetryCount((message.getRetryCount() == null ? 0 : message.getRetryCount()) + 1);
        messageMapper.updateById(message);
        citationMapper.deleteByMessageId(messageId);
        AiSession session = sessionMapper.selectById(message.getSessionId());
        if (session == null) {
            throw AiErrors.notFound();
        }
        List<Long> imageFileIds = com.zhimu.server.biz.ai.convert.AiMessageConvert.parseFileIds(
                message.getImageFileIds());
        return new AiRound(message.getFarmId(), userId, message.getSessionId(), message.getId(),
                message.getRoundNo(), false,
                session.getBatchLinkState() == null ? AiCodes.LINK_NONE : session.getBatchLinkState(),
                message.getQuestionText(), imageFileIds, message.getBatchId(), message.getBatchNo(),
                message.getContextCourseId(), message.getContextCourseTitle(), message.getContextKnowledgeId());
    }

    public AiMessage loadMessage(Long messageId) {
        return messageMapper.selectById(messageId);
    }
}
