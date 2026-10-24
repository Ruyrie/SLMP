package com.zhimu.server.admin.system.service;

import com.zhimu.server.admin.system.query.AdminUserQuery;
import com.zhimu.server.admin.system.request.UserStatusRequest;
import com.zhimu.server.admin.system.vo.AdminUserDetailVO;
import com.zhimu.server.admin.system.vo.AdminUserItemVO;
import com.zhimu.server.admin.system.vo.ExportVO;
import com.zhimu.server.admin.system.vo.PhoneRevealVO;
import com.zhimu.server.common.api.PageVO;

/** ADM-S10–S14 用户账号：列表、详情、停用/启用、导出。 */
public interface UserAdminService {

    PageVO<AdminUserItemVO> list(AdminUserQuery query);

    AdminUserDetailVO detail(Long userId);

    String disable(Long userId, UserStatusRequest request);

    String enable(Long userId, UserStatusRequest request);

    ExportVO export(AdminUserQuery query);

    /** R24 查看完整手机号：要求 WA-S03:HANDLE，写 VIEW_PHONE 留痕。 */
    PhoneRevealVO revealPhone(Long userId);
}
