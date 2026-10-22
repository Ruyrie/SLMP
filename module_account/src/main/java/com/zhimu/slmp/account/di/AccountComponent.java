package com.zhimu.slmp.account.di;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.service.AccountServiceImpl;
import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Component;

/**
 * 模块01 账号与个人中心组件的 Dagger 组件：依赖 CoreComponent，提供本组件的 ViewModel 工厂、仓库与账号服务。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = {AccountModule.class, AccountViewModelModule.class})
public interface AccountComponent {

    ViewModelFactory viewModelFactory();

    AccountRepository accountRepository();

    AccountServiceImpl accountService();

    @Component.Factory
    interface Factory {
        AccountComponent create(CoreComponent core);
    }
}
