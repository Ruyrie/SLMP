package com.zhimu.server.admin.audit.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** R20 客服代登记：账号当前手机号、恢复码、情况说明；新手机号选填，默认与原手机号相同。 */
@Data
public class RecoveryProxyRequest {

    @NotBlank(message = "请输入手机号")
    private String phone;

    private String newPhone;

    @NotBlank(message = "请输入恢复码")
    private String recoveryCode;

    @NotBlank(message = "请填写情况说明")
    @Size(max = 200, message = "情况说明不能超过 200 字")
    private String description;
}
