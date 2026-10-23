package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * AI 回答的更正说明（t_ai_correction）：只读管理端已发布（PUBLISHED）的记录。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_ai_correction")
public class AiCorrection extends AuditEntity {

    private Long messageId;
    private String content;
    private String status;
    private LocalDateTime publishedAt;
}
