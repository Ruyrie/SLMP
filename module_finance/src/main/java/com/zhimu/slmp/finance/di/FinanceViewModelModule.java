package com.zhimu.slmp.finance.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;

import dagger.Module;

/**
 * 模块10 收支、出栏、行情与报表组件的 ViewModel 多绑定：
 * {@code @Binds @IntoMap @ViewModelKey(XxxViewModel.class) abstract ViewModel bindXxx(XxxViewModel vm);}
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class FinanceViewModelModule {
}
