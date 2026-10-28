package com.zhimu.server.admin.system.service;

import com.zhimu.server.admin.system.query.AdminListQuery;
import com.zhimu.server.admin.system.request.AdminCreateRequest;
import com.zhimu.server.admin.system.request.AdminResetPasswordRequest;
import com.zhimu.server.admin.system.request.AdminStatusRequest;
import com.zhimu.server.admin.system.request.AdminUpdateRequest;
import com.zhimu.server.admin.system.vo.AdminItemVO;
import com.zhimu.server.common.api.PageVO;

/** 管理员维护（ADM-S20–S26）：列表、查重、新增、编辑、重置密码、停用、启用。 */
public interface AdminAccountService {

    PageVO<AdminItemVO> list(AdminListQuery query);

    boolean usernameAvailable(String username);

    AdminCreateResult create(AdminCreateRequest request);

    /** 返回被编辑管理员的姓名，供控制器拼成功文案 */
    String update(Long adminId, AdminUpdateRequest request);

    AdminResetResult resetPassword(Long adminId, AdminResetPasswordRequest request);

    String disable(Long adminId, AdminStatusRequest request);

    String enable(Long adminId, AdminStatusRequest request);

    /** 新增结果：姓名、脱敏手机号与一次性临时密码（控制器在任何环境都返回给操作人，页面只显示一次） */
    record AdminCreateResult(Long adminId, String name, String phone, String tempPassword,
                             java.time.LocalDateTime tempPasswordExpireAt) {
    }

    /** 重置结果：姓名、脱敏手机号与一次性临时密码 */
    record AdminResetResult(String name, String phone, String tempPassword,
                            java.time.LocalDateTime tempPasswordExpireAt) {
    }
}
