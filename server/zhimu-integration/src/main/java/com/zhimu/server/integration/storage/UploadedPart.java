package com.zhimu.server.integration.storage;

/**
 * 分片上传的一个分片。
 *
 * @param partNumber 分片序号，从 1 开始
 * @param etag       上传该分片时响应头中的 ETag
 * @param size       分片大小（listParts 返回；合并时不需要）
 */
public record UploadedPart(int partNumber, String etag, Long size) {

    public UploadedPart(int partNumber, String etag) {
        this(partNumber, etag, null);
    }
}
