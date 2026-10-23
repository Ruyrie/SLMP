package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.AdminTestFixtures;
import com.zhimu.server.admin.system.api.AdminLogCommand;
import com.zhimu.server.admin.system.api.AdminPermissionService;
import com.zhimu.server.admin.system.api.OperationLogService;
import com.zhimu.server.admin.system.entity.AdminRole;
import com.zhimu.server.admin.system.entity.AdminRolePermission;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.AdminPermissionMapper;
import com.zhimu.server.admin.system.mapper.AdminRoleMapper;
import com.zhimu.server.admin.system.mapper.AdminRolePermissionMapper;
import com.zhimu.server.admin.system.request.RolePermissionSaveRequest;
import com.zhimu.server.admin.system.service.AdminRoleService;
import com.zhimu.server.admin.system.vo.RoleItemVO;
import com.zhimu.server.admin.system.vo.RolePermissionVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminRoleServiceImplTest {

    private AdminMapper adminMapper;
    private AdminRoleMapper roleMapper;
    private AdminPermissionMapper permissionMapper;
    private AdminRolePermissionMapper rolePermissionMapper;
    private AdminPermissionService permissionService;
    private OperationLogService operationLogService;
    private AdminRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        adminMapper = mock(AdminMapper.class);
        roleMapper = mock(AdminRoleMapper.class);
        permissionMapper = mock(AdminPermissionMapper.class);
        rolePermissionMapper = mock(AdminRolePermissionMapper.class);
        permissionService = mock(AdminPermissionService.class);
        operationLogService = mock(OperationLogService.class);
        service = new AdminRoleServiceImpl(adminMapper, roleMapper, permissionMapper, rolePermissionMapper,
                permissionService, operationLogService);
        when(permissionMapper.selectList(any())).thenReturn(AdminTestFixtures.permissions());
        AdminContext.set(AdminTestFixtures.login(1L, "周敏", "SUPER_ADMIN", AdminTestFixtures.codes("WA-S05:HANDLE")));
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    private AdminRole auditorRole() {
        return AdminTestFixtures.role(2L, "AUDITOR", "审核员", true);
    }

    private AdminRole superRole() {
        return AdminTestFixtures.role(1L, "SUPER_ADMIN", "超级管理员", false);
    }

    @Test
    void 角色列表带成员数与是否可编辑() {
        when(roleMapper.selectList(any())).thenReturn(List.of(superRole(), auditorRole()));
        when(adminMapper.countByRoleId(1L)).thenReturn(1L);
        when(adminMapper.countByRoleId(2L)).thenReturn(3L);

        List<RoleItemVO> roles = service.list();

        assertThat(roles).hasSize(2);
        assertThat(roles.get(0).getRoleCode()).isEqualTo("SUPER_ADMIN");
        assertThat(roles.get(0).getMemberCount()).isEqualTo(1L);
        assertThat(roles.get(0).getEditable()).isFalse();
        assertThat(roles.get(1).getMemberCount()).isEqualTo(3L);
    }

    @Test
    void 超管权限表_19个页面全部勾选并锁定_且不可编辑() {
        when(roleMapper.selectById(1L)).thenReturn(superRole());
        when(adminMapper.selectByRoleId(1L)).thenReturn(List.of(
                AdminTestFixtures.admin(1L, "zhoumin", "周敏", 1L, "NORMAL")));
        when(adminMapper.countByRoleId(1L)).thenReturn(1L);

        RolePermissionVO vo = service.permissions(1L);

        assertThat(vo.getEditable()).isFalse();
        assertThat(vo.getRows()).isNotEmpty();
        assertThat(vo.getRows()).allSatisfy(row -> {
            assertThat(row.getView().getChecked()).isTrue();
            assertThat(row.getView().getLocked()).isTrue();
            if (row.getHandle() != null) {
                assertThat(row.getHandle().getChecked()).isTrue();
                assertThat(row.getHandle().getLocked()).isTrue();
            }
        });
        assertThat(vo.getRows().get(0).getPageCode()).isEqualTo("WA-S02");
        assertThat(vo.getRows().get(0).getHandle()).isNull();
    }

    @Test
    void 审核员权限表_超管专属权限点锁定未勾选() {
        when(roleMapper.selectById(2L)).thenReturn(auditorRole());
        when(rolePermissionMapper.selectPermCodesByRoleId(2L))
                .thenReturn(List.of("WA-S02:VIEW", "WA-R01:VIEW", "WA-R01:HANDLE", "WA-S03:VIEW"));
        when(adminMapper.selectByRoleId(2L)).thenReturn(List.of(
                AdminTestFixtures.admin(2L, "wuhao", "吴昊", 2L, "NORMAL")));
        when(adminMapper.countByRoleId(2L)).thenReturn(1L);

        RolePermissionVO vo = service.permissions(2L);

        RolePermissionVO.Row s03 = vo.getRows().stream()
                .filter(row -> "WA-S03".equals(row.getPageCode())).findFirst().orElseThrow();
        assertThat(s03.getView().getChecked()).isTrue();
        assertThat(s03.getView().getLocked()).isFalse();
        assertThat(s03.getHandle().getChecked()).isFalse();
        assertThat(s03.getHandle().getLocked()).isTrue();
        RolePermissionVO.Row r01 = vo.getRows().stream()
                .filter(row -> "WA-R01".equals(row.getPageCode())).findFirst().orElseThrow();
        assertThat(r01.getHandle().getChecked()).isTrue();
        assertThat(r01.getHandle().getLocked()).isFalse();
    }

    @Test
    void 修改超管权限返回40303() {
        when(roleMapper.selectById(1L)).thenReturn(superRole());
        assertThatThrownBy(() -> service.savePermissions(1L,
                new RolePermissionSaveRequest(List.of("WA-R01:VIEW"), "调整", 0)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40303);
                    assertThat(e.getMessage()).isEqualTo("超级管理员的权限不能修改");
                });
        verify(roleMapper, never()).bumpVersion(any(), any());
    }

    @Test
    void 权限码不存在返回40001() {
        when(roleMapper.selectById(2L)).thenReturn(auditorRole());
        when(roleMapper.bumpVersion(2L, 0)).thenReturn(1);
        assertThatThrownBy(() -> service.savePermissions(2L,
                new RolePermissionSaveRequest(List.of("WA-X99:VIEW"), "调整", 0)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40001);
                    assertThat(e.getMessage()).isEqualTo("权限码不存在");
                });
    }

    @Test
    void 授予超管专属权限点返回40001() {
        when(roleMapper.selectById(2L)).thenReturn(auditorRole());
        when(roleMapper.bumpVersion(2L, 0)).thenReturn(1);
        assertThatThrownBy(() -> service.savePermissions(2L,
                new RolePermissionSaveRequest(List.of("WA-S05:VIEW"), "调整", 0)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40001);
                    assertThat(e.getMessage()).isEqualTo("管理员与权限只限超级管理员");
                });
    }

    @Test
    void 勾选处理但未勾选查看返回40001() {
        when(roleMapper.selectById(2L)).thenReturn(auditorRole());
        when(roleMapper.bumpVersion(2L, 0)).thenReturn(1);
        assertThatThrownBy(() -> service.savePermissions(2L,
                new RolePermissionSaveRequest(List.of("WA-R01:HANDLE"), "调整", 0)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40001);
                    assertThat(e.getMessage()).isEqualTo("勾选“处理”时必须同时勾选“查看”");
                });
    }

    @Test
    void 保存权限_version冲突返回40902() {
        when(roleMapper.selectById(2L)).thenReturn(auditorRole());
        when(roleMapper.bumpVersion(2L, 5)).thenReturn(0);
        assertThatThrownBy(() -> service.savePermissions(2L,
                new RolePermissionSaveRequest(List.of("WA-R01:VIEW"), "调整", 5)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(40902);
                    assertThat(e.getMessage()).isEqualTo("内容已更新，请刷新后再操作");
                });
    }

    @Test
    void 保存权限_整体替换_返回管理员人数_提交后删缓存() {
        when(roleMapper.selectById(2L)).thenReturn(auditorRole());
        when(roleMapper.bumpVersion(2L, 0)).thenReturn(1);
        when(rolePermissionMapper.selectPermCodesByRoleId(2L)).thenReturn(List.of("WA-S02:VIEW"));
        when(adminMapper.countByRoleId(2L)).thenReturn(3L);

        AdminRoleService.SavedResult result = service.savePermissions(2L,
                new RolePermissionSaveRequest(List.of("WA-R01:VIEW", "WA-R01:HANDLE"), "调整权限", 0));

        assertThat(result.roleName()).isEqualTo("审核员");
        assertThat(result.affectedAdminCount()).isEqualTo(3L);
        verify(rolePermissionMapper).delete(any());
        verify(rolePermissionMapper, times(2)).insert(any(AdminRolePermission.class));
        verify(permissionService).evictRole(2L);
        verify(operationLogService).writeAdminLog(any(AdminLogCommand.class));
    }

    @Test
    void 保存权限_允许空权限集合() {
        when(roleMapper.selectById(2L)).thenReturn(auditorRole());
        when(roleMapper.bumpVersion(2L, 0)).thenReturn(1);
        when(rolePermissionMapper.selectPermCodesByRoleId(2L)).thenReturn(List.of("WA-S02:VIEW"));

        service.savePermissions(2L, new RolePermissionSaveRequest(List.of(), "收回全部权限", 0));

        verify(rolePermissionMapper, never()).insert(any(AdminRolePermission.class));
        verify(permissionService).evictRole(2L);
    }

    @Test
    void 角色不存在返回40401() {
        when(roleMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.permissions(99L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40401));
    }

    @Test
    void 原因为空返回40004() {
        when(roleMapper.selectById(2L)).thenReturn(auditorRole());
        assertThatThrownBy(() -> service.savePermissions(2L,
                new RolePermissionSaveRequest(List.of("WA-R01:VIEW"), " ", 0)))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40004));
    }

    @Test
    void 默认权限码解析() {
        AdminRole role = auditorRole();
        role.setDefaultPermCodes("[\"WA-S02:VIEW\",\"WA-R01:VIEW\"]");
        when(roleMapper.selectById(2L)).thenReturn(role);
        when(rolePermissionMapper.selectPermCodesByRoleId(2L)).thenReturn(List.of());
        when(adminMapper.selectByRoleId(2L)).thenReturn(List.of());

        assertThat(service.permissions(2L).getDefaultCodes()).containsExactly("WA-S02:VIEW", "WA-R01:VIEW");
    }
}
