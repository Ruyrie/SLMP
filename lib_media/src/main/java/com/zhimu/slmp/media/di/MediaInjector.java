package com.zhimu.slmp.media.di;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.media.upload.UploadManager;

/**
 * 持有 MediaComponent（由 MediaModuleEntry 创建）。
 */
public final class MediaInjector {

    private static volatile MediaComponent component;

    private MediaInjector() {
    }

    public static void init(@NonNull CoreComponent core) {
        if (component == null) {
            component = DaggerMediaComponent.factory().create(core);
        }
    }

    @NonNull
    public static MediaComponent component() {
        MediaComponent c = component;
        if (c == null) {
            throw new IllegalStateException("MediaComponent 未初始化：Application 的 moduleEntryClassNames 需包含 MediaModuleEntry");
        }
        return c;
    }

    @NonNull
    public static ViewModelFactory factory() {
        return component().viewModelFactory();
    }

    /** 不用 Dagger 注入时取上传工厂 */
    @NonNull
    public static UploadManager.Factory uploadFactory() {
        return component().uploadFactory();
    }
}
