package com.zhimu.slmp.common.session;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * 安装标识（zmt_settings 的 install_id）：首次使用时生成 UUID，作为登录请求的 device_id；卸载重装后变化。
 */
@Singleton
public class InstallIdProvider {

    private static final String KEY_INSTALL_ID = "install_id";

    private final SharedPreferences prefs;

    @Inject
    public InstallIdProvider(@NonNull AppSettings settings) {
        this.prefs = settings.prefs();
    }

    @NonNull
    public synchronized String get() {
        String id = prefs.getString(KEY_INSTALL_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_INSTALL_ID, id).apply();
        }
        return id;
    }
}
