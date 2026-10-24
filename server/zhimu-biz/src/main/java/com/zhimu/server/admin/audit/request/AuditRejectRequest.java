package com.zhimu.server.admin.audit.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-R09/R13 驳回资料变更或修改申请：原因 1–200 字，version 必填。 */
@Data
public class AuditRejectRequest {

    @Size(max = 200, message = "驳回原因不能超过 200 字")
    private String rejectReason;

    @NotNull(message = "version 不能为空")
    private Integer version;
}
