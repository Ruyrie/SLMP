package com.zhimu.slmp.inspect.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Component;

/**
 * 模块07 日常巡检与异常上报 AI 诊断组件的 Dagger 组件：依赖 CoreComponent，提供本组件的 ViewModel 工厂。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = {InspectModule.class, InspectViewModelModule.class})
public interface InspectComponent {

    ViewModelFactory viewModelFactory();

    @Component.Factory
    interface Factory {
        InspectComponent create(CoreComponent core);
    }
}
