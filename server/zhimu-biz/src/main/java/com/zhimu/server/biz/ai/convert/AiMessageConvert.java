package com.zhimu.server.biz.ai.convert;

import com.zhimu.server.biz.ai.entity.AiCitation;
import com.zhimu.server.biz.ai.entity.AiCorrection;
import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.mapper.AiCitationMapper;
import com.zhimu.server.biz.ai.mapper.AiCorrectionMapper;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.support.AiText;
import com.zhimu.server.biz.ai.vo.AiMessageSliceVO;
import com.zhimu.server.biz.ai.vo.AiMessageVO;
import com.zhimu.server.biz.ai.vo.CitationBriefVO;
import com.zhimu.server.biz.ai.vo.CorrectionVO;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.service.impl.FileVoAssembler;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轮次与引用的 VO 组装。批量查询引用、更正与附图，避免逐条查询。
 */
@Component
@RequiredArgsConstructor
public class AiMessageConvert {

    private static final JsonMapper JSON = ZhimuJackson.newPlainMapper();

    private final AiCitationMapper citationMapper;
    private final AiCorrectionMapper correctionMapper;
    private final FileMapper fileMapper;
    private final FileVoAssembler fileVoAssembler;

    public AiMessageSliceVO slice(List<AiMessage> messages, boolean hasMore) {
        AiMessageSliceVO slice = new AiMessageSliceVO();
        slice.setList(toVoList(messages));
        slice.setHasMore(hasMore);
        return slice;
    }

    public AiMessageVO toVo(AiMessage message) {
        List<AiMessageVO> list = toVoList(List.of(message));
        return list.isEmpty() ? null : list.get(0);
    }

    public List<AiMessageVO> toVoList(List<AiMessage> messages) {
        List<AiMessageVO> result = new ArrayList<>();
        if (messages == null || messages.isEmpty()) {
            return result;
        }
        List<Long> ids = messages.stream().map(AiMessage::getId).toList();
        Map<Long, List<CitationBriefVO>> citations = loadCitations(ids);
        Map<Long, CorrectionVO> corrections = loadCorrections(ids);
        Map<Long, List<com.zhimu.server.common.vo.FileVO>> images = loadImages(messages);
        Set<Long> inContext = inContextIds(messages);
        for (AiMessage message : messages) {
            AiMessageVO vo = new AiMessageVO();
            vo.setId(message.getId());
            vo.setSessionId(message.getSessionId());
            vo.setRoundNo(message.getRoundNo() == null ? 0 : message.getRoundNo());
            vo.setAskedAt(BeijingTime.format(message.getAskedAt()));
            vo.setQuestionText(message.getQuestionText());
            vo.setImages(images.getOrDefault(message.getId(), new ArrayList<>()));
            vo.setBatchId(message.getBatchId());
            vo.setBatchNo(message.getBatchNo());
            vo.setContextCourseTitle(message.getContextCourseTitle());
            vo.setStatus(message.getStatus());
            vo.setAnswerType(message.getAnswerType());
            vo.setAnswerText(AiText.answerBody(message.getAnswerText()));
            vo.setCitations(citations.getOrDefault(message.getId(), new ArrayList<>()));
            vo.setRating(message.getRating());
            vo.setRatedAt(BeijingTime.format(message.getRatedAt()));
            boolean answered = AiCodes.STATUS_ANSWERED.equals(message.getStatus());
            boolean offTopic = AiCodes.ANSWER_OFF_TOPIC.equals(message.getAnswerType());
            if (answered && !offTopic) {
                int count = vo.getCitations().size();
                vo.setAnalysisSummary(count > 0
                        ? "回答已整理完成，附 " + count + " 条可查阅的平台资料引用。"
                        : "回答已整理完成；本轮没有可展示的平台资料引用。");
                if (count == 0) {
                    vo.setSourceNote(AiCodes.NO_BASIS_SOURCE_NOTE);
                }
            }
            vo.setCanRate(answered && !offTopic);
            vo.setCanCopy(answered && !offTopic);
            vo.setInContext(inContext.contains(message.getId()));
            vo.setCorrection(corrections.get(message.getId()));
            vo.setErrorCode(message.getErrorCode());
            vo.setErrorMessage(message.getErrorMessage());
            vo.setAnsweredAt(BeijingTime.format(message.getAnsweredAt()));
            result.add(vo);
        }
        return result;
    }

