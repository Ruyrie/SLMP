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
    void holdsBracketSplitBeforeCaret() {
        // 复测 N17：增量恰好在“[”与“^P3]”之间切开时，原样漏出了 [^P3]
        AiStreamTextProcessor processor = new AiStreamTextProcessor(
                List.of(hit(8101L, "p1"), hit(8102L, "p2"), hit(8104L, "p3")));
        assertThat(processor.feed("[[EPIDEMIC]]\n应警惕非洲猪瘟 [")).isEqualTo("应警惕非洲猪瘟 ");
        assertThat(processor.feed("^P3]。发现疑似")).isEqualTo("[^1]。发现疑似");
        assertThat(processor.feed("要隔离 [^P3]。") + processor.flush()).isEqualTo("要隔离 [^1]。");
        assertThat(processor.getCitations()).extracting(AiCitation::getKnowledgeId).containsExactly(8104L);
    }

    @Test
    void normalizesCitationVariantsToClientFormat() {
        AiStreamTextProcessor processor = new AiStreamTextProcessor(
                List.of(hit(8101L, "p1"), hit(8102L, "p2"), hit(8103L, "p3")));
        String text = processor.feed("[[NORMAL]]\n甲[P1]乙[^p2]丙［^P3］丁【^P1】戊[^ P2]己[^3]庚[^P1, P2，P9]。")
                + processor.flush();
        assertThat(text).isEqualTo("甲[^1]乙[^2]丙[^3]丁[^1]戊[^2]己[^3]庚[^1][^2]。");
        assertThat(processor.getCitations()).extracting(AiCitation::getSeqNo).containsExactly(1, 2, 3);
    }

    @Test
    void unclosedCitationAtEndOfStreamIsStillConverted() {
        AiStreamTextProcessor processor = new AiStreamTextProcessor(List.of(hit(8101L, "p1")));
        String text = processor.feed("[[NORMAL]]\n按休药期出栏[^P1") + processor.flush();
        assertThat(text).isEqualTo("按休药期出栏[^1]");
    }

    @Test
    void ordinaryBracketsPassThrough() {
        AiStreamTextProcessor processor = new AiStreamTextProcessor(List.of(hit(8101L, "p1")));
        String text = processor.feed("[[NORMAL]]\n见 [") + processor.feed("规范](https://x) 与 [1] 号栏")
                + processor.feed("，温度 [") + processor.flush();
        assertThat(text).isEqualTo("见 [规范](https://x) 与 [1] 号栏，温度 [");
        assertThat(processor.getCitations()).isEmpty();
    }

    @Test
    void offTopicTypeIsCaptured() {
        AiStreamTextProcessor processor = new AiStreamTextProcessor(List.of());
        processor.feed("[[OFF_TOPIC]]\n这个问题与畜禽养殖无关。");
        assertThat(processor.getAnswerType()).isEqualTo(AiCodes.ANSWER_OFF_TOPIC);
    }

    @Test
    void stripsLegacySourceLeadEvenWhenItArrivesAcrossChunks() {
        AiStreamTextProcessor processor = new AiStreamTextProcessor(List.of());
        String text = processor.feed("[[NO_BASIS]]\n**未找到平台")
                + processor.feed("知识依据，仅供参考。**\n\n先检查饮水。") + processor.flush();
        assertThat(text).isEqualTo("先检查饮水。");
        assertThat(processor.getCitations()).isEmpty();
    }

    @Test
    void keepsShortUntaggedAnswersAndProcessesCitationsAtEndOfStream() {
        AiStreamTextProcessor processor = new AiStreamTextProcessor(List.of(hit(8101L, "p3")));
        String text = processor.feed("先检查饮水[^P1]。") + processor.flush();
        assertThat(text).isEqualTo("先检查饮水[^1]。");
        assertThat(processor.getCitations()).hasSize(1);
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
