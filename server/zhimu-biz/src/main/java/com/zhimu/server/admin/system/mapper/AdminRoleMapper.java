package com.zhimu.server.admin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.system.entity.AdminRole;
import org.apache.ibatis.annotations.Param;

/**
 * 管理员角色（t_admin_role）。三个内置角色不可删除；权限保存带 version 乐观锁。
 */
public interface AdminRoleMapper extends BaseMapper<AdminRole> {

    AdminRole selectByRoleCode(@Param("roleCode") String roleCode);

    /** 事务内锁定角色行：停用/改角色前先锁超级管理员角色，再统计正常超管数 */
    AdminRole selectByIdForUpdate(@Param("id") Long id);

    /** 保存权限的并发控制：带 version 条件把 t_admin_role.version 加 1，返回 0 表示已被他人修改 */
    int bumpVersion(@Param("id") Long id, @Param("version") Integer version);
}
