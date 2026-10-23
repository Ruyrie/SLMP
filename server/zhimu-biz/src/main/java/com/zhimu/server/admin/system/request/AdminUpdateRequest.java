package com.zhimu.server.admin.system.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** ADM-S23 编辑管理员：username 不可改；本人只能改姓名和手机号。 */
public record AdminUpdateRequest(
        @NotBlank(message = "请输入姓名")
        @Size(max = 20, message = "姓名不能超过 20 个字")
        String name,

        @NotBlank(message = "请输入手机号")
        @Pattern(regexp = "1\\d{10}", message = "请输入 11 位手机号")
        String phone,

        @NotNull(message = "请选择角色")
        Long roleId,

        String reason,

        @NotNull(message = "内容已更新，请刷新后再操作")
        Integer version) {
}
