package com.zhimu.server.biz.inspect.entity;

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

/** 巡检记录（t_inspection）：每栏舍每天一行，含未巡、漏巡占位行。 */
@Getter
@Setter
@FarmScoped
@TableName("t_inspection")
public class Inspection extends AuditEntity {

    private Long farmId;
    private Long penId;
    private Long batchId;
    private LocalDate inspectDate;
    /** INSPECT_STATUS：PENDING / DONE / MISSED / CANCELLED */
    private String inspectStatus;
    private Long templateId;
    private Integer templateVersion;
    private Integer stockSnapshot;
    private Long registrantId;
    private Long executorId;
    private LocalDateTime registeredAt;
    private String clientRequestId;
    private LocalTime deadlineTime;
    private Boolean isOverdue;
    private Boolean isBackfill;
    /** LOCATION_STATUS：ON_SITE / OFF_SITE / NO_LOCATION */
    private String locationStatus;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer locationAccuracyM;
    private Integer distanceM;
    private Boolean isAbnormal;
    /** ABNORMAL_SOURCE：CHECK / MANUAL */
    private String abnormalSource;
    private String abnormalSummary;
    private Boolean isManualMarked;
    private String manualReason;
    private Long manualMarkedBy;
    private LocalDateTime manualMarkedAt;
    private String remark;
    private Long reportId;

    @Version
    private Integer version;

    @TableLogic
    private Boolean deleted;

    private Long updatedBy;
}
