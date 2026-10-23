package com.zhimu.slmp.ai.model;

/**
 * 播放器状态（K03 内嵌视频、K05/K06）：NETWORK_LOST 为网络类错误（连上后自动继续），WIFI_BLOCKED 为“仅 Wi-Fi”拦截。
 */
public enum PlayerState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
    NETWORK_LOST,
    WIFI_BLOCKED
}
