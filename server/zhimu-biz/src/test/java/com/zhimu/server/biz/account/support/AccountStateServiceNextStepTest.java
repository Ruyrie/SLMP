package com.zhimu.server.biz.account.support;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.RecoveryCode;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.NextStep;
import com.zhimu.server.biz.account.mapper.RecoveryCodeMapper;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.service.AvatarService;
import com.zhimu.server.biz.account.vo.AuthTokenVO;
import com.zhimu.server.biz.account.vo.SessionVO;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.token.TokenPair;
import com.zhimu.server.framework.security.token.UserTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountStateServiceNextStepTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final RecoveryCodeMapper recoveryCodeMapper = mock(RecoveryCodeMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccountProperties properties = new AccountProperties();
    private final UserTokenService userTokenService = mock(UserTokenService.class);
    private final AuthStateCache authStateCache = mock(AuthStateCache.class);
    private final FarmAccountService farmAccountService = mock(FarmAccountService.class);
    private final AvatarService avatarService = mock(AvatarService.class);
    private final AccountStateService service = new AccountStateService(userMapper, recoveryCodeMapper,
            passwordEncoder, properties, userTokenService, authStateCache, farmAccountService, avatarService);

    private static User user(boolean mustChange) {
        User user = new User();
        user.setId(10001L);
        user.setPhone("13800006620");
        user.setMustChangePassword(mustChange);
        user.setTokenVersion(0);
        return user;
    }

    private static RecoveryCode code(String status, String scene, String client) {
        RecoveryCode code = new RecoveryCode();
        code.setStatus(status);
        code.setScene(scene);
        code.setClientType(client);
        code.setLiveFlag(1);
        return code;
    }

    @Test
    void 必须改密优先于恢复码判定() {
        assertThat(service.resolveNextStep(user(true), ClientType.APP_ANDROID))
                .isEqualTo(NextStep.RECOVERY_CHANGE_PASSWORD);
        Mockito.verifyNoInteractions(recoveryCodeMapper);
    }

    @Test
    void 没有恢复码时不强制保存() {
        when(recoveryCodeMapper.selectOne(Mockito.any())).thenReturn(null);
        assertThat(service.resolveNextStep(user(false), ClientType.APP_ANDROID)).isEqualTo(NextStep.NONE);
    }

    @Test
    void 待确认注册码要求保存() {
        when(recoveryCodeMapper.selectOne(Mockito.any()))
                .thenReturn(code("PENDING_CONFIRM", "REGISTER", "APP_ANDROID"));
        assertThat(service.resolveNextStep(user(false), ClientType.WEB)).isEqualTo(NextStep.SAVE_RECOVERY_CODE);
    }

    @Test
    void 待确认找回码要求保存() {
        when(recoveryCodeMapper.selectOne(Mockito.any()))
                .thenReturn(code("PENDING_CONFIRM", "RECOVERY", "APP_ANDROID"));
        assertThat(service.resolveNextStep(user(false), ClientType.WEB)).isEqualTo(NextStep.SAVE_RECOVERY_CODE);
    }

    @Test
    void 重新生成码只约束发起客户端() {
        when(recoveryCodeMapper.selectOne(Mockito.any()))
                .thenReturn(code("PENDING_CONFIRM", "REGENERATE", "APP_ANDROID"));
        assertThat(service.resolveNextStep(user(false), ClientType.APP_ANDROID)).isEqualTo(NextStep.SAVE_RECOVERY_CODE);
        assertThat(service.resolveNextStep(user(false), ClientType.WEB)).isEqualTo(NextStep.NONE);
    }

    @Test
    void 已确认的恢复码不再要求保存() {
        when(recoveryCodeMapper.selectOne(Mockito.any())).thenReturn(code("ACTIVE", "REGISTER", "APP_ANDROID"));
        assertThat(service.resolveNextStep(user(false), ClientType.APP_ANDROID)).isEqualTo(NextStep.NONE);
    }

    @Test
    void 登录与会话返回头像地址() {
        User user = user(false);
        user.setAvatarFileId(900L);
        when(recoveryCodeMapper.selectOne(Mockito.any())).thenReturn(null);
        when(avatarService.avatarUrl(900L)).thenReturn("https://files.test/avatar/900.jpg");
        TokenPair pair = new TokenPair("access", "Bearer", 7200, "refresh", 2592000);

        AuthTokenVO token = service.toTokenVO(user, pair, ClientType.APP_ANDROID, null);
        SessionVO session = service.toSessionVO(user, ClientType.APP_ANDROID);

        assertThat(token.getUser().getAvatarUrl()).isEqualTo("https://files.test/avatar/900.jpg");
        assertThat(session.getUser().getAvatarUrl()).isEqualTo("https://files.test/avatar/900.jpg");
    }

    @Test
    void 没有头像时登录与会话的头像地址为空() {
        User user = user(false);
        when(recoveryCodeMapper.selectOne(Mockito.any())).thenReturn(null);
        TokenPair pair = new TokenPair("access", "Bearer", 7200, "refresh", 2592000);

        AuthTokenVO token = service.toTokenVO(user, pair, ClientType.APP_ANDROID, null);
        SessionVO session = service.toSessionVO(user, ClientType.APP_ANDROID);

        assertThat(token.getUser().getAvatarUrl()).isNull();
        assertThat(session.getUser().getAvatarUrl()).isNull();
    }
}
