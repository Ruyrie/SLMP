package com.zhimu.server.biz.ai.service.impl;

import com.zhimu.server.biz.ai.convert.AiMessageConvert;
import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.mapper.AiMessageMapper;
import com.zhimu.server.biz.ai.service.AiMessageService;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.support.AiErrors;
import com.zhimu.server.biz.ai.vo.AiMessageVO;
import com.zhimu.server.biz.ai.vo.RatingResultVO;
import com.zhimu.server.common.error.Asserts;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * API-K08、K09。
 */
@Service
@RequiredArgsConstructor
public class AiMessageServiceImpl implements AiMessageService {

    private final AiMessageMapper messageMapper;
    private final AiMessageConvert messageConvert;

    @Override
    public AiMessageVO getMessage(Long userId, Long messageId) {
        AiMessage message = requireOwn(userId, messageId);
        return messageConvert.toVo(message);
    }

    @Override
    @Transactional
    public RatingResultVO rate(Long userId, Long messageId, String rating) {
        String normalized = rating == null ? null : rating.trim().toUpperCase();
        if (normalized != null && !normalized.isEmpty()
                && !AiCodes.RATING_USEFUL.equals(normalized) && !AiCodes.RATING_USELESS.equals(normalized)) {
            throw AiErrors.invalid("评价参数不正确");
        }
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        AiMessage message = requireOwn(userId, messageId);
        if (!AiCodes.STATUS_ANSWERED.equals(message.getStatus())) {
            throw AiErrors.stateChanged("回答还没有完成，暂时不能评价");
        }
        if (AiCodes.ANSWER_OFF_TOPIC.equals(message.getAnswerType())) {
            throw AiErrors.stateChanged("这条回答不能评价");
        }
        LocalDateTime now = BeijingTime.now();
        message.setRating(normalized);
        message.setRatedAt(normalized == null ? null : now);
        String handle = message.getHandleStatus();
        if (AiCodes.RATING_USELESS.equals(normalized) && (handle == null || handle.isBlank())) {
            message.setHandleStatus("PENDING");
        } else if ((normalized == null || AiCodes.RATING_USEFUL.equals(normalized))
                && "PENDING".equals(handle) && !AiCodes.ANSWER_NO_BASIS.equals(message.getAnswerType())) {
            message.setHandleStatus(null);
        }
        messageMapper.updateById(message);
        boolean clearRating = normalized == null;
        boolean clearHandle = handle != null && message.getHandleStatus() == null;
        if (clearRating || clearHandle) {
            // updateById 跳过 null：取消评价、撤回待处理标记要显式写 NULL
            var clear = com.baomidou.mybatisplus.core.toolkit.Wrappers.<AiMessage>lambdaUpdate()
                    .eq(AiMessage::getId, message.getId());
            if (clearRating) {
                clear.set(AiMessage::getRating, null).set(AiMessage::getRatedAt, null);
            }
            if (clearHandle) {
                clear.set(AiMessage::getHandleStatus, null);
            }
            messageMapper.update(null, clear);
        }
        RatingResultVO vo = new RatingResultVO();
        vo.setRating(normalized);
        vo.setRatedAt(BeijingTime.format(message.getRatedAt()));
        return vo;
    }

    private AiMessage requireOwn(Long userId, Long messageId) {
        AiMessage message = Asserts.found(messageMapper.selectById(messageId));
        if (!userId.equals(message.getUserId()) || AiCodes.STATUS_DELETED.equals(message.getStatus())) {
            throw AiErrors.notFound();
        }
        return message;
    }
}
