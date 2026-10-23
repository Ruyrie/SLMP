package com.zhimu.server.biz.account.service.impl;

import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.mapper.AccountRecoveryMapper;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.test.FixedTime;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.token.UserTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAdminServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AccountRecoveryMapper recoveryMapper = mock(AccountRecoveryMapper.class);
    private final OperationLogRecorder operationLogRecorder = mock(OperationLogRecorder.class);
    private final UserTokenService userTokenService = mock(UserTokenService.class);
    private final AuthStateCache authStateCache = mock(AuthStateCache.class);
    private final AccountAdminServiceImpl service = new AccountAdminServiceImpl(userMapper, recoveryMapper,
            operationLogRecorder, userTokenService, authStateCache);

    @BeforeEach
    void setUp() {
        FixedTime.set();
    }

    @AfterEach
    void tearDown() {
        FixedTime.reset();
    }

    private static User user(String status, int version) {
        User user = new User();
        user.setId(10001L);
        user.setPhone("13800006620");
        user.setName("王建国");
        user.setStatus(status);
        user.setVersion(version);
        return user;
    }

    @Test
    void 停用成功_token_version加1并写留痕() {
        when(userMapper.selectById(10001L)).thenReturn(user("NORMAL", 2));
        when(userMapper.update(eq(null), any())).thenReturn(1);

        service.disable(10001L, 9001L, "违规操作", false, 2);

        verify(userMapper).update(eq(null), any());
        verify(recoveryMapper).update(eq(null), any());
        verify(operationLogRecorder).record(any());
        verify(userTokenService).revokeAll(10001L);
        verify(authStateCache).evict(10001L);
    }

    @Test
    void 停用版本不一致40902() {
        when(userMapper.selectById(10001L)).thenReturn(user("NORMAL", 3));
        BizException e = (BizException) catchException(() ->
                service.disable(10001L, 9001L, "原因", false, 2));
        assertThat(e.getCode()).isEqualTo(40902);
    }

    @Test
    void 停用缺少原因40001() {
        BizException e = (BizException) catchException(() -> service.disable(10001L, 9001L, " ", false, 0));
        assertThat(e.getCode()).isEqualTo(40001);
        assertThat(e.getMessage()).isEqualTo("请填写停用原因");
    }

    @Test
    void 停用已停用账号40903() {
        when(userMapper.selectById(10001L)).thenReturn(user("DISABLED", 2));
        BizException e = (BizException) catchException(() ->
                service.disable(10001L, 9001L, "原因", false, 2));
        assertThat(e.getCode()).isEqualTo(40903);
    }

    @Test
    void 启用成功清空停用字段并写留痕() {
        when(userMapper.selectById(10001L)).thenReturn(user("DISABLED", 2));
        when(userMapper.update(eq(null), any())).thenReturn(1);

        service.enable(10001L, 9001L, "恢复使用", 2);

        verify(userMapper).update(eq(null), any());
        verify(operationLogRecorder).record(any());
        verify(userTokenService).revokeAll(10001L);
    }

    @Test
    void 启用非停用账号40903() {
        when(userMapper.selectById(10001L)).thenReturn(user("NORMAL", 2));
        BizException e = (BizException) catchException(() -> service.enable(10001L, 9001L, "恢复使用", 2));
        assertThat(e.getCode()).isEqualTo(40903);
    }

    @Test
    void 手机号占用查询包含注销未满30天账号() {
        when(userMapper.selectByPhone("13800006620")).thenReturn(user("CANCELLED", 0));
        assertThat(service.existsPhoneIncludingCooling("138 0000 6620")).isTrue();
        when(userMapper.selectByPhone("13900000000")).thenReturn(null);
        assertThat(service.existsPhoneIncludingCooling("13900000000")).isFalse();
        assertThat(service.existsPhoneIncludingCooling("123")).isFalse();
    }

    private static Throwable catchException(org.junit.jupiter.api.function.Executable executable) {
        try {
            executable.execute();
        } catch (Throwable e) {
            return e;
        }
        throw new AssertionError("expected exception");
    }
}
