package com.zhimu.server.admin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.admin.system.entity.AdminPermission;

import java.util.List;

/**
 * 权限点（t_admin_permission）。初始化写入 37 个，界面不能增删，只读。
 */
public interface AdminPermissionMapper extends BaseMapper<AdminPermission> {

    /** 全部权限码，按 sort_no 排序（超级管理员拥有全部） */
    List<String> selectAllCodes();
}
