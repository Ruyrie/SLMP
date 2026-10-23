package com.zhimu.server.integration.ai;

/**
 * 进行中的流式调用。客户端断开时调用 {@link #cancel()} 取消上游请求。
 */
public interface AiStreamHandle {

    /** 取消上游请求并释放并发名额；之后不再回调 handler。重复调用无副作用 */
    void cancel();

    /** 已结束（完成、失败或已取消） */
    boolean isDone();
}
