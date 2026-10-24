package com.zhimu.server.biz.account.controller;

import com.zhimu.server.biz.account.request.AccountAvatarRequest;
import com.zhimu.server.biz.account.request.AccountCancelRequest;
import com.zhimu.server.biz.account.request.AfterRecoveryPasswordRequest;
import com.zhimu.server.biz.account.request.PasswordChangeRequest;
import com.zhimu.server.biz.account.request.ProfileUpdateRequest;
import com.zhimu.server.biz.account.request.RecoveryCodeRegenerateRequest;
import com.zhimu.server.biz.account.service.AccountCancelService;
import com.zhimu.server.biz.account.service.AccountService;
import com.zhimu.server.biz.account.service.AuthService;
import com.zhimu.server.biz.account.service.AvatarService;
import com.zhimu.server.biz.account.service.RecoveryCodeService;
import com.zhimu.server.biz.account.support.Clients;
import com.zhimu.server.biz.account.vo.AccountOverviewVO;
import com.zhimu.server.biz.account.vo.AccountUserVO;
import com.zhimu.server.biz.account.vo.AuthTokenVO;
import com.zhimu.server.biz.account.vo.CancellationPreviewVO;
import com.zhimu.server.biz.account.vo.ProfileVO;
import com.zhimu.server.biz.account.vo.RecoveryCodeConfirmVO;
import com.zhimu.server.biz.account.vo.RecoveryCodeVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.security.annotation.AllowForcedStep;
import com.zhimu.server.framework.security.token.WebRefreshCookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-A11–A17、A19、A20 账号与个人中心。 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

    private final AuthService authService;
    private final RecoveryCodeService recoveryCodeService;
    private final AccountService accountService;
    private final AccountCancelService accountCancelService;
    private final AvatarService avatarService;
    private final WebRefreshCookie webRefreshCookie;

    /** API-A11 找回后修改密码（强制步骤） */
    @AllowForcedStep
    @PutMapping("/password/after-recovery")
    public ApiResponse<AuthTokenVO> changePasswordAfterRecovery(
            @RequestBody AfterRecoveryPasswordRequest request,
            @RequestHeader(value = "X-Client", required = false) String client,
            HttpServletResponse response) {
        ClientType type = Clients.require(client);
        return ApiResponse.ok(withWebCookie(authService.changePasswordAfterRecovery(request, type), type, response));
    }

    /** API-A12 重新生成待确认恢复码（强制步骤） */
    @AllowForcedStep
    @PostMapping("/recovery-codes/pending")
    public ApiResponse<RecoveryCodeVO> regeneratePending(
            @RequestHeader(value = "X-Client", required = false) String client) {
        return ApiResponse.ok(recoveryCodeService.regeneratePending(Clients.require(client)));
    }

    /** API-A13 短信验证后重新生成恢复码 */
    @PostMapping("/recovery-codes")
    public ApiResponse<RecoveryCodeVO> regenerate(@RequestBody RecoveryCodeRegenerateRequest request,
                                                  @RequestHeader(value = "X-Client", required = false) String client) {
        return ApiResponse.ok(recoveryCodeService.regenerateBySms(request, Clients.require(client)));
    }

    /** API-A14 确认已保存恢复码 */
    @AllowForcedStep
    @PostMapping("/recovery-codes/{id:\\d+}/confirm")
    public ApiResponse<RecoveryCodeConfirmVO> confirm(@PathVariable("id") Long id,
                                                      @RequestHeader(value = "X-Client", required = false) String client) {
        Clients.require(client);
        return ApiResponse.ok(recoveryCodeService.confirm(id));
    }

    /** API-A15 我的页汇总：只有本接口读取 X-Farm-Id（可选，不挂 @RequireFarm） */
    @GetMapping("/overview")
    public ApiResponse<AccountOverviewVO> overview(
            @RequestHeader(value = "X-Farm-Id", required = false) Long farmId,
            @RequestHeader(value = "X-Client", required = false) String client) {
        return ApiResponse.ok(accountService.overview(UserContext.requireUserId(), farmId, Clients.orApp(client)));
    }

    /** API-A16 修改姓名 */
    @PatchMapping("/profile")
    public ApiResponse<ProfileVO> updateProfile(@RequestBody ProfileUpdateRequest request,
                                                @RequestHeader(value = "X-Client", required = false) String client) {
        Clients.orApp(client);
        return ApiResponse.ok(accountService.updateProfile(request));
    }

    /** 绑定真实头像：file_id 来自 POST /api/v1/files?scene=AVATAR */
    @Idempotent
    @PutMapping("/avatar")
    public ApiResponse<AccountUserVO> updateAvatar(@RequestBody AccountAvatarRequest request,
                                                   @RequestHeader(value = "X-Client", required = false) String client) {
        Clients.orApp(client);
        UserContext.requireUserId();
        return ApiResponse.ok(avatarService.updateAvatar(request == null ? null : request.fileId()));
    }

    /** 清除真实头像 */
    @Idempotent
    @DeleteMapping("/avatar")
    public ApiResponse<AccountUserVO> clearAvatar(
            @RequestHeader(value = "X-Client", required = false) String client) {
        Clients.orApp(client);
        UserContext.requireUserId();
        return ApiResponse.ok(avatarService.clearAvatar());
    }

    /** API-A17 修改密码 */
    @PutMapping("/password")
    public ApiResponse<AuthTokenVO> changePassword(@RequestBody PasswordChangeRequest request,
                                                   @RequestHeader(value = "X-Client", required = false) String client,
                                                   HttpServletResponse response) {
        ClientType type = Clients.require(client);
        return ApiResponse.ok(withWebCookie(authService.changePassword(request, type), type, response));
    }

    /** API-A19 注销预览 */
    @GetMapping("/cancellation-preview")
    public ApiResponse<CancellationPreviewVO> cancellationPreview(
            @RequestHeader(value = "X-Client", required = false) String client) {
        Clients.orApp(client);
        return ApiResponse.ok(accountCancelService.preview());
    }

    /** API-A20 注销账号 */
    @Idempotent(required = true)
    @PostMapping("/cancellation")
    public ApiResponse<Void> cancel(@RequestBody AccountCancelRequest request,
                                    @RequestHeader(value = "X-Client", required = false) String client,
                                    HttpServletResponse response) {
        ClientType type = Clients.require(client);
        accountCancelService.cancel(request, type);
        if (type == ClientType.WEB) {
            webRefreshCookie.clear(response);
        }
        return ApiResponse.ok();
    }

    private AuthTokenVO withWebCookie(AuthTokenVO token, ClientType client, HttpServletResponse response) {
        if (client == ClientType.WEB && token.getRefreshToken() != null) {
            webRefreshCookie.write(response, token.getRefreshToken());
            token.setRefreshToken(null);
        }
        return token;
    }
}
