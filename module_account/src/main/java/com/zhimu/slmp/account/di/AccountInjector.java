package com.zhimu.slmp.account.di;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;

/**
 * 持有 AccountComponent（由 AccountModuleEntry 创建）；页面中用 {@code AccountInjector.factory()} 获取 ViewModel 工厂。
 */
public final class AccountInjector {

    private static volatile AccountComponent component;

    private AccountInjector() {
    }

    public static void init(@NonNull CoreComponent core) {
        if (component == null) {
            component = DaggerAccountComponent.factory().create(core);
        }
    }

    @NonNull
    public static AccountComponent component() {
        AccountComponent c = component;
        if (c == null) {
            throw new IllegalStateException("AccountComponent 未初始化：Application 的 moduleEntryClassNames 需包含 AccountModuleEntry");
        }
        return c;
    }

    @NonNull
    public static ViewModelFactory factory() {
        return component().viewModelFactory();
    }
}
