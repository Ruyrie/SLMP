package com.zhimu.server.biz.ai.convert;

import com.zhimu.server.biz.ai.entity.AiCitation;
import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.mapper.AiCitationMapper;
import com.zhimu.server.biz.ai.mapper.AiCorrectionMapper;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.vo.AiMessageVO;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.service.impl.FileVoAssembler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiMessagePresentationTest {
    private final AiCitationMapper citations = mock(AiCitationMapper.class);
    private final AiMessageConvert convert = new AiMessageConvert(citations, mock(AiCorrectionMapper.class),
            mock(FileMapper.class), mock(FileVoAssembler.class));

    @Test
    void historicalNoBasisLeadMovesOutOfBodyIntoSourceNote() {
        AiMessage message = message();
        message.setAnswerType(AiCodes.ANSWER_NO_BASIS);
        message.setAnswerText("**" + AiCodes.NO_BASIS_LEAD + "**\n\n先检查饮水。");

        AiMessageVO vo = convert.toVo(message);

        assertThat(vo.getAnswerText()).isEqualTo("先检查饮水。");
        assertThat(vo.getSourceNote()).isEqualTo(AiCodes.NO_BASIS_SOURCE_NOTE);
        assertThat(vo.getAnalysisSummary()).contains("没有可展示的平台资料引用");
        assertThat(vo.getCitations()).isEmpty();
    }

    @Test
    void verifiedCitationsRemainAvailableWithoutNoBasisNote() {
        AiMessage message = message();
        message.setAnswerType(AiCodes.ANSWER_NORMAL);
        message.setAnswerText("先检查饮水[^1]。");
        AiCitation citation = new AiCitation();
        citation.setId(91L);
        citation.setMessageId(55L);
        citation.setKnowledgeId(81L);
        citation.setSeqNo(1);
        citation.setTitleSnapshot("饮水管理");
        when(citations.selectByMessageIds(any())).thenReturn(List.of(citation));

        AiMessageVO vo = convert.toVo(message);

        assertThat(vo.getCitations()).hasSize(1);
        assertThat(vo.getSourceNote()).isNull();
        assertThat(vo.getAnalysisSummary()).contains("1 条可查阅");
        assertThat(vo.getAnswerText()).contains("[^1]");
    }

    private static AiMessage message() {
        AiMessage message = new AiMessage();
        message.setId(55L);
        message.setSessionId(10L);
        message.setStatus(AiCodes.STATUS_ANSWERED);
        return message;
    }
}
