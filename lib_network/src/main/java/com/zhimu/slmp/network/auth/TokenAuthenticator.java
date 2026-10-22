package com.zhimu.slmp.network.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.interceptor.HeaderInterceptor;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * 令牌自动刷新：收到 401 时
 * <ol>
 * <li>请求所带令牌与当前令牌不同（其他线程已刷新，或本机刚换发）→ 直接用当前令牌重发；</li>
 * <li>code 为 40101 → 同步块中调用 TokenProvider.refreshBlocking（API-A06），成功后重发；多个请求同时失效只刷新一次；</li>
 * <li>40104 不重发，由 ErrorInterceptor 交给 TokenProvider 清除登录态。</li>
 * </ol>
 * 同一请求只重发一次。
 */
public class TokenAuthenticator implements Authenticator {

    private final TokenProvider tokenProvider;
    private final Object lock = new Object();

    public TokenAuthenticator(@NonNull TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        Request request = response.request();
        if (response.priorResponse() != null) {
            return null;
        }
        String requestToken = HeaderInterceptor.tokenOf(request);
        if (requestToken == null) {
            return null;
        }
        int code = codeOf(response);
        if (code != ErrorCodes.TOKEN_EXPIRED && code != ErrorCodes.SESSION_INVALID) {
            return null;
        }
        synchronized (lock) {
            String current = tokenProvider.getAccessToken();
            if (current == null) {
                return null;
            }
            if (!current.equals(requestToken)) {
                return withToken(request, current);
            }
            if (code == ErrorCodes.SESSION_INVALID) {
                return null;
            }
            String fresh = tokenProvider.refreshBlocking();
            return fresh == null ? null : withToken(request, fresh);
        }
    }

    private static Request withToken(Request request, String token) {
        return request.newBuilder().header(Headers.AUTHORIZATION, Headers.BEARER + token).build();
    }

    private static int codeOf(Response response) {
        try {
            JsonElement e = JsonParser.parseString(response.peekBody(64 * 1024).string());
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                JsonElement c = o.get("code");
                return c == null || c.isJsonNull() ? 0 : c.getAsInt();
            }
        } catch (IOException | RuntimeException ignored) {
            // 非 JSON 响应
        }
        return 0;
    }
}
