package com.zhimu.server.biz.farm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 注册审核记录（t_farm_audit，每次提交一行） */
@Getter
@Setter
@FarmScoped
@TableName("t_farm_audit")
public class FarmAudit extends AuditEntity {

    private Long farmId;
    private Integer submitNo;
    /** AUDIT_STATUS：PENDING / APPROVED / REJECTED / CANCELED */
    private String status;
    private Boolean pendingFlag;
    private LocalDateTime submittedAt;
    private String snapshotJson;
    private String changeSummary;
    private String reshootFileIds;
    /** 审核通过意见；F06 的 APPROVE 记录 desc 使用它 */
    private String auditOpinion;
    private String rejectReason;
    private Long auditedBy;
    private LocalDateTime auditedAt;
    private String cancelReason;
    private String requestId;

    @Version
    private Integer version;
}
