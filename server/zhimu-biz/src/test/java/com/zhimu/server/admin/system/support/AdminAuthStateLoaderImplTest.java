package com.zhimu.server.admin.system.support;

import com.zhimu.server.admin.system.AdminTestFixtures;
import com.zhimu.server.admin.system.entity.AdminRole;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.AdminRoleMapper;
import com.zhimu.server.common.enums.AdminRoleCode;
import com.zhimu.server.framework.security.spi.AdminAuthState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthStateLoaderImplTest {

    private AdminMapper adminMapper;
    private AdminRoleMapper roleMapper;
    private AdminPermissionCache permissionCache;
    private AdminAuthStateLoaderImpl loader;

    @BeforeEach
    void setUp() {
        adminMapper = mock(AdminMapper.class);
        roleMapper = mock(AdminRoleMapper.class);
        permissionCache = mock(AdminPermissionCache.class);
        loader = new AdminAuthStateLoaderImpl(adminMapper, roleMapper, permissionCache);
    }

    @Test
    void 管理员不存在返回null() {
        when(adminMapper.selectById(1L)).thenReturn(null);
        assertThat(loader.load(1L)).isNull();
    }

    @Test
    void 停用管理员_enabled为false且原因为ADMIN_DISABLED() {
        when(adminMapper.selectById(1L)).thenReturn(AdminTestFixtures.admin(1, "wuhao", "吴昊", 2, "DISABLED"));
        when(roleMapper.selectById(2L)).thenReturn(AdminTestFixtures.role(2, "AUDITOR", "审核员", true));
        when(permissionCache.permissionsOfRole(2L)).thenReturn(AdminTestFixtures.codes("WA-S02:VIEW"));

        AdminAuthState state = loader.load(1L);

        assertThat(state.enabled()).isFalse();
        assertThat(state.invalidReason()).isEqualTo("ADMIN_DISABLED");
        assertThat(state.roleCodes()).containsExactly("AUDITOR");
    }

    @Test
    void 超管登录状态_权限来自缓存_nextStep为改密() {
        var admin = AdminTestFixtures.admin(1, "zhoumin", "周敏", 1, "NORMAL");
        admin.setMustChangePassword(true);
        admin.setTokenVersion(3);
        when(adminMapper.selectById(1L)).thenReturn(admin);
        when(roleMapper.selectById(1L)).thenReturn(AdminTestFixtures.role(1, "SUPER_ADMIN", "超级管理员", false));
        when(permissionCache.permissionsOfRole(1L))
                .thenReturn(AdminTestFixtures.codes("WA-S02:VIEW", "WA-S05:HANDLE"));

        AdminAuthState state = loader.load(1L);

        assertThat(state.enabled()).isTrue();
        assertThat(state.tokenVersion()).isEqualTo(3);
        assertThat(state.nextStep()).isEqualTo("CHANGE_PASSWORD");
        assertThat(state.invalidReason()).isEqualTo("PASSWORD_CHANGED");
        assertThat(state.permissions()).containsExactlyInAnyOrder("WA-S02:VIEW", "WA-S05:HANDLE");
        assertThat(state.name()).isEqualTo("周敏");
        assertThat(state.roleCodes()).containsExactly(AdminRoleCode.SUPER_ADMIN.name());
    }

    @Test
    void 正常管理员没有强制改密时nextStep为null() {
        when(adminMapper.selectById(1L)).thenReturn(AdminTestFixtures.admin(1, "wuhao", "吴昊", 2, "NORMAL"));
        when(roleMapper.selectById(2L)).thenReturn(AdminTestFixtures.role(2, "AUDITOR", "审核员", true));
        when(permissionCache.permissionsOfRole(2L)).thenReturn(AdminTestFixtures.codes());

        assertThat(loader.load(1L).nextStep()).isNull();
        assertThat(loader.load(1L).enabled()).isTrue();
    }

    @Test
    void 角色不存在返回null且不查权限缓存() {
        when(adminMapper.selectById(1L)).thenReturn(AdminTestFixtures.admin(1, "wuhao", "吴昊", 99, "NORMAL"));
        when(roleMapper.selectById(99L)).thenReturn(null);
        assertThat(loader.load(1L)).isNull();
        verify(permissionCache, never()).permissionsOfRole(99L);
    }

    @Test
    void 空ID返回null() {
        assertThat(loader.load(null)).isNull();
        verify(adminMapper, never()).selectById(null);
    }
}
