package com.zhimu.slmp.finance.di;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;

/**
 * 持有 FinanceComponent（由 FinanceModuleEntry 创建）；页面中用 {@code FinanceInjector.factory()} 获取 ViewModel 工厂。
 */
public final class FinanceInjector {

    private static volatile FinanceComponent component;

    private FinanceInjector() {
    }

    public static void init(@NonNull CoreComponent core) {
        if (component == null) {
            component = DaggerFinanceComponent.factory().create(core);
        }
    }

    @NonNull
    public static FinanceComponent component() {
        FinanceComponent c = component;
        if (c == null) {
            throw new IllegalStateException("FinanceComponent 未初始化：Application 的 moduleEntryClassNames 需包含 FinanceModuleEntry");
        }
        return c;
    }

    @NonNull
    public static ViewModelFactory factory() {
        return component().viewModelFactory();
    }
}
