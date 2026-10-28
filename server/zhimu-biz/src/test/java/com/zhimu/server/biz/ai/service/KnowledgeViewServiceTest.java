package com.zhimu.server.biz.ai.service;

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
import com.zhimu.server.biz.ai.mapper.CourseMapper;
import com.zhimu.server.biz.ai.service.impl.KnowledgeViewServiceImpl;
import com.zhimu.server.biz.ai.support.AiFixtures;
import com.zhimu.server.biz.ai.vo.KnowledgeDetailVO;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * API-K12：知识原文、段落清洗、引用标注与下架。
 */
class KnowledgeViewServiceTest {

    private final AiKnowledgeMapper knowledgeMapper = mock(AiKnowledgeMapper.class);
    private final AiKnowledgeParagraphMapper paragraphMapper = mock(AiKnowledgeParagraphMapper.class);
    private final AiKnowledgeRelatedMapper relatedMapper = mock(AiKnowledgeRelatedMapper.class);
    private final AiMessageMapper messageMapper = mock(AiMessageMapper.class);
    private final AiCitationMapper citationMapper = mock(AiCitationMapper.class);
    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private KnowledgeViewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeViewServiceImpl(knowledgeMapper, paragraphMapper, relatedMapper, messageMapper,
                citationMapper, courseMapper, mock(com.zhimu.server.biz.ai.support.CourseCoverResolver.class),
                new com.zhimu.server.biz.ai.support.AiShareLinks("https://web.test/"));
    }

    @Test
    void returnsBlocksAndSanitizedHtml() {
        AiKnowledge knowledge = knowledge(3, "ON_SHELF");
        when(knowledgeMapper.selectById(8101L)).thenReturn(knowledge);
        when(paragraphMapper.selectByKnowledgeId(8101L)).thenReturn(List.of(
                paragraph("p1", "TEXT", "正文", null, null),
                paragraph("p2", "LIST", "列表", "<ul><li>a</li></ul><script>x</script>", null)));
        when(relatedMapper.selectByKnowledgeId(8101L)).thenReturn(List.of());
        Course course = AiFixtures.course(6301L, 0L, 1);
        course.setStatus("ON_SHELF");
        when(courseMapper.selectById(6301L)).thenReturn(course);

        KnowledgeDetailVO vo = service.detail(10001L, 8101L, null);

        assertThat(vo.getTitle()).isEqualTo("休药期计算与出栏");
        assertThat(vo.getSourceTypeText()).isEqualTo("平台知识库");
        assertThat(vo.getBlocks()).hasSize(2);
        assertThat(vo.getBlocks().get(1).getContentHtml()).isEqualTo("<ul><li>a</li></ul>");
        assertThat(vo.getEmbeddedVideo().getCourseId()).isEqualTo(6301L);
        assertThat(vo.isUpdatedAfterAnswer()).isFalse();
    }

    @Test
    void offShelfKnowledgeReturns41001() {
        when(knowledgeMapper.selectById(8101L)).thenReturn(knowledge(3, "OFF_SHELF"));
        assertThatThrownBy(() -> service.detail(10001L, 8101L, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(41001);
    }

    @Test
    void missingKnowledgeReturns40401() {
        when(knowledgeMapper.selectById(8199L)).thenReturn(null);
        assertThatThrownBy(() -> service.detail(10001L, 8199L, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40401);
    }

    @Test
    void appliesCitationOfOwnMessageAndDetectsNewerVersion() {
        when(knowledgeMapper.selectById(8101L)).thenReturn(knowledge(4, "ON_SHELF"));
        when(paragraphMapper.selectByKnowledgeId(8101L)).thenReturn(List.of(
                paragraph("p3", "TEXT", "正文", null, null)));
        when(relatedMapper.selectByKnowledgeId(8101L)).thenReturn(List.of());
        AiCitation citation = new AiCitation();
        citation.setId(9902L);
        citation.setMessageId(5501L);
        citation.setKnowledgeId(8101L);
        citation.setKnowledgeVersionNo(3);
        citation.setParaKey("p3");
        when(citationMapper.selectById(9902L)).thenReturn(citation);
        AiMessage message = AiFixtures.message(5501L, 1021L, 1);
        when(messageMapper.selectById(5501L)).thenReturn(message);

        KnowledgeDetailVO vo = service.detail(10001L, 8101L, 9902L);

        assertThat(vo.getHighlightParaKey()).isEqualTo("p3");
        assertThat(vo.getCitedVersionNo()).isEqualTo(3);
        assertThat(vo.isUpdatedAfterAnswer()).isTrue();
    }

    @Test
    void ignoresCitationOfOtherUser() {
        when(knowledgeMapper.selectById(8101L)).thenReturn(knowledge(3, "ON_SHELF"));
        when(paragraphMapper.selectByKnowledgeId(8101L)).thenReturn(List.of(
                paragraph("p3", "TEXT", "正文", null, null)));
        when(relatedMapper.selectByKnowledgeId(8101L)).thenReturn(List.of());
        AiCitation citation = new AiCitation();
        citation.setMessageId(5501L);
        citation.setKnowledgeId(8101L);
        citation.setKnowledgeVersionNo(3);
        citation.setParaKey("p3");
        when(citationMapper.selectById(9902L)).thenReturn(citation);
        AiMessage message = AiFixtures.message(5501L, 1021L, 1);
        message.setUserId(10002L);
        when(messageMapper.selectById(5501L)).thenReturn(message);

        KnowledgeDetailVO vo = service.detail(10001L, 8101L, 9902L);

        assertThat(vo.getHighlightParaKey()).isNull();
        assertThat(vo.getCitedVersionNo()).isNull();
    }

    @Test
    void relatedItemsOnlyOnShelf() {
        when(knowledgeMapper.selectById(8101L)).thenReturn(knowledge(3, "ON_SHELF"));
        when(paragraphMapper.selectByKnowledgeId(8101L)).thenReturn(List.of());
        AiKnowledgeRelated on = new AiKnowledgeRelated();
        on.setRelatedId(8102L);
        AiKnowledgeRelated off = new AiKnowledgeRelated();
        off.setRelatedId(8103L);
        when(relatedMapper.selectByKnowledgeId(8101L)).thenReturn(List.of(on, off));
        AiKnowledge relatedOn = knowledge(4, "ON_SHELF");
        relatedOn.setId(8102L);
        AiKnowledge relatedOff = knowledge(5, "OFF_SHELF");
        relatedOff.setId(8103L);
        when(knowledgeMapper.selectById(8102L)).thenReturn(relatedOn);
        when(knowledgeMapper.selectById(8103L)).thenReturn(relatedOff);

        KnowledgeDetailVO vo = service.detail(10001L, 8101L, null);

        assertThat(vo.getRelatedItems()).hasSize(1);
        assertThat(vo.getRelatedItems().get(0).getId()).isEqualTo(8102L);
    }

    private static AiKnowledge knowledge(int version, String status) {
        AiKnowledge knowledge = new AiKnowledge();
        knowledge.setId(8101L);
        knowledge.setTitle("休药期计算与出栏");
        knowledge.setCategoryName("用药规范");
        knowledge.setSourceType("PLATFORM");
        knowledge.setDisplayType("ARTICLE");
        knowledge.setVersionNo(version);
        knowledge.setStatus(status);
        knowledge.setEmbeddedCourseId(6301L);
        knowledge.setShareUrl("https://web.test/knowledge/8101");
        knowledge.setUpdatedAt(AiFixtures.NOW.minusDays(7));
        knowledge.setPublishedAt(AiFixtures.NOW.minusDays(30));
        return knowledge;
    }

    private static AiKnowledgeParagraph paragraph(String key, String type, String text, String html, String image) {
        AiKnowledgeParagraph paragraph = new AiKnowledgeParagraph();
        paragraph.setKnowledgeId(8101L);
        paragraph.setParaKey(key);
        paragraph.setParaType(type);
        paragraph.setContentText(text);
        paragraph.setContentHtml(html);
        paragraph.setImageUrl(image);
        return paragraph;
    }
}
