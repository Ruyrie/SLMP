package com.zhimu.slmp.today.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.session.AppSettings;

import javax.inject.Inject;

/**
 * 本组件在 zmt_settings 中的偏好：养殖日历上次使用的视图与养殖场范围（退出登录不清除）。
 */
@FeatureScope
public class TodayPrefs {

    private static final String KEY_CALENDAR_MODE = "today_calendar_mode";
    private static final String KEY_CALENDAR_SCOPE = "today_calendar_scope";

    private final SharedPreferences prefs;

    @Inject
    public TodayPrefs(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(AppSettings.FILE, Context.MODE_PRIVATE);
    }

    @Nullable
    public String getCalendarMode() {
        return prefs.getString(KEY_CALENDAR_MODE, null);
    }

    public void setCalendarMode(@NonNull String mode) {
        prefs.edit().putString(KEY_CALENDAR_MODE, mode).apply();
    }

    @Nullable
    public String getCalendarScope() {
        return prefs.getString(KEY_CALENDAR_SCOPE, null);
    }

    public void setCalendarScope(@NonNull String scope) {
        prefs.edit().putString(KEY_CALENDAR_SCOPE, scope).apply();
    }
}
