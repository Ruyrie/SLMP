package com.zhimu.server.admin.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.system.AdminTestFixtures;
import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.entity.AdminRole;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.AdminRoleMapper;
import com.zhimu.server.admin.system.query.AdminListQuery;
import com.zhimu.server.admin.system.request.AdminCreateRequest;
import com.zhimu.server.admin.system.request.AdminResetPasswordRequest;
import com.zhimu.server.admin.system.request.AdminStatusRequest;
import com.zhimu.server.admin.system.request.AdminUpdateRequest;
import com.zhimu.server.admin.system.service.AdminAccountService;
import com.zhimu.server.admin.system.vo.AdminItemVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.security.session.AdminSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAccountServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 10, 22);

    private AdminMapper adminMapper;
    private AdminRoleMapper roleMapper;
    private PasswordEncoder passwordEncoder;
    private OperationLogService operationLogService;
    private AdminSessionService sessionService;
    private AdminAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:22:00Z"), ZoneId.of("Asia/Shanghai")));
        adminMapper = mock(AdminMapper.class);
        roleMapper = mock(AdminRoleMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        operationLogService = mock(OperationLogService.class);
        sessionService = mock(AdminSessionService.class);
        service = new AdminAccountServiceImpl(adminMapper, roleMapper, passwordEncoder, operationLogService,
                sessionService);
        when(roleMapper.selectById(2L)).thenReturn(AdminTestFixtures.role(2L, "AUDITOR", "审核员", true));
        AdminContext.set(AdminTestFixtures.login(1L, "周敏", "SUPER_ADMIN", AdminTestFixtures.codes("WA-S05:HANDLE")));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        AdminContext.clear();
    }

    private Admin auditor() {
        return AdminTestFixtures.admin(2L, "wuhao", "吴昊", 2L, "NORMAL");
    }

    @Test
    void 列表_本人行只有EDIT_其他人给编辑重置与停用() {
        Admin self = AdminTestFixtures.admin(1L, "zhoumin", "周敏", 2L, "NORMAL");
        Admin other = auditor();
        Page<Admin> page = new Page<>(1, 20);
        page.setRecords(List.of(self, other));
        page.setTotal(2);
        when(adminMapper.selectPage(any(), any())).thenReturn(page);
        when(roleMapper.selectList(any())).thenReturn(List.of(
                AdminTestFixtures.role(2L, "AUDITOR", "审核员", true)));

        PageVO<AdminItemVO> result = service.list(new AdminListQuery());

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getList().get(0).getIsSelf()).isTrue();
        assertThat(result.getList().get(0).getActions()).containsExactly("EDIT");
        assertThat(result.getList().get(1).getActions()).containsExactly("EDIT", "RESET_PASSWORD", "DISABLE");
        assertThat(result.getList().get(1).getPhone()).isEqualTo("139****5874");
    }

    @Test
    void 账号查重_格式错误返回40001() {
        assertThatThrownBy(() -> service.usernameAvailable("ab"))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40001));
    }

    @Test
    void 账号查重_已存在返回false() {
        when(adminMapper.selectByUsername("wuhao")).thenReturn(auditor());
        assertThat(service.usernameAvailable("wuhao")).isFalse();
        when(adminMapper.selectByUsername("lisi")).thenReturn(null);
        assertThat(service.usernameAvailable("lisi")).isTrue();
    }

    @Test
    void 新增管理员_密码10位24小时有效且必须改密() {
        when(adminMapper.selectByUsername("lisi")).thenReturn(null);
        when(adminMapper.countByPhone("13900001185", null)).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(adminMapper.insert(any(Admin.class))).thenAnswer(invocation -> {
            Admin admin = invocation.getArgument(0);
            admin.setId(10L);
            return 1;
        });

        AdminAccountService.AdminCreateResult result = service.create(new AdminCreateRequest(
                "李四", "lisi", "13900001185", 2L, "新同事"));

        assertThat(result.adminId()).isEqualTo(10L);
        assertThat(result.name()).isEqualTo("李四");
        assertThat(result.phone()).isEqualTo("139****1185");
        assertThat(result.tempPassword()).hasSize(10).matches("[A-Za-z0-9]{10}");
        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminMapper).insert(captor.capture());
        Admin saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("encoded");
        assertThat(saved.getMustChangePassword()).isTrue();
        assertThat(saved.getTempPasswordExpireAt()).isEqualTo(NOW.plusHours(24));
        assertThat(saved.getStatus()).isEqualTo("NORMAL");
        assertThat(saved.getTokenVersion()).isZero();
        verify(operationLogService).writeAdminLog(any(AdminLogCommand.class));
    }

    @Test
    void 新增管理员_账号重复返回40905() {
        when(adminMapper.selectByUsername("lisi")).thenReturn(auditor());
        assertThatThrownBy(() -> service.create(new AdminCreateRequest(
                "李四", "lisi", "13900001185", 2L, "新同事")))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40905);
                    assertThat(e.getMessage()).isEqualTo("这个账号名已被使用");
                });
    }

    @Test
    void 新增管理员_手机号重复返回40905() {
        when(adminMapper.selectByUsername("lisi")).thenReturn(null);
        when(adminMapper.countByPhone("13900001185", null)).thenReturn(1L);
        assertThatThrownBy(() -> service.create(new AdminCreateRequest(
                "李四", "lisi", "13900001185", 2L, "新同事")))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40905);
                    assertThat(e.getMessage()).isEqualTo("这个手机号已被其他管理员使用");
                });
        verify(adminMapper, never()).insert(any(Admin.class));
    }

    @Test
    void 新增管理员_原因为空返回40004() {
        assertThatThrownBy(() -> service.create(new AdminCreateRequest(
                "李四", "lisi", "13900001185", 2L, " ")))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40004);
                    assertThat(e.getMessage()).isEqualTo("请填写新增原因");
                });
    }

    @Test
    void 编辑管理员_不能修改自己的角色() {
        Admin self = AdminTestFixtures.admin(1L, "zhoumin", "周敏", 2L, "NORMAL");
        when(adminMapper.selectById(1L)).thenReturn(self);
        when(roleMapper.selectById(3L)).thenReturn(AdminTestFixtures.role(3L, "OPERATOR", "内容运营", true));

        assertThatThrownBy(() -> service.update(1L, new AdminUpdateRequest(
                "周敏", "13900001111", 3L, "换岗", 0)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40303);
                    assertThat(e.getMessage()).isEqualTo("不能修改自己的角色");
                });
    }

    @Test
    void 编辑管理员_version冲突返回40902() {
        when(adminMapper.selectById(2L)).thenReturn(auditor());
        when(adminMapper.countByPhone("13900001111", 2L)).thenReturn(0L);
        when(adminMapper.updateById(any(Admin.class))).thenReturn(0);

        assertThatThrownBy(() -> service.update(2L, new AdminUpdateRequest(
                "吴昊", "13900001111", 2L, "改手机号", 7)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40902);
                    assertThat(e.getMessage()).isEqualTo("内容已更新，请刷新后再操作");
                });
    }

    @Test
    void 编辑管理员_成功写入姓名手机号角色并留痕() {
        when(adminMapper.selectById(2L)).thenReturn(auditor());
        when(adminMapper.countByPhone("13900001111", 2L)).thenReturn(0L);
        when(adminMapper.updateById(any(Admin.class))).thenReturn(1);

        String name = service.update(2L, new AdminUpdateRequest(
                "吴昊昊", "13900001111", 2L, "改手机号", 0));

        assertThat(name).isEqualTo("吴昊昊");
        ArgumentCaptor<Admin> adminCaptor = ArgumentCaptor.forClass(Admin.class);
        verify(adminMapper).updateById(adminCaptor.capture());
        assertThat(adminCaptor.getValue().getName()).isEqualTo("吴昊昊");
        assertThat(adminCaptor.getValue().getVersion()).isZero();
        verify(operationLogService).writeAdminLog(any(AdminLogCommand.class));
    }

    @Test
    void 重置密码_不能重置自己() {
        when(adminMapper.selectById(1L)).thenReturn(AdminTestFixtures.admin(1L, "zhoumin", "周敏", 2L, "NORMAL"));

        assertThatThrownBy(() -> service.resetPassword(1L, new AdminResetPasswordRequest("忘记密码")))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(42207);
                    assertThat(e.getMessage()).isEqualTo("不能重置自己的密码，请在个人设置中修改密码");
                });
    }

    @Test
    void 重置密码_生成临时密码_解锁并删除会话() {
        Admin target = auditor();
        target.setTokenVersion(2);
        target.setLockedUntil(NOW.plusMinutes(10));
        when(adminMapper.selectById(2L)).thenReturn(target);
        when(passwordEncoder.encode(any())).thenReturn("encoded");

        AdminAccountService.AdminResetResult result = service.resetPassword(2L,
                new AdminResetPasswordRequest("员工忘记密码"));

        assertThat(result.tempPassword()).hasSize(10);
        assertThat(target.getTokenVersion()).isEqualTo(3);
        assertThat(target.getLockedUntil()).isNull();
        assertThat(target.getLoginFailCount()).isZero();
        assertThat(target.getMustChangePassword()).isTrue();
        assertThat(target.getTempPasswordExpireAt()).isEqualTo(NOW.plusHours(24));
        verify(sessionService).invalidateAll(2L);
        verify(operationLogService).writeAdminLog(any(AdminLogCommand.class));
    }

    @Test
    void 停用管理员_不能停用自己() {
        when(adminMapper.selectById(1L)).thenReturn(AdminTestFixtures.admin(1L, "zhoumin", "周敏", 2L, "NORMAL"));

        assertThatThrownBy(() -> service.disable(1L, new AdminStatusRequest("离职", 0)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(42207);
                    assertThat(e.getMessage()).isEqualTo("不能停用自己的账号");
                });
    }

    @Test
    void 停用管理员_至少保留一个正常超管() {
        Admin target = AdminTestFixtures.admin(2L, "wuhao", "吴昊", 1L, "NORMAL");
        AdminRole superRole = AdminTestFixtures.role(1L, "SUPER_ADMIN", "超级管理员", false);
        when(adminMapper.selectById(2L)).thenReturn(target);
        when(roleMapper.selectById(1L)).thenReturn(superRole);
        when(roleMapper.selectByRoleCode("SUPER_ADMIN")).thenReturn(superRole);
        when(adminMapper.countNormalSuperAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> service.disable(2L, new AdminStatusRequest("换人", 0)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(42207);
                    assertThat(e.getMessage()).isEqualTo("平台至少要保留 1 个正常的超级管理员");
                });
        verify(roleMapper).selectByIdForUpdate(1L);
    }

    @Test
    void 停用管理员_已停用返回40903() {
        Admin target = auditor();
        target.setStatus("DISABLED");
        when(adminMapper.selectById(2L)).thenReturn(target);

        assertThatThrownBy(() -> service.disable(2L, new AdminStatusRequest("再次停用", 0)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40903);
                    assertThat(e.getMessage()).isEqualTo("这个管理员已经停用");
                });
    }

    @Test
    void 停用管理员_成功_token_version加1并删会话() {
        Admin target = auditor();
        target.setTokenVersion(4);
        when(adminMapper.selectById(2L)).thenReturn(target);
        when(adminMapper.updateById(any(Admin.class))).thenReturn(1);

        String name = service.disable(2L, new AdminStatusRequest("违规", 0));

        assertThat(name).isEqualTo("吴昊");
        assertThat(target.getStatus()).isEqualTo("DISABLED");
        assertThat(target.getTokenVersion()).isEqualTo(5);
        assertThat(target.getDisabledAt()).isEqualTo(NOW);
        verify(sessionService).invalidateAll(2L);
    }

    @Test
    void 启用管理员_已是正常返回40903() {
        when(adminMapper.selectById(2L)).thenReturn(auditor());

        assertThatThrownBy(() -> service.enable(2L, new AdminStatusRequest("恢复", 0)))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40903));
    }

    @Test
    void 启用管理员_成功() {
        Admin target = auditor();
        target.setStatus("DISABLED");
        target.setDisabledAt(NOW.minusDays(1));
        when(adminMapper.selectById(2L)).thenReturn(target);
        when(adminMapper.updateById(any(Admin.class))).thenReturn(1);

        assertThat(service.enable(2L, new AdminStatusRequest("误停用", 0))).isEqualTo("吴昊");
        assertThat(target.getStatus()).isEqualTo("NORMAL");
        assertThat(target.getDisabledAt()).isNull();
    }

    @Test
    void 不存在的管理员返回40401() {
        when(adminMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.enable(99L, new AdminStatusRequest("恢复", 0)))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40401));
    }
}
