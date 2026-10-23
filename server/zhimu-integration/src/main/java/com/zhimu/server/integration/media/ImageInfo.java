package com.zhimu.server.integration.media;

/**
 * 图片信息。
 *
 * @param format   JPEG 或 PNG
 * @param mimeType image/jpeg 或 image/png
 */
public record ImageInfo(int width, int height, String format, String mimeType) {
}
