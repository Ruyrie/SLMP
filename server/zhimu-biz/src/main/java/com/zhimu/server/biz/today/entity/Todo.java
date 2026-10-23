package com.zhimu.server.biz.today.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 待办（t_todo）：来源模块生成，模块03 展示与处理。 */
@Getter
@Setter
@FarmScoped
@TableName("t_todo")
public class Todo extends AuditEntity {

    private Long farmId;
    /** TODO_TYPE */
    private String todoType;
    /** TODO_CATEGORY */
    private String category;
    private Long penId;
    private Long batchId;
    /** 栏舍名称快照（模块04 未接入时由来源/种子数据写入） */
    private String penName;
    /** 批次号快照 */
    private String batchNo;
    /** 详情“关联批次”快照：可与列表项的 batch_id 不同（例如消毒任务关联批次） */
    private Long relatedBatchId;
    private String relatedBatchNo;
    private String relatedBatchDesc;
    private String relatedBatchTag;
    /** BIZ_TYPE */
    private String bizType;
    private Long bizId;
    private String dedupKey;
    private String title;
    private String contentText;
    private String statusNote;
    private String detailLabel;
    private String detailValue;
    private BigDecimal amount;
    /** RECEIVER_SCOPE */
    private String receiverScope;
    private Long assigneeId;
    private String assigneeName;
    private LocalDate dueDate;
    /** TODO_TIME_TYPE */
    private String timeType;
    private LocalTime dueTime;
    private LocalDateTime overdueAt;
    private LocalDateTime sortTime;
    /** TODO_STATUS：PENDING / DONE / IGNORED */
    private String status;
    private Boolean overdueDone;
    private LocalDateTime completedAt;
    private Long completedBy;
    /** COMPLETE_WAY */
    private String completeWay;
    private String completeRecordType;
    private Long completeRecordId;
    private LocalDateTime ignoredAt;
    private Long ignoredBy;
    private String ignoreReason;
    /** IGNORE_WAY */
    private String ignoreWay;
    private Integer remindCount;
    private LocalDateTime lastRemindAt;

    @Version
    private Integer version;

    @TableLogic
    private Boolean deleted;
}
