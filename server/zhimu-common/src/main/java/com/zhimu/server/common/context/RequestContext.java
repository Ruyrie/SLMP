package com.zhimu.server.common.context;

import com.zhimu.server.common.enums.ClientType;

/**
 * 请求来源：X-Client、X-App-Version、客户端 IP、User-Agent、Idempotency-Key。由框架过滤器写入。
 */
public final class RequestContext {

    public record Info(ClientType clientType, String appVersion, String ip, String userAgent, String idempotencyKey) {
    }

    private static final ThreadLocal<Info> HOLDER = new ThreadLocal<>();

    private RequestContext() {
    }

    public static Info get() {
        return HOLDER.get();
    }

    public static void set(Info info) {
        HOLDER.set(info);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static ClientType clientType() {
        Info info = HOLDER.get();
        return info == null ? null : info.clientType();
    }

    public static String ip() {
        Info info = HOLDER.get();
        return info == null ? null : info.ip();
    }

    public static String userAgent() {
        Info info = HOLDER.get();
        return info == null ? null : info.userAgent();
    }

    public static String idempotencyKey() {
        Info info = HOLDER.get();
        return info == null ? null : info.idempotencyKey();
    }
}
