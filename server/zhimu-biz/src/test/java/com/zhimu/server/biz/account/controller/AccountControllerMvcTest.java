package com.zhimu.server.biz.account.controller;

import com.zhimu.server.biz.account.service.AccountCancelService;
import com.zhimu.server.biz.account.service.AccountService;
import com.zhimu.server.biz.account.service.AuthService;
import com.zhimu.server.biz.account.service.AvatarService;
import com.zhimu.server.biz.account.service.RecoveryCodeService;
import com.zhimu.server.biz.account.test.AccountTestMvc;
import com.zhimu.server.biz.account.vo.AccountFarmStatsVO;
import com.zhimu.server.biz.account.vo.AccountOverviewVO;
import com.zhimu.server.biz.account.vo.AccountSecurityVO;
import com.zhimu.server.biz.account.vo.AccountUserVO;
import com.zhimu.server.biz.account.vo.AuthTokenVO;
import com.zhimu.server.biz.account.vo.CancelExitFarmVO;
import com.zhimu.server.biz.account.vo.CancelOwnedFarmVO;
import com.zhimu.server.biz.account.vo.CancellationPreviewVO;
import com.zhimu.server.biz.account.vo.ProfileVO;
import com.zhimu.server.biz.account.vo.RecoveryCodeConfirmVO;
import com.zhimu.server.biz.account.vo.RecoveryCodeVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.common.vo.ReminderSummaryVO;
import com.zhimu.server.framework.security.token.WebRefreshCookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class AccountControllerMvcTest {

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

    private static AccountOverviewVO overview() {
        return AccountOverviewVO.builder()
                .user(AccountUserVO.builder().userId(10001L).name("王建国").nameInitial("王")
                        .phoneMasked("138****6620").build())
                .profileVersion(3)
                .currentFarm(FarmBriefVO.builder().farmId(1001L).name("东山猪场").speciesText("育肥猪")
                        .status("NORMAL").statusText("正常").role("OWNER").build())
                .farmStats(AccountFarmStatsVO.builder().totalCount(3).pendingAuditCount(1).rejectedCount(0).build())
                .changeRequestPendingCount(1)
                .reminderSummary(ReminderSummaryVO.builder().digestEnabled(true).digestTime(LocalTime.of(7, 0))
                        .quietEnabled(true).quietStart(LocalTime.of(22, 0)).quietEnd(LocalTime.of(7, 0)).build())
                .security(AccountSecurityVO.builder().phoneMasked("138****6620")
                        .passwordUpdatedAt("2026-10-01 08:00:00").cancelRestricted(false).build())
                .build();
    }

    @Test
    void A15_读取X_Farm_Id并返回完整结构() throws Exception {
        when(accountService.overview(10001L, 1001L, ClientType.APP_ANDROID)).thenReturn(overview());

        String body = mvc.perform(get("/api/v1/account/overview")
                        .header("X-Client", "APP_ANDROID").header("X-Farm-Id", "1001"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = AccountTestMvc.json(body).get("data");

        assertThat(AccountTestMvc.json(body).get("code").asInt()).isZero();
        assertThat(json.get("user").get("name").asString()).isEqualTo("王建国");
        assertThat(json.get("profile_version").asInt()).isEqualTo(3);
        assertThat(json.get("current_farm").get("farm_id").asLong()).isEqualTo(1001L);
        assertThat(json.get("farm_stats").get("total_count").asInt()).isEqualTo(3);
        assertThat(json.get("change_request_pending_count").asInt()).isEqualTo(1);
        assertThat(json.get("reminder_summary").get("digest_time").asString()).isEqualTo("07:00");
        assertThat(json.get("security").get("phone_masked").asString()).isEqualTo("138****6620");
    }

    @Test
    void A15_没有X_Farm_Id时传null() throws Exception {
        when(accountService.overview(10001L, null, ClientType.APP_ANDROID)).thenReturn(overview());
        mvc.perform(get("/api/v1/account/overview").header("X-Client", "APP_ANDROID"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
        verify(accountService).overview(10001L, null, ClientType.APP_ANDROID);
    }

    @Test
    void A16_PATCH修改姓名() throws Exception {
        when(accountService.updateProfile(any())).thenReturn(ProfileVO.builder().name("王建军")
                .nameInitial("王").version(4).build());
        String body = mvc.perform(patch("/api/v1/account/profile")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"王建军\",\"version\":3}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = AccountTestMvc.json(body).get("data");
        assertThat(data.get("name").asString()).isEqualTo("王建军");
        assertThat(data.get("version").asInt()).isEqualTo(4);
    }

    @Test
    void A17_Web改密用Cookie下发且响应体无refresh_token() throws Exception {
        when(authService.changePassword(any(), eq(ClientType.WEB))).thenReturn(AuthTokenVO.builder()
                .accessToken("new-access").refreshToken("new-refresh").nextStep("NONE").build());
        String body = mvc.perform(put("/api/v1/account/password")
                        .header("X-Client", "WEB").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"Zmt12345\",\"new_password\":\"NewPass123\","
                                + "\"confirm_password\":\"NewPass123\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = AccountTestMvc.json(body).get("data");
        assertThat(data.get("access_token").asString()).isEqualTo("new-access");
        assertThat(data.has("refresh_token")).isFalse();
        verify(webRefreshCookie).write(any(), eq("new-refresh"));
    }

    @Test
    void A11_找回后改密() throws Exception {
        when(authService.changePasswordAfterRecovery(any(), eq(ClientType.APP_ANDROID))).thenReturn(
                AuthTokenVO.builder().accessToken("a").nextStep("SAVE_RECOVERY_CODE")
                        .nextStepText("请保存恢复码")
                        .recoveryCode(RecoveryCodeVO.builder().code("5NWC8RJTK3YB6FHA").build()).build());
        String body = mvc.perform(put("/api/v1/account/password/after-recovery")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"new_password\":\"NewPass123\",\"confirm_password\":\"NewPass123\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = AccountTestMvc.json(body).get("data");
        assertThat(data.get("next_step").asString()).isEqualTo("SAVE_RECOVERY_CODE");
        assertThat(data.get("recovery_code").get("code").asString()).isEqualTo("5NWC8RJTK3YB6FHA");
    }

    @Test
    void A12_A13_A14恢复码接口路径与字段() throws Exception {
        when(recoveryCodeService.regeneratePending(ClientType.APP_ANDROID)).thenReturn(RecoveryCodeVO.builder()
                .recoveryCodeId(503L).code("8KQ4T2MW7HXN3PDR").scene("REGISTER").sceneText("注册")
                .isRegenerated(true).build());
        when(recoveryCodeService.regenerateBySms(any(), eq(ClientType.APP_ANDROID))).thenReturn(
                RecoveryCodeVO.builder().recoveryCodeId(504L).code("7HXN3PDR8KQ4T2MW").scene("REGENERATE")
                        .sceneText("重新生成").isRegenerated(false).build());
        when(recoveryCodeService.confirm(501L)).thenReturn(RecoveryCodeConfirmVO.builder()
                .recoveryCodeId(501L).status("ACTIVE").nextStep("NONE").build());

        JsonNode pending = AccountTestMvc.json(mvc.perform(post("/api/v1/account/recovery-codes/pending")
                        .header("X-Client", "APP_ANDROID"))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(pending.get("is_regenerated").asBoolean()).isTrue();

        JsonNode regenerate = AccountTestMvc.json(mvc.perform(post("/api/v1/account/recovery-codes")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sms_code\":\"123456\"}"))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(regenerate.get("scene").asString()).isEqualTo("REGENERATE");

        JsonNode confirm = AccountTestMvc.json(mvc.perform(post("/api/v1/account/recovery-codes/501/confirm")
                        .header("X-Client", "APP_ANDROID"))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(confirm.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(confirm.get("next_step").asString()).isEqualTo("NONE");
    }

    @Test
    void A19注销预览字段完整() throws Exception {
        when(accountCancelService.preview()).thenReturn(CancellationPreviewVO.builder()
                .phoneMasked("138****6620").personalDataDesc("消息、AI 问答、待办设置、恢复码")
                .phoneReleaseDate("2026-11-18").checklistVersion("a1b2c3d4e5f60718").canCancel(false)
                .blockReasons(List.of("OWNED_FARM_ACTIVE")).cancelAvailableDate(null)
                .ownedFarms(List.of(CancelOwnedFarmVO.builder().farmId(1001L).name("东山猪场")
                        .farmStatus("NORMAL").farmStatusText("正常").disposal("TRANSFER_OR_DISABLE")
                        .disposalText("仍在使用，须先转让或停用").currentStock(870).countUnitText("头")
                        .memberCount(3).transferable(true).build()))
                .transferableFarmCount(1)
                .exitFarms(List.of(CancelExitFarmVO.builder().farmId(1002L).name("西岭鸡场")
                        .role("MANAGER").roleText("场长").build()))
                .build());
        JsonNode data = AccountTestMvc.json(mvc.perform(get("/api/v1/account/cancellation-preview")
                        .header("X-Client", "APP_ANDROID"))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(data.get("can_cancel").asBoolean()).isFalse();
        assertThat(data.get("block_reasons").toString()).contains("OWNED_FARM_ACTIVE");
        assertThat(data.get("owned_farms").get(0).get("current_stock").asInt()).isEqualTo(870);
        assertThat(data.get("exit_farms").get(0).get("role_text").asString()).isEqualTo("场长");
    }

    @Test
    void A20注销成功data为空() throws Exception {
        String body = mvc.perform(post("/api/v1/account/cancellation")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sms_code\":\"123456\",\"confirmed\":true,\"checklist_version\":\"v\"}"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = AccountTestMvc.json(body);
        assertThat(json.get("code").asInt()).isZero();
        assertThat(json.has("data")).isFalse();
    }

    @Test
    void A20阻止注销返回40310与场名() throws Exception {
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.CANCEL_NOT_ALLOWED,
                "东山猪场仍在使用，请先转让给场长或停用后再注销")
                .with("block_reasons", List.of("OWNED_FARM_ACTIVE")))
                .when(accountCancelService).cancel(any(), any());
        var result = mvc.perform(post("/api/v1/account/cancellation")
                        .header("X-Client", "APP_ANDROID").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sms_code\":\"123456\",\"confirmed\":true,\"checklist_version\":\"v\"}"))
                .andReturn().getResponse();
        assertThat(result.getStatus()).isEqualTo(403);
        JsonNode json = AccountTestMvc.json(result.getContentAsString());
        assertThat(json.get("code").asInt()).isEqualTo(40310);
        assertThat(json.get("message").asString()).contains("东山猪场");
        assertThat(json.get("data").get("block_reasons").toString()).contains("OWNED_FARM_ACTIVE");
    }

    @Test
    void 缺少X_Client返回40101() throws Exception {
        var result = mvc.perform(post("/api/v1/account/cancellation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sms_code\":\"123456\",\"confirmed\":true,\"checklist_version\":\"v\"}"))
                .andReturn().getResponse();
        assertThat(result.getStatus()).isEqualTo(401);
        assertThat(AccountTestMvc.json(result.getContentAsString()).get("code").asInt()).isEqualTo(40101);
    }
}
