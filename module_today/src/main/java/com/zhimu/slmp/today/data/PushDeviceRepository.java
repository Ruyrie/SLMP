package com.zhimu.slmp.today.data;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.today.model.request.PushDeviceRequest;
import com.zhimu.slmp.today.model.vo.PushDeviceVO;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * 推送设备（API-T20、T21）。失败只记日志，不阻塞登录与退出（由调用方处理）。
 */
@FeatureScope
public class PushDeviceRepository {

    private final TodayApi api;

    @Inject
    public PushDeviceRepository(@NonNull TodayApi api) {
        this.api = api;
    }

    /** API-T20 */
    @NonNull
    public Single<PushDeviceVO> register(@NonNull PushDeviceRequest body) {
        return api.registerPushDevice(body).compose(RxResult.unwrap());
    }

    /**
     * API-T21
     *
     * @param accessToken 解绑时的访问令牌（退出登录前取出），为空时由拦截器添加
     */
    @NonNull
    public Completable unbind(@NonNull String registrationId, @androidx.annotation.Nullable String accessToken) {
        String auth = accessToken == null ? null : com.zhimu.slmp.network.Headers.BEARER + accessToken;
        return RxResult.completable(api.unbindPushDevice(registrationId, auth));
    }
}
