package com.zhimu.server.integration.media;

/**
 * 转码进度回调（在读取 ffmpeg 输出的线程上调用，不要做耗时操作）。
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * @param variant  当前档位
     * @param progress 0–1；该档完成时回调 1
     */
    void onProgress(HlsVariant variant, double progress);
}
