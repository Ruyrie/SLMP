package com.zhimu.server.admin.audit.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** R18 重发结果短信：原因选填，最多 200 字。 */
@Data
public class SmsResendRequest {

    @Size(max = 200, message = "原因不能超过 200 字")
    private String reason;
}
