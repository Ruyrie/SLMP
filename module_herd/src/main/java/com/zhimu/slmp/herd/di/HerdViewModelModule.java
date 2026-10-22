package com.zhimu.slmp.herd.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;

import dagger.Module;

/**
 * 模块04 栏舍与存栏组件的 ViewModel 多绑定：
 * {@code @Binds @IntoMap @ViewModelKey(XxxViewModel.class) abstract ViewModel bindXxx(XxxViewModel vm);}
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class HerdViewModelModule {
}
