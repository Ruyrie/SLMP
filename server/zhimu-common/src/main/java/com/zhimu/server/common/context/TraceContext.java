package com.zhimu.server.common.context;

/**
 * 链路编号 trace_id：由 TraceFilter 在请求开始时写入，结束时清除；同时写入日志 MDC。
 */
public final class TraceContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void set(String traceId) {
        HOLDER.set(traceId);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
