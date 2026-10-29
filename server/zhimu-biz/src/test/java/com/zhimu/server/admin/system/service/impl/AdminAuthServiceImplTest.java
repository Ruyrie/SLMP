package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.AdminTestFixtures;
import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.entity.AdminRole;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.AdminRoleMapper;
import com.zhimu.server.admin.system.request.AdminLoginRequest;
import com.zhimu.server.admin.system.request.AdminPasswordRequest;
import com.zhimu.server.admin.system.service.AdminAuthService;
import com.zhimu.server.admin.system.service.CaptchaService;
import com.zhimu.server.admin.system.support.AdminPermissionCache;
import com.zhimu.server.admin.system.vo.AdminLoginVO;
import com.zhimu.server.admin.system.vo.AdminMeVO;
import com.zhimu.server.biz.account.api.AccountRecoveryAdminService;
import com.zhimu.server.biz.common.api.ChangeRequestAdminService;
import com.zhimu.server.biz.farm.api.FarmAuditAdminService;
import com.zhimu.server.biz.farm.api.FarmChangeAdminService;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.session.AdminSessionService;
import com.zhimu.server.integration.ipregion.IpRegionResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 10, 22);

    private AdminMapper adminMapper;
    private AdminRoleMapper roleMapper;
    private AdminPermissionCache permissionCache;
    private PasswordEncoder passwordEncoder;
    private AdminSessionService sessionService;
    private CaptchaService captchaService;
    private AdminLoginAttemptService attemptService;
    private OperationLogService operationLogService;
    private AdminMessageService adminMessageService;
    private FarmAuditAdminService farmAuditAdminService;
    private FarmChangeAdminService farmChangeAdminService;
    private ChangeRequestAdminService changeRequestAdminService;
    private AccountRecoveryAdminService accountRecoveryAdminService;

    @SuppressWarnings("unchecked")
    private final ObjectProvider<IpRegionResolver> ipRegionResolver = mock(ObjectProvider.class);

    private AdminAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        com.zhimu.server.admin.system.AdminSystemTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:22:00Z"), ZoneId.of("Asia/Shanghai")));
        adminMapper = mock(AdminMapper.class);
        roleMapper = mock(AdminRoleMapper.class);
        permissionCache = mock(AdminPermissionCache.class);
        passwordEncoder = mock(PasswordEncoder.class);
        sessionService = mock(AdminSessionService.class);
        captchaService = mock(CaptchaService.class);
        attemptService = mock(AdminLoginAttemptService.class);
        operationLogService = mock(OperationLogService.class);
        adminMessageService = mock(AdminMessageService.class);
        farmAuditAdminService = mock(FarmAuditAdminService.class);
        farmChangeAdminService = mock(FarmChangeAdminService.class);
        changeRequestAdminService = mock(ChangeRequestAdminService.class);
        accountRecoveryAdminService = mock(AccountRecoveryAdminService.class);
        SecurityProperties properties = new SecurityProperties();
        when(ipRegionResolver.getIfAvailable()).thenReturn(null);
        service = new AdminAuthServiceImpl(adminMapper, roleMapper, permissionCache, passwordEncoder, sessionService,
                captchaService, attemptService, operationLogService, adminMessageService, properties,
                ipRegionResolver, farmAuditAdminService, farmChangeAdminService, changeRequestAdminService,
                accountRecoveryAdminService);
        when(permissionCache.allPermissions()).thenReturn(AdminTestFixtures.permissions());
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        AdminContext.clear();
    }

    private static AdminLoginRequest loginRequest() {
        return new AdminLoginRequest("zhoumin", "Zmt12345", "captcha-id", "Ab2C");
    }

    private Admin successAdmin() {
        Admin admin = AdminTestFixtures.admin(1L, "zhoumin", "周敏", 1L, "NORMAL");
        admin.setMustChangePassword(false);
        return admin;
    }

    private void givenSuperRole() {
        when(roleMapper.selectById(1L)).thenReturn(AdminTestFixtures.role(1L, "SUPER_ADMIN", "超级管理员", false));
        when(permissionCache.permissionsOfRole(1L))
                .thenReturn(AdminTestFixtures.codes("WA-S02:VIEW", "WA-S05:HANDLE"));
    }

    @Test
    void 验证码错误返回40103_不计失败次数() {
        when(captchaService.verify("captcha-id", "Ab2C")).thenReturn(false);

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40103);
                    assertThat(e.getMessage()).isEqualTo("验证码不正确，已换一张");
                });
        verify(attemptService, never()).recordFailure(any());
        verify(adminMapper, never()).selectByUsername(any());
    }

    @Test
    void 账号不存在时返回40102并带remaining() {
        when(captchaService.verify("captcha-id", "Ab2C")).thenReturn(true);
        when(adminMapper.selectByUsername("zhoumin")).thenReturn(null);
        when(attemptService.recordFailure("zhoumin")).thenReturn(1);

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40102);
                    assertThat(e.getMessage()).contains("还可以再试 4 次");
                    assertThat(e.getData()).isEqualTo(java.util.Map.of("remaining", 4));
                });
        verify(attemptService).persistFailure(isNull(), eq(1), isNull(), any());
    }

    @Test
    void 密码错误第3次_remaining为2() {
        when(captchaService.verify("captcha-id", "Ab2C")).thenReturn(true);
        when(adminMapper.selectByUsername("zhoumin")).thenReturn(successAdmin());
        when(passwordEncoder.matches("Zmt12345", "$2a$10$hash")).thenReturn(false);
        when(attemptService.recordFailure("zhoumin")).thenReturn(3);

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40102);
                    assertThat(((Number) ((java.util.Map<?, ?>) e.getData()).get("remaining")).intValue()).isEqualTo(2);
                });
    }

    @Test
    void 第5次密码错误锁定30分钟_返回42902与locked_until() {
        Admin admin = successAdmin();
        when(captchaService.verify("captcha-id", "Ab2C")).thenReturn(true);
        when(adminMapper.selectByUsername("zhoumin")).thenReturn(admin);
        when(passwordEncoder.matches("Zmt12345", "$2a$10$hash")).thenReturn(false);
        when(attemptService.recordFailure("zhoumin")).thenReturn(5);

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(42902);
                    assertThat(e.getMessage()).contains("10:52").contains("账号已锁定");
                    LocalDateTime lockedUntil = (LocalDateTime) ((java.util.Map<?, ?>) e.getData()).get("locked_until");
                    assertThat(lockedUntil).isEqualTo(NOW.plusMinutes(30));
                });
        verify(attemptService).clear("zhoumin");
        verify(attemptService).persistFailure(eq(1L), eq(0), eq(NOW.plusMinutes(30)), any());
    }

    @Test
    void 已锁定的账号不校验密码_直接42902() {
        Admin admin = successAdmin();
        admin.setLockedUntil(NOW.plusMinutes(10));
        when(captchaService.verify("captcha-id", "Ab2C")).thenReturn(true);
        when(adminMapper.selectByUsername("zhoumin")).thenReturn(admin);

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(42902));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void 停用管理员密码正确才提示40301() {
        Admin admin = successAdmin();
        admin.setStatus("DISABLED");
        when(captchaService.verify("captcha-id", "Ab2C")).thenReturn(true);
        when(adminMapper.selectByUsername("zhoumin")).thenReturn(admin);
        when(passwordEncoder.matches("Zmt12345", "$2a$10$hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40301);
                    assertThat(e.getMessage()).isEqualTo("这个管理员账号已停用，请联系超级管理员。");
                });
    }

    @Test
    void 临时密码超过24小时返回40107() {
        Admin admin = successAdmin();
        admin.setMustChangePassword(true);
        admin.setTempPasswordExpireAt(NOW.minusMinutes(1));
        when(captchaService.verify("captcha-id", "Ab2C")).thenReturn(true);
        when(adminMapper.selectByUsername("zhoumin")).thenReturn(admin);
        when(passwordEncoder.matches("Zmt12345", "$2a$10$hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40107);
                    assertThat(e.getMessage()).isEqualTo("初始密码已过期，请联系超级管理员重置密码");
                });
    }

    @Test
    void 登录成功_建立会话_返回权限菜单且响应不含令牌() {
        Admin admin = successAdmin();
        when(captchaService.verify("captcha-id", "Ab2C")).thenReturn(true);
        when(adminMapper.selectByUsername("zhoumin")).thenReturn(admin);
        when(passwordEncoder.matches("Zmt12345", "$2a$10$hash")).thenReturn(true);
        givenSuperRole();
        when(sessionService.create(1L, 0)).thenReturn("session-token");

        AdminAuthService.AdminLoginResult result = service.login(loginRequest());

        assertThat(result.token()).isEqualTo("session-token");
        AdminLoginVO vo = result.vo();
        assertThat(vo.getAdmin().getUsername()).isEqualTo("zhoumin");
        assertThat(vo.getAdmin().getRoleCode()).isEqualTo("SUPER_ADMIN");
        assertThat(vo.getAdmin().getRoleCodeText()).isEqualTo("超级管理员");
        assertThat(vo.getMustChangePassword()).isFalse();
        assertThat(vo.getIdleMinutes()).isEqualTo(30);
        assertThat(vo.getPermissions()).contains("WA-S02:VIEW");
        assertThat(vo.getMenus()).isNotEmpty();
        assertThat(vo.getMenus().get(0).getGroupCode()).isEqualTo("OVERVIEW");
        verify(attemptService).clear("zhoumin");
        verify(attemptService).persistSuccess(eq(1L), isNull(), isNull(), any());
    }

    @Test
    void 改密_原密码错误返回40102() {
        AdminContext.set(AdminTestFixtures.login(1L, "周敏", "SUPER_ADMIN", AdminTestFixtures.codes("WA-S02:VIEW")));
        when(adminMapper.selectById(1L)).thenReturn(successAdmin());
        when(passwordEncoder.matches("old", "$2a$10$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(new AdminPasswordRequest("old", "NewPass123", "NewPass123")))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40102);
                    assertThat(e.getMessage()).isEqualTo("原密码不正确");
                });
    }

    @Test
    void 改密_新密码强度不足返回40001() {
        AdminContext.set(AdminTestFixtures.login(1L, "周敏", "SUPER_ADMIN", AdminTestFixtures.codes("WA-S02:VIEW")));
        when(adminMapper.selectById(1L)).thenReturn(successAdmin());
        when(passwordEncoder.matches("old", "$2a$10$hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(new AdminPasswordRequest("old", "abcdefgh", "abcdefgh")))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40001);
                    assertThat(e.getMessage()).isEqualTo("新密码需为 8–20 位，同时包含字母和数字");
                });
    }

    @Test
    void 改密_两次不一致返回40001() {
        AdminContext.set(AdminTestFixtures.login(1L, "周敏", "SUPER_ADMIN", AdminTestFixtures.codes("WA-S02:VIEW")));
        when(adminMapper.selectById(1L)).thenReturn(successAdmin());
        when(passwordEncoder.matches("old", "$2a$10$hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "$2a$10$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(new AdminPasswordRequest("old", "NewPass123", "OtherPass123")))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40001);
                    assertThat(e.getMessage()).isEqualTo("两次输入的密码不一致");
                });
    }

    @Test
    void 改密_新密码与原密码相同返回40001() {
        AdminContext.set(AdminTestFixtures.login(1L, "周敏", "SUPER_ADMIN", AdminTestFixtures.codes("WA-S02:VIEW")));
        when(adminMapper.selectById(1L)).thenReturn(successAdmin());
        when(passwordEncoder.matches("old", "$2a$10$hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "$2a$10$hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(new AdminPasswordRequest("old", "NewPass123", "NewPass123")))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40001);
                    assertThat(e.getMessage()).isEqualTo("新密码不能与原密码相同");
                });
    }

    @Test
    void 改密成功_token_version加1_清除临时密码_删除全部会话() {
        Admin admin = successAdmin();
        admin.setMustChangePassword(true);
        admin.setTempPasswordExpireAt(NOW.plusHours(1));
        when(adminMapper.selectById(1L)).thenReturn(admin);
        when(passwordEncoder.matches("old", "$2a$10$hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "$2a$10$hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");
        AdminContext.set(AdminTestFixtures.login(1L, "周敏", "SUPER_ADMIN", AdminTestFixtures.codes("WA-S02:VIEW")));

        service.changePassword(new AdminPasswordRequest("old", "NewPass123", "NewPass123"));

        assertThat(admin.getPasswordHash()).isEqualTo("new-hash");
        assertThat(admin.getMustChangePassword()).isFalse();
        assertThat(admin.getTempPasswordExpireAt()).isNull();
        assertThat(admin.getTokenVersion()).isEqualTo(1);
        assertThat(admin.getPasswordUpdatedAt()).isEqualTo(NOW);
        verify(adminMapper).updateById(admin);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<Admin>> sets =
                (ArgumentCaptor) ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(adminMapper, org.mockito.Mockito.atLeastOnce()).update(isNull(), sets.capture());
        assertThat(com.zhimu.server.admin.system.AdminSystemTableInfo.sqlSets(sets.getAllValues()))
                .contains("temp_password_expire_at");
        verify(sessionService).invalidateAll(1L);
        ArgumentCaptor<AdminLogCommand> captor = ArgumentCaptor.forClass(AdminLogCommand.class);
        verify(operationLogService).writeAdminLog(captor.capture());
        assertThat(captor.getValue().getOpAction()).isEqualTo("CHANGE_PASSWORD");
    }

    @Test
    void me_返回四个角标与未读数() {
        AdminContext.set(AdminTestFixtures.login(1L, "周敏", "SUPER_ADMIN",
                AdminTestFixtures.codes("WA-S02:VIEW", "WA-R01:VIEW")));
        when(adminMapper.selectById(1L)).thenReturn(successAdmin());
        when(roleMapper.selectById(1L)).thenReturn(AdminTestFixtures.role(1L, "SUPER_ADMIN", "超级管理员", false));
        when(permissionCache.permissionsOfRole(1L)).thenReturn(AdminTestFixtures.codes("WA-S02:VIEW"));
        when(farmAuditAdminService.countPending()).thenReturn(12L);
        when(farmChangeAdminService.countPending()).thenReturn(3L);
        when(changeRequestAdminService.countPending()).thenReturn(8L);
        when(accountRecoveryAdminService.countPending()).thenReturn(2L);
        when(adminMessageService.unreadCount(1L)).thenReturn(5);

        AdminMeVO me = service.me();

        assertThat(me.getBadges()).containsEntry("farm_audit", 12)
                .containsEntry("farm_change", 3)
                .containsEntry("change_request", 8)
                .containsEntry("account_recovery", 2);
        assertThat(me.getUnreadMessageCount()).isEqualTo(5);
        assertThat(me.getAdmin().getAdminId()).isEqualTo(1L);
    }

    @Test
    void me_角标统计异常按0返回() {
        AdminContext.set(AdminTestFixtures.login(1L, "周敏", "SUPER_ADMIN", AdminTestFixtures.codes("WA-S02:VIEW")));
        when(adminMapper.selectById(1L)).thenReturn(successAdmin());
        when(roleMapper.selectById(1L)).thenReturn(AdminTestFixtures.role(1L, "SUPER_ADMIN", "超级管理员", false));
        when(permissionCache.permissionsOfRole(1L)).thenReturn(AdminTestFixtures.codes("WA-S02:VIEW"));
        when(farmAuditAdminService.countPending()).thenThrow(new IllegalStateException("db down"));
        when(farmChangeAdminService.countPending()).thenReturn(3L);
        when(changeRequestAdminService.countPending()).thenReturn(8L);
        when(accountRecoveryAdminService.countPending()).thenReturn(2L);

        assertThat(service.me().getBadges()).containsEntry("farm_audit", 0)
                .containsEntry("farm_change", 3);
    }

    @Test
    void 退出删除当前会话并写LOGOUT日志() {
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"),
                AdminTestFixtures.codes(), 0, "tok"));
        when(adminMapper.selectById(1L)).thenReturn(successAdmin());

        service.logout();

        verify(sessionService).invalidate("tok");
        ArgumentCaptor<AdminLogCommand> captor = ArgumentCaptor.forClass(AdminLogCommand.class);
        verify(operationLogService).writeAdminLog(captor.capture());
        assertThat(captor.getValue().getOpAction()).isEqualTo("LOGOUT");
        assertThat(captor.getValue().getOpType()).isEqualTo("LOGIN");
    }

    @Test
    void 登录失败日志用独立事务写入() {
        when(captchaService.verify("captcha-id", "Ab2C")).thenReturn(true);
        when(adminMapper.selectByUsername("zhoumin")).thenReturn(null);
        when(attemptService.recordFailure("zhoumin")).thenReturn(1);

        assertThatThrownBy(() -> service.login(loginRequest())).isInstanceOf(BizException.class);
        ArgumentCaptor<OperationLogEntry> captor = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(attemptService).persistFailure(isNull(), eq(1), isNull(), captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo("FAIL");
        assertThat(captor.getValue().getOpType()).isEqualTo("LOGIN");
    }
}
