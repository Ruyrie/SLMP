package com.zhimu.server.integration.ai;

import com.zhimu.server.common.error.BizException;

/**
 * 流式回调。回调在 Reactor 的 boundedElastic 线程上按顺序执行，可以做阻塞写出（如 SseEmitter.send）。
 * 结束时 onComplete 与 onError 只会回调其中一个；调用方主动取消后不再回调。
 */
public interface AiStreamHandler {

    /** 第一段正文之前回调一次：实际使用的服务商、模型与是否备用 */
    default void onStart(String provider, String model, boolean fallback) {
    }

    /** 正文片段，依次追加 */
    void onDelta(String text);

    /** 正常结束之前回调一次：完整结果（含 token 用量与耗时） */
    default void onResult(AiChatResult result) {
    }

    /** 正常结束 */
    void onComplete(String finishReason, String fullText);

    /**
     * 失败：50201（未配置、排队超时、两个服务商都失败、已输出正文后中断）或 50401（超时）。
     * 已输出的正文由调用方自行保留（“回答中断”）。
     */
    void onError(BizException error);
}
