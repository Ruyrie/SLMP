package com.zhimu.server.admin.system.request;

import jakarta.validation.constraints.NotBlank;

/** ADM-S04 修改密码（含首次登录改密）；密码强度与两次一致在 Service 校验。 */
public record AdminPasswordRequest(
        @NotBlank(message = "请输入原密码") String oldPassword,
        @NotBlank(message = "请输入新密码") String newPassword,
        @NotBlank(message = "请再次输入新密码") String confirmPassword) {
}
