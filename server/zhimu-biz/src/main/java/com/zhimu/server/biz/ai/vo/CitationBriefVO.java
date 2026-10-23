package com.zhimu.server.biz.ai.vo;

import lombok.Data;

/**
 * 回答引用的知识条目快照（seq_no 与正文 [^n] 对应）。
 */
@Data
public class CitationBriefVO {

    private Long id;
    private int seqNo;
    private Long knowledgeId;
    private String title;
    private String categoryName;
    /** PLATFORM / DISEASE / DRUG_CATALOG */
    private String sourceType;
    /** STANDARD / ARTICLE */
    private String iconType;
}
