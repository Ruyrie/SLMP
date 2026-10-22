package com.zhimu.slmp.health.di;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.health.data.DisinfectApi;
import com.zhimu.slmp.health.data.HealthSharedApi;
import com.zhimu.slmp.health.data.ImmuneApi;
import com.zhimu.slmp.health.data.MedicationApi;

import dagger.Module;
import dagger.Provides;
import retrofit2.Retrofit;

/**
 * 模块08 免疫、用药与消毒组件的 Api 绑定（@FeatureScope）；Repository 为 @Inject 构造的 @FeatureScope 单例。
 */
@Module
public abstract class HealthModule {

    @Provides
    @FeatureScope
    static ImmuneApi provideImmuneApi(Retrofit retrofit) {
        return retrofit.create(ImmuneApi.class);
    }

    @Provides
    @FeatureScope
    static MedicationApi provideMedicationApi(Retrofit retrofit) {
        return retrofit.create(MedicationApi.class);
    }

    @Provides
    @FeatureScope
    static DisinfectApi provideDisinfectApi(Retrofit retrofit) {
        return retrofit.create(DisinfectApi.class);
    }

    @Provides
    @FeatureScope
    static HealthSharedApi provideSharedApi(Retrofit retrofit) {
        return retrofit.create(HealthSharedApi.class);
    }
}
