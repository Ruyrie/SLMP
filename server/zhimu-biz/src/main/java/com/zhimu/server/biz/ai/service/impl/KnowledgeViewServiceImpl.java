package com.zhimu.server.biz.ai.service.impl;

import com.zhimu.server.biz.ai.entity.AiCitation;
import com.zhimu.server.biz.ai.entity.AiKnowledge;
import com.zhimu.server.biz.ai.entity.AiKnowledgeParagraph;
import com.zhimu.server.biz.ai.entity.AiKnowledgeRelated;
import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.entity.Course;
import com.zhimu.server.biz.ai.mapper.AiCitationMapper;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeMapper;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeParagraphMapper;
import com.zhimu.server.biz.ai.mapper.AiKnowledgeRelatedMapper;
import com.zhimu.server.biz.ai.mapper.AiMessageMapper;
import com.zhimu.server.biz.ai.service.KnowledgeViewService;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.support.AiErrors;
import com.zhimu.server.biz.ai.support.AiHtmlSanitizer;
import com.zhimu.server.biz.ai.support.AiText;
import com.zhimu.server.biz.ai.vo.EmbeddedVideoVO;
import com.zhimu.server.biz.ai.vo.KnowledgeBlockVO;
import com.zhimu.server.biz.ai.vo.KnowledgeDetailVO;
import com.zhimu.server.biz.ai.vo.RelatedKnowledgeVO;
import com.zhimu.server.common.error.Asserts;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API-K12 知识原文与引用标注。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeViewServiceImpl implements KnowledgeViewService {

    private final AiKnowledgeMapper knowledgeMapper;
    private final AiKnowledgeParagraphMapper paragraphMapper;
    private final AiKnowledgeRelatedMapper relatedMapper;
    private final AiMessageMapper messageMapper;
    private final AiCitationMapper citationMapper;
    private final com.zhimu.server.biz.ai.mapper.CourseMapper courseMapper;

    @Override
    public KnowledgeDetailVO detail(Long userId, Long knowledgeId, Long citationId) {
        AiKnowledge knowledge = Asserts.found(knowledgeMapper.selectById(knowledgeId));
        if ("OFF_SHELF".equals(knowledge.getStatus())) {
            throw AiErrors.offShelf("这条知识已下架，不能查看原文");
        }
        KnowledgeDetailVO vo = new KnowledgeDetailVO();
        vo.setId(knowledge.getId());
        vo.setTitle(knowledge.getTitle());
        vo.setCategoryName(knowledge.getCategoryName());
        vo.setSourceType(knowledge.getSourceType());
        vo.setSourceTypeText(AiText.sourceTypeText(knowledge.getSourceType()));
        vo.setVersionNo(knowledge.getVersionNo() == null ? 0 : knowledge.getVersionNo());
        vo.setUpdatedAt(BeijingTime.format(knowledge.getUpdatedAt()));
        vo.setShareUrl(knowledge.getShareUrl());
        List<AiKnowledgeParagraph> paragraphs = paragraphMapper.selectByKnowledgeId(knowledgeId);
        vo.setEmbeddedVideo(embeddedVideo(knowledge, paragraphs));
        vo.setBlocks(blocks(paragraphs));
        vo.setRelatedItems(relatedItems(knowledgeId));
        applyCitation(userId, knowledgeId, citationId, paragraphs, knowledge, vo);
        return vo;
    }

    private EmbeddedVideoVO embeddedVideo(AiKnowledge knowledge, List<AiKnowledgeParagraph> paragraphs) {
        Long courseId = knowledge.getEmbeddedCourseId();
        if (courseId == null) {
            for (AiKnowledgeParagraph paragraph : paragraphs) {
                if ("VIDEO".equals(paragraph.getParaType()) && paragraph.getCourseId() != null) {
                    courseId = paragraph.getCourseId();
                    break;
                }
            }
        }
        if (courseId == null) {
            return null;
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null || !"ON_SHELF".equals(course.getStatus())) {
            return null;
        }
        EmbeddedVideoVO vo = new EmbeddedVideoVO();
        vo.setCourseId(course.getId());
        vo.setTitle(course.getTitle());
        vo.setDurationSec(course.getDurationSec() == null ? 0 : course.getDurationSec());
        vo.setCoverUrl(course.getCoverUrl());
        return vo;
    }

    private List<KnowledgeBlockVO> blocks(List<AiKnowledgeParagraph> paragraphs) {
        List<KnowledgeBlockVO> blocks = new ArrayList<>();
        for (AiKnowledgeParagraph paragraph : paragraphs) {
            if ("VIDEO".equals(paragraph.getParaType())) {
                continue;
            }
            KnowledgeBlockVO block = new KnowledgeBlockVO();
            block.setParaKey(paragraph.getParaKey());
            block.setParaType(paragraph.getParaType());
            block.setText(paragraph.getContentText());
            block.setContentHtml(AiHtmlSanitizer.sanitize(paragraph.getContentHtml()));
            block.setImageUrl(paragraph.getImageUrl());
            blocks.add(block);
        }
        return blocks;
    }

    private List<RelatedKnowledgeVO> relatedItems(Long knowledgeId) {
        List<RelatedKnowledgeVO> result = new ArrayList<>();
        List<AiKnowledgeRelated> related = relatedMapper.selectByKnowledgeId(knowledgeId);
        if (related.isEmpty()) {
            return result;
        }
        Map<Long, AiKnowledge> knowledgeMap = new LinkedHashMap<>();
        for (Long id : related.stream().map(AiKnowledgeRelated::getRelatedId).toList()) {
            AiKnowledge item = knowledgeMapper.selectById(id);
            if (item != null) {
                knowledgeMap.put(id, item);
            }
        }
        for (AiKnowledgeRelated link : related) {
            AiKnowledge item = knowledgeMap.get(link.getRelatedId());
            if (item == null || !"ON_SHELF".equals(item.getStatus())) {
                continue;
            }
            RelatedKnowledgeVO vo = new RelatedKnowledgeVO();
            vo.setId(item.getId());
            vo.setTitle(item.getTitle());
            vo.setCategoryName(item.getCategoryName());
            vo.setSourceType(item.getSourceType());
            vo.setSourceTypeText(AiText.sourceTypeText(item.getSourceType()));
            vo.setVersionNo(item.getVersionNo() == null ? 0 : item.getVersionNo());
            vo.setIconType(item.getDisplayType());
            result.add(vo);
        }
        return result;
    }

    private void applyCitation(Long userId, Long knowledgeId, Long citationId,
                               List<AiKnowledgeParagraph> paragraphs, AiKnowledge knowledge,
                               KnowledgeDetailVO vo) {
        if (citationId == null || userId == null) {
            return;
        }
        AiCitation citation = citationMapper.selectById(citationId);
        if (citation == null || !knowledgeId.equals(citation.getKnowledgeId())) {
            return;
        }
        AiMessage message = messageMapper.selectById(citation.getMessageId());
        if (message == null || !userId.equals(message.getUserId())) {
            return;
        }
        boolean exists = paragraphs.stream().anyMatch(p -> p.getParaKey().equals(citation.getParaKey()));
        if (exists) {
            vo.setHighlightParaKey(citation.getParaKey());
        }
        vo.setCitedVersionNo(citation.getKnowledgeVersionNo());
        int online = knowledge.getVersionNo() == null ? 0 : knowledge.getVersionNo();
        vo.setUpdatedAfterAnswer(citation.getKnowledgeVersionNo() != null
                && online > citation.getKnowledgeVersionNo());
    }
}
