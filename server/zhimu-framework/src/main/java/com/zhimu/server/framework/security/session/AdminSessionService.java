package com.zhimu.server.framework.security.session;

import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.jackson.CacheJson;
import com.zhimu.server.framework.security.SecureTokens;
import com.zhimu.server.framework.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.Set;

/**
 * 管理端会话：32 字节随机令牌存 admin:session:{token}（30 分钟无请求失效，每次请求滑动续期），
 * 索引 admin:session:index:{admin_id}。令牌以 HttpOnly、Secure、SameSite=Strict、Path=/admin/v1 的会话 Cookie 下发。
 */
@Component
@RequiredArgsConstructor
public class AdminSessionService {

    public static final String SESSION_PREFIX = "admin:session:";
    public static final String INDEX_PREFIX = "admin:session:index:";
    public static final String COOKIE_PATH = "/admin/v1";

    private final StringRedisTemplate redis;
    private final SecurityProperties properties;

    /** 创建会话，返回会话令牌 */
    public String create(Long adminId, int tokenVersion) {
        String token = SecureTokens.random32();
        Duration ttl = properties.getAdminSessionTtl();
        AdminSession session = new AdminSession(adminId, tokenVersion, BeijingTime.now());
        redis.opsForValue().set(SESSION_PREFIX + token, CacheJson.write(session), ttl);
        String indexKey = INDEX_PREFIX + adminId;
        redis.opsForSet().add(indexKey, token);
        redis.expire(indexKey, ttl);
        return token;
    }

    /** 会话不存在或已过期时返回 null */
    public AdminSession get(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return CacheJson.read(redis.opsForValue().get(SESSION_PREFIX + token), AdminSession.class);
    }

    /** 滑动续期 */
    public void touch(String token, Long adminId) {
        Duration ttl = properties.getAdminSessionTtl();
        redis.expire(SESSION_PREFIX + token, ttl);
        redis.expire(INDEX_PREFIX + adminId, ttl);
    }

    public void invalidate(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        AdminSession session = get(token);
        redis.delete(SESSION_PREFIX + token);
        if (session != null) {
            redis.opsForSet().remove(INDEX_PREFIX + session.adminId(), token);
        }
    }

    /** 作废该管理员的全部会话（停用、重置密码、修改密码） */
    public void invalidateAll(Long adminId) {
        String indexKey = INDEX_PREFIX + adminId;
        Set<String> tokens = redis.opsForSet().members(indexKey);
        if (tokens != null && !tokens.isEmpty()) {
            redis.delete(tokens.stream().map(t -> SESSION_PREFIX + t).toList());
        }
        redis.delete(indexKey);
    }

    public void writeCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, null).toString());
    }

    public void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    /** 读取请求 Cookie 中的会话令牌，没有时返回 null */
    public String readCookie(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, properties.getAdminSessionCookieName());
        return cookie == null || !StringUtils.hasText(cookie.getValue()) ? null : cookie.getValue();
    }

    /** maxAge 为 null 时为会话 Cookie（关闭浏览器即失效，空闲超时由服务端控制） */
    private ResponseCookie cookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(properties.getAdminSessionCookieName(), value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH);
        if (maxAge != null) {
            builder.maxAge(maxAge);
        }
        return builder.build();
    }
}
