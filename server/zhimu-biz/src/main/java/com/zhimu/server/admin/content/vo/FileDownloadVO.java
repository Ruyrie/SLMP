package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 文件下载地址（ADM-S39）。 */
@Data
public class FileDownloadVO {

    private Long fileId;
    private String fileName;
    private String url;
    private LocalDateTime expireAt;
}
