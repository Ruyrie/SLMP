package com.zhimu.server.admin.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 知识版本（t_ai_knowledge_version）：已发布版本永久保留，每个条目最多一条草稿（draft_flag = 1）。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_ai_knowledge_version")
public class KnowledgeVersion extends AuditEntity {

    private Long knowledgeId;
    private Integer versionNo;
    private String status;
    private Integer draftFlag;
    private String title;
    private Long categoryId;
    private String displayType;
    private Integer isMajorEpidemic;
    private String applicableCategoryIds;
    private String keywords;
    private String content;
    private String contentText;
    private String changeNote;
    private Integer rollbackFromNo;
    private Long sourceTaskId;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Long updatedBy;
}
