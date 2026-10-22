package com.zhimu.slmp.farm.di;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.farm.api.FarmApi;

import dagger.Module;
import dagger.Provides;
import retrofit2.Retrofit;

/**
 * 模块02 养殖场与成员管理组件的 Api 与 Repository 绑定（@FeatureScope）。
 * FarmRepository、MemberRepository、FarmContextServiceImpl、FarmInviteServiceImpl 为 @Inject 构造。
 */
@Module
public abstract class FarmModule {

    @Provides
    @FeatureScope
    static FarmApi provideFarmApi(Retrofit retrofit) {
        return retrofit.create(FarmApi.class);
    }
}
