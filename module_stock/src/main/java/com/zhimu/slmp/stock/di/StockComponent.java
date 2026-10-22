package com.zhimu.slmp.stock.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Component;

/**
 * 模块09 饲料与兽药疫苗库存组件的 Dagger 组件：依赖 CoreComponent，提供本组件的 ViewModel 工厂。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = {StockModule.class, StockViewModelModule.class})
public interface StockComponent {

    ViewModelFactory viewModelFactory();

    @Component.Factory
    interface Factory {
        StockComponent create(CoreComponent core);
    }
}
