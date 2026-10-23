package com.zhimu.server.admin.system.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** ADM-S29 保存角色权限：权限码必须存在；含 HANDLE 必须含同页 VIEW；不得含超管专属权限点。 */
public record RolePermissionSaveRequest(
        @NotNull(message = "请选择权限") List<String> permCodes,
        String reason,
        @NotNull(message = "内容已更新，请刷新后再操作") Integer version) {
}
