package com.zhimu.server.integration.storage;

import com.google.common.collect.ImmutableMultimap;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListPartsResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Part;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

/**
 * MinIO 实现：内网地址的客户端负责读写；public-endpoint 的客户端只用来签名（显式指定 region，签名时不访问网络）。
 * 分片上传使用 MinioAsyncClient 的分片接口（8.5 中为 public）。
 */
@Slf4j
public class MinioObjectStorage implements ObjectStorage {

    /** MinIO 默认区域；签名客户端必须指定，否则签名前会请求 GetBucketLocation */
    static final String PUBLIC_REGION = "us-east-1";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_EXPIRY_SECONDS = 7 * 24 * 3600;
    private static final Pattern EXT = Pattern.compile("[a-z0-9]{1,10}");
    private static final String MSG_UNAVAILABLE = "文件服务暂时不可用，请稍后重试";
    private static final Set<String> NOT_FOUND_CODES = Set.of("NoSuchKey", "NoSuchBucket", "NoSuchObject");

    private final MinioProperties props;
    private final HlsProperties hls;
    private final MinioClient client;
    private final MinioAsyncClient asyncClient;
    private final MinioClient signer;

    public MinioObjectStorage(MinioProperties props, HlsProperties hls) {
        this.props = props;
        this.hls = hls;
        String ak = props.getAccessKey();
        String sk = props.getSecretKey();
        // 未配置 Key 时按匿名访问创建客户端（不阻止启动，调用时返回 50301）
        boolean hasKey = ak != null && !ak.isBlank() && sk != null && !sk.isBlank();
        if (!hasKey) {
            log.warn("MinIO AccessKey / SecretKey 未配置，对象存储调用将失败");
        }
        MinioClient.Builder cb = MinioClient.builder().endpoint(props.getEndpoint());
        MinioAsyncClient.Builder ab = MinioAsyncClient.builder().endpoint(props.getEndpoint());
        MinioClient.Builder sb = MinioClient.builder().endpoint(props.getPublicEndpoint()).region(PUBLIC_REGION);
        if (hasKey) {
            cb.credentials(ak, sk);
            ab.credentials(ak, sk);
            sb.credentials(ak, sk);
        }
        this.client = cb.build();
        this.client.setTimeout(5_000, 120_000, 120_000);
        this.asyncClient = ab.build();
        this.asyncClient.setTimeout(5_000, 120_000, 120_000);
        this.signer = sb.build();
    }

