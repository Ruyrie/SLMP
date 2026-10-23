package com.zhimu.server.integration.storage;

import com.zhimu.server.common.enums.FileScene;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 对象存储（MinIO，04 号 8.1 节）。数据库只存桶与对象路径，返回给客户端的地址由这里临时生成。
 * 存储服务不可用时抛出 50301；对象不存在时 get 抛出 40401。
 */
public interface ObjectStorage {

    void put(String bucket, String objectKey, InputStream in, long size, String contentType);

    void putFile(String bucket, String objectKey, Path file, String contentType);

    /** 调用方负责关闭 */
    InputStream get(String bucket, String objectKey);

    /** 对象不存在时不报错 */
    void remove(String bucket, String objectKey);

    /** 对象不存在返回 null */
    StoredObject stat(String bucket, String objectKey);

    boolean exists(String bucket, String objectKey);

    /**
     * 临时下载地址（按 public-endpoint 签名，不访问网络）。
     *
     * @param ttl              有效期，最长 7 天
     * @param downloadFileName 不为空时浏览器按该文件名下载（response-content-disposition，中文按 RFC 5987 编码）
     */
    String presignedGetUrl(String bucket, String objectKey, Duration ttl, String downloadFileName);

    /** 按配置 presign-ttl（2 小时）生成临时下载地址 */
    String presignedGetUrl(String bucket, String objectKey);

    // ===== 分片直传（管理端课程视频） =====

    /** 创建分片上传任务，返回 uploadId */
    String initMultipart(String bucket, String objectKey, String contentType);

    /** 某个分片的预签名 PUT 地址（按 public-endpoint 签名），浏览器上传后从响应头取 ETag */
    String presignedPartUrl(String bucket, String objectKey, String uploadId, int partNumber, Duration ttl);

    /** 合并分片（按 partNumber 排序后提交） */
    void completeMultipart(String bucket, String objectKey, String uploadId, List<UploadedPart> parts);

    void abortMultipart(String bucket, String objectKey, String uploadId);

    /** 已上传的分片（用于断点续传与合并前核对） */
    List<UploadedPart> listParts(String bucket, String objectKey, String uploadId);

    // ===== 路径与地址 =====

    /** {场景小写}/{yyyyMMdd}/{uuid}.{ext}（北京时间日期）；ext 为空时不带扩展名 */
    String buildObjectKey(FileScene scene, String ext);

    /** 场景对应的存储桶名 */
    String bucketOf(FileScene scene);

    String privateBucket();

    String courseBucket();

    /** 课程 HLS 播放地址：{hls.base-url}/{fileId}/{variant}/index.m3u8 */
    String hlsUrl(long fileId, String variant);

    /** 课程桶中某个文件 HLS 输出的对象前缀：hls/{fileId}/ */
    String hlsObjectPrefix(long fileId);
}
