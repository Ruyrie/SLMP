package com.zhimu.server.framework.security.interceptor;

import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.security.annotation.AdminPermission;
import com.zhimu.server.framework.security.annotation.AllowForcedStep;
import com.zhimu.server.framework.security.annotation.NoSessionRenew;
import com.zhimu.server.framework.security.annotation.PublicApi;
import com.zhimu.server.framework.security.session.AdminSession;
import com.zhimu.server.framework.security.session.AdminSessionService;
import com.zhimu.server.framework.security.spi.AdminAuthState;
import com.zhimu.server.framework.security.spi.AdminAuthStateLoader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 管理端登录校验（/admin/v1/**）：只认 Cookie 中的会话令牌，带用户端 Bearer 令牌一律 40106。
 * 每次请求按 {@link AdminAuthStateLoader} 校验管理员状态、token_version、强制步骤与权限点，通过后写入 AdminContext 并续期会话。
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminSessionService sessionService;
    private final ObjectProvider<AdminAuthStateLoader> loaderProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        if (HandlerSupport.has(method, PublicApi.class)) {
            tryAuthenticate(request);
            return true;
        }
        if (HandlerSupport.hasAuthorization(request)) {
            throw new BizException(ErrorCode.ADMIN_SESSION_TIMEOUT);
        }
        String token = sessionService.readCookie(request);
        AdminSession session = sessionService.get(token);
        if (session == null) {
            throw sessionInvalid("EXPIRED");
        }
        AdminAuthStateLoader loader = loaderProvider.getIfAvailable();
        AdminContext.LoginAdmin admin;
        if (loader == null) {
            if (HandlerSupport.find(method, AdminPermission.class) != null) {
                // fail-closed：权限点存在但还没有状态加载器时，不能静默放行
                throw new BizException(ErrorCode.ADMIN_PERMISSION_DENIED);
            }
            admin = new AdminContext.LoginAdmin(session.adminId(), null, Set.of(), Set.of(), session.tokenVersion(), token);
        } else {
            AdminAuthState state = loader.load(session.adminId());
            if (state == null) {
                sessionService.invalidate(token);
                throw sessionInvalid("EXPIRED");
            }
            if (!state.enabled()) {
                sessionService.invalidate(token);
                throw sessionInvalid("ADMIN_DISABLED");
            }
            if (state.tokenVersion() != session.tokenVersion()) {
                sessionService.invalidate(token);
                throw sessionInvalid(state.invalidReason() == null || state.invalidReason().isBlank()
                        ? "PASSWORD_CHANGED" : state.invalidReason());
            }
            if (hasNextStep(state) && !HandlerSupport.has(method, AllowForcedStep.class)) {
                throw new BizException(ErrorCode.FORCED_STEP_REQUIRED).with("next_step", state.nextStep());
            }
            admin = toLoginAdmin(session, state, token);
            AdminPermission permission = HandlerSupport.find(method, AdminPermission.class);
            if (permission != null && !hasAnyPermission(admin, permission.value())) {
                throw new BizException(ErrorCode.ADMIN_PERMISSION_DENIED);
            }
        }
        AdminContext.set(admin);
        if (!HandlerSupport.has(method, NoSessionRenew.class)) {
            sessionService.touch(token, session.adminId());
        }
        return true;
    }

    /** 白名单接口：会话有效才写入 AdminContext，否则当作未登录 */
    private void tryAuthenticate(HttpServletRequest request) {
        String token = sessionService.readCookie(request);
        AdminSession session = sessionService.get(token);
        if (session == null) {
            return;
        }
        AdminAuthStateLoader loader = loaderProvider.getIfAvailable();
        if (loader == null) {
            AdminContext.set(new AdminContext.LoginAdmin(session.adminId(), null, Set.of(), Set.of(), session.tokenVersion(), token));
            return;
        }
        AdminAuthState state = loader.load(session.adminId());
        if (state != null && state.enabled() && state.tokenVersion() == session.tokenVersion()) {
            AdminContext.set(toLoginAdmin(session, state, token));
        }
    }

    private static AdminContext.LoginAdmin toLoginAdmin(AdminSession session, AdminAuthState state, String token) {
        return new AdminContext.LoginAdmin(session.adminId(), state.name(),
                state.roleCodes() == null ? Set.of() : state.roleCodes(),
                state.permissions() == null ? Set.of() : state.permissions(),
                state.tokenVersion(), token);
    }

    private static boolean hasNextStep(AdminAuthState state) {
        return StringUtils.hasText(state.nextStep()) && !"NONE".equals(state.nextStep());
    }

    private static BizException sessionInvalid(String reason) {
        return new BizException(ErrorCode.ADMIN_SESSION_TIMEOUT).with("reason", reason);
    }

    private static boolean hasAnyPermission(AdminContext.LoginAdmin admin, String[] required) {
        for (String permission : required) {
            if (admin.hasPermission(permission)) {
                return true;
            }
        }
        return required.length == 0;
    }
}
