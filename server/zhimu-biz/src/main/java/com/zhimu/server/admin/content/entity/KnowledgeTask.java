package com.zhimu.server.admin.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 知识补充任务（t_ai_knowledge_task）。WA-C04 补充任务标签页与 WA-C05 转任务共用。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_ai_knowledge_task")
public class KnowledgeTask extends AuditEntity {

    private String title;
    private String sourceType;
    private Long sourceMessageId;
    private String mode;
    private Long targetKnowledgeId;
    private Long categoryId;
    private Long assigneeId;
    private LocalDate dueDate;
    private String points;
    private Integer similarCount;
    private String status;
    private Long resultKnowledgeId;
    private Integer resultVersionNo;
    private LocalDateTime completedAt;
    private String closeReason;
    private Long closedBy;
    private LocalDateTime closedAt;

    @Version
    private Integer version;
}
