package com.zhimu.server.biz.ai.service;

import com.zhimu.server.biz.ai.query.AiMessageQuery;
import com.zhimu.server.biz.ai.query.AiSessionQuery;
import com.zhimu.server.biz.ai.vo.AiMessageSliceVO;
import com.zhimu.server.biz.ai.vo.AiSessionDetailVO;
import com.zhimu.server.biz.ai.vo.AiSessionItemVO;
import com.zhimu.server.biz.ai.vo.LinkableBatchesVO;
import com.zhimu.server.common.api.PageVO;

/**
 * API-K01–K05：历史会话、会话详情、按轮次切片、修改关联批次、可关联批次。
 */
public interface AiSessionService {

    PageVO<AiSessionItemVO> listSessions(Long userId, AiSessionQuery query);

    AiSessionDetailVO detail(Long userId, Long sessionId);

    AiMessageSliceVO messages(Long userId, Long sessionId, AiMessageQuery query);

    AiSessionDetailVO changeBatch(Long userId, Long sessionId, Long batchId);

    LinkableBatchesVO linkableBatches(Long userId);
}
