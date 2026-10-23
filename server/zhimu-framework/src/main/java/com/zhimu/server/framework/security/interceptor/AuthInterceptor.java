package com.zhimu.server.framework.security.interceptor;

import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.security.annotation.AllowForcedStep;
import com.zhimu.server.framework.security.annotation.PublicApi;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.jwt.UserJwtService;
import com.zhimu.server.framework.security.spi.UserAuthState;
import com.zhimu.server.framework.web.TraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户端登录校验（/api/v1/**）：校验 Bearer 访问令牌与 token_version，检查强制步骤，写入 UserContext。
 * <ul>
 * <li>@PublicApi 不要求登录；带了有效令牌时仍写入 UserContext。</li>
 * <li>令牌缺失、格式错误、签名错误、过期、typ 不是 USER：40101。</li>
 * <li>token_version 不一致：40104（data.reason 取状态中的原因，默认 EXPIRED）；用户不存在：40104 EXPIRED。</li>
 * <li>有强制步骤且接口没有 @AllowForcedStep：40309（data.next_step）。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    static final String REASON_EXPIRED = "EXPIRED";

    private final UserJwtService jwtService;
    private final AuthStateCache authStateCache;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        if (HandlerSupport.has(method, PublicApi.class)) {
            tryAuthenticate(request);
            return true;
        }
        String token = HandlerSupport.bearerToken(request);
        if (token == null) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        }
        UserJwtService.UserClaims claims = jwtService.parse(token);
        if (authStateCache.hasLoader()) {
            UserAuthState state = authStateCache.get(claims.userId());
            if (state == null) {
                throw sessionInvalid(REASON_EXPIRED);
            }
            if (state.tokenVersion() != claims.tokenVersion()) {
                throw sessionInvalid(StringUtils.hasText(state.invalidReason()) ? state.invalidReason() : REASON_EXPIRED);
            }
            if (state.hasForcedStep() && !HandlerSupport.has(method, AllowForcedStep.class)) {
                throw new BizException(ErrorCode.FORCED_STEP_REQUIRED).with("next_step", state.nextStep());
            }
        }
        login(claims);
        return true;
    }

    /** 白名单接口：令牌有效（且 token_version 一致）才写入 UserContext，否则当作未登录 */
    private void tryAuthenticate(HttpServletRequest request) {
        String token = HandlerSupport.bearerToken(request);
        if (token == null) {
            return;
        }
        try {
            UserJwtService.UserClaims claims = jwtService.parse(token);
            if (authStateCache.hasLoader()) {
                UserAuthState state = authStateCache.get(claims.userId());
                if (state == null || state.tokenVersion() != claims.tokenVersion()) {
                    return;
                }
            }
            login(claims);
        } catch (BizException e) {
            // 白名单接口忽略无效令牌
        }
    }

    private static void login(UserJwtService.UserClaims claims) {
        UserContext.set(new UserContext.LoginUser(claims.userId(), claims.clientType(), claims.tokenVersion(), claims.jti()));
        MDC.put(TraceFilter.MDC_USER_ID, String.valueOf(claims.userId()));
    }

    private static BizException sessionInvalid(String reason) {
        return new BizException(ErrorCode.SESSION_INVALID).with("reason", reason);
    }
}
