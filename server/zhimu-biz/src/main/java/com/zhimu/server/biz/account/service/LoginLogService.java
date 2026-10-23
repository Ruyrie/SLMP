package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.enums.LoginFailReason;
import com.zhimu.server.biz.account.enums.LoginType;
import com.zhimu.server.common.enums.ClientType;

/** 登录日志（成功与失败都写；刷新令牌不记日志）。 */
public interface LoginLogService {

    void recordSuccess(Long userId, String phone, LoginType loginType, ClientType clientType, String deviceId,
                       String deviceModel, String osVersion, String userAgent, String ip, String ipRegion);

    void recordFailure(Long userId, String phone, LoginType loginType, ClientType clientType,
                       LoginFailReason reason, String ip, String ipRegion);
}
