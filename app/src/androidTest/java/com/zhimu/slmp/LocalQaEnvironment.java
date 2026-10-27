package com.zhimu.slmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.network.mock.MockInterceptor;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

/** Guard real regression writes using the installed app's client, never inlined BuildConfig values. */
public final class LocalQaEnvironment {
    private static final String LOCAL_BACKEND = "http://127.0.0.1:8091/";

    private LocalQaEnvironment() { }

    public static CoreComponent requireLocalCore() {
        return requireLocalCore(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    public static CoreComponent requireLocalCore(Context context) {
        CoreComponent core = CoreInjector.get(context);
        assertEquals("禁止向非本地隔离QA服务执行回归写入，请先安装本地版App", LOCAL_BACKEND,
                core.retrofit().baseUrl().toString());
        assertTrue("无法核验当前客户端的Mock状态", core.retrofit().callFactory() instanceof OkHttpClient);
        OkHttpClient client = (OkHttpClient) core.retrofit().callFactory();
        for (Interceptor interceptor : client.interceptors()) {
            assertFalse("回归必须请求真实本地接口，不能使用Mock响应", interceptor instanceof MockInterceptor);
        }
        for (Interceptor interceptor : client.networkInterceptors()) {
            assertFalse("回归必须请求真实本地接口，不能使用Mock响应", interceptor instanceof MockInterceptor);
        }
        return core;
    }
}
