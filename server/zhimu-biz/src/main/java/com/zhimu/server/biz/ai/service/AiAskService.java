package com.zhimu.server.biz.ai.service;

import com.zhimu.server.biz.ai.request.AiAskRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API-K06、K07：提问与重新生成（SSE）。
 */
public interface AiAskService {

    /** 开流前的校验失败以普通 ApiResponse 返回；成功返回已开始生成的 SseEmitter */
    SseEmitter ask(Long userId, String idempotencyKey, AiAskRequest request);

    SseEmitter retry(Long userId, Long messageId);
}
