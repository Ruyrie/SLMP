package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 一轮 AI 问答（t_ai_message）。一行为提问、回答与评价。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_ai_message")
@FarmScoped
public class AiMessage extends AuditEntity {

    private Long sessionId;
    private Long farmId;
    private Long userId;
    private Integer roundNo;
    private String clientMsgId;
    private String clientType;
    private String questionText;
    /** 附图 file_id 数组的 JSON 文本 */
    private String imageFileIds;
    private Long batchId;
    private String batchNo;
    private Long contextCourseId;
    private String contextCourseTitle;
    private Long contextKnowledgeId;
    private String status;
    private String answerType;
    private String answerText;
    private Integer errorCode;
    private String errorMessage;
    private Integer retryCount;
    private String rating;
    private LocalDateTime ratedAt;
    private String handleStatus;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer latencyMs;
    private LocalDateTime askedAt;
    private LocalDateTime startedAt;
    private LocalDateTime answeredAt;
}
