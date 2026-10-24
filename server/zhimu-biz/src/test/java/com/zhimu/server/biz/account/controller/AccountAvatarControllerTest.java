package com.zhimu.server.biz.account.controller;

import com.zhimu.server.biz.account.service.AccountCancelService;
import com.zhimu.server.biz.account.service.AccountService;
import com.zhimu.server.biz.account.service.AuthService;
import com.zhimu.server.biz.account.service.AvatarService;
import com.zhimu.server.biz.account.service.RecoveryCodeService;
import com.zhimu.server.biz.account.test.AccountTestMvc;
import com.zhimu.server.biz.account.vo.AccountUserVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.security.token.WebRefreshCookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** API-A22/A23 头像绑定与清除：PUT/DELETE 200、未登录 401、文件不存在 40401。 */
class AccountAvatarControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final RecoveryCodeService recoveryCodeService = mock(RecoveryCodeService.class);
    private final AccountService accountService = mock(AccountService.class);
    private final AccountCancelService accountCancelService = mock(AccountCancelService.class);
    private final AvatarService avatarService = mock(AvatarService.class);
    private final WebRefreshCookie webRefreshCookie = mock(WebRefreshCookie.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AccountController controller = new AccountController(authService, recoveryCodeService, accountService,
                accountCancelService, avatarService, webRefreshCookie);
        mvc = AccountTestMvc.builder(controller).build();
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static AccountUserVO accountUser(String avatarUrl) {
        return AccountUserVO.builder().userId(10001L).name("王建国").nameInitial("王")
                .phoneMasked("138****6620").avatarUrl(avatarUrl).build();
    }

    @Test
    void PUT绑定头像返回200与avatar_url() throws Exception {
        when(avatarService.updateAvatar(900L)).thenReturn(accountUser("https://files.test/avatar/900.jpg"));

        var result = mvc.perform(put("/api/v1/account/avatar")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file_id\":900}"))
                .andReturn().getResponse();

        assertThat(result.getStatus()).isEqualTo(200);
        JsonNode json = AccountTestMvc.json(result.getContentAsString());
        assertThat(json.get("code").asInt()).isZero();
        assertThat(json.get("data").get("avatar_url").asString())
                .isEqualTo("https://files.test/avatar/900.jpg");
        assertThat(json.get("data").get("user_id").asLong()).isEqualTo(10001L);
        verify(avatarService).updateAvatar(900L);
    }

    @Test
    void DELETE清除头像返回200与空avatar_url() throws Exception {
        when(avatarService.clearAvatar()).thenReturn(accountUser(null));

        var result = mvc.perform(delete("/api/v1/account/avatar").header("X-Client", "APP_ANDROID"))
                .andReturn().getResponse();

        assertThat(result.getStatus()).isEqualTo(200);
        JsonNode data = AccountTestMvc.json(result.getContentAsString()).get("data");
        assertThat(data.has("avatar_url")).isTrue();
        assertThat(data.get("avatar_url").isNull()).isTrue();
        verify(avatarService).clearAvatar();
    }

    @Test
    void 未登录PUT返回401() throws Exception {
        UserContext.clear();

        var result = mvc.perform(put("/api/v1/account/avatar")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file_id\":900}"))
                .andReturn().getResponse();

        assertThat(result.getStatus()).isEqualTo(401);
        assertThat(AccountTestMvc.json(result.getContentAsString()).get("code").asInt()).isEqualTo(40101);
    }

    @Test
    void 未登录DELETE返回401() throws Exception {
        UserContext.clear();

        var result = mvc.perform(delete("/api/v1/account/avatar").header("X-Client", "APP_ANDROID"))
                .andReturn().getResponse();

        assertThat(result.getStatus()).isEqualTo(401);
        assertThat(AccountTestMvc.json(result.getContentAsString()).get("code").asInt()).isEqualTo(40101);
    }

    @Test
    void PUT文件不存在返回40401() throws Exception {
        when(avatarService.updateAvatar(999L)).thenThrow(new BizException(ErrorCode.NOT_FOUND));

        var result = mvc.perform(put("/api/v1/account/avatar")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file_id\":999}"))
                .andReturn().getResponse();

        assertThat(result.getStatus()).isEqualTo(404);
        assertThat(AccountTestMvc.json(result.getContentAsString()).get("code").asInt()).isEqualTo(40401);
    }
}
