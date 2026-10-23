package com.zhimu.server.admin.system.request;

import jakarta.validation.constraints.NotNull;

/** ADM-S25 / S26 停用、启用管理员；reason 为空按 40004 提示。 */
public record AdminStatusRequest(
        String reason,
        @NotNull(message = "内容已更新，请刷新后再操作") Integer version) {
}
