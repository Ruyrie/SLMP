package com.zhimu.server.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文件上传结果与业务记录中的照片、视频。url、thumb_url 为临时签名地址（有效 2 小时），客户端以 file_id 作缓存键。
 * media_type：IMAGE / VIDEO / AUDIO / DOCUMENT；status：UPLOADING / READY / FAILED。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FileVO {
    private Long fileId;
    private String url;
    private String thumbUrl;
    private String mediaType;
    private String mimeType;
    private Long size;
    private Integer width;
    private Integer height;
    private Integer durationSec;
    private String status;
}