package com.zhimu.slmp.ai.di;

import android.content.Context;

import com.zhimu.slmp.ai.data.AiApi;
import com.zhimu.slmp.ai.data.AiRepository;
import com.zhimu.slmp.ai.data.AiStreamApi;
import com.zhimu.slmp.ai.data.AnswerEngine;
import com.zhimu.slmp.ai.data.DirectAnswerEngine;
import com.zhimu.slmp.ai.data.ServerAnswerEngine;
import com.zhimu.slmp.ai.data.SpeechEngine;
import com.zhimu.slmp.ai.util.InputModeStore;
import com.zhimu.slmp.ai.util.NetworkStatus;
import com.zhimu.slmp.common.ai.AiChatClient;
import com.zhimu.slmp.common.ai.AiSpeechClient;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.network.di.NetworkModule;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

/**
 * 模块11 AI 养殖助手与养殖课堂组件的 Api 与 Repository 绑定（@FeatureScope）。
 * 回答与语音识别来源：debug 包配置了百炼 Key 时测试直连（2026-10-22 用户确认），否则走服务端接口（debug 由 Mock 返回）。
 */
@Module
public abstract class AiModule {

    @Provides
    @FeatureScope
    static AiApi api(Retrofit retrofit) {
        return retrofit.create(AiApi.class);
    }

    /** API-K06 / K07：流式 OkHttpClient（读超时 180 秒，共用令牌拦截器与 Mock） */
    @Provides
    @FeatureScope
    static AiStreamApi streamApi(Retrofit retrofit, @Named(NetworkModule.STREAM) OkHttpClient streamClient) {
        return retrofit.newBuilder().client(streamClient).build().create(AiStreamApi.class);
    }

    @Provides
    @FeatureScope
    static AnswerEngine answerEngine(AiRepository repository, FarmContext farm) {
        AiChatClient client = AiChatClient.getDefault();
        return client.isEnabled() ? new DirectAnswerEngine(client, farm) : new ServerAnswerEngine(repository);
    }

    @Provides
    @FeatureScope
    static SpeechEngine speechEngine(AiRepository repository) {
        return SpeechEngine.create(repository, AiSpeechClient.getDefault());
    }

    @Provides
    @FeatureScope
    static NetworkStatus networkStatus(Context context) {
        return new NetworkStatus.Android(context);
    }

    @Provides
    @FeatureScope
    static InputModeStore inputModeStore(Context context) {
        return new InputModeStore(context);
    }
}
