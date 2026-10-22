package com.zhimu.slmp.stock.di;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;

/**
 * 持有 StockComponent（由 StockModuleEntry 创建）；页面中用 {@code StockInjector.factory()} 获取 ViewModel 工厂。
 */
public final class StockInjector {

    private static volatile StockComponent component;

    private StockInjector() {
    }

    public static void init(@NonNull CoreComponent core) {
        if (component == null) {
            component = DaggerStockComponent.factory().create(core);
        }
    }

    @NonNull
    public static StockComponent component() {
        StockComponent c = component;
        if (c == null) {
            throw new IllegalStateException("StockComponent 未初始化：Application 的 moduleEntryClassNames 需包含 StockModuleEntry");
        }
        return c;
    }

    @NonNull
    public static ViewModelFactory factory() {
        return component().viewModelFactory();
    }
}
