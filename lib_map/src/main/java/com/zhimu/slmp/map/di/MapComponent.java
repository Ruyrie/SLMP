package com.zhimu.slmp.map.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.map.repository.AmapRepository;

import dagger.Component;

/**
 * 地图组件的 Dagger 组件：依赖 CoreComponent，提供 F04 的 ViewModel 工厂与 AmapRepository。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = MapModule.class)
public interface MapComponent {

    ViewModelFactory viewModelFactory();

    AmapRepository amapRepository();

    @Component.Factory
    interface Factory {
        MapComponent create(CoreComponent core);
    }
}
