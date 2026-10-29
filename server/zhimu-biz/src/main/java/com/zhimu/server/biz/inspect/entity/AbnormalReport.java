package com.zhimu.server.biz.inspect.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
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

/** 异常上报（t_abnormal_report）：诊断主记录。 */
@Getter
@Setter
@FarmScoped
@TableName("t_abnormal_report")
public class AbnormalReport extends AuditEntity {

    private Long farmId;
    private Long penId;
    private Long batchId;
    private Long inspectionId;
    /** REPORT_SOURCE：INSPECTION / DIRECT */
    private String source;
    private String clientRequestId;
    private Long reporterId;
    private LocalDateTime reportedAt;
    private LocalDate reportDate;
    private Integer sickCount;
    private Integer stockSnapshot;
    private BigDecimal sickRate;
    private String description;
    private Integer sameDaySeq;
    /** REPORT_STATUS：PENDING / DIAGNOSED / HANDLED / CLOSED */
    private String status;
    /** AI_STATE：DIAGNOSING / UNAVAILABLE / FAILED / DONE */
    private String aiState;
    private Boolean autoRetryUsed;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime unavailableSince;
    private Long currentDiagnosisId;
    /** URGENCY：NORMAL / ATTENTION / URGENT */
    private String urgency;
    private Boolean isMajorEpidemic;
    private String topCauseName;
    private String notifiedNames;
    private LocalDateTime diagnosedAt;
    private LocalDateTime handledAt;
    private LocalDateTime closedAt;

    @Version
    private Integer version;

    @TableLogic
    private Boolean deleted;

    private Long updatedBy;
}
