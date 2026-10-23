package com.zhimu.server.admin.system.support;

import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.entity.AdminRole;
import com.zhimu.server.admin.system.enums.AdminStatus;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.AdminRoleMapper;
import com.zhimu.server.framework.security.spi.AdminAuthState;
import com.zhimu.server.framework.security.spi.AdminAuthStateLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 管理员鉴权状态加载（全仓库唯一 Bean，管理端-04 实现）：t_admin + t_admin_role + 角色权限缓存。
 * <ul>
 *     <li>停用：enabled = false，invalidReason = ADMIN_DISABLED</li>
 *     <li>token_version 变化：invalidReason = PASSWORD_CHANGED</li>
 *     <li>must_change_password = 1：nextStep = CHANGE_PASSWORD（首次登录、重置密码后）</li>
 * </ul>
 * 权限点来自 admin:perm:{role_id}；超管直接拥有全部 37 个权限点。
 */
@Component
@RequiredArgsConstructor
public class AdminAuthStateLoaderImpl implements AdminAuthStateLoader {

    public static final String NEXT_STEP_CHANGE_PASSWORD = "CHANGE_PASSWORD";

    private final AdminMapper adminMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminPermissionCache permissionCache;

    @Override
    public AdminAuthState load(Long adminId) {
        if (adminId == null) {
            return null;
        }
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            return null;
        }
        AdminRole role = adminRoleMapper.selectById(admin.getRoleId());
        if (role == null) {
            return null;
        }
        boolean enabled = AdminStatus.NORMAL.name().equals(admin.getStatus());
        Set<String> permissions = permissionCache.permissionsOfRole(role.getId());
        int tokenVersion = admin.getTokenVersion() == null ? 0 : admin.getTokenVersion();
        String nextStep = Boolean.TRUE.equals(admin.getMustChangePassword()) ? NEXT_STEP_CHANGE_PASSWORD : null;
        String invalidReason = enabled ? "PASSWORD_CHANGED" : "ADMIN_DISABLED";
        return new AdminAuthState(tokenVersion, enabled, admin.getName(), Set.of(role.getRoleCode()),
                permissions, nextStep, invalidReason);
    }
}
