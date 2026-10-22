package com.zhimu.slmp.base.mvvm;

import androidx.lifecycle.ViewModel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import dagger.MapKey;

/**
 * ViewModel 多绑定的键：在组件的 XxxViewModelModule 中
 * {@code @Binds @IntoMap @ViewModelKey(LoginViewModel.class) abstract ViewModel bindLogin(LoginViewModel vm);}
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@MapKey
public @interface ViewModelKey {
    Class<? extends ViewModel> value();
}
