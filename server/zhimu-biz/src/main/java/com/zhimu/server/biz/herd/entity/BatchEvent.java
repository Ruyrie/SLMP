package com.zhimu.server.biz.herd.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 存栏变动流水（t_batch_event）：入栏 / 转栏 / 死淘 / 出栏。 */
@Getter
@Setter
@FarmScoped
@TableName("t_batch_event")
public class BatchEvent extends AuditEntity {

    private Long farmId;
    private Long batchId;
    /** BATCH_EVENT_TYPE：ENTRY / TRANSFER / DEATH / SALE */
    private String eventType;
    private LocalDate eventDate;
    private Long penId;
    private Long toPenId;
    private Integer quantity;
    /** DEATH_REASON：SICK / CULL / ACCIDENT / OTHER */
    private String reasonCode;
    private String remark;
    private Long executorUserId;
    private String sourceBizType;
    private Long sourceBizId;
    private Boolean capacityConfirmed;
    private Integer overCapacityCount;
    /** BATCH_EVENT_STATUS：EFFECTIVE / VOIDED */
    private String status;
    private String clientRequestId;

    @Version
    private Integer version;

    @TableLogic
    private Boolean deleted;

    private Long updatedBy;
}
