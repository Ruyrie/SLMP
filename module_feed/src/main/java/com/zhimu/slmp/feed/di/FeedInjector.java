package com.zhimu.slmp.feed.di;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;

/**
 * 持有 FeedComponent（由 FeedModuleEntry 创建）；页面中用 {@code FeedInjector.factory()} 获取 ViewModel 工厂。
 */
public final class FeedInjector {

    private static volatile FeedComponent component;

    private FeedInjector() {
    }

    public static void init(@NonNull CoreComponent core) {
        if (component == null) {
            component = DaggerFeedComponent.factory().create(core);
        }
    }

    @NonNull
    public static FeedComponent component() {
        FeedComponent c = component;
        if (c == null) {
            throw new IllegalStateException("FeedComponent 未初始化：Application 的 moduleEntryClassNames 需包含 FeedModuleEntry");
        }
        return c;
    }

    @NonNull
    public static ViewModelFactory factory() {
        return component().viewModelFactory();
    }
}
