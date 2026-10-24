package com.zhimu.server.biz.ai.service;

import com.zhimu.server.biz.ai.query.AiMessageQuery;
import com.zhimu.server.biz.ai.query.AiSessionQuery;
import com.zhimu.server.biz.ai.request.AiSessionBatchRequest;
import com.zhimu.server.biz.ai.request.AiSessionCreateRequest;
import com.zhimu.server.biz.ai.vo.AiMessageSliceVO;
import com.zhimu.server.biz.ai.vo.AiSessionDetailVO;
import com.zhimu.server.biz.ai.vo.AiSessionItemVO;
import com.zhimu.server.biz.ai.vo.LinkableBatchesVO;
import com.zhimu.server.common.api.PageVO;

/**
 * API-K01–K05：历史会话、会话详情、按轮次切片、修改关联批次/标题、可关联批次；
 * 以及新建空会话、删除会话。
 */
public interface AiSessionService {

    PageVO<AiSessionItemVO> listSessions(Long userId, AiSessionQuery query);

    AiSessionDetailVO detail(Long userId, Long sessionId);

    AiMessageSliceVO messages(Long userId, Long sessionId, AiMessageQuery query);

    /** 新建空会话：归属当前用户与当前养殖场 */
    AiSessionDetailVO create(Long userId, AiSessionCreateRequest request);

    /** 修改会话：title 与/或 batch_id；未传的字段保持不变 */
    AiSessionDetailVO update(Long userId, Long sessionId, AiSessionBatchRequest request);

    /** 只修改关联批次（K04 原有语义：batchId 为 null 表示取消关联） */
    AiSessionDetailVO changeBatch(Long userId, Long sessionId, Long batchId);

    /** 逻辑删除本人会话及其消息；重复删除幂等成功 */
    void delete(Long userId, Long sessionId);

    LinkableBatchesVO linkableBatches(Long userId);
}
