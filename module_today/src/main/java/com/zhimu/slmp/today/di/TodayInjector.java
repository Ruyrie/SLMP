package com.zhimu.slmp.today.di;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;

/**
 * 持有 TodayComponent（由 TodayModuleEntry 创建）；页面中用 {@code TodayInjector.factory()} 获取 ViewModel 工厂。
 */
public final class TodayInjector {

    private static volatile TodayComponent component;

    private TodayInjector() {
    }

    public static void init(@NonNull CoreComponent core) {
        if (component == null) {
            component = DaggerTodayComponent.factory().create(core);
        }
    }

    @NonNull
    public static TodayComponent component() {
        TodayComponent c = component;
        if (c == null) {
            throw new IllegalStateException("TodayComponent 未初始化：Application 的 moduleEntryClassNames 需包含 TodayModuleEntry");
        }
        return c;
    }

    @NonNull
    public static ViewModelFactory factory() {
        return component().viewModelFactory();
    }
}
