package com.zhimu.server.admin.audit.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-R12 通过修改申请：审核意见 1–200 字，version 必填。 */
@Data
public class ChangeRequestApproveRequest {

    @Size(max = 200, message = "审核意见不能超过 200 字")
    private String reviewComment;

    @NotNull(message = "version 不能为空")
    private Integer version;
}
