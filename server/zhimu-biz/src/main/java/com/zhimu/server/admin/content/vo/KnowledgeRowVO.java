package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 知识条目列表行（ADM-C44）。 */
@Data
public class KnowledgeRowVO {

    private Long id;
    private String title;
    private Boolean hasVideo;
    private String videoTitle;
    private Integer videoDurationSec;
    private Long categoryId;
    private String categoryName;
    private String sourceType;
    private String sourceTypeText;
    private String versionLabel;
    private Boolean hasDraft;
    private String status;
    private String statusText;
    private Boolean isMajorEpidemic;
    private String displayType;
    private Integer citeCount30d;
    private BigDecimal usefulRate30d;
    private LocalDateTime updatedAt;
    private String updatedByName;
    private Boolean isReadonly;
}
