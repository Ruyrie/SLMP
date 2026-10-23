package com.zhimu.server.admin.system.support;

import com.zhimu.server.admin.system.entity.AdminPermission;
import com.zhimu.server.admin.system.entity.AdminRole;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.AdminPermissionMapper;
import com.zhimu.server.admin.system.mapper.AdminRoleMapper;
import com.zhimu.server.admin.system.mapper.AdminRolePermissionMapper;
import com.zhimu.server.common.enums.AdminRoleCode;
import com.zhimu.server.framework.jackson.CacheJson;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色权限缓存：admin:perm:{role_id}（1 小时），保存权限提交后由
 * {@link com.zhimu.server.admin.system.api.AdminPermissionService#evictRole(Long)} 删除。
 * 超级管理员角色不写 t_admin_role_permission，这里按全部权限点回填。
 */
@Component
@RequiredArgsConstructor
public class AdminPermissionCache {

    public static final String KEY_PREFIX = "admin:perm:";
    public static final Duration TTL = Duration.ofHours(1);
    /** 角色不存在或没有权限时缓存空集合的占位；这里仍存合法 JSON 数组 */
    private static final TypeReference<LinkedHashSet<String>> SET_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final AdminMapper adminMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminPermissionMapper adminPermissionMapper;
    private final AdminRolePermissionMapper adminRolePermissionMapper;

    /** 全部 37 个权限码（按 sort_no），管理员菜单与权限表用 */
    public List<String> allPermissionCodes() {
        return adminPermissionMapper.selectAllCodes();
    }

    /** 全部权限点（按 sort_no），权限表分组用 */
    public List<AdminPermission> allPermissions() {
        return adminPermissionMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AdminPermission>lambdaQuery()
                        .orderByAsc(AdminPermission::getSortNo)
                        .orderByAsc(AdminPermission::getId));
    }

    /** 角色权限码；SUPER_ADMIN 返回全部权限点 */
    public Set<String> permissionsOfRole(Long roleId) {
        if (roleId == null) {
            return new LinkedHashSet<>();
        }
        String cached = redis.opsForValue().get(KEY_PREFIX + roleId);
        Set<String> result = CacheJson.read(cached, SET_TYPE);
        if (result != null) {
            return result;
        }
        AdminRole role = adminRoleMapper.selectById(roleId);
        if (role == null) {
            return new LinkedHashSet<>();
        }
        LinkedHashSet<String> codes = AdminRoleCode.SUPER_ADMIN.name().equals(role.getRoleCode())
                ? new LinkedHashSet<>(allPermissionCodes())
                : new LinkedHashSet<>(adminRolePermissionMapper.selectPermCodesByRoleId(roleId));
        redis.opsForValue().set(KEY_PREFIX + roleId, CacheJson.write(codes), TTL);
        return codes;
    }

    /** 管理员的角色与权限码；管理员不存在时返回 null */
    public RolePermissions permissionsOfAdmin(Long adminId) {
        com.zhimu.server.admin.system.entity.Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            return null;
        }
        AdminRole role = adminRoleMapper.selectById(admin.getRoleId());
        if (role == null) {
            return null;
        }
        return new RolePermissions(role, permissionsOfRole(role.getId()));
    }

    public void evict(Long roleId) {
        if (roleId != null) {
            redis.delete(KEY_PREFIX + roleId);
        }
    }

    /** 角色与权限码 */
    public record RolePermissions(AdminRole role, Set<String> codes) {
    }
}
