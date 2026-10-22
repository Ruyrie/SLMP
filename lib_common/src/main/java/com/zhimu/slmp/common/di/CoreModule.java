package com.zhimu.slmp.common.di;

import android.content.Context;

import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.common.data.BaseDataApi;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.session.SessionApi;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.network.ClientContextProvider;
import com.zhimu.slmp.network.auth.TokenProvider;
import com.zhimu.slmp.network.di.NetworkModule;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import retrofit2.Retrofit;

/**
 * CoreComponent 的绑定：网络层接口由会话与当前养殖场实现；数据库与公共 Api。
 */
@Module
public abstract class CoreModule {

    @Binds
    abstract TokenProvider bindTokenProvider(SessionManager sessionManager);

    @Binds
    abstract ClientContextProvider bindClientContext(FarmContext farmContext);

    @Provides
    @Singleton
    static AppDatabase provideDatabase(Context context) {
        return AppDatabase.build(context);
    }

    @Provides
    @Singleton
    static SessionApi provideSessionApi(@Named(NetworkModule.REFRESH) Retrofit retrofit) {
        return retrofit.create(SessionApi.class);
    }

    @Provides
    @Singleton
    static BaseDataApi provideBaseDataApi(Retrofit retrofit) {
        return retrofit.create(BaseDataApi.class);
    }
}
