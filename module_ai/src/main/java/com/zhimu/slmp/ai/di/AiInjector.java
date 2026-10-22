package com.zhimu.slmp.ai.di;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;

/**
 * 持有 AiComponent（由 AiModuleEntry 创建）；页面中用 {@code AiInjector.factory()} 获取 ViewModel 工厂。
 */
public final class AiInjector {

    private static volatile AiComponent component;

    private AiInjector() {
    }

    public static void init(@NonNull CoreComponent core) {
        if (component == null) {
            component = DaggerAiComponent.factory().create(core);
        }
    }

    @NonNull
    public static AiComponent component() {
        AiComponent c = component;
        if (c == null) {
            throw new IllegalStateException("AiComponent 未初始化：Application 的 moduleEntryClassNames 需包含 AiModuleEntry");
        }
        return c;
    }

    @NonNull
    public static ViewModelFactory factory() {
        return component().viewModelFactory();
    }
}
