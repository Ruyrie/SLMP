package com.zhimu.slmp.breed.di;

import com.zhimu.slmp.breed.data.BreedApi;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Module;
import dagger.Provides;
import retrofit2.Retrofit;

/**
 * 模块05 繁育管理组件的 Api 与 Repository 绑定（@FeatureScope）；BreedRepository 为 @Inject 构造。
 */
@Module
public abstract class BreedModule {

    @Provides
    @FeatureScope
    static BreedApi api(Retrofit retrofit) {
        return retrofit.create(BreedApi.class);
    }
}
