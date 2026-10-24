package com.zhimu.server.admin.system.controller;

import com.zhimu.server.admin.system.request.AdminLoginRequest;
import com.zhimu.server.admin.system.request.AdminPasswordRequest;
import com.zhimu.server.admin.system.service.AdminAuthService;
import com.zhimu.server.admin.system.service.CaptchaService;
import com.zhimu.server.admin.system.vo.AdminLoginVO;
import com.zhimu.server.admin.system.vo.AdminMeVO;
import com.zhimu.server.admin.system.vo.CaptchaVO;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.framework.ratelimit.RateLimit;
import com.zhimu.server.framework.security.annotation.AllowForcedStep;
import com.zhimu.server.framework.security.annotation.PublicApi;
import com.zhimu.server.framework.security.session.AdminSessionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端认证（ADM-S01–S05）。
 * 登录成功只通过 HttpOnly、Secure、SameSite=Strict、Path=/admin/v1 的 Cookie 下发会话令牌，
 * 响应体不含令牌；退出与改密清除 Cookie。
 */
@RestController
@RequestMapping("/admin/v1/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final CaptchaService captchaService;
    private final AdminSessionService adminSessionService;

    /** ADM-S01 图形验证码：4 位、2 分钟、一次有效；同一 IP 每分钟 20 次 */
    @PublicApi
    @RateLimit(key = "admin-login", limit = 20, by = RateLimit.LimitBy.IP)
    @GetMapping("/captcha")
    public ApiResponse<CaptchaVO> captcha() {
        return ApiResponse.ok(captchaService.create());
    }

    /** ADM-S02 管理员登录：验证码 + 账号密码；失败 5 次锁 30 分钟 */
    @PublicApi
    @RateLimit(key = "admin-login", limit = 20, by = RateLimit.LimitBy.IP)
    @PostMapping("/login")
    public ApiResponse<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request,
                                           HttpServletResponse response) {
        AdminAuthService.AdminLoginResult result = adminAuthService.login(request);
        adminSessionService.writeCookie(response, result.token());
        return ApiResponse.ok(result.vo());
    }

    /** ADM-S03 退出：删除会话并清除 Cookie */
    @AllowForcedStep
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        adminAuthService.logout();
        adminSessionService.clearCookie(response);
        return ApiResponse.ok();
    }

    /** ADM-S04 修改密码：首次登录强制改密期间仍可调用；成功后全部会话失效并要求重新登录 */
    @AllowForcedStep
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AdminPasswordRequest request,
                                            HttpServletResponse response) {
        adminAuthService.changePassword(request);
        adminSessionService.clearCookie(response);
        return ApiResponse.ok();
    }

    /** ADM-S05 当前管理员、权限、菜单、四个角标与未读消息数 */
    @AllowForcedStep
    @GetMapping("/me")
    public ApiResponse<AdminMeVO> me() {
        return ApiResponse.ok(adminAuthService.me());
    }
}
