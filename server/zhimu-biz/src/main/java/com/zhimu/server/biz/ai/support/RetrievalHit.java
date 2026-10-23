package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.entity.AiKnowledge;
import com.zhimu.server.biz.ai.entity.AiKnowledgeParagraph;

/**
 * 检索命中的知识段落（P1、P2…）。
 */
public record RetrievalHit(AiKnowledge knowledge, AiKnowledgeParagraph paragraph) {
}
