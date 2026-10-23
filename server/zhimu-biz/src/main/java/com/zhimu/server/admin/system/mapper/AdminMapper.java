package com.zhimu.server.admin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.system.entity.Admin;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 管理员（t_admin）。管理员不删除，只停用；按 username / phone 查重时包含已停用账号。
 */
public interface AdminMapper extends BaseMapper<Admin> {

    Admin selectByUsername(@Param("username") String username);

    Admin selectByPhone(@Param("phone") String phone);

    /** 手机号在管理员中的占用数，excludeId 为编辑时的本人 ID */
    long countByPhone(@Param("phone") String phone, @Param("excludeId") Long excludeId);

    /** 正常状态（NORMAL）的超级管理员数量，停用/改角色前加锁后统计，保证至少 1 个 */
    long countNormalSuperAdmins();

    Admin selectByIdForUpdate(@Param("id") Long id);

    List<Admin> selectByRoleId(@Param("roleId") Long roleId);

    long countByRoleId(@Param("roleId") Long roleId);

    /** 全部正常管理员，AdminMessageService.sendToPermission 按权限筛选接收人 */
    List<Admin> selectAllNormal();
}
