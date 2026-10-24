package com.zhimu.server.admin.system.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-S17 冻结养殖场：原因 1–200 字，version 必填，可选短信通知所有者。 */
@Data
public class FarmFreezeRequest {

    @Size(max = 200, message = "冻结原因不能超过 200 字")
    private String reason;

    private Boolean smsOwner = false;

    @NotNull(message = "version 不能为空")
    private Integer version;
}
