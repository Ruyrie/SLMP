package com.zhimu.server.biz.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 修改申请（t_change_request，05 号 5.4 节）。全部模块共用，公共能力统一读写。
 * 没有 deleted；pending_flag 待审核时为 1，处理后置 NULL（唯一索引 (biz_type, biz_id, pending_flag)）。
 * 该表跨养殖场查询（U03、U04、管理端队列、系统撤销）一律使用显式 SQL / TenantSkip，不依赖多租户条件。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_change_request")
@FarmScoped
public class ChangeRequest extends AuditEntity {

    private String requestNo;
    private Long farmId;
    private String bizType;
    private Long bizId;
    private String sourceModule;
    private Long batchId;
    private Long penId;
    private String recordLocator;
    private String changeType;
    private String fieldCode;
    private String fieldName;
    private String valueUnit;
    private String beforeDisplay;
    private String afterDisplay;
    /** JSON 文本：{record_card, fields, compare_keys, before, after} */
    private String beforeJson;
    /** JSON 文本：同一 schema，after 为主修改项的新值 */
    private String afterJson;
    private String recordVersion;
    private String reason;
    private Integer impactStock;
    private Integer impactCost;
    private Integer impactInventory;
    private Integer impactSellableDate;
    private Integer impactIncome;
    private Integer impactStats;
    private String recalcTags;
    private String status;
    private Integer pendingFlag;
    private Long submittedBy;
    private String submitterRole;
    private LocalDateTime submittedAt;
    private String submitChannel;
    private Long resubmitOfId;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private String rejectReason;
    private String impactJson;
    private String recalcSummary;
    private Integer recalcCount;
    private String cancelReason;

    @Version
    private Integer version;
}
