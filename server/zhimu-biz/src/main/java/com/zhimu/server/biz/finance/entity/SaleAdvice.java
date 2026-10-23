package com.zhimu.server.biz.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** AI 出栏建议（t_sale_advice）。 */
@Getter
@Setter
@FarmScoped
@TableName("t_sale_advice")
public class SaleAdvice extends AuditEntity {

    private Long farmId;
    private Long batchId;
    private String status;
    private String genMode;
    private String conclusion;
    private LocalDate windowStart;
    private LocalDate windowEnd;
    private LocalDate sellableDate;
    private Boolean inWithdrawal;
    private Integer withdrawalDaysLeft;
    private String reasonWithdrawal;
    private String reasonAge;
    private String reasonWeight;
    private String reasonPrice;
    private Boolean basisInsufficient;
    private String basisNote;
    private String inputSnapshotJson;
    private String aiModel;
    private String promptVersion;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime generatedAt;
}
