package com.zhimu.slmp.common.di;

import android.content.Context;

import com.google.gson.Gson;
import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.session.AppSettings;
import com.zhimu.slmp.common.session.InstallIdProvider;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.network.di.NetworkModule;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.BindsInstance;
import dagger.Component;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

/**
 * 全应用单例组件（由 BaseApplication 创建）：网络、会话、当前养殖场、数据库、字典。
 * 业务组件的 XxxComponent 以 dependencies = CoreComponent.class 使用这里暴露的对象。
 */
@Singleton
@Component(modules = {CoreModule.class, NetworkModule.class})
public interface CoreComponent {

    /** Application Context */
    Context context();

    Gson gson();

    /** 默认 Retrofit（业务组件用它创建 XxxApi） */
    Retrofit retrofit();

    /** 上传用 Retrofit（写入超时 120 秒） */
    @Named(NetworkModule.UPLOAD)
    Retrofit uploadRetrofit();

    /** 流式 OkHttpClient（AI 问答，读取超时 180 秒） */
    @Named(NetworkModule.STREAM)
    OkHttpClient streamClient();

    SessionManager sessionManager();

    FarmContext farmContext();

    AppDatabase database();

    BaseDataRepository baseDataRepository();

    AppSettings appSettings();

    InstallIdProvider installIdProvider();

    @Component.Factory
    interface Factory {
        CoreComponent create(@BindsInstance Context applicationContext);
    }
}
