package com.zhimu.server.admin.system.controller;

import com.zhimu.server.admin.system.request.AdminLoginRequest;
import com.zhimu.server.admin.system.service.AdminAuthService;
import com.zhimu.server.admin.system.service.CaptchaService;
import com.zhimu.server.admin.system.vo.AdminLoginVO;
import com.zhimu.server.admin.system.vo.AdminMeVO;
import com.zhimu.server.admin.system.vo.AdminProfileVO;
import com.zhimu.server.admin.system.vo.CaptchaVO;
import com.zhimu.server.admin.system.vo.MenuGroupVO;
import com.zhimu.server.admin.system.vo.MenuPageVO;
import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.security.session.AdminSessionService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class AdminAuthControllerMvcTest {

    private final AdminAuthService authService = mock(AdminAuthService.class);
    private final CaptchaService captchaService = mock(CaptchaService.class);
    private final AdminSessionService sessionService = mock(AdminSessionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestMvc.builder(new AdminAuthController(authService, captchaService, sessionService)).build();
        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(0);
            response.addHeader("Set-Cookie", "ZMT_ADMIN_SESSION=" + invocation.getArgument(1)
                    + "; Path=/admin/v1; HttpOnly; SameSite=Strict");
            return null;
        }).when(sessionService).writeCookie(any(), any());
        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(0);
            response.addHeader("Set-Cookie", "ZMT_ADMIN_SESSION=; Path=/admin/v1; Max-Age=0");
            return null;
        }).when(sessionService).clearCookie(any());
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void S01验证码返回captcha_id与PNG_data_url() throws Exception {
        when(captchaService.create()).thenReturn(CaptchaVO.builder()
                .captchaId("abc123").image("data:image/png;base64,iVBORw0KGgo=").expireSeconds(120).build());

        JsonNode data = TestMvc.json(mvc.perform(get("/admin/v1/auth/captcha"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get("captcha_id").asString()).isEqualTo("abc123");
        assertThat(data.get("image").asString()).startsWith("data:image/png;base64,");
        assertThat(data.get("expire_seconds").asInt()).isEqualTo(120);
    }

    private static AdminLoginVO loginVO() {
        return AdminLoginVO.builder()
                .admin(AdminProfileVO.builder().adminId(1L).name("周敏").username("zhoumin")
                        .roleCode("SUPER_ADMIN").roleCodeText("超级管理员").build())
                .mustChangePassword(false)
                .idleMinutes(30)
                .permissions(List.of("WA-S02:VIEW", "WA-S05:HANDLE"))
                .menus(List.of(MenuGroupVO.builder().groupCode("OVERVIEW").groupName("平台概览")
                        .pages(List.of(MenuPageVO.builder().pageCode("WA-S02").pageName("平台概览").build()))
                        .build()))
                .build();
    }

    @Test
    void S02登录成功_响应体不含令牌_通过Cookie下发会话() throws Exception {
        when(authService.login(any(AdminLoginRequest.class)))
                .thenReturn(new AdminAuthService.AdminLoginResult(loginVO(), "tok-123"));

        var result = mvc.perform(post("/admin/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zhoumin\",\"password\":\"Zmt12345\","
                                + "\"captcha_id\":\"cid\",\"captcha_code\":\"ab2c\"}"))
                .andReturn();
        JsonNode json = TestMvc.json(result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("ZMT_ADMIN_SESSION=tok-123").contains("Path=/admin/v1").contains("HttpOnly");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("tok-123");
        JsonNode data = json.get("data");
        assertThat(data.get("admin").get("admin_id").asLong()).isEqualTo(1L);
        assertThat(data.get("admin").get("role_code_text").asString()).isEqualTo("超级管理员");
        assertThat(data.get("must_change_password").asBoolean()).isFalse();
        assertThat(data.get("idle_minutes").asInt()).isEqualTo(30);
        assertThat(data.get("permissions").get(0).asString()).isEqualTo("WA-S02:VIEW");
        assertThat(data.get("menus").get(0).get("group_code").asString()).isEqualTo("OVERVIEW");
        verify(sessionService).writeCookie(any(), eq("tok-123"));
    }

    @Test
    void S02登录失败40102带remaining() throws Exception {
        when(authService.login(any())).thenThrow(new BizException(ErrorCode.PASSWORD_WRONG,
                "账号或密码不正确，还可以再试 2 次").with("remaining", 2));

        var response = mvc.perform(post("/admin/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zhoumin\",\"password\":\"bad\","
                                + "\"captcha_id\":\"cid\",\"captcha_code\":\"ab2c\"}"))
                .andReturn().getResponse();
        JsonNode json = TestMvc.json(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(json.get("code").asInt()).isEqualTo(40102);
        assertThat(json.get("data").get("remaining").asInt()).isEqualTo(2);
    }

    @Test
    void S02登录锁定42902带locked_until() throws Exception {
        when(authService.login(any())).thenThrow(new BizException(ErrorCode.LOGIN_LOCKED,
                "账号已锁定。密码连续错误 5 次，10:52 后可以再次登录，或联系超级管理员解锁。")
                .with("locked_until", java.time.LocalDateTime.of(2026, 10, 19, 10, 52)));

        var response = mvc.perform(post("/admin/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zhoumin\",\"password\":\"bad\","
                                + "\"captcha_id\":\"cid\",\"captcha_code\":\"ab2c\"}"))
                .andReturn().getResponse();
        JsonNode json = TestMvc.json(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(json.get("code").asInt()).isEqualTo(42902);
        assertThat(json.get("data").get("locked_until").asString()).isEqualTo("2026-10-19 10:52:00");
    }

    @Test
    void S02缺字段返回40001与field_errors() throws Exception {
        var response = mvc.perform(post("/admin/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"x\",\"captcha_id\":\"c\",\"captcha_code\":\"ab2c\"}"))
                .andReturn().getResponse();
        JsonNode json = TestMvc.json(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(json.get("code").asInt()).isEqualTo(40001);
        assertThat(json.get("data").get("field_errors").get(0).get("field").asString()).isEqualTo("username");
    }

    @Test
    void S03退出删除会话并清Cookie() throws Exception {
        var response = mvc.perform(post("/admin/v1/auth/logout")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
        verify(authService).logout();
        verify(sessionService).clearCookie(any());
    }

    @Test
    void S04改密成功清Cookie() throws Exception {
        var response = mvc.perform(put("/admin/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"old_password\":\"Old12345\",\"new_password\":\"New12345\","
                                + "\"confirm_password\":\"New12345\"}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
        verify(authService).changePassword(any());
        verify(sessionService).clearCookie(any());
    }

    @Test
    void S04改密失败返回错误码且不清Cookie() throws Exception {
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.PASSWORD_WRONG, "原密码不正确"))
                .when(authService).changePassword(any());

        var response = mvc.perform(put("/admin/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"old_password\":\"bad\",\"new_password\":\"New12345\","
                                + "\"confirm_password\":\"New12345\"}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(TestMvc.json(response.getContentAsString()).get("code").asInt()).isEqualTo(40102);
        verify(sessionService, org.mockito.Mockito.never()).clearCookie(any());
    }

    @Test
    void S05返回角标与未读数() throws Exception {
        AdminMeVO me = AdminMeVO.from(loginVO());
        me.setBadges(Map.of("farm_audit", 12, "farm_change", 3,
                "change_request", 8, "account_recovery", 2));
        me.setUnreadMessageCount(5);
        when(authService.me()).thenReturn(me);

        JsonNode data = TestMvc.json(mvc.perform(get("/admin/v1/auth/me"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get("badges").get("farm_audit").asInt()).isEqualTo(12);
        assertThat(data.get("unread_message_count").asInt()).isEqualTo(5);
        assertThat(data.get("admin").get("username").asString()).isEqualTo("zhoumin");
    }
}
