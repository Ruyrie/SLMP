package com.zhimu.slmp.ai.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;

import dagger.Module;

/**
 * 模块11 AI 养殖助手与养殖课堂组件的 ViewModel 多绑定：
 * {@code @Binds @IntoMap @ViewModelKey(XxxViewModel.class) abstract ViewModel bindXxx(XxxViewModel vm);}
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class AiViewModelModule {
}
