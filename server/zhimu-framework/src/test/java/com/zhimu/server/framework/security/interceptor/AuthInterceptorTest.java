package com.zhimu.server.framework.security.interceptor;

import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.framework.security.SecurityTestData;
import com.zhimu.server.framework.security.annotation.AllowForcedStep;
import com.zhimu.server.framework.security.annotation.PublicApi;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.jwt.UserJwtService;
import com.zhimu.server.framework.security.spi.UserAuthState;
import com.zhimu.server.framework.web.TestMvc;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthInterceptorTest {

    @RestController
    @RequestMapping("/api/v1/t")
    public static class TestController {

        @GetMapping("/me")
        public ApiResponse<Map<String, Object>> me() {
            return ApiResponse.ok(current());
        }

        @PublicApi
        @GetMapping("/public")
        public ApiResponse<Map<String, Object>> open() {
            return ApiResponse.ok(current());
        }

        @AllowForcedStep
        @PostMapping("/password")
        public ApiResponse<Map<String, Object>> password() {
            return ApiResponse.ok(current());
        }

        private static Map<String, Object> current() {
            Map<String, Object> data = new LinkedHashMap<>();
            UserContext.LoginUser user = UserContext.get();
            data.put("user_id", user == null ? 0 : user.userId());
            data.put("client", user == null ? "" : String.valueOf(user.clientType()));
            return data;
        }
    }

    private final UserJwtService jwtService = new UserJwtService(SecurityTestData.properties(), false, Clock.systemUTC());
    private final AuthStateCache authStateCache = mock(AuthStateCache.class);
    private final MockMvc mvc = TestMvc.builder(new TestController())
            .addMappedInterceptors(new String[]{"/api/v1/**"}, new AuthInterceptor(jwtService, authStateCache))
            .build();

    private String token(long userId, int tokenVersion) {
        return "Bearer " + jwtService.issue(userId, ClientType.APP_ANDROID, tokenVersion).token();
    }

    private JsonNode perform(RequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return TestMvc.json(result.getResponse().getContentAsString());
    }

    @Test
    void 缺少令牌_格式错误_签名错误_过期都返回401和40101() throws Exception {
        assertThat(perform(get("/api/v1/t/me"), 401).get("code").asInt()).isEqualTo(40101);
        assertThat(perform(get("/api/v1/t/me").header("Authorization", "Basic abc"), 401).get("code").asInt()).isEqualTo(40101);
        assertThat(perform(get("/api/v1/t/me").header("Authorization", "Bearer abc.def.ghi"), 401).get("code").asInt()).isEqualTo(40101);

        UserJwtService past = new UserJwtService(SecurityTestData.properties(), false,
                Clock.offset(Clock.systemUTC(), Duration.ofHours(-3)));
        String expired = "Bearer " + past.issue(1L, ClientType.APP_ANDROID, 0).token();
        JsonNode body = perform(get("/api/v1/t/me").header("Authorization", expired), 401);
        assertThat(body.get("code").asInt()).isEqualTo(40101);
        assertThat(body.get("trace_id").asString()).hasSize(32);
    }

    @Test
    void 有效令牌写入UserContext() throws Exception {
        JsonNode body = perform(get("/api/v1/t/me").header("Authorization", token(1001L, 0)), 200);
        assertThat(body.get("data").get("user_id").asLong()).isEqualTo(1001L);
        assertThat(body.get("data").get("client").asString()).isEqualTo("APP_ANDROID");
        assertThat(UserContext.get()).isNull();
    }

    @Test
    void token_version不一致返回40104并带原因() throws Exception {
        when(authStateCache.hasLoader()).thenReturn(true);
        when(authStateCache.get(1001L)).thenReturn(new UserAuthState(2, "NORMAL", null, "PASSWORD_CHANGED"));
        JsonNode changed = perform(get("/api/v1/t/me").header("Authorization", token(1001L, 1)), 401);
        assertThat(changed.get("code").asInt()).isEqualTo(40104);
        assertThat(changed.get("data").get("reason").asString()).isEqualTo("PASSWORD_CHANGED");

        when(authStateCache.get(1001L)).thenReturn(new UserAuthState(2, "NORMAL", null, null));
        JsonNode noReason = perform(get("/api/v1/t/me").header("Authorization", token(1001L, 1)), 401);
        assertThat(noReason.get("data").get("reason").asString()).isEqualTo("EXPIRED");

        when(authStateCache.get(1002L)).thenReturn(null);
        JsonNode missing = perform(get("/api/v1/t/me").header("Authorization", token(1002L, 0)), 401);
        assertThat(missing.get("code").asInt()).isEqualTo(40104);
        assertThat(missing.get("data").get("reason").asString()).isEqualTo("EXPIRED");

        when(authStateCache.get(1001L)).thenReturn(new UserAuthState(1, "NORMAL", "NONE", null));
        perform(get("/api/v1/t/me").header("Authorization", token(1001L, 1)), 200);
    }

    @Test
    void 有强制步骤时只能调用AllowForcedStep接口() throws Exception {
        when(authStateCache.hasLoader()).thenReturn(true);
        when(authStateCache.get(1001L)).thenReturn(new UserAuthState(0, "NORMAL", "RECOVERY_CHANGE_PASSWORD", null));

        JsonNode blocked = perform(get("/api/v1/t/me").header("Authorization", token(1001L, 0)), 403);
        assertThat(blocked.get("code").asInt()).isEqualTo(40309);
        assertThat(blocked.get("data").get("next_step").asString()).isEqualTo("RECOVERY_CHANGE_PASSWORD");

        JsonNode allowed = perform(post("/api/v1/t/password").header("Authorization", token(1001L, 0)), 200);
        assertThat(allowed.get("data").get("user_id").asLong()).isEqualTo(1001L);
    }

    @Test
    void 白名单接口不要求登录_带有效令牌仍写入UserContext() throws Exception {
        assertThat(perform(get("/api/v1/t/public"), 200).get("data").get("user_id").asLong()).isZero();
        assertThat(perform(get("/api/v1/t/public").header("Authorization", "Bearer bad"), 200)
                .get("data").get("user_id").asLong()).isZero();
        assertThat(perform(get("/api/v1/t/public").header("Authorization", token(1003L, 0)), 200)
                .get("data").get("user_id").asLong()).isEqualTo(1003L);
    }
}
