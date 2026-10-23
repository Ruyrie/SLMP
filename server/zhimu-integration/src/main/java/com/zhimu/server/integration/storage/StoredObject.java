package com.zhimu.server.integration.storage;

import java.time.ZonedDateTime;

/**
 * 对象元信息。
 */
public record StoredObject(String bucket, String objectKey, long size, String contentType, String etag,
                           ZonedDateTime lastModified) {
}
