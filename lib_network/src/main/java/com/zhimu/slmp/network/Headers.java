package com.zhimu.slmp.network;

/**
 * 请求头与响应头名称（04 号文档 7.1、7.5 节，03 号文档 6.1 节）。
 */
public final class Headers {

    private Headers() {
    }

    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer ";
    /** 当前养殖场 */
    public static final String FARM_ID = "X-Farm-Id";
    /** 客户端类型，固定 APP_ANDROID */
    public static final String CLIENT = "X-Client";
    public static final String CLIENT_VALUE = "APP_ANDROID";
    public static final String APP_VERSION = "X-App-Version";
    /** 新增类接口的幂等键，由 ViewModel 在点击提交时生成后作为 @Header 参数传入 */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    /**
     * 仅客户端使用：单个接口的读取超时秒数（默认 30 秒），由 HeaderInterceptor 取出后移除，不发给服务端。
     * 在 Retrofit 方法上用 {@code @retrofit2.http.Headers(Headers.READ_TIMEOUT_60S)} 声明。
     */
    public static final String READ_TIMEOUT = "X-Zmt-Read-Timeout";
    /** 登录、注册：服务端处理较慢、网络慢时 30 秒不够 */
    public static final String READ_TIMEOUT_60S = READ_TIMEOUT + ": 60";
    /** 响应头：链路编号 */
    public static final String TRACE_ID = "X-Trace-Id";
    /** 响应头：最低支持版本与下载地址 */
    public static final String MIN_APP_VERSION = "X-Min-App-Version";
    public static final String APP_DOWNLOAD_URL = "X-App-Download-Url";
}
