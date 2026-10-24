package com.zhimu.server.admin.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** AI 问答抽查与处理记录（t_ai_qa_review）：不存处理状态，状态在 t_ai_message.handle_status。 */
@Getter
@Setter
@TableName("t_ai_qa_review")
public class AiQaReview extends BaseEntity {

    private Long messageId;
    private Long sessionId;
    private Boolean isSpotCheck;
    private String spotCheckRule;
    private String spotCheckTitle;
    private String spotCheckContent;
    private LocalDateTime spotCheckedAt;
    private Long handledBy;
    private LocalDateTime handledAt;
    private String lastAction;
    private Long knowledgeTaskId;
}
