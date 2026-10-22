package com.zhimu.slmp.farm.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.farm.service.FarmContextServiceImpl;
import com.zhimu.slmp.farm.service.FarmInviteServiceImpl;

import dagger.Component;

/**
 * 模块02 养殖场与成员管理组件的 Dagger 组件：依赖 CoreComponent，提供本组件的 ViewModel 工厂与两个跨组件服务。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = {FarmModule.class, FarmViewModelModule.class})
public interface FarmComponent {

    ViewModelFactory viewModelFactory();

    FarmContextServiceImpl farmContextService();

    FarmInviteServiceImpl farmInviteService();

    @Component.Factory
    interface Factory {
        FarmComponent create(CoreComponent core);
    }
}
