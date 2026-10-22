package com.zhimu.slmp.stock.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;

import dagger.Module;

/**
 * 模块09 饲料与兽药疫苗库存组件的 ViewModel 多绑定：
 * {@code @Binds @IntoMap @ViewModelKey(XxxViewModel.class) abstract ViewModel bindXxx(XxxViewModel vm);}
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class StockViewModelModule {
}
