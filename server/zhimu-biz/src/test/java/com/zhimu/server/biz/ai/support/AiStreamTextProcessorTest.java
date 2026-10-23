package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.entity.AiCitation;
import com.zhimu.server.biz.ai.entity.AiKnowledge;
import com.zhimu.server.biz.ai.entity.AiKnowledgeParagraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流式正文处理：类型标记截获、引用改号、半个标记的尾部缓存。
 */
class AiStreamTextProcessorTest {

    @Test
    void stripsTypeAndTopicTagsAndRenumbersCitations() {
        AiStreamTextProcessor processor = new AiStreamTextProcessor(List.of(hit(8101L, "p3"), hit(8102L, "p2")));
        String text = processor.feed("[[NORMAL]][[TOPIC:MEDICATION]]\n按平台")
                + processor.feed("标准[^P1]，") + processor.feed("休药 20 天[^P2]。") + processor.flush();
        assertThat(text).isEqualTo("按平台标准[^1]，休药 20 天[^2]。");
        assertThat(processor.getAnswerType()).isEqualTo(AiCodes.ANSWER_NORMAL);
        assertThat(processor.getTopic()).isEqualTo(AiCodes.TOPIC_MEDICATION);
        assertThat(processor.getCitations()).extracting(AiCitation::getSeqNo).containsExactly(1, 2);
        assertThat(processor.getCitations().get(0).getParaKey()).isEqualTo("p3");
    }

    @Test
    void sameParagraphKeepsSameSequenceNumber() {
        AiStreamTextProcessor processor = new AiStreamTextProcessor(List.of(hit(8101L, "p3")));
        String text = processor.feed("[[NO_BASIS]]\n先看[^P1]，再看[^P1]。") + processor.flush();
        assertThat(text).isEqualTo("先看[^1]，再看[^1]。");
        assertThat(processor.getCitations()).hasSize(1);
    }

    @Test
    void dropsUnknownCitationNumbers() {
        AiStreamTextProcessor processor = new AiStreamTextProcessor(List.of(hit(8101L, "p3")));
        String text = processor.feed("[[NORMAL]]\n甲[^P9]乙") + processor.flush();
        assertThat(text).isEqualTo("甲乙");
    }

    @Test
    void holdsHalfCitationUntilNextChunk() {
        AiStreamTextProcessor processor = new AiStreamTextProcessor(List.of(hit(8101L, "p3")));
        assertThat(processor.feed("[[NORMAL]]\n休药[^P")).isEqualTo("休药");
        assertThat(processor.feed("1]。")).isEqualTo("[^1]。");
    }

    @Test
    void offTopicTypeIsCaptured() {
        AiStreamTextProcessor processor = new AiStreamTextProcessor(List.of());
        processor.feed("[[OFF_TOPIC]]\n这个问题与畜禽养殖无关。");
        assertThat(processor.getAnswerType()).isEqualTo(AiCodes.ANSWER_OFF_TOPIC);
    }

    private static RetrievalHit hit(Long knowledgeId, String paraKey) {
        AiKnowledge knowledge = new AiKnowledge();
        knowledge.setId(knowledgeId);
        knowledge.setTitle("知识" + knowledgeId);
        knowledge.setCategoryName("用药规范");
        knowledge.setSourceType(AiCodes.SOURCE_PLATFORM);
        knowledge.setDisplayType(AiCodes.ICON_ARTICLE);
        knowledge.setVersionNo(3);
        AiKnowledgeParagraph paragraph = new AiKnowledgeParagraph();
        paragraph.setKnowledgeId(knowledgeId);
        paragraph.setParaKey(paraKey);
        paragraph.setContentText("段落内容");
        return new RetrievalHit(knowledge, paragraph);
    }
}
