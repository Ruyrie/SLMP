package com.zhimu.server.framework.security.token;

import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.framework.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

/**
 * Web 用户端的刷新令牌 Cookie（HttpOnly、Secure、SameSite=Strict、Path=/api/v1/auth，30 天）。
 * X-Client 为 WEB 时，注册、登录、刷新接口用它下发刷新令牌（响应体不返回 refresh_token），退出接口清除。
 */
@Component
@RequiredArgsConstructor
public class WebRefreshCookie {

    public static final String PATH = "/api/v1/auth";

    private final SecurityProperties properties;

    /** 当前请求来自 Web 用户端（X-Client: WEB） */
    public boolean isWebClient() {
        return RequestContext.clientType() == ClientType.WEB;
    }

    public void write(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(refreshToken, properties.getRefreshTokenTtl()).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    /** 读取请求中的刷新令牌，没有时返回 null */
    public String read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, properties.getWebRefreshCookieName());
        return cookie == null || !StringUtils.hasText(cookie.getValue()) ? null : cookie.getValue();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(properties.getWebRefreshCookieName(), value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
