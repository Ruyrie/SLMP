package com.zhimu.slmp.today.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Component;

/**
 * 模块03 今日首页、日历待办与消息中心组件的 Dagger 组件：依赖 CoreComponent，提供本组件的 ViewModel 工厂。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = {TodayModule.class, TodayViewModelModule.class})
public interface TodayComponent {

    ViewModelFactory viewModelFactory();

    @Component.Factory
    interface Factory {
        TodayComponent create(CoreComponent core);
    }
}
