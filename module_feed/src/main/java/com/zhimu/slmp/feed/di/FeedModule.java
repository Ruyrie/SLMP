package com.zhimu.slmp.feed.di;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.feed.data.FeedApi;
import com.zhimu.slmp.feed.data.WeighApi;

import dagger.Module;
import dagger.Provides;
import retrofit2.Retrofit;

/**
 * 模块06 饲喂与生长称重组件的 Api 绑定（@FeatureScope）；FeedRepository、WeighRepository 为 @Inject 构造。
 */
@Module
public abstract class FeedModule {

    @Provides
    @FeatureScope
    static FeedApi feedApi(Retrofit retrofit) {
        return retrofit.create(FeedApi.class);
    }

    @Provides
    @FeatureScope
    static WeighApi weighApi(Retrofit retrofit) {
        return retrofit.create(WeighApi.class);
    }
}
