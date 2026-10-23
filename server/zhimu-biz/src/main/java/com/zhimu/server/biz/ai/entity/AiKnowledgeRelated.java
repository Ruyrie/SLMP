package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 知识条目相关条目（t_ai_knowledge_related）。
 */
@Getter
@Setter
@ToString
@TableName("t_ai_knowledge_related")
public class AiKnowledgeRelated {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long knowledgeId;
    private Long relatedId;
    private Integer sortNo;
}
