package com.zhimu.slmp.base.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一业务错误：服务端 code 不为 0、HTTP 非 2xx 的错误体、网络失败都转换为本异常（由 lib_network 的 RxResult 生成）。
 * fieldErrors 为 40001 时 data.field_errors 的“字段 → 提示文案”；data 保留原始 data 供读取 reason、next_step 等。
 */
public class ApiException extends RuntimeException {

    /** 网络连接失败（与 ErrorCodes.NETWORK 相同） */
    public static final int NETWORK = ErrorCodes.NETWORK;
    public static final int UNKNOWN = ErrorCodes.UNKNOWN;
    public static final int PARSE = ErrorCodes.PARSE;
    public static final int EMPTY_DATA = ErrorCodes.EMPTY_DATA;

    public static final String MSG_NETWORK = "网络连接失败，请检查网络后重试";
    public static final String MSG_UNKNOWN = "出了点问题，请稍后重试";

    private final int code;
    @NonNull
    private final Map<String, String> fieldErrors;
    @Nullable
    private final String traceId;
    @Nullable
    private final JsonElement data;

    public ApiException(int code, @Nullable String message) {
        this(code, message, null, null, null, null);
    }

    public ApiException(int code, @Nullable String message, @Nullable String traceId,
                        @Nullable JsonElement data, @Nullable Map<String, String> fieldErrors,
                        @Nullable Throwable cause) {
        super(message == null || message.isEmpty() ? defaultMessage(code) : message, cause);
        this.code = code;
        this.traceId = traceId;
        this.data = data;
        this.fieldErrors = fieldErrors == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public static ApiException network(@Nullable Throwable cause) {
        return new ApiException(NETWORK, MSG_NETWORK, null, null, null, cause);
    }

    public static ApiException unknown(@Nullable Throwable cause) {
        return new ApiException(UNKNOWN, MSG_UNKNOWN, null, null, null, cause);
    }

    /** 把任意异常转换为 ApiException：已是 ApiException 原样返回，IOException 视为网络失败 */
    @NonNull
    public static ApiException from(@Nullable Throwable t) {
        if (t instanceof ApiException) {
            return (ApiException) t;
        }
        if (t instanceof java.io.IOException) {
            return network(t);
        }
        return unknown(t);
    }

    private static String defaultMessage(int code) {
        return code == NETWORK ? MSG_NETWORK : MSG_UNKNOWN;
    }

    public int getCode() {
        return code;
    }

    @NonNull
    @Override
    public String getMessage() {
        String m = super.getMessage();
        return m == null ? MSG_UNKNOWN : m;
    }

    @NonNull
    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    @Nullable
    public String getTraceId() {
        return traceId;
    }

    @Nullable
    public JsonElement getData() {
        return data;
    }

    public boolean isNetwork() {
        return code == NETWORK;
    }

    public boolean hasFieldErrors() {
        return !fieldErrors.isEmpty();
    }

    /** 读取 data 中的字符串字段，例如 40104 的 reason、40309 的 next_step */
    @Nullable
    public String dataString(@NonNull String key) {
        if (data == null || !data.isJsonObject()) {
            return null;
        }
        JsonElement e = ((JsonObject) data).get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return null;
        }
        return ((JsonPrimitive) e).getAsString();
    }

    /** 读取 data 中的布尔字段，例如 42206 的 need_confirm */
    public boolean dataBoolean(@NonNull String key) {
        String v = dataString(key);
        return Boolean.parseBoolean(v);
    }

    @NonNull
    @Override
    public String toString() {
        return "ApiException{code=" + code + ", message=" + getMessage() + ", traceId=" + traceId + "}";
    }
}
