package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 导出结果（ADM-C35）：前端轮询 ADM-S37 后经 ADM-S39 取下载地址。 */
@Data
public class ContentExportVO {

    private Long fileId;
    private String fileName;
    private String status;
    private Integer rowCount;
    private String url;
    private LocalDateTime expireAt;
}
