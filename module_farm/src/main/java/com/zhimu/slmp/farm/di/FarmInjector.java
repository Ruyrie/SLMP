package com.zhimu.slmp.farm.di;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;

/**
 * 持有 FarmComponent（由 FarmModuleEntry 创建）；页面中用 {@code FarmInjector.factory()} 获取 ViewModel 工厂。
 */
public final class FarmInjector {

    private static volatile FarmComponent component;

    private FarmInjector() {
    }

    public static void init(@NonNull CoreComponent core) {
        if (component == null) {
            component = DaggerFarmComponent.factory().create(core);
        }
    }

    @NonNull
    public static FarmComponent component() {
        FarmComponent c = component;
        if (c == null) {
            throw new IllegalStateException("FarmComponent 未初始化：Application 的 moduleEntryClassNames 需包含 FarmModuleEntry");
        }
        return c;
    }

    @NonNull
    public static ViewModelFactory factory() {
        return component().viewModelFactory();
    }
}
