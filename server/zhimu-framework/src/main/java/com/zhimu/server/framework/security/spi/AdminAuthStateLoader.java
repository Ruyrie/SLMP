package com.zhimu.server.framework.security.spi;

/**
 * 读取管理员鉴权状态，由管理端-04（admin.system）实现，实现方负责角色权限缓存 admin:perm:{role_id}。
 */
public interface AdminAuthStateLoader {

    /** 管理员不存在时返回 null（按 40106 处理） */
    AdminAuthState load(Long adminId);
}