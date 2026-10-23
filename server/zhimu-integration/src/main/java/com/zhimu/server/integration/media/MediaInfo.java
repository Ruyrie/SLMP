package com.zhimu.server.integration.media;

/**
 * ffprobe 读取的视频信息。宽高已按旋转角度（手机竖拍）换算为显示方向。
 *
 * @param durationMs 时长（毫秒）
 * @param width      宽（像素），没有视频流时为 0
 * @param height     高（像素），没有视频流时为 0
 * @param videoCodec 视频编码，例如 h264；没有视频流时为 null
 * @param audioCodec 音频编码，例如 aac；没有音频流时为 null
 */
public record MediaInfo(long durationMs, int width, int height, String videoCodec, String audioCodec) {

    /** 长边 */
    public int longSide() {
        return Math.max(width, height);
    }
}
