package com.zhimu.slmp.health.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Component;

/**
 * 模块08 免疫、用药与消毒组件的 Dagger 组件：依赖 CoreComponent，提供本组件的 ViewModel 工厂。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = {HealthModule.class, HealthViewModelModule.class})
public interface HealthComponent {

    ViewModelFactory viewModelFactory();

    @Component.Factory
    interface Factory {
        HealthComponent create(CoreComponent core);
    }
}
