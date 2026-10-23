package com.zhimu.server.admin.system.service;

import com.zhimu.server.admin.system.request.RolePermissionSaveRequest;
import com.zhimu.server.admin.system.vo.RoleItemVO;
import com.zhimu.server.admin.system.vo.RolePermissionVO;

import java.util.List;

/** 角色与权限（ADM-S27–S29）：角色列表、权限表、保存权限。 */
public interface AdminRoleService {

    List<RoleItemVO> list();

    RolePermissionVO permissions(Long roleId);

    SavedResult savePermissions(Long roleId, RolePermissionSaveRequest request);

    /** 保存结果：角色名与该角色管理员人数，控制器拼“已保存审核员的权限，3 名管理员立即生效” */
    record SavedResult(String roleName, long affectedAdminCount) {
    }
}
