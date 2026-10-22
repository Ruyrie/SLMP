package com.zhimu.slmp.common.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 直连失败。kind 决定是否换备用服务商：网络错误、超时、5xx、429 会换；其余 4xx、回答为空不换。
 * message 不包含 Key 与请求体。
 */
public class AiException extends Exception {

    public enum Kind {
        /** 直连未开启（没有 Key） */
        DISABLED,
        /** 网络错误或超时 */
        NETWORK,
        /** HTTP 5xx 或 429 */
        SERVER,
        /** 其他 HTTP 4xx（Key 无效、参数错误、内容审核等） */
        CLIENT,
        /** 回答为空或格式无法解析 */
        BAD_RESPONSE
    }

    @NonNull
    private final Kind kind;
    private final int httpCode;
    @Nullable
    private final String provider;

    public AiException(@NonNull Kind kind, int httpCode, @Nullable String provider, @NonNull String message,
                       @Nullable Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpCode = httpCode;
        this.provider = provider;
    }

    @NonNull
    public Kind getKind() {
        return kind;
    }

    /** HTTP 状态码；网络错误为 -1 */
    public int getHttpCode() {
        return httpCode;
    }

    @Nullable
    public String getProvider() {
        return provider;
    }

    /** 是否应换备用服务商重试 */
    public boolean isRetryable() {
        return kind == Kind.NETWORK || kind == Kind.SERVER;
    }

    /** 按 HTTP 状态码归类（单元测试） */
    @NonNull
    public static Kind kindOf(int httpCode) {
        if (httpCode >= 500 || httpCode == 429 || httpCode == 408) {
            return Kind.SERVER;
        }
        return Kind.CLIENT;
    }
}
