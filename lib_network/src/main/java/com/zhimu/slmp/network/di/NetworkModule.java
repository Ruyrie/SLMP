package com.zhimu.slmp.network.di;

import android.content.Context;

import com.google.gson.Gson;
import com.zhimu.slmp.network.BuildConfig;
import com.zhimu.slmp.network.ClientContextProvider;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.network.auth.TokenAuthenticator;
import com.zhimu.slmp.network.auth.TokenProvider;
import com.zhimu.slmp.network.interceptor.ErrorInterceptor;
import com.zhimu.slmp.network.interceptor.HeaderInterceptor;
import com.zhimu.slmp.network.interceptor.SafeLogger;
import com.zhimu.slmp.network.mock.MockInterceptor;
import com.zhimu.slmp.network.mock.MockRouter;
import com.zhimu.slmp.network.mock.MockSource;

import java.util.concurrent.TimeUnit;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 网络层单例：Gson、默认 / 上传 / 流式三个 OkHttpClient、刷新令牌专用客户端（不带 Authenticator 与 Authorization）、
 * 默认与上传 Retrofit。超时见 03 号文档 6.1 节：默认连接 15 秒、读取 30 秒；上传写入 120 秒；流式读取 180 秒。
 * 依赖 Context、TokenProvider、ClientContextProvider，由 lib_common 的 CoreModule 绑定。
 */
@Module
public class NetworkModule {

    public static final String UPLOAD = "upload";
    public static final String STREAM = "stream";
    public static final String REFRESH = "refresh";

    private static final String APP_VERSION = BuildConfig.APP_VERSION_NAME;

    private static boolean mockEnabled() {
        return BuildConfig.DEBUG && BuildConfig.USE_MOCK;
    }

    @Provides
    @Singleton
    static Gson provideGson() {
        return GsonFactory.get();
    }

    /** Mock 规则表（各客户端共用，首次请求时加载） */
    @Provides
    @Singleton
    static MockRouter provideMockRouter(Context context) {
        return new MockRouter(new MockSource.Assets(context));
    }

    /** 默认客户端：请求头、令牌刷新、错误检查、调试日志、Mock */
    @Provides
    @Singleton
    static OkHttpClient provideDefaultClient(TokenProvider tokenProvider, ClientContextProvider contextProvider,
                                             MockRouter mockRouter) {
        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .authenticator(new TokenAuthenticator(tokenProvider));
        addInterceptors(b, tokenProvider, contextProvider, mockRouter, HttpLoggingInterceptor.Level.BODY);
        return b.build();
    }

    /** 上传客户端：写入超时 120 秒，日志只打请求头（避免输出文件内容） */
    @Provides
    @Singleton
    @Named(UPLOAD)
    static OkHttpClient provideUploadClient(OkHttpClient base, TokenProvider tokenProvider,
                                            ClientContextProvider contextProvider, MockRouter mockRouter) {
        OkHttpClient.Builder b = base.newBuilder().writeTimeout(120, TimeUnit.SECONDS);
        b.interceptors().clear();
        addInterceptors(b, tokenProvider, contextProvider, mockRouter, HttpLoggingInterceptor.Level.HEADERS);
        return b.build();
    }

    /** 流式客户端（AI 问答 SSE）：读取超时 180 秒，日志只打请求头 */
    @Provides
    @Singleton
    @Named(STREAM)
    static OkHttpClient provideStreamClient(OkHttpClient base, TokenProvider tokenProvider,
                                            ClientContextProvider contextProvider, MockRouter mockRouter) {
        OkHttpClient.Builder b = base.newBuilder().readTimeout(180, TimeUnit.SECONDS);
        b.interceptors().clear();
        addInterceptors(b, tokenProvider, contextProvider, mockRouter, HttpLoggingInterceptor.Level.HEADERS);
        return b.build();
    }

    /** 刷新令牌专用客户端（API-A06）：不带 Authenticator，不加 Authorization，避免刷新时递归 */
    @Provides
    @Singleton
    @Named(REFRESH)
    static OkHttpClient provideRefreshClient(OkHttpClient base, ClientContextProvider contextProvider,
                                             MockRouter mockRouter) {
        OkHttpClient.Builder b = base.newBuilder().authenticator(okhttp3.Authenticator.NONE);
        b.interceptors().clear();
        addInterceptors(b, null, contextProvider, mockRouter, HttpLoggingInterceptor.Level.BODY);
        return b.build();
    }

    @Provides
    @Singleton
    static Retrofit provideRetrofit(OkHttpClient client, Gson gson) {
        return buildRetrofit(client, gson);
    }

    @Provides
    @Singleton
    @Named(UPLOAD)
    static Retrofit provideUploadRetrofit(@Named(UPLOAD) OkHttpClient client, Gson gson) {
        return buildRetrofit(client, gson);
    }

    @Provides
    @Singleton
    @Named(REFRESH)
    static Retrofit provideRefreshRetrofit(@Named(REFRESH) OkHttpClient client, Gson gson) {
        return buildRetrofit(client, gson);
    }

    private static Retrofit buildRetrofit(OkHttpClient client, Gson gson) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJava3CallAdapterFactory.createWithScheduler(Schedulers.io()))
                .build();
    }

    /** 应用拦截器顺序：请求头 → 错误检查 → 调试日志 → Mock（最后，直接返回示例数据） */
    private static void addInterceptors(OkHttpClient.Builder b, TokenProvider tokenProvider,
                                        ClientContextProvider contextProvider, MockRouter mockRouter,
                                        HttpLoggingInterceptor.Level logLevel) {
        b.addInterceptor(new HeaderInterceptor(tokenProvider, contextProvider, APP_VERSION));
        b.addInterceptor(new ErrorInterceptor(tokenProvider, APP_VERSION));
        if (BuildConfig.DEBUG) {
            b.addInterceptor(SafeLogger.create(logLevel));
        }
        if (mockEnabled()) {
            b.addInterceptor(new MockInterceptor(mockRouter));
        }
    }
}
