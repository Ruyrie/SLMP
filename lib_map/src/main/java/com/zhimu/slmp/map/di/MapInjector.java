package com.zhimu.slmp.map.di;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;

/**
 * 持有 MapComponent（由 MapModuleEntry 创建）。
 */
public final class MapInjector {

    private static volatile MapComponent component;

    private MapInjector() {
    }

    public static void init(@NonNull CoreComponent core) {
        if (component == null) {
            component = DaggerMapComponent.factory().create(core);
        }
    }

    @NonNull
    public static MapComponent component() {
        MapComponent c = component;
        if (c == null) {
            throw new IllegalStateException("MapComponent 未初始化：Application 的 moduleEntryClassNames 需包含 MapModuleEntry");
        }
        return c;
    }

    @NonNull
    public static ViewModelFactory factory() {
        return component().viewModelFactory();
    }
}
