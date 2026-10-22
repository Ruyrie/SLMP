package com.zhimu.slmp.network.auth;

import androidx.annotation.Nullable;

import java.io.IOException;

/**
 * 令牌提供者（lib_common 的 SessionManager 实现）：HeaderInterceptor 取访问令牌，TokenAuthenticator 刷新，
 * ErrorInterceptor 在登录失效（40104）时通知。
 */
public interface TokenProvider {

    /** 当前访问令牌，未登录为 null */
    @Nullable
    String getAccessToken();

    /**
     * 同步刷新令牌（API-A06，在 OkHttp 线程调用）。
     *
     * @return 新的访问令牌；登录已失效（40104）或没有刷新令牌时返回 null（实现方自行清除登录态）
     * @throws IOException 网络失败，不清除登录态
     */
    @Nullable
    String refreshBlocking() throws IOException;

    /** 任意接口返回 40104：清除登录态并发出“需要重新登录”事件，reason 为 data.reason */
    void onSessionInvalid(@Nullable String reason);
}
