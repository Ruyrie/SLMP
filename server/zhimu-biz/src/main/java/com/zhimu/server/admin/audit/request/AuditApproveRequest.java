package com.zhimu.server.admin.audit.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-R04/R08 通过审核：审核意见 1–200 字，version 与详情一致。 */
@Data
public class AuditApproveRequest {

    @Size(max = 200, message = "审核意见不能超过 200 字")
    private String auditOpinion;

    @NotNull(message = "version 不能为空")
    private Integer version;
}
