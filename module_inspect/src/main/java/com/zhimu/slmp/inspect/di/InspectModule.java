package com.zhimu.slmp.inspect.di;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.inspect.data.InspectApi;

import dagger.Module;
import dagger.Provides;
import retrofit2.Retrofit;

/**
 * 模块07 日常巡检与异常上报 AI 诊断组件的 Api 与 Repository 绑定（@FeatureScope）。
 * Repository（InspectionRepository、AbnormalReportRepository、DirectDiagnosisRepository）为 @Inject 构造，
 * 照片和视频网格的 MediaQueueController.Factory 依赖都来自 CoreComponent。
 */
@Module
public abstract class InspectModule {

    @Provides
    @FeatureScope
    static InspectApi api(Retrofit retrofit) {
        return retrofit.create(InspectApi.class);
    }
}
