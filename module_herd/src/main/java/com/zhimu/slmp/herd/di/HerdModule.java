package com.zhimu.slmp.herd.di;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.herd.data.HerdApi;

import dagger.Module;
import dagger.Provides;
import retrofit2.Retrofit;

/**
 * 模块04 栏舍与存栏组件的 Api 绑定（@FeatureScope）；Repository 为 @Inject 构造的 @FeatureScope 单例。
 */
@Module
public abstract class HerdModule {

    @Provides
    @FeatureScope
    static HerdApi provideHerdApi(Retrofit retrofit) {
        return retrofit.create(HerdApi.class);
    }
}
