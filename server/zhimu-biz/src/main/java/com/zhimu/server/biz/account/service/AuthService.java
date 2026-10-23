package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.request.AfterRecoveryPasswordRequest;
import com.zhimu.server.biz.account.request.PasswordChangeRequest;
import com.zhimu.server.biz.account.request.PasswordLoginRequest;
import com.zhimu.server.biz.account.request.PasswordResetRequest;
import com.zhimu.server.biz.account.request.RegisterRequest;
import com.zhimu.server.biz.account.request.SmsLoginRequest;
import com.zhimu.server.biz.account.vo.AuthTokenVO;
import com.zhimu.server.biz.account.vo.SessionVO;
import com.zhimu.server.common.enums.ClientType;

/** API-A03–A09、A11、A17 的登录、注册、会话、重置与修改密码。 */
public interface AuthService {

    AuthTokenVO register(RegisterRequest request, ClientType client);

    AuthTokenVO loginByPassword(PasswordLoginRequest request, ClientType client);

    AuthTokenVO loginBySms(SmsLoginRequest request, ClientType client);

    /** 刷新令牌；refreshToken 由控制器按 X-Client 从请求体或 Cookie 取出 */
    AuthTokenVO refresh(String refreshToken, ClientType client);

    void logout(ClientType client);

    SessionVO session(ClientType client);

    void resetPassword(PasswordResetRequest request);

    AuthTokenVO changePasswordAfterRecovery(AfterRecoveryPasswordRequest request, ClientType client);

    AuthTokenVO changePassword(PasswordChangeRequest request, ClientType client);
}
