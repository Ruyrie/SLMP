package com.zhimu.server.biz.account.controller;

import com.zhimu.server.biz.account.request.PasswordLoginRequest;
import com.zhimu.server.biz.account.request.PasswordResetRequest;
import com.zhimu.server.biz.account.request.RegisterRequest;
import com.zhimu.server.biz.account.request.SmsLoginRequest;
import com.zhimu.server.biz.account.request.TokenRefreshRequest;
import com.zhimu.server.biz.account.service.AuthService;
import com.zhimu.server.biz.account.support.Clients;
import com.zhimu.server.biz.account.vo.AuthTokenVO;
import com.zhimu.server.biz.account.vo.SessionVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.idempotent.Idempotent;
import com.zhimu.server.framework.ratelimit.RateLimit;
import com.zhimu.server.framework.security.annotation.AllowForcedStep;
import com.zhimu.server.framework.security.annotation.PublicApi;
import com.zhimu.server.framework.security.token.WebRefreshCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-A03–A09：注册、两种登录、刷新、退出、会话、短信重置密码。 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final WebRefreshCookie webRefreshCookie;

    /** API-A03 注册并自动登录 */
    @PublicApi
    @Idempotent(required = true)
    @PostMapping("/register")
    public ApiResponse<AuthTokenVO> register(@RequestBody RegisterRequest request,
                                             @RequestHeader(value = "X-Client", required = false) String client,
                                             HttpServletResponse response) {
        ClientType type = Clients.require(client);
        AuthTokenVO token = authService.register(request, type);
        return ApiResponse.ok(withWebCookie(token, type, response));
    }

    /** API-A04 密码登录（含临时密码） */
    @PublicApi
    @RateLimit(key = "auth-login", limit = 20, by = RateLimit.LimitBy.IP)
    @PostMapping("/login/password")
    public ApiResponse<AuthTokenVO> loginByPassword(@RequestBody PasswordLoginRequest request,
                                                    @RequestHeader(value = "X-Client", required = false) String client,
                                                    HttpServletResponse response) {
        ClientType type = Clients.require(client);
        AuthTokenVO token = authService.loginByPassword(request, type);
        return ApiResponse.ok(withWebCookie(token, type, response));
    }

    /** API-A05 验证码登录 */
    @PublicApi
    @RateLimit(key = "auth-login", limit = 20, by = RateLimit.LimitBy.IP)
    @PostMapping("/login/sms")
    public ApiResponse<AuthTokenVO> loginBySms(@RequestBody SmsLoginRequest request,
                                               @RequestHeader(value = "X-Client", required = false) String client,
                                               HttpServletResponse response) {
        ClientType type = Clients.require(client);
        AuthTokenVO token = authService.loginBySms(request, type);
        return ApiResponse.ok(withWebCookie(token, type, response));
    }

    /** API-A06 刷新令牌：App 取请求体，Web 取 Cookie */
    @PublicApi
    @AllowForcedStep
    @PostMapping("/token/refresh")
    public ApiResponse<AuthTokenVO> refresh(@RequestBody(required = false) TokenRefreshRequest request,
                                            @RequestHeader(value = "X-Client", required = false) String client,
                                            HttpServletRequest httpRequest, HttpServletResponse response) {
        ClientType type = Clients.require(client);
        String refreshToken;
        if (type == ClientType.WEB) {
            refreshToken = webRefreshCookie.read(httpRequest);
            if (refreshToken == null) {
                throw new BizException(ErrorCode.SESSION_INVALID).with("reason", "EXPIRED");
            }
        } else {
            refreshToken = request == null ? null : request.refreshToken();
        }
        AuthTokenVO token = authService.refresh(refreshToken, type);
        return ApiResponse.ok(withWebCookie(token, type, response));
    }

    /** API-A07 退出登录 */
    @AllowForcedStep
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "X-Client", required = false) String client,
                                    HttpServletResponse response) {
        ClientType type = Clients.require(client);
        authService.logout(type);
        if (type == ClientType.WEB) {
            webRefreshCookie.clear(response);
        }
        return ApiResponse.ok();
    }

    /** API-A08 当前会话与强制步骤 */
    @AllowForcedStep
    @GetMapping("/session")
    public ApiResponse<SessionVO> session(@RequestHeader(value = "X-Client", required = false) String client) {
        return ApiResponse.ok(authService.session(Clients.require(client)));
    }

    /** API-A09 短信重置密码 */
    @PublicApi
    @Idempotent(required = true)
    @PostMapping("/password-resets")
    public ApiResponse<Void> resetPassword(@RequestBody PasswordResetRequest request,
                                           @RequestHeader(value = "X-Client", required = false) String client) {
        Clients.require(client);
        authService.resetPassword(request);
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
