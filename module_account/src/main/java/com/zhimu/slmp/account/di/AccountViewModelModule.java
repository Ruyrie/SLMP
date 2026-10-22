package com.zhimu.slmp.account.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.account.ui.about.AboutViewModel;
import com.zhimu.slmp.account.ui.afterrecovery.AfterRecoveryViewModel;
import com.zhimu.slmp.account.ui.cancel.CancelAccountViewModel;
import com.zhimu.slmp.account.ui.login.LoginViewModel;
import com.zhimu.slmp.account.ui.mine.MineViewModel;
import com.zhimu.slmp.account.ui.password.ChangePasswordViewModel;
import com.zhimu.slmp.account.ui.recovery.RecoveryApplyViewModel;
import com.zhimu.slmp.account.ui.recoverycode.RecoveryCodeViewModel;
import com.zhimu.slmp.account.ui.register.RegisterViewModel;
import com.zhimu.slmp.account.ui.reset.ResetPasswordViewModel;
import com.zhimu.slmp.account.ui.security.AccountSecurityViewModel;
import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 模块01 账号与个人中心组件的 ViewModel 多绑定（11 个）。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class AccountViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(LoginViewModel.class)
    abstract ViewModel bindLogin(LoginViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(RegisterViewModel.class)
    abstract ViewModel bindRegister(RegisterViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(ResetPasswordViewModel.class)
    abstract ViewModel bindResetPassword(ResetPasswordViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(RecoveryCodeViewModel.class)
    abstract ViewModel bindRecoveryCode(RecoveryCodeViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(RecoveryApplyViewModel.class)
    abstract ViewModel bindRecoveryApply(RecoveryApplyViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(AfterRecoveryViewModel.class)
    abstract ViewModel bindAfterRecovery(AfterRecoveryViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(MineViewModel.class)
    abstract ViewModel bindMine(MineViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(AccountSecurityViewModel.class)
    abstract ViewModel bindAccountSecurity(AccountSecurityViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(ChangePasswordViewModel.class)
    abstract ViewModel bindChangePassword(ChangePasswordViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(AboutViewModel.class)
    abstract ViewModel bindAbout(AboutViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(CancelAccountViewModel.class)
    abstract ViewModel bindCancelAccount(CancelAccountViewModel vm);
}
