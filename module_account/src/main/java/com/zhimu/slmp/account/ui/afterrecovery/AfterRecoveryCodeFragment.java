package com.zhimu.slmp.account.ui.afterrecovery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.account.databinding.AccountFragmentAfterRecoveryCodeBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.ui.recoverycode.RecoveryCodeUi;
import com.zhimu.slmp.base.ui.BaseFragment;

/**
 * A06 第 2 步：保存新的恢复码（复用 A04 内容区；说明行“密码已修改。旧恢复码已作废，下面是新的恢复码。”）。
 */
public class AfterRecoveryCodeFragment extends BaseFragment {

    public static final String DESC = "密码已修改。旧恢复码已作废，下面是新的恢复码。";

    private AccountFragmentAfterRecoveryCodeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = AccountFragmentAfterRecoveryCodeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AfterRecoveryViewModel vm = obtainActivityViewModel(AccountInjector.factory(), AfterRecoveryViewModel.class);
        RecoveryCodeUi.bindBody(getViewLifecycleOwner(), binding.accountAfterCodeBody, new MutableLiveData<>(DESC),
                vm.getCodeGroups(), vm.savedChecked, new MutableLiveData<>(true), vm::copyCode, vm::saveImage);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
