package com.zhimu.server.biz.account.controller;

import com.zhimu.server.biz.account.service.AccountRecoveryService;
import com.zhimu.server.biz.account.test.AccountTestMvc;
import com.zhimu.server.biz.account.vo.AccountRecoverySubmitVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AccountRecoveryControllerMvcTest {

    private final AccountRecoveryService service = mock(AccountRecoveryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = AccountTestMvc.builder(new AccountRecoveryController(service)).build();
    }

    private static String requestBody() {
        return "{\"old_phone\":\"13800006620\",\"recovery_code\":\"8KQ4T2MW7HXN3PDR\","
                + "\"new_phone\":\"15147602288\",\"new_phone_sms_code\":\"123456\","
                + "\"description\":\"原手机卡丢失，已经停机，现在换了新号码。\"}";
    }

    @Test
    void A10提交找回申请成功() throws Exception {
        when(service.submit(any(), any())).thenReturn(AccountRecoverySubmitVO.builder()
                .recoveryId(3021L).newPhoneMasked("151****2288").status("PENDING")
                .statusText("待审核").submittedAt("2026-10-19 09:40:00").build());
        JsonNode data = AccountTestMvc.json(mvc.perform(post("/api/v1/account-recoveries")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(data.get("recovery_id").asLong()).isEqualTo(3021L);
        assertThat(data.get("new_phone_masked").asString()).isEqualTo("151****2288");
        assertThat(data.get("status_text").asString()).isEqualTo("待审核");
    }

    @Test
    void A10恢复码错误40105带remaining_attempts与field() throws Exception {
        when(service.submit(any(), any())).thenThrow(new BizException(ErrorCode.RECOVERY_CODE_WRONG,
                "恢复码不正确，还可以再试 3 次", Map.of(
                "remaining_attempts", 3,
                "field_errors", List.of(new FieldErrorVO("recovery_code", "恢复码不正确，还可以再试 3 次")))));
        var response = mvc.perform(post("/api/v1/account-recoveries")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode json = AccountTestMvc.json(response.getContentAsString());
        assertThat(json.get("code").asInt()).isEqualTo(40105);
        assertThat(json.get("data").get("remaining_attempts").asInt()).isEqualTo(3);
        assertThat(json.get("data").get("field_errors").get(0).get("field").asString()).isEqualTo("recovery_code");
    }

    @Test
    void A10找回锁定42903带unlock_at() throws Exception {
        when(service.submit(any(), any())).thenThrow(new BizException(ErrorCode.RECOVERY_LOCKED,
                "恢复码已连续填错 5 次。10月20日 09:30 后才能再次提交找回申请。")
                .with("unlock_at", "2026-10-20 09:30:00"));
        var response = mvc.perform(post("/api/v1/account-recoveries")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(429);
        JsonNode data = AccountTestMvc.json(response.getContentAsString()).get("data");
        assertThat(data.get("unlock_at").asString()).isEqualTo("2026-10-20 09:30:00");
    }

    @Test
    void A10已有待审核申请40906() throws Exception {
        when(service.submit(any(), any())).thenThrow(new BizException(ErrorCode.PENDING_EXISTS,
                "这个账号已有一条找回申请正在处理，请等待短信通知"));
        var response = mvc.perform(post("/api/v1/account-recoveries")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(AccountTestMvc.json(response.getContentAsString()).get("code").asInt()).isEqualTo(40906);
    }
}
