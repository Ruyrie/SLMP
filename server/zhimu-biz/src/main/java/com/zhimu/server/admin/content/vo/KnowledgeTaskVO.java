package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 知识补充任务（ADM-C54–C56）。 */
@Data
public class KnowledgeTaskVO {

    private Long id;
    private Integer version;
    private String title;
    private String sourceType;
    private String sourceTypeText;
    private Long sourceMessageId;
    private String sourceQuestion;
    private String mode;
    private String modeText;
    private Long targetKnowledgeId;
    private String targetTitle;
    private Long categoryId;
    private String categoryName;
    private Integer similarCount;
    private Long assigneeId;
    private String assigneeName;
    private LocalDate dueDate;
    private Boolean isOverdue;
    private String points;
    private String status;
    private String statusText;
    private Long resultKnowledgeId;
    private Integer resultVersionNo;
    private LocalDateTime completedAt;
    private String closeReason;
    private String createdByName;
    private LocalDateTime createdAt;
}
