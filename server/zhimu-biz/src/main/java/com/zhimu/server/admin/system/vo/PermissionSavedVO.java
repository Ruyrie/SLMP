package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** ADM-S29 保存角色权限结果：affected_admin_count 为该角色管理员人数（立即生效）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionSavedVO {
    private Long affectedAdminCount;
}
