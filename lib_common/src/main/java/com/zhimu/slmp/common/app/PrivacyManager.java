package com.zhimu.slmp.common.app;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.session.AppSettings;

/**
 * 隐私政策（03 号文档第十三节）：首次启动先弹第 5 级对话框，同意后才初始化高德、极光（各入口 onPrivacyAgreed）；
 * 不同意只能退出；隐私政策版本更新后重新确认。
 */
public final class PrivacyManager {

    /** 当前隐私政策版本（与 API-A21 的协议版本一致） */
    public static final String POLICY_VERSION = "2026-10-01";

    static final String REQUEST_KEY = "common_core_privacy";
    static final String RESULT_AGREED = "agreed";
    private static final String TAG = "common_core_privacy_dialog";

    private PrivacyManager() {
    }

    public static boolean isAgreed(@NonNull AppSettings settings) {
        return settings.isPrivacyAgreed() && POLICY_VERSION.equals(settings.getPrivacyVersion());
    }

    /**
     * 在页面 onCreate 中调用：已同意时立即执行 onAgreed；否则显示对话框，同意后执行，不同意退出应用。
     * 页面重建后再次调用会重新绑定结果监听，不会重复弹出。
     */
    public static void ensureAgreed(@NonNull FragmentActivity host, @NonNull Runnable onAgreed) {
        AppSettings settings = CoreInjector.get(host).appSettings();
        if (isAgreed(settings)) {
            onAgreed.run();
            return;
        }
        FragmentManager fm = host.getSupportFragmentManager();
        fm.setFragmentResultListener(REQUEST_KEY, host, (key, result) -> {
            if (result.getBoolean(RESULT_AGREED)) {
                settings.setPrivacyAgreed(POLICY_VERSION);
                BaseApplication.from(host).dispatchPrivacyAgreed();
                onAgreed.run();
            } else {
                host.finishAffinity();
            }
        });
        if (fm.findFragmentByTag(TAG) == null) {
            new PrivacyDialogFragment().show(fm, TAG);
        }
    }
}
