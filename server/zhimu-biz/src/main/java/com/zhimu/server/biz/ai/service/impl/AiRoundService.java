package com.zhimu.server.biz.ai.service.impl;

import com.zhimu.server.biz.account.support.AccountPersonalDataGuard;
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

    private final AccountPersonalDataGuard personalDataGuard;
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
        personalDataGuard.requireActive(userId);
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
            if (session == null || !userId.equals(session.getUserId())
                    || AiCodes.LINK_DELETED.equals(session.getBatchLinkState())) {
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
        // 新建的空会话（标题“新对话”）在首个提问时用问题摘要替换标题；用户自定义标题保持不变
        if (!created && roundNo == 1 && AiText.isPlaceholderTitle(session.getTitle())
                && title != null && !title.isBlank()) {
            session.setTitle(title);
        }
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
        if (!personalDataGuard.lockActive(round.userId())) return;
        LocalDateTime now = BeijingTime.now();
        AiMessage message = messageMapper.selectById(round.messageId());
        if (message == null || AiCodes.STATUS_DELETED.equals(message.getStatus())) {
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
        clearNullable(message, false);

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
        if (!personalDataGuard.lockActive(round.userId())) return;
        AiMessage message = messageMapper.selectById(round.messageId());
        if (message == null || AiCodes.STATUS_DELETED.equals(message.getStatus())) {
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
        clearNullable(message, false);
    }

    /**
     * 重新生成：清空回答与引用后重新进入 ANSWERING。
     * 仍在生成中的消息由调用方先取消旧生成（停止后立即重新生成），这里不再拒绝。
     */
    @Transactional
    public AiRound reset(Long userId, Long messageId, String clientMsgId) {
        personalDataGuard.requireActive(userId);
        AiMessage message = messageMapper.selectById(messageId);
        if (message == null || !userId.equals(message.getUserId())
                || AiCodes.STATUS_DELETED.equals(message.getStatus())) {
            throw AiErrors.notFound();
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
        clearNullable(message, true);
        citationMapper.deleteByMessageId(messageId);
        AiSession session = sessionMapper.selectById(message.getSessionId());
        if (session == null || AiCodes.LINK_DELETED.equals(session.getBatchLinkState())) {
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

    /**
     * updateById 会跳过 null 字段：显式把本次应为空的列置空（错误信息、上次回答），
     * 否则重新生成成功后仍留着“已停止生成”，或停止时残留上一次的回答。
     */
    private void clearNullable(AiMessage message, boolean resetAnswer) {
        var update = com.baomidou.mybatisplus.core.toolkit.Wrappers.<AiMessage>lambdaUpdate()
                .eq(AiMessage::getId, message.getId());
        boolean any = false;
        if (message.getErrorCode() == null) {
            update.set(AiMessage::getErrorCode, null);
            any = true;
        }
        if (message.getErrorMessage() == null) {
            update.set(AiMessage::getErrorMessage, null);
            any = true;
        }
        if (message.getAnswerText() == null) {
            update.set(AiMessage::getAnswerText, null);
            any = true;
        }
        if (resetAnswer) {
            update.set(AiMessage::getAnswerType, null).set(AiMessage::getAnsweredAt, null);
            any = true;
        }
        if (any) {
            messageMapper.update(null, update);
        }
    }

    public AiMessage loadMessage(Long messageId) {
        return messageMapper.selectById(messageId);
    }
}
