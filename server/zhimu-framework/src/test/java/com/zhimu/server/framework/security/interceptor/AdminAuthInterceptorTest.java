package com.zhimu.server.framework.security.interceptor;

import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.SecurityTestData;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import com.zhimu.server.framework.security.annotation.AllowForcedStep;
import com.zhimu.server.framework.security.annotation.PublicApi;
import com.zhimu.server.framework.security.session.AdminSession;
import com.zhimu.server.framework.security.session.AdminSessionService;
import com.zhimu.server.framework.security.spi.AdminAuthState;
import com.zhimu.server.framework.security.spi.AdminAuthStateLoader;
import com.zhimu.server.framework.web.TestMvc;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AdminAuthInterceptorTest {

    private static final Cookie SESSION_COOKIE = new Cookie("ZMT_ADMIN_SESSION", "tok");

    @RestController
    public static class TestController {

        @GetMapping("/admin/v1/me")
        public ApiResponse<Map<String, Object>> me() {
            return ApiResponse.ok(current());
        }

        @AdminPermission({"WA-R01:HANDLE", "WA-R01:ADMIN"})
        @PostMapping("/admin/v1/audits/1/approve")
        public ApiResponse<Map<String, Object>> approve() {
            return ApiResponse.ok(current());
        }

        @AllowForcedStep
        @PostMapping("/admin/v1/account/password")
        public ApiResponse<Map<String, Object>> password() {
            return ApiResponse.ok(current());
        }

        @PublicApi
        @PostMapping("/admin/v1/auth/login")
        public ApiResponse<Map<String, Object>> login() {
            return ApiResponse.ok(current());
        }

        @PublicApi
        @PostMapping("/api/v1/auth/token/refresh")
        public ApiResponse<Map<String, Object>> refresh() {
            return ApiResponse.ok(current());
        }

        private static Map<String, Object> current() {
            Map<String, Object> data = new LinkedHashMap<>();
            AdminContext.LoginAdmin admin = AdminContext.get();
            data.put("admin_id", admin == null ? 0 : admin.adminId());
            data.put("name", admin == null || admin.name() == null ? "" : admin.name());
            return data;
        }
    }

    private final SecurityProperties properties = new SecurityProperties();
    private final AdminSessionService sessionService = mock(AdminSessionService.class);
    private AdminAuthStateLoader loader;

    @BeforeEach
    void setUp() {
        properties.setAllowedOrigins(List.of("https://admin.example.com", " "));
        when(sessionService.readCookie(any())).thenAnswer(invocation -> {
            Cookie cookie = WebUtils.getCookie((HttpServletRequest) invocation.getArgument(0), "ZMT_ADMIN_SESSION");
            return cookie == null ? null : cookie.getValue();
        });
        when(sessionService.get("tok")).thenReturn(new AdminSession(7L, 2, LocalDateTime.now()));
    }

    private MockMvc mvc() {
        AdminAuthInterceptor admin = new AdminAuthInterceptor(sessionService,
                SecurityTestData.provider(AdminAuthStateLoader.class, loader));
        OriginCheckInterceptor origin = new OriginCheckInterceptor(properties);
        return TestMvc.builder(new TestController())
                .addMappedInterceptors(new String[]{"/api/v1/auth/**", "/admin/v1/**"}, origin)
                .addMappedInterceptors(new String[]{"/admin/v1/**"}, admin)
                .build();
    }

    private JsonNode perform(MockMvc mvc, RequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return TestMvc.json(result.getResponse().getContentAsString());
    }

    @Test
    void 没有会话或会话过期返回401和40106() throws Exception {
        MockMvc mvc = mvc();
        assertThat(perform(mvc, get("/admin/v1/me"), 401).get("code").asInt()).isEqualTo(40106);
        assertThat(perform(mvc, get("/admin/v1/me").cookie(new Cookie("ZMT_ADMIN_SESSION", "gone")), 401)
                .get("code").asInt()).isEqualTo(40106);
    }

    @Test
    void 带用户端Bearer令牌一律40106() throws Exception {
        JsonNode body = perform(mvc(), get("/admin/v1/me").cookie(SESSION_COOKIE).header("Authorization", "Bearer x.y.z"), 401);
        assertThat(body.get("code").asInt()).isEqualTo(40106);
    }

    @Test
    void 没有Loader时只校验会话_通过后写入上下文并续期() throws Exception {
        JsonNode data = perform(mvc(), get("/admin/v1/me").cookie(SESSION_COOKIE), 200).get("data");
        assertThat(data.get("admin_id").asLong()).isEqualTo(7L);
        verify(sessionService).touch("tok", 7L);
    }

    @Test
    void 管理员停用或token_version变化时作废会话() throws Exception {
        loader = id -> new AdminAuthState(2, false, "吴昊", Set.of("AUDITOR"), Set.of(), null);
        assertThat(perform(mvc(), get("/admin/v1/me").cookie(SESSION_COOKIE), 401).get("code").asInt()).isEqualTo(40106);
        verify(sessionService).invalidate("tok");

        loader = id -> new AdminAuthState(3, true, "吴昊", Set.of("AUDITOR"), Set.of(), null);
        assertThat(perform(mvc(), get("/admin/v1/me").cookie(SESSION_COOKIE), 401).get("code").asInt()).isEqualTo(40106);

        loader = id -> null;
        assertThat(perform(mvc(), get("/admin/v1/me").cookie(SESSION_COOKIE), 401).get("code").asInt()).isEqualTo(40106);
    }

    @Test
    void 强制步骤返回40309() throws Exception {
        loader = id -> new AdminAuthState(2, true, "吴昊", Set.of("AUDITOR"), Set.of(), "CHANGE_PASSWORD");
        MockMvc mvc = mvc();
        JsonNode blocked = perform(mvc, get("/admin/v1/me").cookie(SESSION_COOKIE), 403);
        assertThat(blocked.get("code").asInt()).isEqualTo(40309);
        assertThat(blocked.get("data").get("next_step").asString()).isEqualTo("CHANGE_PASSWORD");

        perform(mvc, post("/admin/v1/account/password").cookie(SESSION_COOKIE), 200);
    }

    @Test
    void 权限点一个都没有返回40308() throws Exception {
        loader = id -> new AdminAuthState(2, true, "吴昊", Set.of("AUDITOR"), Set.of("WA-R01:VIEW"), null);
        assertThat(perform(mvc(), post("/admin/v1/audits/1/approve").cookie(SESSION_COOKIE), 403).get("code").asInt())
                .isEqualTo(40308);

        loader = id -> new AdminAuthState(2, true, "吴昊", Set.of("AUDITOR"), Set.of("WA-R01:HANDLE"), null);
        JsonNode ok = perform(mvc(), post("/admin/v1/audits/1/approve").cookie(SESSION_COOKIE), 200).get("data");
        assertThat(ok.get("name").asString()).isEqualTo("吴昊");
    }

    @Test
    void 白名单接口跳过登录校验() throws Exception {
        assertThat(perform(mvc(), post("/admin/v1/auth/login"), 200).get("data").get("admin_id").asLong()).isZero();
        verify(sessionService, never()).touch(any(), any());
    }

    @Test
    void 凭Cookie认证的写请求校验Origin() throws Exception {
        MockMvc mvc = mvc();
        JsonNode evil = perform(mvc, post("/admin/v1/account/password").cookie(SESSION_COOKIE)
                .header("Origin", "https://evil.example.com"), 403);
        assertThat(evil.get("code").asInt()).isEqualTo(40303);

        perform(mvc, post("/admin/v1/account/password").cookie(SESSION_COOKIE).header("Origin", "https://admin.example.com/"), 200);
        perform(mvc, post("/admin/v1/account/password").cookie(SESSION_COOKIE), 200);
        perform(mvc, get("/admin/v1/me").cookie(SESSION_COOKIE).header("Origin", "https://evil.example.com"), 200);

        JsonNode refresh = perform(mvc, post("/api/v1/auth/token/refresh").cookie(new Cookie("ZMT_REFRESH", "r"))
                .header("Origin", "https://evil.example.com"), 403);
        assertThat(refresh.get("code").asInt()).isEqualTo(40303);
        perform(mvc, post("/api/v1/auth/token/refresh").header("Origin", "https://evil.example.com"), 200);

        properties.setAllowedOrigins(List.of());
        perform(mvc, post("/admin/v1/account/password").cookie(SESSION_COOKIE).header("Origin", "https://evil.example.com"), 200);
    }
}
