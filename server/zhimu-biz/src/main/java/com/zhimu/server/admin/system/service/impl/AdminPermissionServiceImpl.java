package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.api.AdminPermissionService;
import com.zhimu.server.admin.system.support.AdminPermissionCache;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 权限码判定：优先使用拦截器写入 AdminContext 的权限集合（超管为全部）；
 * 没有上下文时按管理员 ID 查角色权限缓存。权限变更下一次请求即生效，无需重新登录。
 */
@Service
@RequiredArgsConstructor
public class AdminPermissionServiceImpl implements AdminPermissionService {

    private final AdminPermissionCache permissionCache;

    @Override
    public boolean has(String permCode) {
        AdminContext.LoginAdmin admin = AdminContext.get();
        return admin != null && admin.hasPermission(permCode);
    }

    @Override
    public void require(String permCode) {
        if (!has(permCode)) {
            throw new BizException(ErrorCode.ADMIN_PERMISSION_DENIED);
        }
    }

    @Override
    public Set<String> permissionsOf(Long adminId) {
        AdminPermissionCache.RolePermissions rolePermissions = permissionCache.permissionsOfAdmin(adminId);
        return rolePermissions == null ? new LinkedHashSet<>() : rolePermissions.codes();
    }

    @Override
    public Set<String> permissionCodes() {
        return new LinkedHashSet<>(permissionCache.allPermissionCodes());
    }

    @Override
    public void evictRole(Long roleId) {
        permissionCache.evict(roleId);
    }
}
