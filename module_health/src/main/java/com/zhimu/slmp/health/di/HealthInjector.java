package com.zhimu.slmp.health.di;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;

/**
 * 持有 HealthComponent（由 HealthModuleEntry 创建）；页面中用 {@code HealthInjector.factory()} 获取 ViewModel 工厂。
 */
public final class HealthInjector {

    private static volatile HealthComponent component;

    private HealthInjector() {
    }

    public static void init(@NonNull CoreComponent core) {
        if (component == null) {
            component = DaggerHealthComponent.factory().create(core);
        }
    }

    @NonNull
    public static HealthComponent component() {
        HealthComponent c = component;
        if (c == null) {
            throw new IllegalStateException("HealthComponent 未初始化：Application 的 moduleEntryClassNames 需包含 HealthModuleEntry");
        }
        return c;
    }

    @NonNull
    public static ViewModelFactory factory() {
        return component().viewModelFactory();
    }
}
