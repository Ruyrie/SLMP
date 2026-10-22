package com.zhimu.slmp.common.session;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * 应用设置 zmt_settings：隐私政策同意状态与版本、上次登录手机号、课程视频仅 Wi-Fi 播放、通知权限说明是否已展示。
 * 退出登录不清除。
 */
@Singleton
public class AppSettings {

    public static final String FILE = "zmt_settings";
    private static final String KEY_PRIVACY_AGREED = "privacy_agreed";
    private static final String KEY_PRIVACY_VERSION = "privacy_version";
    private static final String KEY_LAST_PHONE = "last_phone";
    private static final String KEY_VIDEO_WIFI_ONLY = "video_wifi_only";
    private static final String KEY_NOTIFY_GUIDE_SHOWN = "notify_guide_shown";

    private final SharedPreferences prefs;

    @Inject
    public AppSettings(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean isPrivacyAgreed() {
        return prefs.getBoolean(KEY_PRIVACY_AGREED, false);
    }

    @Nullable
    public String getPrivacyVersion() {
        return prefs.getString(KEY_PRIVACY_VERSION, null);
    }

    public void setPrivacyAgreed(@NonNull String version) {
        prefs.edit().putBoolean(KEY_PRIVACY_AGREED, true).putString(KEY_PRIVACY_VERSION, version).apply();
    }

    @Nullable
    public String getLastPhone() {
        return prefs.getString(KEY_LAST_PHONE, null);
    }

    public void setLastPhone(@Nullable String phone) {
        prefs.edit().putString(KEY_LAST_PHONE, phone).apply();
    }

    /** 默认仅 Wi-Fi 播放 */
    public boolean isVideoWifiOnly() {
        return prefs.getBoolean(KEY_VIDEO_WIFI_ONLY, true);
    }

    public void setVideoWifiOnly(boolean wifiOnly) {
        prefs.edit().putBoolean(KEY_VIDEO_WIFI_ONLY, wifiOnly).apply();
    }

    public boolean isNotifyGuideShown() {
        return prefs.getBoolean(KEY_NOTIFY_GUIDE_SHOWN, false);
    }

    public void setNotifyGuideShown(boolean shown) {
        prefs.edit().putBoolean(KEY_NOTIFY_GUIDE_SHOWN, shown).apply();
    }

    @NonNull
    SharedPreferences prefs() {
        return prefs;
    }
}
