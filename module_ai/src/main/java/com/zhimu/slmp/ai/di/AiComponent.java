package com.zhimu.slmp.ai.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Component;

/**
 * 模块11 AI 养殖助手与养殖课堂组件的 Dagger 组件：依赖 CoreComponent，提供本组件的 ViewModel 工厂。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = {AiModule.class, AiViewModelModule.class})
public interface AiComponent {

    ViewModelFactory viewModelFactory();

    @Component.Factory
    interface Factory {
        AiComponent create(CoreComponent core);
    }
}
