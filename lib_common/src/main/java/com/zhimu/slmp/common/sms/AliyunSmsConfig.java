package com.zhimu.slmp.common.sms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.BuildConfig;

/**
 * 短信验证码测试直连的配置（2026-10-22 确认）：AccessKey 与签名名称来自 lib_common 的 BuildConfig
 * （local.properties 的 aliyun.accessKeyId、aliyun.accessKeySecret、aliyun.smsSignName，只生成到 debug 包）。
 * debug 包且三项都不为空时直连开启；release 包三项为空，直连关闭，业务代码走服务端接口。
 * 不输出、不记录任何 Key（toString 只给出是否开启）。
 */
public final class AliyunSmsConfig {

    private final boolean debug;
    @NonNull
    private final String accessKeyId;
    @NonNull
    private final String accessKeySecret;
    @NonNull
    private final String signName;

    public AliyunSmsConfig(boolean debug, @Nullable String accessKeyId, @Nullable String accessKeySecret,
                           @Nullable String signName) {
        this.debug = debug;
        this.accessKeyId = accessKeyId == null ? "" : accessKeyId.trim();
        this.accessKeySecret = accessKeySecret == null ? "" : accessKeySecret.trim();
        this.signName = signName == null ? "" : signName.trim();
    }

    /** 本包 BuildConfig 中的配置 */
    @NonNull
    public static AliyunSmsConfig fromBuildConfig() {
        return new AliyunSmsConfig(BuildConfig.DEBUG, BuildConfig.ALIYUN_ACCESS_KEY_ID,
                BuildConfig.ALIYUN_ACCESS_SECRET, BuildConfig.ALIYUN_SMS_SIGN_NAME);
    }

    /** 当前包是否开启短信直连（debug 且三项都不为空） */
    public static boolean isDirectEnabled() {
        return fromBuildConfig().isEnabled();
    }

    public boolean isEnabled() {
        return debug && !accessKeyId.isEmpty() && !accessKeySecret.isEmpty() && !signName.isEmpty();
    }

    @NonNull
    String getAccessKeyId() {
        return accessKeyId;
    }

    @NonNull
    String getAccessKeySecret() {
        return accessKeySecret;
    }

    @NonNull
    public String getSignName() {
        return signName;
    }

    @NonNull
    @Override
    public String toString() {
        return "AliyunSmsConfig{enabled=" + isEnabled() + "}";
    }
}
