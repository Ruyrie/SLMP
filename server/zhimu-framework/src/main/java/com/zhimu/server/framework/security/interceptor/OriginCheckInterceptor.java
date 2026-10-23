package com.zhimu.server.framework.security.interceptor;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.WebUtils;

import java.util.Locale;

/**
 * 防跨站请求伪造：凭 Cookie 认证的请求（/admin/v1 的写操作、带刷新 Cookie 的 /api/v1/auth/** 请求）
 * 带了 Origin 头时，Origin 必须在 zhimu.security.allowed-origins 中，否则 40303。allowed-origins 为空时不校验。
 */
@Component
@RequiredArgsConstructor
public class OriginCheckInterceptor implements HandlerInterceptor {

    static final String MSG_ORIGIN_FORBIDDEN = "请求来源不受信任";

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private final SecurityProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String origin = request.getHeader(WebHeaders.ORIGIN);
        if (!StringUtils.hasText(origin) || properties.getAllowedOrigins() == null || !cookieAuthenticated(request)) {
            return true;
        }
        boolean checked = false;
        String normalized = normalize(origin);
        for (String allowed : properties.getAllowedOrigins()) {
            if (!StringUtils.hasText(allowed)) {
                continue;
            }
            checked = true;
            if (normalize(allowed).equals(normalized)) {
                return true;
            }
        }
        if (!checked) {
            return true;
        }
        throw new BizException(ErrorCode.ROLE_FORBIDDEN, MSG_ORIGIN_FORBIDDEN);
    }

    private boolean cookieAuthenticated(HttpServletRequest request) {
        String path = PATH_HELPER.getPathWithinApplication(request);
        if (path.startsWith("/admin/v1/")) {
            return !HandlerSupport.isReadMethod(request);
        }
        if (path.startsWith("/api/v1/auth/")) {
            return WebUtils.getCookie(request, properties.getWebRefreshCookieName()) != null;
        }
        return false;
    }

    private static String normalize(String origin) {
        String s = origin.trim().toLowerCase(Locale.ROOT);
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
