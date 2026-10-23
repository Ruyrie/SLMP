package com.zhimu.server.framework.security.interceptor;

import com.zhimu.server.framework.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.Annotation;

/**
 * 拦截器公用方法：按“方法优先、其次类”查找注解，读取 Bearer 令牌。
 */
final class HandlerSupport {

    private HandlerSupport() {
    }

    static <A extends Annotation> A find(HandlerMethod handler, Class<A> type) {
        A annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), type);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), type);
        }
        return annotation;
    }

    static boolean has(HandlerMethod handler, Class<? extends Annotation> type) {
        return find(handler, type) != null;
    }

    static boolean hasAuthorization(HttpServletRequest request) {
        String header = request.getHeader(WebHeaders.AUTHORIZATION);
        return header != null && !header.isBlank();
    }

    /** Authorization: Bearer xxx 中的令牌；没有或格式不对时返回 null */
    static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(WebHeaders.AUTHORIZATION);
        if (header == null) {
            return null;
        }
        String value = header.trim();
        int prefix = WebHeaders.BEARER_PREFIX.length();
        if (value.length() <= prefix || !value.regionMatches(true, 0, WebHeaders.BEARER_PREFIX, 0, prefix)) {
            return null;
        }
        String token = value.substring(prefix).trim();
        return token.isEmpty() ? null : token;
    }

    static boolean isReadMethod(HttpServletRequest request) {
        String method = request.getMethod();
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
    }
}
