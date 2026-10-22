package com.zhimu.slmp.account.ui.password;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountActivityChangePasswordBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.ui.base.FormBinder;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.TopAppBarHelper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 修改密码页（RouterPath.A08_PASSWORD）：成功 RESULT_OK 返回 A08。
 */
public class ChangePasswordActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ChangePasswordViewModel vm = obtainViewModel(AccountInjector.factory(), ChangePasswordViewModel.class);
        AccountActivityChangePasswordBinding binding =
                DataBindingUtil.setContentView(this, R.layout.account_activity_change_password);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.accountPasswordTopBar.baseToolbar, getString(R.string.account_password_title));

        FormBinder.setupPassword(binding.accountPasswordNew);
        FormBinder.setupPassword(binding.accountPasswordConfirm);
        Map<String, TextInputLayout> fields = new LinkedHashMap<>();
        fields.put(ChangePasswordViewModel.FIELD_CURRENT, binding.accountPasswordCurrent);
        fields.put(ChangePasswordViewModel.FIELD_NEW, binding.accountPasswordNew);
        fields.put(ChangePasswordViewModel.FIELD_CONFIRM, binding.accountPasswordConfirm);
        FormBinder.bindFieldErrors(this, vm.getFieldErrors(), fields);
        FormBinder.bindFocus(this, vm.getFocusField(), fields);
        FormBinder.onImeDone(binding.accountPasswordConfirm, vm::submit);
        binding.accountPasswordBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getDone().observe(this, d -> {
            setResult(Activity.RESULT_OK);
            finish();
        });
    }
}
