package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * 知识条目相关条目。
 */
@Data
public class RelatedKnowledgeVO {

    private Long id;
    private String title;
    private String categoryName;
    private String sourceType;
    private String sourceTypeText;
    private int versionNo;
    /** STANDARD / ARTICLE */
    private String iconType;
}
