package com.zhimu.server.admin.system.interceptor;

import com.zhimu.server.admin.system.controller.AdminAuthController;
import com.zhimu.server.admin.system.controller.AdminMessageController;
import com.zhimu.server.admin.system.controller.AdminRbacController;
import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.admin.system.service.AdminAccountService;
import com.zhimu.server.admin.system.service.AdminAuthService;
import com.zhimu.server.admin.system.service.AdminRoleService;
import com.zhimu.server.admin.system.service.CaptchaService;
import com.zhimu.server.admin.system.vo.AdminLoginVO;
import com.zhimu.server.admin.system.vo.AdminMeVO;
import com.zhimu.server.admin.system.vo.AdminProfileVO;
import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.framework.security.session.AdminSession;
import com.zhimu.server.framework.security.session.AdminSessionService;
import com.zhimu.server.framework.security.spi.AdminAuthState;
import com.zhimu.server.framework.security.spi.AdminAuthStateLoader;
import com.zhimu.server.framework.security.interceptor.AdminAuthInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 用真实的 AdminAuthInterceptor + AdminRbacController / AdminMessageController / AdminAuthController
 * 验证 40308、40106 data.reason、@NoSessionRenew 不续期、强制改密 40309 与 loader 缺失时 fail-closed。
 */
class AdminAuthGateTest {

    private final AdminSessionService sessionService = mock(AdminSessionService.class);
    private final AdminAccountService accountService = mock(AdminAccountService.class);
    private final AdminRoleService roleService = mock(AdminRoleService.class);
    private final AdminMessageService messageService = mock(AdminMessageService.class);
    private final AdminAuthService authService = mock(AdminAuthService.class);
    private final CaptchaService captchaService = mock(CaptchaService.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<AdminAuthStateLoader> loaderProvider = mock(ObjectProvider.class);

    private AdminAuthStateLoader loader;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(sessionService.readCookie(any())).thenReturn("tok");
        when(sessionService.get("tok")).thenReturn(new AdminSession(2L, 1, LocalDateTime.of(2026, 10, 19, 10, 0)));
        when(loaderProvider.getIfAvailable()).thenAnswer(invocation -> loader);
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(sessionService, loaderProvider);
        mvc = TestMvc.builder(
                        new AdminRbacController(accountService, roleService),
                        new AdminMessageController(messageService),
                        new AdminAuthController(authService, captchaService, sessionService))
                .addMappedInterceptors(new String[]{"/admin/v1/**"}, interceptor)
                .build();
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    private static AdminAuthState state(int tokenVersion, boolean enabled, Set<String> permissions,
                                        String nextStep, String reason) {
        return new AdminAuthState(tokenVersion, enabled, "吴昊", Set.of("AUDITOR"), permissions, nextStep, reason);
    }

    @Test
    void 没有权限点返回40308() throws Exception {
        loader = id -> state(1, true, Set.of("WA-R01:VIEW"), null, "PASSWORD_CHANGED");

        var response = mvc.perform(get("/admin/v1/admins")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(TestMvc.json(response.getContentAsString()).get("code").asInt()).isEqualTo(40308);
    }

    @Test
    void 有权限点时通过并续期会话() throws Exception {
        loader = id -> state(1, true, Set.of("WA-S05:VIEW"), null, "PASSWORD_CHANGED");
        when(accountService.list(any())).thenReturn(PageVO.empty(1, 20));

        assertThat(mvc.perform(get("/admin/v1/admins")).andReturn().getResponse().getStatus()).isEqualTo(200);
        verify(sessionService).touch("tok", 2L);
    }

    @Test
    void S41未读数不续期会话() throws Exception {
        loader = id -> state(1, true, Set.of(), null, "PASSWORD_CHANGED");
        when(messageService.unreadCount()).thenReturn(3);

        var response = mvc.perform(get("/admin/v1/messages/unread-count")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(TestMvc.json(response.getContentAsString()).get("data").get("unread_count").asInt())
                .isEqualTo(3);
        verify(sessionService, never()).touch(any(), any());
    }

    @Test
    void token_version变化返回40106与PASSWORD_CHANGED() throws Exception {
        loader = id -> state(2, true, Set.of("WA-S05:VIEW"), null, "PASSWORD_CHANGED");

        var response = mvc.perform(get("/admin/v1/admins")).andReturn().getResponse();
        var json = TestMvc.json(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(json.get("code").asInt()).isEqualTo(40106);
        assertThat(json.get("data").get("reason").asString()).isEqualTo("PASSWORD_CHANGED");
        verify(sessionService).invalidate("tok");
    }

    @Test
    void 管理员被停用返回40106与ADMIN_DISABLED() throws Exception {
        loader = id -> state(1, false, Set.of(), null, "ADMIN_DISABLED");

        var response = mvc.perform(get("/admin/v1/messages/unread-count")).andReturn().getResponse();
        var json = TestMvc.json(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(json.get("data").get("reason").asString()).isEqualTo("ADMIN_DISABLED");
    }

    @Test
    void 强制改密时其他接口返回40309_S05仍可访问() throws Exception {
        loader = id -> state(1, true, Set.of("WA-S05:VIEW"), "CHANGE_PASSWORD", "PASSWORD_CHANGED");

        var blocked = mvc.perform(get("/admin/v1/admins")).andReturn().getResponse();
        var json = TestMvc.json(blocked.getContentAsString());
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(json.get("code").asInt()).isEqualTo(40309);
        assertThat(json.get("data").get("next_step").asString()).isEqualTo("CHANGE_PASSWORD");

        AdminMeVO me = AdminMeVO.from(AdminLoginVO.builder()
                .admin(AdminProfileVO.builder().adminId(2L).name("吴昊").username("wuhao")
                        .roleCode("AUDITOR").roleCodeText("审核员").build())
                .mustChangePassword(true).idleMinutes(30).build());
        when(authService.me()).thenReturn(me);
        assertThat(mvc.perform(get("/admin/v1/auth/me")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void loader缺失时带权限点的接口fail_closed_返回40308() throws Exception {
        loader = null;

        var blocked = mvc.perform(get("/admin/v1/admins")).andReturn().getResponse();
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(TestMvc.json(blocked.getContentAsString()).get("code").asInt()).isEqualTo(40308);

        var allowed = mvc.perform(get("/admin/v1/messages/unread-count")).andReturn().getResponse();
        assertThat(allowed.getStatus()).isEqualTo(200);
    }
}
