package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 课程原片信息。 */
@Data
public class VideoInfoVO {

    private Long fileId;
    private String originalName;
    private Long fileSize;
    private Integer durationSec;
    private Integer width;
    private Integer height;
    private LocalDateTime uploadedAt;
    private String uploadStatus;
    private String transcodeStatus;
    private String transcodeError;
    private Long coverFileId;
}
