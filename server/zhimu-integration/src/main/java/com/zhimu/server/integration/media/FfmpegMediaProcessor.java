package com.zhimu.server.integration.media;

import com.zhimu.server.integration.support.Jsons;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 调用 FFmpeg 7.1 / ffprobe 进程实现音视频处理。
 */
@Slf4j
public class FfmpegMediaProcessor implements MediaProcessor {

    static final int HLS_SEGMENT_SECONDS = 6;
    static final int MAX_LONG_SIDE = 1280;
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration FRAME_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration ENCODE_BASE_TIMEOUT = Duration.ofMinutes(10);

    private final FfmpegProperties props;

    public FfmpegMediaProcessor(FfmpegProperties props) {
        this.props = props;
    }

    @Override
    public MediaInfo probe(Path file) {
        List<String> cmd = List.of(props.getFfprobePath(), "-v", "error", "-print_format", "json",
                "-show_format", "-show_streams", file.toString());
        ProcessRunner.Result r = ProcessRunner.run(cmd, PROBE_TIMEOUT, null);
        JsonNode root = Jsons.parse(r.stdout());
        if (r.exitCode() != 0 || root == null) {
            log.warn("ffprobe 失败 file={} exit={}：{}", file.getFileName(), r.exitCode(), r.stderrTail());
            throw new MediaException("无法读取视频信息", r.stderrTail());
        }
        JsonNode video = null;
        JsonNode audio = null;
        JsonNode streams = root.get("streams");
        if (streams != null && streams.isArray()) {
            for (JsonNode s : streams) {
                String type = Jsons.text(s, "codec_type");
                if ("video".equals(type) && video == null && !isAttachedPicture(s)) {
                    video = s;
                } else if ("audio".equals(type) && audio == null) {
                    audio = s;
                }
            }
        }
        double seconds = parseDouble(Jsons.text(root.get("format"), "duration"));
        if (seconds <= 0 && video != null) {
            seconds = parseDouble(Jsons.text(video, "duration"));
        }
        int width = 0;
        int height = 0;
        if (video != null) {
            width = intOf(video, "width");
            height = intOf(video, "height");
            if (Math.abs(rotation(video)) % 180 == 90) {
                int t = width;
                width = height;
                height = t;
            }
        }
        return new MediaInfo(Math.round(seconds * 1000), width, height,
                video == null ? null : Jsons.text(video, "codec_name"),
                audio == null ? null : Jsons.text(audio, "codec_name"));
    }

    @Override
    public void extractFrame(Path video, double atSecond, Path outJpg) {
        createParent(outJpg);
        List<String> cmd = List.of(props.getFfmpegPath(), "-hide_banner", "-nostdin", "-loglevel", "error", "-y",
                "-ss", seconds(Math.max(0, atSecond)), "-i", video.toString(), "-frames:v", "1", "-q:v", "2",
                outJpg.toString());
        ProcessRunner.Result r = ProcessRunner.run(cmd, FRAME_TIMEOUT, null);
        if (r.exitCode() != 0 || !nonEmpty(outJpg)) {
            log.warn("截取画面失败 file={} at={} exit={}：{}", video.getFileName(), atSecond, r.exitCode(),
                    r.stderrTail());
            throw new MediaException("截取视频画面失败", r.stderrTail());
        }
    }

