package com.zhimu.server.integration.storage;

import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不需要 MinIO 的部分：对象路径、桶映射、HLS 地址、下载文件名编码、签名不访问网络。
 */
class MinioObjectStorageTest {

    @AfterEach
    void resetClock() {
        BeijingTime.setClock(null);
    }

    private static MinioObjectStorage storage(String publicEndpoint) {
        MinioProperties p = new MinioProperties();
        p.setEndpoint("http://127.0.0.1:1");
        p.setPublicEndpoint(publicEndpoint);
        p.setAccessKey("test-access");
        p.setSecretKey("test-secret");
        HlsProperties hls = new HlsProperties();
        hls.setBaseUrl("https://zhimu.example.com/hls/");
        return new MinioObjectStorage(p, hls);
    }

    @Test
    void buildObjectKey_sceneDateUuidExt() {
        // 2026-10-19 23:30 北京时间
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T15:30:00Z"), ZoneId.of("UTC")));
        MinioObjectStorage s = storage("http://127.0.0.1:9000");
        assertThat(s.buildObjectKey(FileScene.INSPECTION, ".JPG")).matches("inspection/20261019/[0-9a-f]{32}\\.jpg");
        assertThat(s.buildObjectKey(FileScene.EXPORT, "xlsx")).matches("export/20261019/[0-9a-f]{32}\\.xlsx");
        assertThat(s.buildObjectKey(FileScene.AI_VOICE, null)).matches("ai_voice/20261019/[0-9a-f]{32}");
        assertThat(s.buildObjectKey(FileScene.AI_VOICE, "../x")).matches("ai_voice/20261019/[0-9a-f]{32}");
    }

    @Test
    void bucketAndHlsUrl() {
        MinioObjectStorage s = storage("http://127.0.0.1:9000");
        assertThat(s.bucketOf(FileScene.FARM_QUAL)).isEqualTo("zmt-private");
        assertThat(s.bucketOf(FileScene.COURSE_VIDEO)).isEqualTo("zmt-course");
        assertThat(s.bucketOf(FileScene.EXPORT)).isEqualTo("zmt-private");
        assertThat(s.hlsUrl(123, "720p")).isEqualTo("https://zhimu.example.com/hls/123/720p/index.m3u8");
        assertThat(s.hlsObjectPrefix(123)).isEqualTo("hls/123/");
    }

    @Test
    void contentDisposition_rfc5987() {
        assertThat(MinioObjectStorage.contentDisposition("猪场日报 10月.xlsx")).isEqualTo(
                "attachment; filename=\"download.xlsx\"; filename*=UTF-8''"
                        + "%E7%8C%AA%E5%9C%BA%E6%97%A5%E6%8A%A5%2010%E6%9C%88.xlsx");
        assertThat(MinioObjectStorage.contentDisposition("report.pdf"))
                .isEqualTo("attachment; filename=\"report.pdf\"; filename*=UTF-8''report.pdf");
    }

    @Test
    void presign_usesPublicEndpointWithoutNetwork() {
        // 公网入口不可解析也能签名（指定了 region，不请求 GetBucketLocation）
        MinioObjectStorage s = storage("https://files.zhimu.invalid");
        String url = s.presignedGetUrl("zmt-private", "export/20261019/a.xlsx", Duration.ofMinutes(10), "日报.xlsx");
        assertThat(url).startsWith("https://files.zhimu.invalid/zmt-private/export/20261019/a.xlsx?")
                .contains("X-Amz-Expires=600", "response-content-disposition=", "X-Amz-Signature=");
        String part = s.presignedPartUrl("zmt-course", "raw/x.mp4", "upload-1", 3, Duration.ofHours(1));
        assertThat(part).contains("partNumber=3", "uploadId=upload-1", "X-Amz-Signature=");
        assertThat(s.presignedGetUrl("zmt-private", "a.jpg")).contains("X-Amz-Expires=7200");
    }
}
