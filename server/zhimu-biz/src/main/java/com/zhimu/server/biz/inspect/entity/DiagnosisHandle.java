package com.zhimu.server.biz.inspect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 处理结果（t_diagnosis_handle）：每条上报一行。 */
@Getter
@Setter
@FarmScoped
@TableName("t_diagnosis_handle")
public class DiagnosisHandle extends AuditEntity {

    private Long farmId;
    private Long reportId;
    private String diagnostician;
    private String treatment;
    private Boolean usedDrug;
    private Integer curedCount;
    private Integer culledCount;
    /** AI_MATCH：MATCH / NOT_MATCH / NONE */
    private String aiMatch;
    private Long handledBy;
    private LocalDateTime handledAt;
    private Long updatedBy;

    @Version
    private Integer version;
}
