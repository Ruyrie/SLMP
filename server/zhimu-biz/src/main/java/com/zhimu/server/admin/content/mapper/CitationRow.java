package com.zhimu.server.admin.content.mapper;

import lombok.Data;

/** AI 引用来源行。 */
@Data
public class CitationRow {
    private Long messageId;
    private Integer seqNo;
    private Long knowledgeId;
    private Integer knowledgeVersionNo;
    private String paraKey;
    private String sourceType;
    private String iconType;
    private String titleSnapshot;
    private String categoryNameSnapshot;
}