    @Override
    public void compressTo720p(Path in, Path out) {
        MediaInfo info = probe(in);
        createParent(out);
        String scale = "scale=w='if(gte(iw,ih),trunc(min(" + MAX_LONG_SIDE + ",iw)/2)*2,-2)'"
                + ":h='if(gte(iw,ih),-2,trunc(min(" + MAX_LONG_SIDE + ",ih)/2)*2)'";
        List<String> cmd = List.of(props.getFfmpegPath(), "-hide_banner", "-nostdin", "-loglevel", "error", "-y",
                "-i", in.toString(), "-map", "0:v:0", "-map", "0:a:0?", "-vf", scale,
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", out.toString());
        long t0 = System.nanoTime();
        ProcessRunner.Result r = ProcessRunner.run(cmd, encodeTimeout(info), null);
        if (r.exitCode() != 0 || !nonEmpty(out)) {
            log.warn("视频压缩失败 file={} exit={}：{}", in.getFileName(), r.exitCode(), r.stderrTail());
            throw new MediaException("视频压缩失败", r.stderrTail());
        }
        log.info("视频压缩完成 file={} {}x{} durationMs={} costMs={}", in.getFileName(), info.width(), info.height(),
                info.durationMs(), costMs(t0));
    }

    @Override
    public void transcodeHls(Path in, Path outDir, List<HlsVariant> variants, ProgressListener listener) {
        MediaInfo info = probe(in);
        for (HlsVariant v : variants) {
            Path dir = outDir.resolve(v.getCode());
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new MediaException("转码失败（" + v.getCode() + "）", "无法创建目录：" + e.getMessage());
            }
            int s = v.getShortSide();
            String scale = "scale=w='if(gte(iw,ih),-2,trunc(min(" + s + ",iw)/2)*2)'"
                    + ":h='if(gte(iw,ih),trunc(min(" + s + ",ih)/2)*2,-2)'";
            List<String> cmd = List.of(props.getFfmpegPath(), "-hide_banner", "-nostdin", "-loglevel", "error", "-y",
                    "-i", in.toString(), "-map", "0:v:0", "-map", "0:a:0?", "-vf", scale,
                    "-c:v", "libx264", "-preset", "veryfast", "-profile:v", "main", "-pix_fmt", "yuv420p",
                    "-b:v", v.getVideoKbps() + "k", "-maxrate", (v.getVideoKbps() * 107 / 100) + "k",
                    "-bufsize", (v.getVideoKbps() * 2) + "k",
                    "-force_key_frames", "expr:gte(t,n_forced*" + HLS_SEGMENT_SECONDS + ")", "-sc_threshold", "0",
                    "-c:a", "aac", "-b:a", v.getAudioKbps() + "k",
                    "-f", "hls", "-hls_time", String.valueOf(HLS_SEGMENT_SECONDS), "-hls_playlist_type", "vod",
                    "-hls_segment_filename", dir.resolve("seg_%03d.ts").toString(),
                    "-progress", "pipe:1", "-nostats", dir.resolve("index.m3u8").toString());
            ProgressParser parser = new ProgressParser(v, info.durationMs(), listener);
            long t0 = System.nanoTime();
            ProcessRunner.Result r = ProcessRunner.run(cmd, encodeTimeout(info), parser::accept);
            if (r.exitCode() != 0 || !nonEmpty(dir.resolve("index.m3u8"))) {
                log.warn("HLS 转码失败 file={} variant={} exit={}：{}", in.getFileName(), v.getCode(), r.exitCode(),
                        r.stderrTail());
                throw new MediaException("转码失败（" + v.getCode() + "）", r.stderrTail());
            }
            if (listener != null) {
                listener.onProgress(v, 1.0);
            }
            log.info("HLS 转码完成 file={} variant={} durationMs={} costMs={}", in.getFileName(), v.getCode(),
                    info.durationMs(), costMs(t0));
        }
    }

    @Override
    public List<Path> coverCandidates(Path video, int count, Path outDir) {
        MediaInfo info = probe(video);
        double seconds = info.durationMs() / 1000.0;
        List<Path> list = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            Path out = outDir.resolve("cover_" + i + ".jpg");
            extractFrame(video, seconds * i / (count + 1), out);
            list.add(out);
        }
        return list;
    }

    /** 解析 -progress 输出（out_time_us，单位微秒；out_time_ms 实际也是微秒） */
    static final class ProgressParser {
        private final HlsVariant variant;
        private final long durationUs;
        private final ProgressListener listener;
        private int lastPercent = -1;

        ProgressParser(HlsVariant variant, long durationMs, ProgressListener listener) {
            this.variant = variant;
            this.durationUs = durationMs * 1000;
            this.listener = listener;
        }

        void accept(String line) {
            if (listener == null || durationUs <= 0) {
                return;
            }
            String value;
            if (line.startsWith("out_time_us=")) {
                value = line.substring("out_time_us=".length());
            } else if (line.startsWith("out_time_ms=")) {
                value = line.substring("out_time_ms=".length());
            } else {
                return;
            }
            long us;
            try {
                us = Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                return;
            }
            double p = Math.max(0, Math.min(0.99, (double) us / durationUs));
            int percent = (int) (p * 100);
            if (percent > lastPercent) {
                lastPercent = percent;
                listener.onProgress(variant, p);
            }
        }
    }

    /** 编码超时：10 分钟 + 视频时长的 3 倍 */
    private static Duration encodeTimeout(MediaInfo info) {
        return ENCODE_BASE_TIMEOUT.plusMillis(info.durationMs() * 3);
    }

    private static boolean isAttachedPicture(JsonNode stream) {
        JsonNode d = stream.get("disposition");
        return d != null && Jsons.integer(d, "attached_pic") != null && Jsons.integer(d, "attached_pic") == 1;
    }

    private static int rotation(JsonNode video) {
        JsonNode tags = video.get("tags");
        String rotate = Jsons.text(tags, "rotate");
        if (rotate != null) {
            return (int) parseDouble(rotate);
        }
        JsonNode sideData = video.get("side_data_list");
        if (sideData != null && sideData.isArray()) {
            for (JsonNode sd : sideData) {
                String r = Jsons.text(sd, "rotation");
                if (r != null) {
                    return (int) parseDouble(r);
                }
            }
        }
        return 0;
    }

    private static int intOf(JsonNode node, String field) {
        Integer v = Jsons.integer(node, field);
        return v == null ? 0 : v;
    }

    private static double parseDouble(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String seconds(double s) {
        return String.format(Locale.ROOT, "%.3f", s);
    }

    private static boolean nonEmpty(Path p) {
        try {
            return Files.isRegularFile(p) && Files.size(p) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static void createParent(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new MediaException("音视频处理失败", "无法创建目录：" + e.getMessage());
            }
        }
    }

    private static long costMs(long t0) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
    }
}
