package com.zhimu.slmp.farm.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;

import dagger.Module;

/**
 * 模块02 养殖场与成员管理组件的 ViewModel 多绑定：
 * {@code @Binds @IntoMap @ViewModelKey(XxxViewModel.class) abstract ViewModel bindXxx(XxxViewModel vm);}
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class FarmViewModelModule {
}