    /** 当前轮之前最近 10 轮已回答的问答（列表需按轮次升序） */
    private Set<Long> inContextIds(List<AiMessage> messages) {
        List<AiMessage> answered = messages.stream()
                .filter(m -> AiCodes.STATUS_ANSWERED.equals(m.getStatus()))
                .sorted(Comparator.comparing(m -> m.getRoundNo() == null ? 0 : m.getRoundNo()))
                .toList();
        Set<Long> result = new LinkedHashSet<>();
        for (int i = Math.max(0, answered.size() - 10); i < answered.size(); i++) {
            result.add(answered.get(i).getId());
        }
        return result;
    }

    private Map<Long, List<CitationBriefVO>> loadCitations(List<Long> messageIds) {
        Map<Long, List<CitationBriefVO>> result = new LinkedHashMap<>();
        if (messageIds.isEmpty()) {
            return result;
        }
        for (AiCitation citation : citationMapper.selectByMessageIds(messageIds)) {
            CitationBriefVO vo = new CitationBriefVO();
            vo.setId(citation.getId());
            vo.setSeqNo(citation.getSeqNo() == null ? 0 : citation.getSeqNo());
            vo.setKnowledgeId(citation.getKnowledgeId());
            vo.setTitle(citation.getTitleSnapshot());
            vo.setCategoryName(citation.getCategoryNameSnapshot());
            vo.setSourceType(citation.getSourceType());
            vo.setIconType(citation.getIconType());
            result.computeIfAbsent(citation.getMessageId(), key -> new ArrayList<>()).add(vo);
        }
        return result;
    }

    private Map<Long, CorrectionVO> loadCorrections(List<Long> messageIds) {
        Map<Long, CorrectionVO> result = new LinkedHashMap<>();
        if (messageIds.isEmpty()) {
            return result;
        }
        for (AiCorrection correction : correctionMapper.selectPublishedByMessageIds(messageIds)) {
            if (result.containsKey(correction.getMessageId())) {
                continue;
            }
            CorrectionVO vo = new CorrectionVO();
            vo.setContent(correction.getContent());
            vo.setDisplayTime(AiText.displayTime(correction.getPublishedAt()));
            result.put(correction.getMessageId(), vo);
        }
        return result;
    }

    private Map<Long, List<com.zhimu.server.common.vo.FileVO>> loadImages(List<AiMessage> messages) {
        Map<Long, List<Long>> fileIdsByMessage = new LinkedHashMap<>();
        Set<Long> allFileIds = new LinkedHashSet<>();
        for (AiMessage message : messages) {
            List<Long> fileIds = parseFileIds(message.getImageFileIds());
            fileIdsByMessage.put(message.getId(), fileIds);
            allFileIds.addAll(fileIds);
        }
        Map<Long, FileEntity> files = new LinkedHashMap<>();
        if (!allFileIds.isEmpty()) {
            for (FileEntity file : fileMapper.selectBatchIds(allFileIds)) {
                files.put(file.getId(), file);
            }
        }
        Map<Long, List<com.zhimu.server.common.vo.FileVO>> result = new LinkedHashMap<>();
        fileIdsByMessage.forEach((messageId, fileIds) -> {
            List<com.zhimu.server.common.vo.FileVO> list = new ArrayList<>();
            for (Long fileId : fileIds) {
                FileEntity file = files.get(fileId);
                if (file != null) {
                    com.zhimu.server.common.vo.FileVO vo = fileVoAssembler.toFileVo(file);
                    if (vo != null) {
                        list.add(vo);
                    }
                }
            }
            result.put(messageId, list);
        });
        return result;
    }

    public static List<Long> parseFileIds(String json) {
        List<Long> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            Long[] ids = JSON.readValue(json, Long[].class);
            if (ids != null) {
                for (Long id : ids) {
                    if (id != null) {
                        result.add(id);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 快照损坏按无附图处理
        }
        return result;
    }

    public static String writeFileIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return null;
        }
        return JSON.writeValueAsString(fileIds);
    }
}
