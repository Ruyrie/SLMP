package com.zhimu.slmp.common.service;

import android.app.Application;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;

import java.util.Collections;
import java.util.List;

/**
 * 组件入口：BaseApplication 按类名反射创建（需要无参构造方法），在 onCreate 中创建本组件的 Dagger 组件、
 * 向 ServiceHub 注册服务、向 TabRegistry 注册底部导航标签。
 */
public interface IModuleEntry {

    /** Application.onCreate 中调用（主线程，不要做耗时操作，不要初始化需要隐私同意的 SDK） */
    void onCreate(@NonNull Application app, @NonNull CoreComponent core);

    /** 用户已同意隐私政策（启动时已同意或刚刚同意，每个进程只调用一次）：初始化高德、极光等 SDK */
    default void onPrivacyAgreed(@NonNull Application app) {
    }

    /** 单独运行时调试启动页列出的本组件页面 */
    @NonNull
    default List<DebugPage> debugPages() {
        return Collections.emptyList();
    }
}
