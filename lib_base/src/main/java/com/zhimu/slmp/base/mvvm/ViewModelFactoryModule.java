package com.zhimu.slmp.base.mvvm;

import androidx.lifecycle.ViewModel;

import java.util.Map;

import dagger.Module;
import dagger.multibindings.Multibinds;

/**
 * 声明 ViewModel 映射（允许为空），各业务组件的 XxxViewModelModule 通过 includes 引入。
 */
@Module
public abstract class ViewModelFactoryModule {

    @Multibinds
    abstract Map<Class<? extends ViewModel>, ViewModel> viewModels();
}
