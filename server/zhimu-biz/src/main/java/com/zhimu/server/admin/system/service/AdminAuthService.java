package com.zhimu.server.admin.system.service;

import com.zhimu.server.admin.system.request.AdminLoginRequest;
import com.zhimu.server.admin.system.vo.AdminLoginVO;

/** 管理端登录、退出、改密、当前管理员（ADM-S02–S05）。 */
public interface AdminAuthService {

    /**
     * ADM-S02 登录：验证码 → 锁定判断 → 密码 → 状态与临时密码有效期 → 建立会话。
     * 返回的令牌只通过 Cookie 下发，不进入响应体。
     */
    AdminLoginResult login(AdminLoginRequest request);

    /** ADM-S03 退出：删除当前会话；LOGOUT 日志由调用方在事务内写 */
    void logout();

    /** ADM-S04 修改密码：旧密码校验、强度校验、token_version + 1、删除全部会话 */
    void changePassword(com.zhimu.server.admin.system.request.AdminPasswordRequest request);

    /** ADM-S05 当前管理员、权限、菜单、角标与未读消息数 */
    com.zhimu.server.admin.system.vo.AdminMeVO me();

    /** 登录结果：VO 给响应体，token 只写 Cookie */
    record AdminLoginResult(AdminLoginVO vo, String token) {
    }
}
