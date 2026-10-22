package com.zhimu.slmp.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.network.model.ApiResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleTransformer;
import okhttp3.ResponseBody;
import retrofit2.HttpException;

/**
 * 把 Retrofit 返回的 {@code Single<ApiResponse<T>>} 转换为业务数据：code 为 0 取出 data，否则抛出 ApiException；
 * HTTP 非 2xx 时解析错误体；IOException 转为网络错误（code = -1）。
 * <pre>
 * return api.getSession().compose(RxResult.unwrap());
 * return RxResult.completable(api.logout());
 * </pre>
 */
public final class RxResult {

    private RxResult() {
    }

    /** code 为 0 时取 data（data 为空视为错误 EMPTY_DATA） */
    @NonNull
    public static <T> SingleTransformer<ApiResponse<T>, T> unwrap() {
        return upstream -> upstream
                .flatMap(resp -> {
                    if (!resp.isSuccess()) {
                        return Single.<T>error(fromResponse(resp));
                    }
                    T data = resp.getData();
                    if (data == null) {
                        return Single.<T>error(new ApiException(ErrorCodes.EMPTY_DATA, ApiException.MSG_UNKNOWN,
                                resp.getTraceId(), null, null, null));
                    }
                    return Single.just(data);
                })
                .onErrorResumeNext(t -> Single.error(toApiException(t)));
    }

    @NonNull
    public static <T> Single<T> unwrap(@NonNull Single<ApiResponse<T>> source) {
        return source.compose(unwrap());
    }

    /** 没有 data 的接口：code 为 0 即完成 */
    @NonNull
    public static Completable completable(@NonNull Single<? extends ApiResponse<?>> source) {
        return source
                .flatMapCompletable(resp -> resp.isSuccess()
                        ? Completable.complete()
                        : Completable.error(fromResponse(resp)))
                .onErrorResumeNext(t -> Completable.error(toApiException(t)));
    }

    /** 任意异常转换为 ApiException */
    @NonNull
    public static ApiException toApiException(@Nullable Throwable t) {
        if (t instanceof ApiException) {
            return (ApiException) t;
        }
        if (t instanceof HttpException) {
            HttpException he = (HttpException) t;
            String body = null;
            String headerTrace = null;
            retrofit2.Response<?> response = he.response();
            if (response != null) {
                headerTrace = response.headers().get(Headers.TRACE_ID);
                ResponseBody eb = response.errorBody();
                if (eb != null) {
                    try {
                        body = eb.string();
                    } catch (IOException ignored) {
                        // 读取失败按无错误体处理
                    }
                }
            }
            return parseErrorBody(he.code(), body, headerTrace, t);
        }
        if (t instanceof IOException) {
            return ApiException.network(t);
        }
        if (t instanceof JsonParseException || t instanceof IllegalStateException) {
            return new ApiException(ErrorCodes.PARSE, ApiException.MSG_UNKNOWN, null, null, null, t);
        }
        return ApiException.unknown(t);
    }

    /** code 不为 0 的 ApiResponse 转为 ApiException */
    @NonNull
    public static ApiException fromResponse(@NonNull ApiResponse<?> resp) {
        Gson gson = GsonFactory.get();
        JsonElement data = resp.getData() == null ? null : gson.toJsonTree(resp.getData());
        return new ApiException(resp.getCode(), resp.getMessage(), resp.getTraceId(), data,
                parseFieldErrors(data), null);
    }

    /**
     * 解析 HTTP 错误体（完整的 ApiResponse JSON）；错误体不是 JSON 时按 HTTP 状态生成。
     */
    @NonNull
    public static ApiException parseErrorBody(int httpCode, @Nullable String body, @Nullable String headerTraceId,
                                              @Nullable Throwable cause) {
        if (body != null && !body.isEmpty()) {
            try {
                JsonElement root = JsonParser.parseString(body);
                if (root.isJsonObject() && root.getAsJsonObject().has("code")) {
                    JsonObject obj = root.getAsJsonObject();
                    int code = obj.get("code").getAsInt();
                    String message = stringOf(obj, "message");
                    String traceId = stringOf(obj, "trace_id");
                    JsonElement data = obj.has("data") && !obj.get("data").isJsonNull() ? obj.get("data") : null;
                    return new ApiException(code, message, traceId != null ? traceId : headerTraceId, data,
                            parseFieldErrors(data), cause);
                }
            } catch (RuntimeException ignored) {
                // 非 JSON 错误体（例如 Nginx 错误页）
            }
        }
        int code = httpCode >= 500 ? ErrorCodes.SERVER_ERROR : ErrorCodes.UNKNOWN;
        return new ApiException(code, ApiException.MSG_UNKNOWN, headerTraceId, null, null, cause);
    }

    /** 读取 data.field_errors：[{field, message}] → 字段 → 文案 */
    @Nullable
    static Map<String, String> parseFieldErrors(@Nullable JsonElement data) {
        if (data == null || !data.isJsonObject()) {
            return null;
        }
        JsonElement fe = data.getAsJsonObject().get("field_errors");
        if (fe == null || !fe.isJsonArray()) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>();
        JsonArray arr = fe.getAsJsonArray();
        for (JsonElement e : arr) {
            if (e.isJsonObject()) {
                String field = stringOf(e.getAsJsonObject(), "field");
                String msg = stringOf(e.getAsJsonObject(), "message");
                if (field != null && msg != null && !map.containsKey(field)) {
                    map.put(field, msg);
                }
            }
        }
        return map;
    }

    @Nullable
    private static String stringOf(@NonNull JsonObject obj, @NonNull String key) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
