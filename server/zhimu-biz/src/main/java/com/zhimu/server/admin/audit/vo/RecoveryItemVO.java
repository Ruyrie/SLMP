package com.zhimu.server.admin.audit.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/** ADM-R14 账号找回人工核实队列行（只含恢复码已核对正确的申请）。 */
@Data
public class RecoveryItemVO {

    private Long id;
    private Long userId;
    private String userName;
    /** 查询时为完整号码，Service 输出前打码 */
    private String oldPhone;
    private String newPhone;
    private Boolean newPhoneVerified;
    private Integer codeAttemptNo;
    private Integer codeFailCount;
    private String description;
    private String submitChannel;
    @JsonIgnore
    private String proxyAdminName;
    private String submitChannelText;
    private LocalDateTime submittedAt;
    private Integer waitMinutes;
    private String waitText;
    private Boolean overdue;
    private String status;
    private String statusText;
    private String handledByName;
    private LocalDateTime handledAt;
    private Integer version;
}
