package com.zhimu.slmp.breed.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Component;

/**
 * 模块05 繁育管理组件的 Dagger 组件：依赖 CoreComponent，提供本组件的 ViewModel 工厂。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = {BreedModule.class, BreedViewModelModule.class})
public interface BreedComponent {

    ViewModelFactory viewModelFactory();

    @Component.Factory
    interface Factory {
        BreedComponent create(CoreComponent core);
    }
}
