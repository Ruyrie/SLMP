package com.zhimu.server.biz.account.support;

import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.NextStep;
import com.zhimu.server.biz.account.enums.UserStatus;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.framework.security.spi.UserAuthState;
import com.zhimu.server.framework.security.spi.UserAuthStateLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模块01 提供的鉴权状态加载器（全仓库唯一 Bean）：t_user + t_recovery_code 计算
 * tokenVersion、status、nextStep、invalidReason；状态变化后由业务调用 AuthStateCache.evict 清缓存。
 */
@Component
@RequiredArgsConstructor
public class UserAuthStateLoaderImpl implements UserAuthStateLoader {

    private final UserMapper userMapper;
    private final AccountStateService accountState;

    @Override
    public UserAuthState load(Long userId) {
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        String status = user.getStatus() == null ? UserStatus.NORMAL.name() : user.getStatus();
        String invalidReason = null;
        if (UserStatus.CANCELLED.name().equals(status)) {
            invalidReason = "ACCOUNT_CANCELLED";
        } else if (UserStatus.DISABLED.name().equals(status)) {
            invalidReason = "ACCOUNT_DISABLED";
        } else if (user.getTokenRevokeReason() != null) {
            invalidReason = user.getTokenRevokeReason();
        }
        NextStep nextStep = UserStatus.NORMAL.name().equals(status)
                ? accountState.resolveNextStep(user, currentClient())
                : NextStep.NONE;
        return new UserAuthState(user.getTokenVersion() == null ? 0 : user.getTokenVersion(), status,
                nextStep.name(), invalidReason);
    }

    /** 缓存的 nextStep 按请求客户端判定；REGENERATE 只约束发起客户端 */
    private static ClientType currentClient() {
        ClientType client = RequestContext.clientType();
        return client == ClientType.WEB ? ClientType.WEB : ClientType.APP_ANDROID;
    }
}
