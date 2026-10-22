package com.zhimu.slmp.network.interceptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.auth.TokenProvider;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * 响应检查：记录最近的 trace_id；响应头带最低支持版本且高于当前版本时发出“需要更新”；
 * 最终响应为 40104（令牌已在其他设备失效）且请求所带令牌仍是当前令牌时交给 TokenProvider 清除登录态。
 * 位于应用拦截器中，看到的是认证器重试之后的最终响应。
 */
public class ErrorInterceptor implements Interceptor {

    private static volatile String lastTraceId;

    @Nullable
    private final TokenProvider tokenProvider;
    @NonNull
    private final String appVersion;

    public ErrorInterceptor(@Nullable TokenProvider tokenProvider, @NonNull String appVersion) {
        this.tokenProvider = tokenProvider;
        this.appVersion = appVersion;
    }

    /** 最近一次响应的链路编号（反馈问题时展示） */
    @Nullable
    public static String lastTraceId() {
        return lastTraceId;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        String trace = response.header(Headers.TRACE_ID);
        if (trace != null) {
            lastTraceId = trace;
        }
        String minVersion = response.header(Headers.MIN_APP_VERSION);
        if (minVersion != null && compareVersion(appVersion, minVersion) < 0) {
            GlobalEvents.postUpdateRequired(minVersion, response.header(Headers.APP_DOWNLOAD_URL));
        }
        if (response.code() == 401 && tokenProvider != null) {
            JsonObject body = peekJson(response);
            if (body != null && codeOf(body) == ErrorCodes.SESSION_INVALID) {
                String requestToken = HeaderInterceptor.tokenOf(response.request());
                String current = tokenProvider.getAccessToken();
                if (current != null && current.equals(requestToken)) {
                    tokenProvider.onSessionInvalid(reasonOf(body));
                }
            }
        }
        return response;
    }

    /** 读取响应体 JSON（不消耗原响应体），失败返回 null */
    @Nullable
    static JsonObject peekJson(@NonNull Response response) {
        try {
            String s = response.peekBody(64 * 1024).string();
            JsonElement e = JsonParser.parseString(s);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static int codeOf(@NonNull JsonObject body) {
        JsonElement c = body.get("code");
        try {
            return c == null || c.isJsonNull() ? 0 : c.getAsInt();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Nullable
    static String reasonOf(@NonNull JsonObject body) {
        JsonElement data = body.get("data");
        if (data == null || !data.isJsonObject()) {
            return null;
        }
        JsonElement r = data.getAsJsonObject().get("reason");
        return r == null || r.isJsonNull() ? null : r.getAsString();
    }

    /** 比较“主.次.修订”版本号 */
    @VisibleForTesting
    public static int compareVersion(@NonNull String a, @NonNull String b) {
        String[] pa = a.trim().split("\\.");
        String[] pb = b.trim().split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? parseInt(pa[i]) : 0;
            int vb = i < pb.length ? parseInt(pb[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D.*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
