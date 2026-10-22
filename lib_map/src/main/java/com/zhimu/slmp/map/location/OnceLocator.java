package com.zhimu.slmp.map.location;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.zhimu.slmp.map.AmapInitializer;
import com.zhimu.slmp.map.model.vo.MapPoint;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;

/**
 * 单次定位工具（03 号文档 9.2、9.3 节；F04 选点、I02 巡检共用）：高精度、单次、超时 10 秒，
 * 回调转为 Single；订阅结束（成功、失败、取消）时停止并销毁 AMapLocationClient。
 * 调用前需已获得 ACCESS_FINE_LOCATION 或 ACCESS_COARSE_LOCATION（只给大致位置时照常定位，精度按返回值）。
 */
public final class OnceLocator {

    /** 定位超时 */
    public static final long TIMEOUT_MS = 10_000;

    private OnceLocator() {
    }

    /** 定位失败：高德错误码（12 为缺少定位权限，10 为 Key 错误） */
    public static class LocateException extends Exception {
        private final int errorCode;

        public LocateException(int errorCode, @Nullable String message) {
            super(message == null ? "定位失败" : message);
            this.errorCode = errorCode;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }

    /** 单次定位；在主线程创建定位客户端，结果在主线程回调 */
    @NonNull
    public static Single<MapPoint> locate(@NonNull Context context) {
        final Context app = context.getApplicationContext();
        return Single.<MapPoint>create(emitter -> {
                    if (!AmapInitializer.isMapAvailable()) {
                        emitter.tryOnError(new LocateException(-1, "未配置高德地图 Key"));
                        return;
                    }
                    AMapLocationClient client = new AMapLocationClient(app);
                    AMapLocationClientOption option = new AMapLocationClientOption();
                    option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                    option.setOnceLocation(true);
                    option.setOnceLocationLatest(true);
                    option.setNeedAddress(false);
                    option.setHttpTimeOut(TIMEOUT_MS);
                    client.setLocationOption(option);
                    client.setLocationListener(location -> {
                        if (location != null && location.getErrorCode() == AMapLocation.LOCATION_SUCCESS) {
                            emitter.onSuccess(new MapPoint(location.getLatitude(), location.getLongitude(),
                                    Math.round(location.getAccuracy())));
                        } else {
                            emitter.tryOnError(new LocateException(location == null ? -1 : location.getErrorCode(),
                                    location == null ? null : location.getErrorInfo()));
                        }
                    });
                    emitter.setCancellable(() -> {
                        client.stopLocation();
                        client.onDestroy();
                    });
                    client.startLocation();
                })
                .subscribeOn(AndroidSchedulers.mainThread())
                .timeout(TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS);
    }
}
