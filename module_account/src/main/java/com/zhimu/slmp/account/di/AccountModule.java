package com.zhimu.slmp.account.di;

import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Module;
import dagger.Provides;
import retrofit2.Retrofit;

/**
 * 模块01 账号与个人中心组件的 Api 与 Repository 绑定（@FeatureScope）。
 */
@Module
public abstract class AccountModule {

    @Provides
    @FeatureScope
    static AccountApi accountApi(Retrofit retrofit) {
        return retrofit.create(AccountApi.class);
    }
}
