package com.zhimu.server.biz.ai.support;

import java.util.List;

/**
 * 一次生成所需的轮次上下文（事务一创建后传给流式生成与收尾）。
 */
public record AiRound(Long farmId, Long userId, Long sessionId, Long messageId, int roundNo, boolean sessionCreated,
                      String batchLinkState, String questionText, List<Long> imageFileIds, Long batchId,
                      String batchNo, Long contextCourseId, String contextCourseTitle, Long contextKnowledgeId) {
}
