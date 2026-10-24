package com.zhimu.server.admin.system.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-S12 停用账号：原因 1–200 字，默认短信通知本人。 */
@Data
public class UserStatusRequest {

    @Size(max = 200, message = "原因不能超过 200 字")
    private String reason;

    private Boolean notifySms = true;

    @NotNull(message = "version 不能为空")
    private Integer version;
}
