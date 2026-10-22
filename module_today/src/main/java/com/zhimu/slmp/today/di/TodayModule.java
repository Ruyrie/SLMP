package com.zhimu.slmp.today.di;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.today.data.TodayApi;
import com.zhimu.slmp.today.push.PushDeviceManager;
import com.zhimu.slmp.today.service.TodayServiceImpl;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import retrofit2.Retrofit;

/**
 * 模块03 今日首页、日历待办与消息中心组件的 Api 与 Repository 绑定（@FeatureScope）。
 * Repository、TodayServiceImpl、PushDeviceManager 使用 @Inject 构造方法；服务接口绑定到本组件的实现。
 */
@Module
public abstract class TodayModule {

    @Provides
    @FeatureScope
    static TodayApi todayApi(Retrofit retrofit) {
        return retrofit.create(TodayApi.class);
    }

    @Binds
    abstract ITodoService bindTodoService(TodayServiceImpl impl);

    @Binds
    abstract IPushService bindPushService(PushDeviceManager impl);
}
