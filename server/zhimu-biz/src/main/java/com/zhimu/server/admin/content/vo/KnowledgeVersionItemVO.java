package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 版本列表项（ADM-C49）。 */
@Data
public class KnowledgeVersionItemVO {

    private Integer versionNo;
    private Boolean isOnline;
    private LocalDateTime publishedAt;
    private String publishedByName;
    private String changeNote;
    private Integer rollbackFromNo;
}
