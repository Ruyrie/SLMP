package com.zhimu.slmp.health.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.Router;

/**
 * 页面跳转与返回：打开其他页面（需要结果时用已注册的 launcher）、关闭并在上一页显示全局提示、
 * 切换养殖场后关闭页面（farmSwitched）。
 */
public final class HealthNav {

    private HealthNav() {
    }

    public static void open(@NonNull Activity activity, @Nullable ActivityResultLauncher<Intent> launcher,
                            @NonNull String action, @Nullable Bundle extras) {
        if (launcher != null) {
            Router.openForResult(activity, launcher, action, extras);
        } else {
            Router.open(activity, action, extras);
        }
    }

    /**
     * 关闭当前页面并在上一页显示提示（BaseApplication 在下一个前台页面显示全局提示），
     * 同时 setResult(RESULT_OK) 让上一页刷新。
     */
    public static void finishWithMessage(@NonNull Activity activity, @Nullable UiMessage message) {
        activity.setResult(Activity.RESULT_OK);
        activity.finish();
        if (message != null) {
            GlobalEvents.postMessage(message);
        }
    }

    public static void finishWithMessage(@NonNull Activity activity, @Nullable String message) {
        finishWithMessage(activity, message == null || message.isEmpty() ? null : UiMessage.of(message));
    }

    /** 当前养殖场与进入页面时不同（切换或退出养殖场）时关闭页面 */
    public static void finishOnFarmSwitch(@NonNull AppCompatActivity activity) {
        FarmContext farm = CoreInjector.get(activity).farmContext();
        final long entered = farm.farmIdOrZero();
        farm.getContext().observe(activity, vo -> {
            long now = farm.farmIdOrZero();
            if (entered != 0 && now != entered && !activity.isFinishing()) {
                activity.finish();
            }
        });
    }
}
