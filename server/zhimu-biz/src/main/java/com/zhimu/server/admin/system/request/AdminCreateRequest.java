package com.zhimu.server.admin.system.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** ADM-S22 新增管理员；reason 为空按 40004 提示“请填写新增原因”。 */
public record AdminCreateRequest(
        @NotBlank(message = "请输入姓名")
        @Size(max = 20, message = "姓名不能超过 20 个字")
        String name,

        @NotBlank(message = "请输入管理员账号")
        @Pattern(regexp = "[A-Za-z0-9]{4,20}", message = "账号需为 4–20 位字母和数字")
        String username,

        @NotBlank(message = "请输入手机号")
        @Pattern(regexp = "1\\d{10}", message = "请输入 11 位手机号")
        String phone,

        @NotNull(message = "请选择角色")
        Long roleId,

        String reason) {
}
