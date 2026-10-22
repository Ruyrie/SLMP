package com.zhimu.slmp.base.event;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;

/**
 * 全局一次性事件：由网络层、会话、ViewModel 发出，BaseApplication 注册的 ActivityLifecycleCallbacks 在前台页面处理。
 * 可在任意线程调用 post 方法。
 */
public final class GlobalEvents {

    private GlobalEvents() {
    }

    /** 需要更新的版本信息（响应头 X-Min-App-Version、X-App-Download-Url） */
    public static final class UpdateInfo {
        @NonNull
        private final String minVersion;
        @Nullable
        private final String downloadUrl;

        public UpdateInfo(@NonNull String minVersion, @Nullable String downloadUrl) {
            this.minVersion = minVersion;
            this.downloadUrl = downloadUrl;
        }

        @NonNull
        public String getMinVersion() {
            return minVersion;
        }

        @Nullable
        public String getDownloadUrl() {
            return downloadUrl;
        }
    }

    private static final SingleLiveEvent<String> RELOGIN = new SingleLiveEvent<>();
    private static final SingleLiveEvent<UiMessage> MESSAGE = new SingleLiveEvent<>();
    private static final SingleLiveEvent<UpdateInfo> UPDATE_REQUIRED = new SingleLiveEvent<>();
    private static final SingleLiveEvent<String> FORCED_STEP = new SingleLiveEvent<>();

    /** 需要重新登录，值为 40104 的 data.reason（可为 null） */
    public static LiveData<String> relogin() {
        return RELOGIN;
    }

    /** 全局提示（Snackbar 显示在前台页面） */
    public static LiveData<UiMessage> message() {
        return MESSAGE;
    }

    /** 需要更新 */
    public static LiveData<UpdateInfo> updateRequired() {
        return UPDATE_REQUIRED;
    }

    /** 须先完成强制步骤（40309），值为 data.next_step */
    public static LiveData<String> forcedStep() {
        return FORCED_STEP;
    }

    public static void postRelogin(@Nullable String reason) {
        dispatch(RELOGIN, reason);
    }

    public static void postMessage(@NonNull UiMessage message) {
        dispatch(MESSAGE, message);
    }

    public static void postMessage(@NonNull String text) {
        dispatch(MESSAGE, UiMessage.of(text));
    }

    public static void postUpdateRequired(@NonNull String minVersion, @Nullable String downloadUrl) {
        UpdateInfo last = UPDATE_REQUIRED.getValue();
        if (last != null && last.getMinVersion().equals(minVersion)) {
            return;
        }
        dispatch(UPDATE_REQUIRED, new UpdateInfo(minVersion, downloadUrl));
    }

    public static void postForcedStep(@Nullable String nextStep) {
        if (nextStep != null && !nextStep.isEmpty()) {
            dispatch(FORCED_STEP, nextStep);
        }
    }

    private static <T> void dispatch(SingleLiveEvent<T> event, T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            event.setValue(value);
        } else {
            event.postValue(value);
        }
    }
}
