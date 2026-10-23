package com.zhimu.server.biz.ai.support;

/**
 * 同一用户同时只能有一条回答在生成（ai:running:{userId}，2 分钟自动过期）。
 * 生产实现用 Redis；测试可传入内存实现或直接放行。
 */
public interface AiRunningGuard {

    boolean tryAcquire(Long userId);

    void release(Long userId);

    /** 测试与未配置 Redis 时使用：直接放行 */
    AiRunningGuard NOOP = new AiRunningGuard() {
        @Override
        public boolean tryAcquire(Long userId) {
            return true;
        }

        @Override
        public void release(Long userId) {
        }
    };
}
