package com.zhimu.server.biz.account.controller;

import com.zhimu.server.biz.account.service.SmsCodeService;
import com.zhimu.server.biz.account.test.AccountTestMvc;
import com.zhimu.server.biz.account.vo.SmsCodeSendVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class SmsCodeControllerMvcTest {

    private final SmsCodeService smsCodeService = mock(SmsCodeService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = AccountTestMvc.builder(new SmsCodeController(smsCodeService)).build();
    }

    @Test
    void A01发送验证码成功_不返回验证码本身() throws Exception {
        when(smsCodeService.send(any())).thenReturn(SmsCodeSendVO.builder()
                .phoneMasked("138****6620").cooldownSeconds(60).expireSeconds(300).build());
        JsonNode json = AccountTestMvc.json(mvc.perform(post("/api/v1/sms-codes")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800006620\",\"scene\":\"LOGIN\"}"))
                .andReturn().getResponse().getContentAsString());
        JsonNode data = json.get("data");
        assertThat(data.get("phone_masked").asString()).isEqualTo("138****6620");
        assertThat(data.get("cooldown_seconds").asInt()).isEqualTo(60);
        assertThat(data.get("expire_seconds").asInt()).isEqualTo(300);
        assertThat(data.has("code")).isFalse();
    }

    @Test
    void A01限流返回42901与cooldown_seconds() throws Exception {
        when(smsCodeService.send(any())).thenThrow(new BizException(ErrorCode.TOO_FREQUENT,
                "获取验证码太频繁，请稍后再试").with("cooldown_seconds", 42));
        var response = mvc.perform(post("/api/v1/sms-codes")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800006620\",\"scene\":\"LOGIN\"}"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(429);
        JsonNode data = AccountTestMvc.json(response.getContentAsString()).get("data");
        assertThat(data.get("cooldown_seconds").asInt()).isEqualTo(42);
    }

    @Test
    void A01未注册手机号40001带reason() throws Exception {
        when(smsCodeService.send(any())).thenThrow(new BizException(ErrorCode.PARAM_INVALID,
                "这个手机号还没有注册", java.util.Map.of("reason", "PHONE_NOT_REGISTERED",
                "field_errors", List.of(new com.zhimu.server.common.error.FieldErrorVO("phone", "这个手机号还没有注册")))));
        var response = mvc.perform(post("/api/v1/sms-codes")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800006620\",\"scene\":\"LOGIN\"}"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        JsonNode data = AccountTestMvc.json(response.getContentAsString()).get("data");
        assertThat(data.get("reason").asString()).isEqualTo("PHONE_NOT_REGISTERED");
    }

    @Test
    void A02发送到本人手机号() throws Exception {
        when(smsCodeService.sendToSelf(any())).thenReturn(SmsCodeSendVO.builder()
                .phoneMasked("138****6620").cooldownSeconds(60).expireSeconds(300).build());
        JsonNode data = AccountTestMvc.json(mvc.perform(post("/api/v1/account/sms-codes")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"REGENERATE_RECOVERY_CODE\"}"))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(data.get("phone_masked").asString()).isEqualTo("138****6620");
    }

    @Test
    void A02注销场景阻止返回40310() throws Exception {
        when(smsCodeService.sendToSelf(any())).thenThrow(new BizException(ErrorCode.CANCEL_NOT_ALLOWED,
                "东山猪场仍在使用，请先转让给场长或停用后再注销")
                .with("block_reasons", List.of("OWNED_FARM_ACTIVE")));
        var response = mvc.perform(post("/api/v1/account/sms-codes")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scene\":\"CANCEL_ACCOUNT\"}"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(403);
        JsonNode json = AccountTestMvc.json(response.getContentAsString());
        assertThat(json.get("code").asInt()).isEqualTo(40310);
        assertThat(json.get("message").asString()).contains("东山猪场");
        assertThat(json.get("data").get("block_reasons").toString()).contains("OWNED_FARM_ACTIVE");
    }
}
