package com.zhimu.slmp.account.ui.security;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountSheetRegenerateCodeBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.ui.base.FormBinder;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;

import java.util.Collections;
import java.util.Map;

import androidx.lifecycle.ViewModelProvider;

/**
 * A08 重新生成恢复码面板（第 4 级，单字段）：经 API-A02 发码到本人手机号，“验证并生成”调 API-A13；
 * 成功后关闭面板，A08 以 Activity Result 打开 A04。关闭不保存输入。
 */
public class RegenerateCodeBottomSheet extends BaseBottomSheetDialogFragment {

    private AccountSheetRegenerateCodeBinding binding;
    private AccountSecurityViewModel vm;

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.account_regenerate_title);
    }

    @Nullable
    @Override
    protected CharSequence getSheetDescription() {
        return getString(R.string.account_regenerate_desc);
    }

    @Nullable
    @Override
    protected CharSequence getPrimaryButtonText() {
        return getString(R.string.account_regenerate_submit);
    }

    @Override
    protected void onPrimaryButtonClick() {
        vm.regenerate();
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = AccountSheetRegenerateCodeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(requireActivity(), AccountInjector.factory()).get(AccountSecurityViewModel.class);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);
        Map<String, TextInputLayout> fields = Collections.singletonMap(AccountSecurityViewModel.FIELD_SMS_CODE,
                binding.accountRegenerateSms);
        FormBinder.bindFieldErrors(getViewLifecycleOwner(), vm.getFieldErrors(), fields);
        FormBinder.bindFocus(getViewLifecycleOwner(), vm.getFocusField(), fields);
        FormBinder.bindSmsField(binding.accountRegenerateSms, getViewLifecycleOwner(), vm.getSmsCountdown(),
                vm.getSmsSentHint(), vm::sendSmsCode);
        vm.getCanRegenerate().observe(getViewLifecycleOwner(), ok -> {
            if (getPrimaryButton() != null) {
                getPrimaryButton().setEnabled(Boolean.TRUE.equals(ok) && !vm.isSubmitting());
            }
        });
        vm.getSubmitting().observe(getViewLifecycleOwner(), s -> setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getDismissSheet().observe(getViewLifecycleOwner(), d -> dismissAllowingStateLoss());
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
