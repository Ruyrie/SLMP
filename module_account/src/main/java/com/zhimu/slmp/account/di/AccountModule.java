package com.zhimu.slmp.account.di;

import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.sms.AliyunSmsClient;
import com.zhimu.slmp.common.sms.AliyunSmsConfig;
import com.zhimu.slmp.network.di.NetworkModule;

import javax.inject.Named;

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

    /** 头像上传用同一套 AccountApi，但走上传 Retrofit（写入超时 120 秒） */
    @Provides
    @FeatureScope
    @Named(NetworkModule.UPLOAD)
    static AccountApi accountUploadApi(@Named(NetworkModule.UPLOAD) Retrofit retrofit) {
        return retrofit.create(AccountApi.class);
    }

    /** 短信验证码测试直连（debug 包且 Key 已配置时开启，release 包始终关闭） */
    @Provides
    @FeatureScope
    static AliyunSmsClient aliyunSmsClient() {
        return new AliyunSmsClient(AliyunSmsConfig.fromBuildConfig());
    }
}
