package com.zhimu.slmp.common.di;

import androidx.annotation.NonNull;

/**
 * 由 Application（BaseApplication）实现。
 */
public interface CoreComponentProvider {

    @NonNull
    CoreComponent getCoreComponent();
}
