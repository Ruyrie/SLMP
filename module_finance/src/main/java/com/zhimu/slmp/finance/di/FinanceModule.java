package com.zhimu.slmp.finance.di;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.ui.change.ChangeApi;
import com.zhimu.slmp.finance.data.FinanceApi;
import com.zhimu.slmp.finance.data.ReportExportApi;

import java.util.concurrent.TimeUnit;

import dagger.Module;
import dagger.Provides;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

/**
 * 模块10 收支、出栏、行情与报表组件的 Api 绑定（@FeatureScope）；Repository 均为 @Inject 构造。
 */
@Module
public abstract class FinanceModule {

    @Provides
    @FeatureScope
    static FinanceApi financeApi(Retrofit retrofit) {
        return retrofit.create(FinanceApi.class);
    }

    /** API-E42：在默认客户端（拦截器、Mock 不变）上派生读超时 45 秒的客户端 */
    @Provides
    @FeatureScope
    static ReportExportApi reportExportApi(Retrofit retrofit) {
        Call.Factory factory = retrofit.callFactory();
        if (!(factory instanceof OkHttpClient)) {
            return retrofit.create(ReportExportApi.class);
        }
        OkHttpClient longRead = ((OkHttpClient) factory).newBuilder()
                .readTimeout(ReportExportApi.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();
        return retrofit.newBuilder().client(longRead).build().create(ReportExportApi.class);
    }

    /** E08 直接调用 U01、U02（lib_common 的 ChangeRepository） */
    @Provides
    @FeatureScope
    static ChangeApi changeApi(Retrofit retrofit) {
        return retrofit.create(ChangeApi.class);
    }
}
