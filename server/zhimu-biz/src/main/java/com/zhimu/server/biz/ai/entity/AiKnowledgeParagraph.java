package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 知识条目段落（t_ai_knowledge_paragraph）。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_ai_knowledge_paragraph")
public class AiKnowledgeParagraph extends BaseEntity {

    private Long knowledgeId;
    private String paraKey;
    private Integer paraNo;
    private String paraType;
    private String contentText;
    private String contentHtml;
    private String imageUrl;
    private Long courseId;
}
