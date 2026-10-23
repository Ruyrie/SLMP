package com.zhimu.server.integration.media;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 用 ffmpeg 现场生成 3 秒测试视频（testsrc + 正弦音频），测 probe、截帧、压缩、HLS、封面候选；在临时目录完成并清理。
 * 找不到 ffmpeg（FFMPEG_PATH / FFPROBE_PATH 或 server/tools/ffmpeg）时跳过。
 */
class FfmpegMediaProcessorTest {

    private static final String DEFAULT_BIN = "D:/Android_Program/SLMP/server/tools/ffmpeg/bin/";

    private static Path workDir;
    private static FfmpegProperties props;
    private static FfmpegMediaProcessor processor;
    private static Path landscape;

    private static String tool(String env, String exe) {
        String v = System.getenv(env);
        if (v != null && !v.isBlank()) {
            return v;
        }
        Path p = Path.of(DEFAULT_BIN + exe);
        return Files.exists(p) ? p.toString() : null;
    }

    @BeforeAll
    static void setUp() throws Exception {
        String ffmpeg = tool("FFMPEG_PATH", "ffmpeg.exe");
        String ffprobe = tool("FFPROBE_PATH", "ffprobe.exe");
        assumeTrue(ffmpeg != null && ffprobe != null && Files.exists(Path.of(ffmpeg)), "未找到 ffmpeg，跳过");
        props = new FfmpegProperties();
        props.setFfmpegPath(ffmpeg);
        props.setFfprobePath(ffprobe);
        processor = new FfmpegMediaProcessor(props);
        workDir = Files.createTempDirectory("zmt-media-test");
        landscape = generate("landscape.mp4", 1920, 1080);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (workDir != null) {
            try (Stream<Path> walk = Files.walk(workDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static Path generate(String name, int w, int h) {
        Path out = workDir.resolve(name);
        ProcessRunner.Result r = ProcessRunner.run(List.of(props.getFfmpegPath(), "-hide_banner", "-nostdin",
                "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc=size=" + w + "x" + h + ":rate=25",
                "-f", "lavfi", "-i", "sine=frequency=1000:sample_rate=44100",
                "-t", "3", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac",
                out.toString()), Duration.ofSeconds(60), null);
        assertThat(r.exitCode()).as(r.stderrTail()).isZero();
        return out;
    }

    @Test
    void probe_readsDurationSizeAndCodecs() {
        MediaInfo info = processor.probe(landscape);
        assertThat(info.durationMs()).isBetween(2900L, 3200L);
        assertThat(info.width()).isEqualTo(1920);
        assertThat(info.height()).isEqualTo(1080);
        assertThat(info.videoCodec()).isEqualTo("h264");
        assertThat(info.audioCodec()).isEqualTo("aac");
    }

    @Test
    void probe_invalidFile_throws() throws IOException {
        Path bad = Files.writeString(workDir.resolve("bad.mp4"), "not a video");
        assertThatThrownBy(() -> processor.probe(bad))
                .isInstanceOfSatisfying(MediaException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(50001);
                    assertThat(e.getDetail()).isNotBlank();
                });
    }

    @Test
    void extractFrame_writesJpeg() throws IOException {
        Path jpg = workDir.resolve("frames/frame1.jpg");
        processor.extractFrame(landscape, 1.0, jpg);
        BufferedImage img = ImageIO.read(jpg.toFile());
        assertThat(img.getWidth()).isEqualTo(1920);
        assertThat(img.getHeight()).isEqualTo(1080);
    }

    @Test
    void compressTo720p_landscapeAndPortrait() {
        Path out = workDir.resolve("compressed.mp4");
        processor.compressTo720p(landscape, out);
        MediaInfo info = processor.probe(out);
        assertThat(info.width()).isEqualTo(1280);
        assertThat(info.height()).isEqualTo(720);
        assertThat(info.videoCodec()).isEqualTo("h264");
        assertThat(info.audioCodec()).isEqualTo("aac");

        Path portrait = generate("portrait.mp4", 1080, 1920);
        Path out2 = workDir.resolve("compressed-portrait.mp4");
        processor.compressTo720p(portrait, out2);
        MediaInfo info2 = processor.probe(out2);
        assertThat(info2.width()).isEqualTo(720);
        assertThat(info2.height()).isEqualTo(1280);
    }

    @Test
    void transcodeHls_twoVariantsWithProgress() throws IOException {
        Path outDir = workDir.resolve("hls");
        Map<HlsVariant, Double> last = new ConcurrentHashMap<>();
        processor.transcodeHls(landscape, outDir, List.of(HlsVariant.P720, HlsVariant.P480),
                (variant, progress) -> {
                    assertThat(progress).isBetween(0.0, 1.0);
                    last.merge(variant, progress, Math::max);
                });
        for (HlsVariant v : HlsVariant.values()) {
            Path playlist = outDir.resolve(v.getCode()).resolve("index.m3u8");
            assertThat(playlist).exists();
            String m3u8 = Files.readString(playlist);
            assertThat(m3u8).contains("#EXT-X-TARGETDURATION", "#EXT-X-ENDLIST", "seg_000.ts");
            assertThat(outDir.resolve(v.getCode()).resolve("seg_000.ts")).exists();
            assertThat(last.get(v)).isEqualTo(1.0);
            assertThat(processor.probe(outDir.resolve(v.getCode()).resolve("seg_000.ts")).height())
                    .isEqualTo(v.getShortSide());
        }
    }

    @Test
    void coverCandidates_three() throws IOException {
        List<Path> covers = processor.coverCandidates(landscape, 3, workDir.resolve("covers"));
        assertThat(covers).hasSize(3);
        for (Path p : covers) {
            assertThat(Files.size(p)).isPositive();
        }
    }

    @Test
    void processTimeout_destroysProcess() {
        long t0 = System.currentTimeMillis();
        assertThatThrownBy(() -> ProcessRunner.run(List.of(props.getFfmpegPath(), "-hide_banner", "-nostdin",
                "-re", "-f", "lavfi", "-i", "testsrc", "-f", "null", "-"), Duration.ofSeconds(1), null))
                .isInstanceOfSatisfying(MediaException.class, e -> assertThat(e.getMessage()).contains("超时"));
        assertThat(System.currentTimeMillis() - t0).isLessThan(8000);
    }
}
