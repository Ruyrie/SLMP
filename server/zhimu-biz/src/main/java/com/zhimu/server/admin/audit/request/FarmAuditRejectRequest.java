package com.zhimu.server.admin.audit.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** ADM-R05 驳回注册：原因 1–200 字；reshoot_file_ids 为本次提交的资质图（最多 9 张）。 */
@Data
public class FarmAuditRejectRequest {

    @Size(max = 200, message = "驳回原因不能超过 200 字")
    private String rejectReason;

    @Size(max = 9, message = "需重拍的资质图最多 9 张")
    private List<Long> reshootFileIds = new ArrayList<>();

    @NotNull(message = "version 不能为空")
    private Integer version;
}
