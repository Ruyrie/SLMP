package com.zhimu.slmp.herd.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Component;

/**
 * 模块04 栏舍与存栏组件的 Dagger 组件：依赖 CoreComponent，提供本组件的 ViewModel 工厂。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = {HerdModule.class, HerdViewModelModule.class})
public interface HerdComponent {

    ViewModelFactory viewModelFactory();

    @Component.Factory
    interface Factory {
        HerdComponent create(CoreComponent core);
    }
}
