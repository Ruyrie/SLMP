package com.zhimu.server.integration.storage;

import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 连本机 MinIO（http://127.0.0.1:9000，可用 MINIO_ENDPOINT 等环境变量覆盖）：上传、预签名下载、分片上传、匿名读策略。
 * MinIO 未启动时跳过；结束后删除测试对象。
 */
class MinioObjectStorageIntegrationTest {

    private static final String ENDPOINT = env("MINIO_ENDPOINT", "http://127.0.0.1:9000");

    private MinioObjectStorage storage;
    private final List<String[]> created = new ArrayList<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? def : v;
    }

    private static boolean reachable(String endpoint) {
        URI u = URI.create(endpoint);
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress(u.getHost(), u.getPort() < 0 ? 80 : u.getPort()), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(reachable(ENDPOINT), "本机 MinIO 未启动，跳过");
        MinioProperties p = new MinioProperties();
        p.setEndpoint(ENDPOINT);
        p.setPublicEndpoint(ENDPOINT);
        p.setAccessKey(env("MINIO_ACCESS_KEY", "minioadmin"));
        p.setSecretKey(env("MINIO_SECRET_KEY", "minioadmin123"));
        HlsProperties hls = new HlsProperties();
        hls.setBaseUrl(ENDPOINT + "/zmt-course/hls");
        storage = new MinioObjectStorage(p, hls);
        assertThat(storage.ensureBuckets()).isTrue();
    }

    @AfterEach
    void cleanUp() {
        for (String[] o : created) {
            storage.remove(o[0], o[1]);
        }
    }

    private String track(String bucket, String key) {
        created.add(new String[]{bucket, key});
        return key;
    }

    @Test
    void putStatGetAndPresignedDownload() throws Exception {
        String bucket = storage.bucketOf(FileScene.EXPORT);
        String key = track(bucket, storage.buildObjectKey(FileScene.EXPORT, "txt"));
        byte[] data = "智牧通导出测试".getBytes(StandardCharsets.UTF_8);
        storage.put(bucket, key, new ByteArrayInputStream(data), data.length, "text/plain");

        StoredObject stat = storage.stat(bucket, key);
        assertThat(stat.size()).isEqualTo(data.length);
        assertThat(stat.contentType()).isEqualTo("text/plain");
        assertThat(storage.exists(bucket, key)).isTrue();
        try (InputStream in = storage.get(bucket, key)) {
            assertThat(in.readAllBytes()).isEqualTo(data);
        }

        String url = storage.presignedGetUrl(bucket, key, Duration.ofMinutes(5), "猪场日报.txt");
        HttpResponse<byte[]> resp = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).isEqualTo(data);
        assertThat(resp.headers().firstValue("Content-Disposition").orElse(""))
                .contains("filename*=UTF-8''%E7%8C%AA%E5%9C%BA%E6%97%A5%E6%8A%A5.txt");

        // 私有桶不能匿名读取
        HttpResponse<Void> anon = http.send(HttpRequest.newBuilder(URI.create(ENDPOINT + "/" + bucket + "/" + key))
                .GET().build(), HttpResponse.BodyHandlers.discarding());
        assertThat(anon.statusCode()).isEqualTo(403);
    }

    @Test
    void missingObject() {
        String bucket = storage.privateBucket();
        assertThat(storage.stat(bucket, "it-test/not-exists.bin")).isNull();
        assertThat(storage.exists(bucket, "it-test/not-exists.bin")).isFalse();
        storage.remove(bucket, "it-test/not-exists.bin");
        assertThatThrownBy(() -> storage.get(bucket, "it-test/not-exists.bin"))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40401));
    }

    @Test
    void multipart_initPresignedPartUploadAndComplete() throws Exception {
        String bucket = storage.courseBucket();
        String key = track(bucket, "raw/it-test/" + System.nanoTime() + ".mp4");
        String uploadId = storage.initMultipart(bucket, key, "video/mp4");
        assertThat(uploadId).isNotBlank();

        byte[] part = new byte[256 * 1024];
        for (int i = 0; i < part.length; i++) {
            part[i] = (byte) (i % 251);
        }
        String partUrl = storage.presignedPartUrl(bucket, key, uploadId, 1, Duration.ofMinutes(10));
        HttpResponse<Void> put = http.send(HttpRequest.newBuilder(URI.create(partUrl))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(part)).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
        String etag = put.headers().firstValue("ETag").orElseThrow();

        List<UploadedPart> parts = storage.listParts(bucket, key, uploadId);
        assertThat(parts).hasSize(1);
        assertThat(parts.getFirst().size()).isEqualTo(part.length);

        storage.completeMultipart(bucket, key, uploadId, List.of(new UploadedPart(1, etag)));
        StoredObject stat = storage.stat(bucket, key);
        assertThat(stat.size()).isEqualTo(part.length);
        assertThat(stat.contentType()).isEqualTo("video/mp4");

        // raw/ 不对外
        HttpResponse<Void> anon = http.send(HttpRequest.newBuilder(URI.create(ENDPOINT + "/" + bucket + "/" + key))
                .GET().build(), HttpResponse.BodyHandlers.discarding());
        assertThat(anon.statusCode()).isEqualTo(403);
    }

    @Test
    void multipart_abort() {
        String bucket = storage.courseBucket();
        String key = "raw/it-test/abort-" + System.nanoTime() + ".mp4";
        String uploadId = storage.initMultipart(bucket, key, "video/mp4");
        storage.abortMultipart(bucket, key, uploadId);
        // 重复取消不报错
        storage.abortMultipart(bucket, key, uploadId);
    }

    @Test
    void courseBucket_hlsPrefixAnonymousReadable() throws Exception {
        String bucket = storage.courseBucket();
        long fileId = 900000000L + (System.nanoTime() % 1000000);
        String key = track(bucket, storage.hlsObjectPrefix(fileId) + "720p/index.m3u8");
        byte[] data = "#EXTM3U\n".getBytes(StandardCharsets.UTF_8);
        storage.put(bucket, key, new ByteArrayInputStream(data), data.length, "application/vnd.apple.mpegurl");

        HttpResponse<byte[]> resp = http.send(HttpRequest.newBuilder(URI.create(storage.hlsUrl(fileId, "720p")))
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).isEqualTo(data);
    }
}
