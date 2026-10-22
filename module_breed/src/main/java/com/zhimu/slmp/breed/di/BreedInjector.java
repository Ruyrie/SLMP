package com.zhimu.slmp.breed.di;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;

/**
 * 持有 BreedComponent（由 BreedModuleEntry 创建）；页面中用 {@code BreedInjector.factory()} 获取 ViewModel 工厂。
 */
public final class BreedInjector {

    private static volatile BreedComponent component;

    private BreedInjector() {
    }

    public static void init(@NonNull CoreComponent core) {
        if (component == null) {
            component = DaggerBreedComponent.factory().create(core);
        }
    }

    @NonNull
    public static BreedComponent component() {
        BreedComponent c = component;
        if (c == null) {
            throw new IllegalStateException("BreedComponent 未初始化：Application 的 moduleEntryClassNames 需包含 BreedModuleEntry");
        }
        return c;
    }

    @NonNull
    public static ViewModelFactory factory() {
        return component().viewModelFactory();
    }
}
