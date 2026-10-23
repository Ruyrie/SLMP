package com.zhimu.server.biz.ai.service;

import com.zhimu.server.biz.ai.vo.AiMessageVO;
import com.zhimu.server.biz.ai.vo.RatingResultVO;

/**
 * API-K08、K09：单轮问答、评价。
 */
public interface AiMessageService {

    AiMessageVO getMessage(Long userId, Long messageId);

    RatingResultVO rate(Long userId, Long messageId, String rating);
}
