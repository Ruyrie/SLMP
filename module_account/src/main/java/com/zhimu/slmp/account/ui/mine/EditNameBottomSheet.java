package com.zhimu.slmp.account.ui.mine;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountSheetEditNameBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.ui.base.FormBinder;
import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;

/**
 * A07 修改姓名底部面板（第 4 级）：通过父 Fragment 获取 MineViewModel；关闭不保存输入。
 */
public class EditNameBottomSheet extends BaseBottomSheetDialogFragment {

    private AccountSheetEditNameBinding binding;

    @Nullable
    @Override
    protected CharSequence getSheetTitle() {
        return getString(R.string.account_edit_name_title);
    }

    @Override
    protected boolean expandOnShow() {
        return true;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = AccountSheetEditNameBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MineViewModel vm = new ViewModelProvider(requireActivity(), AccountInjector.factory())
                .get(MineViewModel.class);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.setVm(vm);
        if (binding.accountEditNameField.getEditText() != null) {
            binding.accountEditNameField.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
            binding.accountEditNameField.getEditText().addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    vm.clearNameError();
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                }
            });
            FormBinder.showKeyboard(binding.accountEditNameField.getEditText());
            binding.accountEditNameField.getEditText().requestFocus();
        }
        vm.getNameError().observe(getViewLifecycleOwner(), err -> binding.accountEditNameField.setError(err));
        vm.getDismissNameSheet().observe(getViewLifecycleOwner(), d -> dismissAllowingStateLoss());
        ClickThrottle.setOnClickListener(binding.accountEditNameCancel, v -> dismiss());
        ClickThrottle.setOnClickListener(binding.accountEditNameSave, v -> vm.saveName());
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
