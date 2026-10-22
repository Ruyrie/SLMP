package com.zhimu.slmp.map.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;
import com.zhimu.slmp.map.ui.pick.MapPickViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 地图组件的 ViewModel 绑定（AmapRepository 为 @Inject 构造）。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class MapModule {

    @Binds
    @IntoMap
    @ViewModelKey(MapPickViewModel.class)
    abstract ViewModel bindMapPick(MapPickViewModel vm);
}
