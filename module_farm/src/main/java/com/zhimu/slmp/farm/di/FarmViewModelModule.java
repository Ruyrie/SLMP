package com.zhimu.slmp.farm.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;
import com.zhimu.slmp.farm.ui.audit.FarmAuditViewModel;
import com.zhimu.slmp.farm.ui.change.FarmChangeViewModel;
import com.zhimu.slmp.farm.ui.create.FarmCreateViewModel;
import com.zhimu.slmp.farm.ui.detail.MemberDetailViewModel;
import com.zhimu.slmp.farm.ui.info.FarmInfoViewModel;
import com.zhimu.slmp.farm.ui.invite.InviteMemberViewModel;
import com.zhimu.slmp.farm.ui.member.MemberListViewModel;
import com.zhimu.slmp.farm.ui.switcher.FarmSwitchViewModel;
import com.zhimu.slmp.farm.ui.transfer.TransferViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 模块02 养殖场与成员管理组件的 ViewModel 多绑定（F01–F03、F05–F10；F04 在 lib_map）。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class FarmViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(FarmSwitchViewModel.class)
    abstract ViewModel bindFarmSwitch(FarmSwitchViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FarmCreateViewModel.class)
    abstract ViewModel bindFarmCreate(FarmCreateViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FarmAuditViewModel.class)
    abstract ViewModel bindFarmAudit(FarmAuditViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FarmInfoViewModel.class)
    abstract ViewModel bindFarmInfo(FarmInfoViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FarmChangeViewModel.class)
    abstract ViewModel bindFarmChange(FarmChangeViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(MemberListViewModel.class)
    abstract ViewModel bindMemberList(MemberListViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(InviteMemberViewModel.class)
    abstract ViewModel bindInviteMember(InviteMemberViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(MemberDetailViewModel.class)
    abstract ViewModel bindMemberDetail(MemberDetailViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(TransferViewModel.class)
    abstract ViewModel bindTransfer(TransferViewModel vm);
}
