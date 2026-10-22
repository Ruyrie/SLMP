package com.zhimu.slmp.account.util;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.session.InstallIdProvider;

import javax.inject.Inject;

/**
 * 登录类请求的设备信息：device_id 为安装标识（zmt_settings 的 install_id，≤64），
 * device_model（≤50）、os_version（≤30）取自 Build。
 */
@FeatureScope
public class DeviceInfoProvider {

    private final InstallIdProvider installIdProvider;

    @Inject
    public DeviceInfoProvider(@NonNull InstallIdProvider installIdProvider) {
        this.installIdProvider = installIdProvider;
    }

    @NonNull
    public DeviceInfo get() {
        String id = truncate(installIdProvider.get(), 64);
        String release = Build.VERSION.RELEASE;
        return new DeviceInfo(id == null ? "" : id, truncate(Build.MODEL, 50),
                truncate(release == null ? null : "Android " + release, 30));
    }

    @Nullable
    private static String truncate(@Nullable String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
