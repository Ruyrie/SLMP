package com.zhimu.server.admin.content.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-S35 分片上传初始化 / 续传。 */
@Data
public class MultipartInitRequest {

    /** COURSE_VIDEO / KNOWLEDGE_MEDIA */
    @NotBlank(message = "不支持的上传场景")
    private String scene;

    @NotBlank(message = "请选择文件")
    @Size(max = 255, message = "文件名不能超过 255 字")
    private String originalName;

    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    private String mimeType;

    /** 续传时传入已初始化的文件 ID */
    private Long fileId;
}
