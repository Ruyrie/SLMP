package com.zhimu.slmp.inspect.di;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;

/**
 * 持有 InspectComponent（由 InspectModuleEntry 创建）；页面中用 {@code InspectInjector.factory()} 获取 ViewModel 工厂。
 */
public final class InspectInjector {

    private static volatile InspectComponent component;

    private InspectInjector() {
    }

    public static void init(@NonNull CoreComponent core) {
        if (component == null) {
            component = DaggerInspectComponent.factory().create(core);
        }
    }

    @NonNull
    public static InspectComponent component() {
        InspectComponent c = component;
        if (c == null) {
            throw new IllegalStateException("InspectComponent 未初始化：Application 的 moduleEntryClassNames 需包含 InspectModuleEntry");
        }
        return c;
    }

    @NonNull
    public static ViewModelFactory factory() {
        return component().viewModelFactory();
    }
}
