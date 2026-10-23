package com.zhimu.server.integration.media;

import java.nio.file.Path;
import java.util.List;

/**
 * FFmpeg / ffprobe 音视频处理（04 号 8.2、8.3 节）。所有方法阻塞执行，失败抛出 {@link MediaException}。
 * 课程转码请提交到 Bean {@code transcodeExecutor}（注入时加 {@code @Qualifier("transcodeExecutor")}）。
 */
public interface MediaProcessor {

    /** 读取时长、宽高与音视频编码 */
    MediaInfo probe(Path file);

    /** 截取 atSecond 处的一帧保存为 JPEG */
    void extractFrame(Path video, double atSecond, Path outJpg);

    /** 压缩为 MP4（H.264 + AAC），长边不超过 1280，不放大 */
    void compressTo720p(Path in, Path out);

    /**
     * 转 HLS：每档输出到 {outDir}/{720p|480p}/index.m3u8 与 seg_000.ts…，每段 6 秒，依次转码各档。
     *
     * @param listener 进度回调，可为 null
     */
    void transcodeHls(Path in, Path outDir, List<HlsVariant> variants, ProgressListener listener);

    /** 在视频中均匀截取 count 张封面候选（{outDir}/cover_1.jpg…），返回文件路径 */
    List<Path> coverCandidates(Path video, int count, Path outDir);
}
