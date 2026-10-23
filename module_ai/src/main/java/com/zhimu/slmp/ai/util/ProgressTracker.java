package com.zhimu.slmp.ai.util;

/**
 * 观看秒数累计（技术文档 2.6.8）：Activity 每秒报一次位置，播放中取位置增量，只接受 0 到“实际间隔 × 倍速 + 1 秒”之间的值
 * （排除拖动跳跃），累加为待报观看秒数；上报时 watched_sec 取待报数与 60 的较小者，成功后扣除，失败留到下次。
 * 另累计播放时长，每满 10 秒到一次 TICK。纯 Java，单元测试覆盖。
 */
public final class ProgressTracker {

    public static final int MAX_WATCHED_PER_REPORT = 60;
    public static final long TICK_INTERVAL_MS = 10_000;

    private long lastPositionMs = -1;
    private long lastClockMs = -1;
    private double pendingSec;
    private long playedSinceTickMs;

    /**
     * 每秒调用一次。
     *
     * @return 本次是否到了 TICK 上报时机（播放中累计满 10 秒）
     */
    public boolean onTick(long positionMs, long clockMs, boolean playing, float speed) {
        boolean due = false;
        if (playing && lastPositionMs >= 0 && lastClockMs >= 0) {
            long delta = positionMs - lastPositionMs;
            long interval = Math.max(0, clockMs - lastClockMs);
            double max = interval * Math.max(speed, 0.1f) + 1000;
            if (delta >= 0 && delta <= max) {
                pendingSec += delta / 1000.0;
            }
            playedSinceTickMs += interval;
            if (playedSinceTickMs >= TICK_INTERVAL_MS) {
                playedSinceTickMs = 0;
                due = true;
            }
        }
        lastPositionMs = positionMs;
        lastClockMs = clockMs;
        return due;
    }

    /** 拖动、切课程后重新取位置基准（不计入观看） */
    public void reanchor(long positionMs, long clockMs) {
        lastPositionMs = positionMs;
        lastClockMs = clockMs;
    }

    /** 本次上报的观看秒数（≤60） */
    public int peekWatched() {
        return (int) Math.min(MAX_WATCHED_PER_REPORT, Math.floor(pendingSec));
    }

    /** 上报成功后扣除 */
    public void commit(int watchedSec) {
        pendingSec = Math.max(0, pendingSec - watchedSec);
    }

    public double getPendingSec() {
        return pendingSec;
    }

    /** 新的一次播放 */
    public void reset() {
        lastPositionMs = -1;
        lastClockMs = -1;
        pendingSec = 0;
        playedSinceTickMs = 0;
    }
}
