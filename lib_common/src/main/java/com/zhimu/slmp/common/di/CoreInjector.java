package com.zhimu.slmp.common.di;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * 取得 CoreComponent：{@code CoreInjector.get(context).sessionManager()}。
 */
public final class CoreInjector {

    private CoreInjector() {
    }

    @NonNull
    public static CoreComponent get(@NonNull Context context) {
        Object app = context.getApplicationContext();
        if (app instanceof CoreComponentProvider) {
            return ((CoreComponentProvider) app).getCoreComponent();
        }
        throw new IllegalStateException("Application 需要继承 BaseApplication（实现 CoreComponentProvider）");
    }
}
