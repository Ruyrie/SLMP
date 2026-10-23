package com.zhimu.server.biz.inspect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** AI 诊断（t_ai_diagnosis）：每次调用一行。 */
@Getter
@Setter
@FarmScoped
@TableName("t_ai_diagnosis")
public class AiDiagnosis extends AuditEntity {

    private Long farmId;
    private Long reportId;
    private Integer attemptNo;
    /** AI_TRIGGER：SUBMIT / AUTO_RETRY / MANUAL_RETRY */
    private String triggerType;
    /** AI_ATTEMPT_STATUS：RUNNING / SUCCESS / TIMEOUT / FAILED */
    private String status;
    private Long requestedBy;
    private LocalDateTime requestedAt;
    private LocalDateTime finishedAt;
    private Integer latencyMs;
    /** AI_FAIL_REASON：TIMEOUT / SERVICE_UNAVAILABLE / INVALID_OUTPUT */
    private String failReason;
    private String modelName;
    private String promptVersion;
    private String aiUrgency;
    private String urgency;
    private String urgencyBasis;
    private String conclusion;
    private Boolean isMajorEpidemic;
    private Boolean suggestVet;
    private String inputSnapshot;
    private String outputSnapshot;
}
