package com.zhimu.server.framework.web;

import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.TraceContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * 最外层过滤器：生成 trace_id（响应头 X-Trace-Id、MDC），解析请求来源写入 RequestContext，
 * 给低版本 App 加最低版本响应头，记录请求日志；结束时清理全部上下文与 MDC。本身不抛异常。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TraceFilter extends OncePerRequestFilter {

    public static final String MDC_TRACE_ID = "trace_id";
    public static final String MDC_USER_ID = "user_id";
    public static final String MDC_FARM_ID = "farm_id";

    private static final long SLOW_MILLIS = 1000;

    private final AppVersionProperties appVersionProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        boolean failed = false;
        try {
            prepare(request, response);
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            logRequest(request, response, start, failed);
            clearAll();
        }
    }

    private void prepare(HttpServletRequest request, HttpServletResponse response) {
        try {
            String traceId = UUID.randomUUID().toString().replace("-", "");
            TraceContext.set(traceId);
            MDC.put(MDC_TRACE_ID, traceId);
            response.setHeader(WebHeaders.TRACE_ID, traceId);

            ClientType clientType = parseClient(request.getHeader(WebHeaders.CLIENT));
            String appVersion = trimToNull(request.getHeader(WebHeaders.APP_VERSION));
            RequestContext.set(new RequestContext.Info(clientType, appVersion, clientIp(request),
                    trimToNull(request.getHeader("User-Agent")), trimToNull(request.getHeader(WebHeaders.IDEMPOTENCY_KEY))));
            writeAppVersionHint(response, clientType, appVersion);
        } catch (RuntimeException e) {
            log.warn("初始化请求上下文失败", e);
        }
    }

    private void writeAppVersionHint(HttpServletResponse response, ClientType clientType, String appVersion) {
        String minVersion = trimToNull(appVersionProperties.getMinVersion());
        if (clientType != ClientType.APP_ANDROID || appVersion == null || minVersion == null) {
            return;
        }
        if (AppVersions.compare(appVersion, minVersion) < 0) {
            response.setHeader(WebHeaders.MIN_APP_VERSION, minVersion);
            String url = trimToNull(appVersionProperties.getDownloadUrl());
            if (url != null) {
                response.setHeader(WebHeaders.APP_DOWNLOAD_URL, url);
            }
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long start, boolean failed) {
        try {
            long cost = (System.nanoTime() - start) / 1_000_000;
            int status = failed ? 500 : response.getStatus();
            if (cost > SLOW_MILLIS) {
                log.warn("慢请求 {} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, cost);
            } else {
                log.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, cost);
            }
        } catch (RuntimeException e) {
            log.warn("记录请求日志失败", e);
        }
    }

    private static void clearAll() {
        TraceContext.clear();
        RequestContext.clear();
        UserContext.clear();
        AdminContext.clear();
        FarmContext.clear();
        MDC.clear();
    }

    static ClientType parseClient(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ClientType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 依次取 X-Forwarded-For 的第一个、X-Real-IP、remoteAddr */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(WebHeaders.FORWARDED_FOR);
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty() && !"unknown".equalsIgnoreCase(first)) {
                return first;
            }
        }
        String realIp = request.getHeader(WebHeaders.REAL_IP);
        if (StringUtils.hasText(realIp) && !"unknown".equalsIgnoreCase(realIp.trim())) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
