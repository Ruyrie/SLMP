package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 知识条目线上版本段落（t_ai_knowledge_paragraph）。发布 / 回退时整体重建，AI 只检索 is_online = 1 的段落。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_ai_knowledge_paragraph")
public class AiKnowledgeParagraph extends BaseEntity {

    private Long knowledgeId;
    private String paraKey;
    private Integer paraNo;
    private Integer versionNo;
    private String paraType;
    private String contentText;
    private String contentHtml;
    private String imageUrl;
    private Long imageFileId;
    private Long courseId;
    private Integer citeCount30d;
    private Integer isOnline;
}
