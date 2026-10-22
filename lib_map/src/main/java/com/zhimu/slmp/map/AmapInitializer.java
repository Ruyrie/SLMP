package com.zhimu.slmp.map;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.services.core.ServiceSettings;

/**
 * 高德 SDK 隐私合规（03 号文档 9.1 节）：用户同意隐私政策前不调用任何高德接口；同意后依次调用地图、定位、搜索
 * 三个 SDK 的 updatePrivacyShow、updatePrivacyAgree，之后才创建 MapView 与 AMapLocationClient。
 * Key 由清单 meta-data {@code com.amap.api.v2.apikey} 的占位符 {@code ${AMAP_KEY}} 注入（local.properties 的 amap.key）。
 */
public final class AmapInitializer {

    private static final String TAG = "ZmtMap";

    private static volatile boolean privacyAgreed;

    private AmapInitializer() {
    }

    /** MapModuleEntry.onPrivacyAgreed 中调用（每个进程一次） */
    public static synchronized void onPrivacyAgreed(@NonNull Context context) {
        if (privacyAgreed) {
            return;
        }
        Context app = context.getApplicationContext();
        try {
            MapsInitializer.updatePrivacyShow(app, true, true);
            MapsInitializer.updatePrivacyAgree(app, true);
            AMapLocationClient.updatePrivacyShow(app, true, true);
            AMapLocationClient.updatePrivacyAgree(app, true);
            ServiceSettings.updatePrivacyShow(app, true, true);
            ServiceSettings.updatePrivacyAgree(app, true);
            privacyAgreed = true;
        } catch (Throwable t) {
            Log.w(TAG, "高德隐私合规接口调用失败", t);
        }
    }

    /** 已调用隐私合规接口 */
    public static boolean isPrivacyAgreed() {
        return privacyAgreed;
    }

    /** local.properties 中配置了 amap.key */
    public static boolean isKeyConfigured() {
        return !TextUtils.isEmpty(BuildConfig.AMAP_KEY);
    }

    /** 可以创建地图与定位对象：已配置 Key 且已同意隐私政策；否则 F04 改为手动填写地址 */
    public static boolean isMapAvailable() {
        return isKeyConfigured() && privacyAgreed;
    }
}
