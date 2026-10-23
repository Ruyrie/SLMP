package com.zhimu.server.admin.system.api;

import java.util.Set;

/**
 * 管理端权限判定（供 admin.audit、admin.content、admin.base 等管理端模块调用）。
 * 超管直接通过；其他角色读 Redis admin:perm:{role_id}（未命中查库回填，保存权限后删除）。
 */
public interface AdminPermissionService {

    /** 当前管理员是否拥有该权限码；没有登录上下文时返回 false */
    boolean has(String permCode);

    /** 没有该权限码时抛出 40308 */
    void require(String permCode);

    /** 某管理员的全部权限码；超管返回全部 37 个 */
    Set<String> permissionsOf(Long adminId);

    /** 全部权限码（按 sort_no 排序） */
    Set<String> permissionCodes();

    /** 角色权限缓存失效（保存角色权限提交后调用） */
    void evictRole(Long roleId);
}
