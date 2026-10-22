package com.zhimu.slmp.finance.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Component;

/**
 * 模块10 收支、出栏、行情与报表组件的 Dagger 组件：依赖 CoreComponent，提供本组件的 ViewModel 工厂。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = {FinanceModule.class, FinanceViewModelModule.class})
public interface FinanceComponent {

    ViewModelFactory viewModelFactory();

    @Component.Factory
    interface Factory {
        FinanceComponent create(CoreComponent core);
    }
}
