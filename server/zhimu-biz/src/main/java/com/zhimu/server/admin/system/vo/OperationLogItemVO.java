package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** ADM-S30 操作日志行：operator_* 与 target_name 均为写入时的快照（手机号已打码）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogItemVO {
    private Long logId;
    private LocalDateTime createdAt;
    private String operatorName;
    private String operatorRole;
    private String operatorRoleText;
    private String opType;
    private String opTypeText;
    private String opAction;
    private String opActionText;
    private String result;
    private String targetName;
    private String reason;
}
