package com.zhimu.server.biz.account.service.impl;

import com.zhimu.server.biz.account.entity.LoginLog;
import com.zhimu.server.biz.account.enums.LoginFailReason;
import com.zhimu.server.biz.account.enums.LoginType;
import com.zhimu.server.biz.account.mapper.LoginLogMapper;
import com.zhimu.server.biz.account.service.LoginLogService;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.ClientType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 登录日志写库；失败只记应用日志，不影响登录结果。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl implements LoginLogService {

    private final LoginLogMapper loginLogMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId, String phone, LoginType loginType, ClientType clientType, String deviceId,
                              String deviceModel, String osVersion, String userAgent, String ip, String ipRegion) {
        save(userId, phone, loginType, clientType, deviceId, deviceModel, osVersion, userAgent, ip, ipRegion,
                "SUCCESS", null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId, String phone, LoginType loginType, ClientType clientType,
                              LoginFailReason reason, String ip, String ipRegion) {
        save(userId, phone, loginType, clientType, null, null, null, null, ip, ipRegion,
                "FAIL", reason == null ? null : reason.name());
    }

    private void save(Long userId, String phone, LoginType loginType, ClientType clientType, String deviceId,
                      String deviceModel, String osVersion, String userAgent, String ip, String ipRegion,
                      String result, String failReason) {
        try {
            LoginLog entry = new LoginLog();
            entry.setUserId(userId);
            entry.setPhone(phone);
            entry.setLoginType(loginType == null ? LoginType.PASSWORD.name() : loginType.name());
            entry.setClientType(clientType == null ? ClientType.APP_ANDROID.name() : clientType.name());
            entry.setDeviceId(deviceId);
            entry.setDeviceModel(deviceModel);
            entry.setOsVersion(osVersion);
            entry.setAppVersion(RequestContext.get() == null ? null : RequestContext.get().appVersion());
            entry.setUserAgent(userAgent);
            entry.setIp(ip);
            entry.setIpRegion(ipRegion);
            entry.setResult(result);
            entry.setFailReason(failReason);
            loginLogMapper.insert(entry);
        } catch (RuntimeException e) {
            log.warn("写登录日志失败 phone={} result={}", com.zhimu.server.common.util.Masks.phone(phone), result, e);
        }
    }
}
