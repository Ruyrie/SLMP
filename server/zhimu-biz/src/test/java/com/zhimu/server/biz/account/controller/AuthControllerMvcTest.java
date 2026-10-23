package com.zhimu.server.biz.account.controller;

import com.zhimu.server.biz.account.service.AuthService;
import com.zhimu.server.biz.account.test.AccountTestMvc;
import com.zhimu.server.biz.account.vo.AccountUserVO;
import com.zhimu.server.biz.account.vo.AuthTokenVO;
import com.zhimu.server.biz.account.vo.SessionVO;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import com.zhimu.server.framework.security.token.WebRefreshCookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthControllerMvcTest {

    private final AuthService authService = mock(AuthService.class);
    private final WebRefreshCookie webRefreshCookie = mock(WebRefreshCookie.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService, webRefreshCookie);
        mvc = AccountTestMvc.builder(controller).build();
    }

    private static AuthTokenVO token() {
        return AuthTokenVO.builder()
                .accessToken("access").tokenType("Bearer").expiresIn(7200).refreshExpiresIn(2592000)
                .refreshToken("refresh").nextStep("NONE").nextStepText("无需操作")
                .user(AccountUserVO.builder().userId(10001L).name("王建国").nameInitial("王")
                        .phoneMasked("138****6620").build())
                .lastFarmId(1001L)
                .build();
    }

    @Test
    void A03注册_App返回refresh_token与next_step() throws Exception {
        when(authService.register(any(), eq(ClientType.APP_ANDROID))).thenReturn(AuthTokenVO.builder()
                .accessToken("access").refreshToken("refresh").nextStep("SAVE_RECOVERY_CODE")
                .nextStepText("请保存恢复码")
                .user(AccountUserVO.builder().userId(10001L).name("用户6620").nameInitial("用").build())
                .build());
        JsonNode json = AccountTestMvc.json(mvc.perform(post("/api/v1/auth/register")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800006620\",\"sms_code\":\"123456\",\"password\":\"Zmt12345\","
                                + "\"agree_terms\":true,\"agreement_version\":\"2026-10-01\",\"device_id\":\"d\"}"))
                .andReturn().getResponse().getContentAsString());
        JsonNode data = json.get("data");
        assertThat(json.get("code").asInt()).isZero();
        assertThat(data.get("refresh_token").asString()).isEqualTo("refresh");
        assertThat(data.get("next_step").asString()).isEqualTo("SAVE_RECOVERY_CODE");
    }

    @Test
    void A03注册_Web用Cookie下发且响应体无refresh_token() throws Exception {
        when(authService.register(any(), eq(ClientType.WEB))).thenReturn(token());
        JsonNode data = AccountTestMvc.json(mvc.perform(post("/api/v1/auth/register")
                        .header("X-Client", "WEB").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800006620\",\"sms_code\":\"123456\",\"password\":\"Zmt12345\","
                                + "\"agree_terms\":true,\"agreement_version\":\"2026-10-01\",\"device_id\":\"d\"}"))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(data.has("refresh_token")).isFalse();
        verify(webRefreshCookie).write(any(), eq("refresh"));
    }

    @Test
    void A04密码登录成功() throws Exception {
        when(authService.loginByPassword(any(), eq(ClientType.APP_ANDROID))).thenReturn(token());
        JsonNode data = AccountTestMvc.json(mvc.perform(post("/api/v1/auth/login/password")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800006620\",\"password\":\"Zmt12345\",\"device_id\":\"d\"}"))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(data.get("access_token").asString()).isEqualTo("access");
        assertThat(data.get("last_farm_id").asLong()).isEqualTo(1001L);
    }

    @Test
    void A04密码错误40102_带field_errors() throws Exception {
        when(authService.loginByPassword(any(), any())).thenThrow(new BizException(ErrorCode.PASSWORD_WRONG,
                "手机号或密码不正确", Map.of("field_errors",
                List.of(new FieldErrorVO("password", "手机号或密码不正确")))));
        var response = mvc.perform(post("/api/v1/auth/login/password")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800006620\",\"password\":\"wrong\",\"device_id\":\"d\"}"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode json = AccountTestMvc.json(response.getContentAsString());
        assertThat(json.get("code").asInt()).isEqualTo(40102);
        assertThat(json.get("message").asString()).isEqualTo("手机号或密码不正确");
        assertThat(json.get("data").get("field_errors").get(0).get("field").asString()).isEqualTo("password");
    }

    @Test
    void A04密码锁定42902_带unlock_at() throws Exception {
        when(authService.loginByPassword(any(), any())).thenThrow(new BizException(ErrorCode.LOGIN_LOCKED,
                "密码连续错误 5 次，10月19日 10:00 后可以再试，也可以用验证码登录")
                .with("unlock_at", "2026-10-19 10:00:00"));
        var response = mvc.perform(post("/api/v1/auth/login/password")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800006620\",\"password\":\"wrong\",\"device_id\":\"d\"}"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(429);
        JsonNode json = AccountTestMvc.json(response.getContentAsString());
        assertThat(json.get("data").get("unlock_at").asString()).isEqualTo("2026-10-19 10:00:00");
    }

    @Test
    void A05验证码登录成功() throws Exception {
        when(authService.loginBySms(any(), eq(ClientType.APP_ANDROID))).thenReturn(token());
        JsonNode data = AccountTestMvc.json(mvc.perform(post("/api/v1/auth/login/sms")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800006620\",\"sms_code\":\"123456\",\"device_id\":\"d\"}"))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(data.get("access_token").asString()).isEqualTo("access");
    }

    @Test
    void A06刷新_App从请求体取refresh_token() throws Exception {
        when(authService.refresh("old-refresh", ClientType.APP_ANDROID)).thenReturn(token());
        JsonNode data = AccountTestMvc.json(mvc.perform(post("/api/v1/auth/token/refresh")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"old-refresh\"}"))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(data.get("refresh_token").asString()).isEqualTo("refresh");
    }

    @Test
    void A06刷新_Web没有Cookie返回40104_EXPIRED() throws Exception {
        when(webRefreshCookie.read(any())).thenReturn(null);
        var response = mvc.perform(post("/api/v1/auth/token/refresh").header("X-Client", "WEB"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode json = AccountTestMvc.json(response.getContentAsString());
        assertThat(json.get("code").asInt()).isEqualTo(40104);
        assertThat(json.get("data").get("reason").asString()).isEqualTo("EXPIRED");
    }

    @Test
    void A06刷新_Web从Cookie读取并重新写Cookie() throws Exception {
        when(webRefreshCookie.read(any())).thenReturn("cookie-refresh");
        when(authService.refresh("cookie-refresh", ClientType.WEB)).thenReturn(token());
        JsonNode data = AccountTestMvc.json(mvc.perform(post("/api/v1/auth/token/refresh").header("X-Client", "WEB"))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(data.has("refresh_token")).isFalse();
        verify(webRefreshCookie).write(any(), eq("refresh"));
    }

    @Test
    void A07退出_Web清Cookie() throws Exception {
        var response = mvc.perform(post("/api/v1/auth/logout").header("X-Client", "WEB"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(webRefreshCookie).clear(any());
    }

    @Test
    void A08会话返回next_step与last_farm_id() throws Exception {
        when(authService.session(ClientType.APP_ANDROID)).thenReturn(SessionVO.builder()
                .user(AccountUserVO.builder().userId(10001L).name("王建国").build())
                .nextStep("NONE").nextStepText("无需操作").lastFarmId(1001L).build());
        JsonNode data = AccountTestMvc.json(mvc.perform(get("/api/v1/auth/session")
                        .header("X-Client", "APP_ANDROID"))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(data.get("next_step").asString()).isEqualTo("NONE");
        assertThat(data.get("next_step_text").asString()).isEqualTo("无需操作");
        assertThat(data.get("last_farm_id").asLong()).isEqualTo(1001L);
    }

    @Test
    void A09重置密码返回200且data为空() throws Exception {
        JsonNode json = AccountTestMvc.json(mvc.perform(post("/api/v1/auth/password-resets")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800006620\",\"sms_code\":\"123456\",\"new_password\":\"NewPass123\"}"))
                .andReturn().getResponse().getContentAsString());
        assertThat(json.get("code").asInt()).isZero();
        assertThat(json.has("data")).isFalse();
    }

    @Test
    void 缺少X_Client返回40101() throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800006620\",\"password\":\"x\",\"device_id\":\"d\"}"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(AccountTestMvc.json(response.getContentAsString()).get("code").asInt()).isEqualTo(40101);
    }
}
