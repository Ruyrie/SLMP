package com.zhimu.slmp.ai.model;

/**
 * K01 流式回答阶段：IDLE 空闲；WAITING 发送后到首个 delta（“正在思考…”）；STREAMING 逐段追加。
 */
public enum StreamPhase {
    IDLE,
    WAITING,
    STREAMING
}
