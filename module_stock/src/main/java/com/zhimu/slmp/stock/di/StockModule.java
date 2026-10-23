package com.zhimu.slmp.stock.di;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.stock.data.StockApi;

import dagger.Module;
import dagger.Provides;
import retrofit2.Retrofit;

/**
 * 模块09 饲料与兽药疫苗库存组件的 Api 绑定（@FeatureScope）；StockRepository、StockDraftRepository 为 @Inject 构造。
 */
@Module
public abstract class StockModule {

    @Provides
    @FeatureScope
    static StockApi stockApi(Retrofit retrofit) {
        return retrofit.create(StockApi.class);
    }
}
