package com.zhimu.slmp.feed.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Component;

/**
 * 模块06 饲喂与生长称重组件的 Dagger 组件：依赖 CoreComponent，提供本组件的 ViewModel 工厂。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = {FeedModule.class, FeedViewModelModule.class})
public interface FeedComponent {

    ViewModelFactory viewModelFactory();

    @Component.Factory
    interface Factory {
        FeedComponent create(CoreComponent core);
    }
}
