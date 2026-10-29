package com.zhimu.server.biz.ai.service;

import com.zhimu.server.biz.ai.convert.AiMessageConvert;
import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.mapper.AiMessageMapper;
import com.zhimu.server.biz.ai.service.impl.AiMessageServiceImpl;
import com.zhimu.server.biz.ai.support.AiFixtures;
import com.zhimu.server.biz.ai.vo.RatingResultVO;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * API-K08/K09：单轮问答、评价覆盖与状态校验。
 */
class AiMessageServiceTest {

    private final AiMessageMapper messageMapper = mock(AiMessageMapper.class);
    private final AiMessageConvert messageConvert = mock(AiMessageConvert.class);
    private AiMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        if (com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(AiMessage.class) == null) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(
                            new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), AiMessage.class);
        }
        service = new AiMessageServiceImpl(messageMapper, messageConvert);
    }

    @Test
    void ratesAnsweredMessageAndWritesHandleStatus() {
        AiMessage message = AiFixtures.message(5501L, 1021L, 1);
        message.setAnswerType("NORMAL");
        when(messageMapper.selectById(5501L)).thenReturn(message);

        RatingResultVO vo = service.rate(10001L, 5501L, "USELESS");

        assertThat(vo.getRating()).isEqualTo("USELESS");
        assertThat(vo.getRatedAt()).isNotBlank();
        assertThat(message.getHandleStatus()).isEqualTo("PENDING");
        verify(messageMapper).updateById(message);
    }

    @Test
    void usefulRatingClearsPendingWhenNotNoBasis() {
        AiMessage message = AiFixtures.message(5501L, 1021L, 1);
        message.setRating("USELESS");
        message.setHandleStatus("PENDING");
        when(messageMapper.selectById(5501L)).thenReturn(message);

        service.rate(10001L, 5501L, "USEFUL");

        assertThat(message.getHandleStatus()).isNull();
        assertThat(message.getRating()).isEqualTo("USEFUL");
    }

    @Test
    void cancelRatingClearsFields() {
        AiMessage message = AiFixtures.message(5501L, 1021L, 1);
        message.setRating("USEFUL");
        when(messageMapper.selectById(5501L)).thenReturn(message);

        RatingResultVO vo = service.rate(10001L, 5501L, null);

        assertThat(vo.getRating()).isNull();
        assertThat(vo.getRatedAt()).isNull();
        assertThat(message.getRating()).isNull();
        // updateById 跳过 null：取消评价必须显式写 rating / rated_at = NULL
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AiMessage>> clear =
                org.mockito.ArgumentCaptor.forClass(
                        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(messageMapper).update(org.mockito.ArgumentMatchers.isNull(), clear.capture());
        assertThat(clear.getValue().getSqlSet()).contains("rating=", "rated_at=");
    }

    @Test
    void rejectsInvalidRatingValue() {
        assertThatThrownBy(() -> service.rate(10001L, 5501L, "GREAT"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40001);
        verify(messageMapper, never()).updateById(any(AiMessage.class));
    }

    @Test
    void rejectsMessageNotAnswered() {
        AiMessage message = AiFixtures.message(5501L, 1021L, 1);
        message.setStatus("TIMEOUT");
        when(messageMapper.selectById(5501L)).thenReturn(message);
        assertThatThrownBy(() -> service.rate(10001L, 5501L, "USEFUL"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40903);
    }

    @Test
    void rejectsOffTopicMessage() {
        AiMessage message = AiFixtures.message(5501L, 1021L, 1);
        message.setAnswerType("OFF_TOPIC");
        when(messageMapper.selectById(5501L)).thenReturn(message);
        assertThatThrownBy(() -> service.rate(10001L, 5501L, "USEFUL"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40903);
    }

    @Test
    void rejectsOtherUsersMessage() {
        AiMessage message = AiFixtures.message(5501L, 1021L, 1);
        message.setUserId(10002L);
        when(messageMapper.selectById(5501L)).thenReturn(message);
        assertThatThrownBy(() -> service.rate(10001L, 5501L, "USEFUL"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(40401);
    }
}
