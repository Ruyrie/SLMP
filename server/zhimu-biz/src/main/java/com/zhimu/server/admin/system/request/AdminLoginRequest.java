package com.zhimu.server.admin.system.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** ADM-S02 管理员登录：账号 + 密码 + 图形验证码。 */
public record AdminLoginRequest(
        @NotBlank(message = "请输入管理员账号")
        @Size(max = 32, message = "请输入管理员账号")
        String username,

        @NotBlank(message = "请输入密码")
        String password,

        @NotBlank(message = "请输入验证码")
        String captchaId,

        @NotBlank(message = "请输入验证码")
        @Size(min = 4, max = 4, message = "请输入验证码")
        String captchaCode) {
}
