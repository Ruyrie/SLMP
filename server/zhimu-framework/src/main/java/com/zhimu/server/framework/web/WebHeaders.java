package com.zhimu.server.framework.web;

/**
 * 请求头与响应头名称（与 App lib_network 的 Headers 一致）。
 */
public final class WebHeaders {

    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String FARM_ID = "X-Farm-Id";
    public static final String CLIENT = "X-Client";
    public static final String APP_VERSION = "X-App-Version";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String MIN_APP_VERSION = "X-Min-App-Version";
    public static final String APP_DOWNLOAD_URL = "X-App-Download-Url";
    public static final String FORWARDED_FOR = "X-Forwarded-For";
    public static final String REAL_IP = "X-Real-IP";
    public static final String ORIGIN = "Origin";

    private WebHeaders() {
    }
}
