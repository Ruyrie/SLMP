package com.zhimu.server.admin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.system.entity.AdminRolePermission;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色权限（t_admin_role_permission）。保存权限时按角色整体替换（先删后批量插入）。
 */
public interface AdminRolePermissionMapper extends BaseMapper<AdminRolePermission> {

    /** 角色的权限码集合；SUPER_ADMIN 不写本表，调用方按全部权限处理 */
    List<String> selectPermCodesByRoleId(@Param("roleId") Long roleId);

    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /** 拥有某权限码的角色 ID，sendToPermission 用 */
    List<Long> selectRoleIdsByPermCode(@Param("permCode") String permCode);

    long countByRoleId(@Param("roleId") Long roleId);
}
