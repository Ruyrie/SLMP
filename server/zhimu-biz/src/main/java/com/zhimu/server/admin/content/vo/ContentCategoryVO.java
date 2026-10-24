package com.zhimu.server.admin.content.vo;

import lombok.Data;

/** 分类（ADM-C40–C43）。 */
@Data
public class ContentCategoryVO {

    private Long id;
    private String name;
    private Integer sortNo;
    private String status;
    private String statusText;
    private Integer courseCount;
    private Integer knowledgeCount;
    private Integer version;
}