    /** 确保两个存储桶存在，课程桶开放 hls/ 与 cover/ 前缀的匿名只读；失败只打 WARN */
    public boolean ensureBuckets() {
        try {
            for (String bucket : List.of(props.getPrivateBucket(), props.getCourseBucket())) {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("已创建存储桶 {}", bucket);
                }
            }
            client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(props.getCourseBucket())
                    .config(coursePolicy(props.getCourseBucket())).build());
            log.info("存储桶就绪 private={} course={}（hls/、cover/ 匿名只读） endpoint={}", props.getPrivateBucket(),
                    props.getCourseBucket(), props.getEndpoint());
            return true;
        } catch (Exception e) {
            log.warn("MinIO 暂不可用，存储桶检查跳过 endpoint={}：{}", props.getEndpoint(), describe(e));
            return false;
        }
    }

    static String coursePolicy(String bucket) {
        return "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},"
                + "\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + bucket + "/hls/*\","
                + "\"arn:aws:s3:::" + bucket + "/cover/*\"]}]}";
    }

    @Override
    public void put(String bucket, String objectKey, InputStream in, long size, String contentType) {
        try {
            long partSize = size < 0 ? props.getMultipartPartSize().toBytes() : -1;
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(in, size, partSize)
                    .contentType(contentType(contentType)).build());
        } catch (Exception e) {
            throw failure("上传", bucket, objectKey, e);
        }
    }

    @Override
    public void putFile(String bucket, String objectKey, Path file, String contentType) {
        try {
            client.uploadObject(UploadObjectArgs.builder().bucket(bucket).object(objectKey)
                    .filename(file.toString()).contentType(contentType(contentType)).build());
        } catch (Exception e) {
            throw failure("上传", bucket, objectKey, e);
        }
    }

    @Override
    public InputStream get(String bucket, String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            if (isNotFound(e)) {
                throw new BizException(ErrorCode.NOT_FOUND);
            }
            throw failure("读取", bucket, objectKey, e);
        }
    }

    @Override
    public void remove(String bucket, String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            if (!isNotFound(e)) {
                throw failure("删除", bucket, objectKey, e);
            }
        }
    }

    @Override
    public StoredObject stat(String bucket, String objectKey) {
        try {
            StatObjectResponse r = client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return new StoredObject(bucket, objectKey, r.size(), r.contentType(), r.etag(), r.lastModified());
        } catch (Exception e) {
            if (isNotFound(e)) {
                return null;
            }
            throw failure("查询", bucket, objectKey, e);
        }
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        return stat(bucket, objectKey) != null;
    }

    @Override
    public String presignedGetUrl(String bucket, String objectKey, Duration ttl, String downloadFileName) {
        Map<String, String> query = new LinkedHashMap<>();
        if (downloadFileName != null && !downloadFileName.isBlank()) {
            query.put("response-content-disposition", contentDisposition(downloadFileName));
        }
        return presign(Method.GET, bucket, objectKey, ttl, query);
    }

    @Override
    public String presignedGetUrl(String bucket, String objectKey) {
        return presignedGetUrl(bucket, objectKey, props.getPresignTtl(), null);
    }

    private String presign(Method method, String bucket, String objectKey, Duration ttl, Map<String, String> query) {
        int seconds = (int) Math.max(1, Math.min(MAX_EXPIRY_SECONDS, ttl.toSeconds()));
        try {
            GetPresignedObjectUrlArgs.Builder b = GetPresignedObjectUrlArgs.builder()
                    .method(method).bucket(bucket).object(objectKey).expiry(seconds);
            if (!query.isEmpty()) {
                b.extraQueryParams(query);
            }
            return signer.getPresignedObjectUrl(b.build());
        } catch (Exception e) {
            throw failure("签名", bucket, objectKey, e);
        }
    }

    /** attachment; filename="ASCII 备用名"; filename*=UTF-8''RFC 5987 编码 */
    static String contentDisposition(String fileName) {
        String name = fileName.replace("\r", "").replace("\n", "");
        return "attachment; filename=\"" + asciiFallback(name) + "\"; filename*=UTF-8''" + rfc5987(name);
    }

    private static String asciiFallback(String name) {
        boolean ascii = name.chars().allMatch(c -> c >= 0x20 && c < 0x7F && c != '"' && c != '\\');
        if (ascii) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return EXT.matcher(ext).matches() ? "download." + ext : "download";
    }

    static String rfc5987(String value) {
        StringBuilder sb = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || "!#$&+-.^_`|~".indexOf(c) >= 0) {
                sb.append((char) c);
            } else {
                sb.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0x0F, 16)));
            }
        }
        return sb.toString();
    }

    // ===== 分片上传 =====

    @Override
    public String initMultipart(String bucket, String objectKey, String contentType) {
        try {
            String uploadId = asyncClient.createMultipartUploadAsync(bucket, null, objectKey,
                    ImmutableMultimap.of("Content-Type", contentType(contentType)), null).get().result().uploadId();
            log.info("创建分片上传 bucket={} key={} uploadId={}", bucket, objectKey, uploadId);
            return uploadId;
        } catch (Exception e) {
            throw failure("创建分片上传", bucket, objectKey, e);
        }
    }

    @Override
    public String presignedPartUrl(String bucket, String objectKey, String uploadId, int partNumber, Duration ttl) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("partNumber", String.valueOf(partNumber));
        query.put("uploadId", uploadId);
        return presign(Method.PUT, bucket, objectKey, ttl, query);
    }

    @Override
    public void completeMultipart(String bucket, String objectKey, String uploadId, List<UploadedPart> parts) {
        Part[] sorted = parts.stream().sorted(Comparator.comparingInt(UploadedPart::partNumber))
                .map(p -> new Part(p.partNumber(), p.etag())).toArray(Part[]::new);
        try {
            asyncClient.completeMultipartUploadAsync(bucket, null, objectKey, uploadId, sorted, null, null).get();
            log.info("合并分片完成 bucket={} key={} parts={}", bucket, objectKey, sorted.length);
        } catch (Exception e) {
            throw failure("合并分片", bucket, objectKey, e);
        }
    }

    @Override
    public void abortMultipart(String bucket, String objectKey, String uploadId) {
        try {
            asyncClient.abortMultipartUploadAsync(bucket, null, objectKey, uploadId, null, null).get();
        } catch (Exception e) {
            if (!"NoSuchUpload".equals(errorCode(e))) {
                throw failure("取消分片上传", bucket, objectKey, e);
            }
        }
    }

    @Override
    public List<UploadedPart> listParts(String bucket, String objectKey, String uploadId) {
        List<UploadedPart> list = new ArrayList<>();
        try {
            Integer marker = null;
            while (true) {
                ListPartsResponse resp = asyncClient.listPartsAsync(bucket, null, objectKey, 1000, marker, uploadId,
                        null, null).get();
                for (Part p : resp.result().partList()) {
                    list.add(new UploadedPart(p.partNumber(), p.etag(), p.partSize()));
                }
                if (!resp.result().isTruncated()) {
                    return list;
                }
                marker = resp.result().nextPartNumberMarker();
            }
        } catch (Exception e) {
            throw failure("查询分片", bucket, objectKey, e);
        }
    }

    // ===== 路径与地址 =====

    @Override
    public String buildObjectKey(FileScene scene, String ext) {
        String e = ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT);
        if (e.startsWith(".")) {
            e = e.substring(1);
        }
        String key = scene.pathPrefix() + "/" + BeijingTime.today().format(DAY) + "/"
                + UUID.randomUUID().toString().replace("-", "");
        return EXT.matcher(e).matches() ? key + "." + e : key;
    }

    @Override
    public String bucketOf(FileScene scene) {
        return scene.getBucket() == FileScene.Bucket.COURSE ? props.getCourseBucket() : props.getPrivateBucket();
    }

    @Override
    public String privateBucket() {
        return props.getPrivateBucket();
    }

    @Override
    public String courseBucket() {
        return props.getCourseBucket();
    }

    @Override
    public String hlsUrl(long fileId, String variant) {
        String base = hls.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + fileId + "/" + variant + "/index.m3u8";
    }

    @Override
    public String hlsObjectPrefix(long fileId) {
        return "hls/" + fileId + "/";
    }

    // ===== 错误处理 =====

    private static String contentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    private static Throwable unwrap(Throwable e) {
        Throwable t = e;
        while (t instanceof ExecutionException && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof java.util.concurrent.CompletionException && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static String errorCode(Throwable e) {
        Throwable t = unwrap(e);
        return t instanceof ErrorResponseException ere && ere.errorResponse() != null ? ere.errorResponse().code()
                : null;
    }

    private static boolean isNotFound(Throwable e) {
        String code = errorCode(e);
        return code != null && NOT_FOUND_CODES.contains(code);
    }

    private static String describe(Throwable e) {
        Throwable t = unwrap(e);
        String code = errorCode(t);
        return t.getClass().getSimpleName() + (code != null ? "(" + code + ")" : "")
                + (t.getMessage() != null ? " " + t.getMessage() : "");
    }

    private BizException failure(String op, String bucket, String objectKey, Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        log.warn("对象存储{}失败 bucket={} key={}：{}", op, bucket, objectKey, describe(e));
        return new BizException(ErrorCode.SERVICE_UNAVAILABLE, MSG_UNAVAILABLE);
    }
}
