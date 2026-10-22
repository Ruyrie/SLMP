package com.zhimu.slmp.account.ui.afterrecovery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.account.databinding.AccountFragmentAfterRecoveryPasswordBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.ui.base.FormBinder;
import com.zhimu.slmp.base.ui.BaseFragment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A06 第 1 步：修改密码（不能使用短信中的临时密码）。ViewModel 为 Activity 范围。
 */
public class AfterRecoveryPasswordFragment extends BaseFragment {

    private AccountFragmentAfterRecoveryPasswordBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = AccountFragmentAfterRecoveryPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AfterRecoveryViewModel vm = obtainActivityViewModel(AccountInjector.factory(), AfterRecoveryViewModel.class);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);
        FormBinder.setupPassword(binding.accountAfterNewPassword);
        FormBinder.setupPassword(binding.accountAfterConfirmPassword);
        Map<String, TextInputLayout> fields = new LinkedHashMap<>();
        fields.put(AfterRecoveryViewModel.FIELD_NEW_PASSWORD, binding.accountAfterNewPassword);
        fields.put(AfterRecoveryViewModel.FIELD_CONFIRM_PASSWORD, binding.accountAfterConfirmPassword);
        FormBinder.bindFieldErrors(getViewLifecycleOwner(), vm.getFieldErrors(), fields);
        FormBinder.bindFocus(getViewLifecycleOwner(), vm.getFocusField(), fields);
        FormBinder.onImeDone(binding.accountAfterConfirmPassword, vm::submitPassword);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
