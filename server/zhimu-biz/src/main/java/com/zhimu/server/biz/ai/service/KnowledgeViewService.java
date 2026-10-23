package com.zhimu.server.biz.ai.service;

import com.zhimu.server.biz.ai.vo.KnowledgeDetailVO;

/**
 * API-K12 知识原文。
 */
public interface KnowledgeViewService {

    /**
     * @param citationId 从回答角标进入时带上；不属于本人或不属于该条目时忽略（不报错、不标注）
     */
    KnowledgeDetailVO detail(Long userId, Long knowledgeId, Long citationId);
}
