package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.AdminTestFixtures;
import com.zhimu.server.admin.system.support.AdminPermissionCache;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPermissionServiceImplTest {

    private AdminPermissionCache cache;
    private AdminPermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        cache = mock(AdminPermissionCache.class);
        service = new AdminPermissionServiceImpl(cache);
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void has读取当前上下文权限() {
        AdminContext.set(AdminTestFixtures.login(2L, "吴昊", "AUDITOR",
                AdminTestFixtures.codes("WA-R01:VIEW", "WA-R01:HANDLE")));
        assertThat(service.has("WA-R01:HANDLE")).isTrue();
        assertThat(service.has("WA-S05:VIEW")).isFalse();
    }

    @Test
    void 没有登录上下文时has为false并抛40308() {
        assertThat(service.has("WA-R01:VIEW")).isFalse();
        assertThatThrownBy(() -> service.require("WA-R01:VIEW"))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40308));
    }

    @Test
    void permissionsOf按管理员查角色权限() {
        var role = AdminTestFixtures.role(2, "AUDITOR", "审核员", true);
        when(cache.permissionsOfAdmin(9L)).thenReturn(new AdminPermissionCache.RolePermissions(role,
                AdminTestFixtures.codes("WA-R01:VIEW")));
        assertThat(service.permissionsOf(9L)).containsExactly("WA-R01:VIEW");
    }

    @Test
    void permissionsOf管理员不存在返回空集合() {
        when(cache.permissionsOfAdmin(9L)).thenReturn(null);
        assertThat(service.permissionsOf(9L)).isEmpty();
    }

    @Test
    void evictRole删除缓存() {
        service.evictRole(2L);
        verify(cache).evict(2L);
    }
}
