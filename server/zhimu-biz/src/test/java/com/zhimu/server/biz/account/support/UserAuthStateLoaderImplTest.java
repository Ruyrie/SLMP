package com.zhimu.server.biz.account.support;

import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.NextStep;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.framework.security.spi.UserAuthState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAuthStateLoaderImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AccountStateService accountState = mock(AccountStateService.class);
    private final UserAuthStateLoaderImpl loader = new UserAuthStateLoaderImpl(userMapper, accountState);

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private static User user(String status, int tokenVersion, String reason) {
        User user = new User();
        user.setId(10001L);
        user.setStatus(status);
        user.setTokenVersion(tokenVersion);
        user.setTokenRevokeReason(reason);
        return user;
    }

    @Test
    void 用户不存在返回null() {
        assertThat(loader.load(1L)).isNull();
    }

    @Test
    void 正常账号返回tokenVersion与nextStep() {
        when(userMapper.selectById(10001L)).thenReturn(user("NORMAL", 3, "PASSWORD_CHANGED"));
        when(accountState.resolveNextStep(org.mockito.ArgumentMatchers.any(User.class),
                org.mockito.ArgumentMatchers.any(ClientType.class))).thenReturn(NextStep.SAVE_RECOVERY_CODE);

        UserAuthState state = loader.load(10001L);

        assertThat(state.tokenVersion()).isEqualTo(3);
        assertThat(state.status()).isEqualTo("NORMAL");
        assertThat(state.nextStep()).isEqualTo("SAVE_RECOVERY_CODE");
        assertThat(state.invalidReason()).isEqualTo("PASSWORD_CHANGED");
        assertThat(state.hasForcedStep()).isTrue();
    }

    @Test
    void 停用与注销返回固定reason且不进入强制步骤() {
        when(userMapper.selectById(10001L)).thenReturn(user("DISABLED", 4, null));
        UserAuthState disabled = loader.load(10001L);
        assertThat(disabled.status()).isEqualTo("DISABLED");
        assertThat(disabled.invalidReason()).isEqualTo("ACCOUNT_DISABLED");
        assertThat(disabled.nextStep()).isEqualTo("NONE");

        when(userMapper.selectById(10001L)).thenReturn(user("CANCELLED", 5, null));
        UserAuthState cancelled = loader.load(10001L);
        assertThat(cancelled.invalidReason()).isEqualTo("ACCOUNT_CANCELLED");
        assertThat(cancelled.nextStep()).isEqualTo("NONE");
    }

    @Test
    void Web请求按Web客户端判定恢复码强制步骤() {
        when(userMapper.selectById(10001L)).thenReturn(user("NORMAL", 0, null));
        when(accountState.resolveNextStep(org.mockito.ArgumentMatchers.any(User.class),
                org.mockito.ArgumentMatchers.eq(ClientType.WEB))).thenReturn(NextStep.NONE);
        RequestContext.set(new RequestContext.Info(ClientType.WEB, "1.0.0", "127.0.0.1", null, null));

        assertThat(loader.load(10001L).nextStep()).isEqualTo("NONE");
    }
}
