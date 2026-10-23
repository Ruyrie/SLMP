package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * AI 引用来源（t_ai_citation）：回答中 [^n] 角标对应的知识条目快照。
 */
@Getter
@Setter
@ToString
@TableName("t_ai_citation")
public class AiCitation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private Long sessionId;
    private Integer seqNo;
    private Long knowledgeId;
    private Integer knowledgeVersionNo;
    private String paraKey;
    private String sourceType;
    private String iconType;
    private String titleSnapshot;
    private String categoryNameSnapshot;
    private String quoteText;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
