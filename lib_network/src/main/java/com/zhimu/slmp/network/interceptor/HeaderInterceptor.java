package com.zhimu.slmp.network.interceptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.network.ClientContextProvider;
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.auth.TokenProvider;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 公共请求头：Authorization（访问令牌）、X-Farm-Id（当前养殖场）、X-Client、X-App-Version。
 * Retrofit 方法上已显式声明的 Authorization / X-Farm-Id 不覆盖；Idempotency-Key 由方法参数传入。
 */
public class HeaderInterceptor implements Interceptor {

    @Nullable
    private final TokenProvider tokenProvider;
    @NonNull
    private final ClientContextProvider contextProvider;
    @NonNull
    private final String appVersion;

    /**
     * @param tokenProvider 为 null 时不加 Authorization（刷新令牌专用客户端）
     */
    public HeaderInterceptor(@Nullable TokenProvider tokenProvider, @NonNull ClientContextProvider contextProvider,
                             @NonNull String appVersion) {
        this.tokenProvider = tokenProvider;
        this.contextProvider = contextProvider;
        this.appVersion = appVersion;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        Request.Builder b = request.newBuilder()
                .header(Headers.CLIENT, Headers.CLIENT_VALUE)
                .header(Headers.APP_VERSION, appVersion);
        if (tokenProvider != null && request.header(Headers.AUTHORIZATION) == null) {
            String token = tokenProvider.getAccessToken();
            if (token != null && !token.isEmpty()) {
                b.header(Headers.AUTHORIZATION, Headers.BEARER + token);
            }
        }
        if (request.header(Headers.FARM_ID) == null) {
            Long farmId = contextProvider.currentFarmId();
            if (farmId != null && farmId > 0) {
                b.header(Headers.FARM_ID, String.valueOf(farmId));
            }
        }
        return chain.proceed(b.build());
    }

    /** 取请求头中的令牌（去掉 Bearer 前缀） */
    @Nullable
    public static String tokenOf(@NonNull Request request) {
        String h = request.header(Headers.AUTHORIZATION);
        if (h == null) {
            return null;
        }
        return h.startsWith(Headers.BEARER) ? h.substring(Headers.BEARER.length()) : h;
    }
}
