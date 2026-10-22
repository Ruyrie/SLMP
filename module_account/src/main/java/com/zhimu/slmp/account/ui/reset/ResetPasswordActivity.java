package com.zhimu.slmp.account.ui.reset;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountActivityResetPasswordBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.ui.base.FormBinder;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A03 重置密码：成功后 setResult(RESULT_OK) 关闭，A01 调 onPasswordReset。
 */
public class ResetPasswordActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ResetPasswordViewModel vm = obtainViewModel(AccountInjector.factory(), ResetPasswordViewModel.class);
        AccountActivityResetPasswordBinding binding =
                DataBindingUtil.setContentView(this, R.layout.account_activity_reset_password);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.accountResetTopBar.baseToolbar, getString(R.string.account_reset_title));
        TopAppBarHelper.bindLiftOnScroll(binding.accountResetTopBar.baseAppBar, binding.accountResetScroll);

        FormBinder.setupPhone(binding.accountResetPhone);
        FormBinder.setupPassword(binding.accountResetPassword);
        Map<String, TextInputLayout> fields = new LinkedHashMap<>();
        fields.put(ResetPasswordViewModel.FIELD_PHONE, binding.accountResetPhone);
        fields.put(ResetPasswordViewModel.FIELD_SMS_CODE, binding.accountResetSms);
        fields.put(ResetPasswordViewModel.FIELD_NEW_PASSWORD, binding.accountResetPassword);
        FormBinder.bindFieldErrors(this, vm.getFieldErrors(), fields);
        FormBinder.bindFocus(this, vm.getFocusField(), fields);
        FormBinder.bindSmsField(binding.accountResetSms, this, vm.getSmsCountdown(), vm.getSmsSentHint(),
                vm::sendSmsCode);
        FormBinder.onImeDone(binding.accountResetPassword, vm::resetPassword);

        binding.accountResetBar.setOnPrimaryClickListener(v -> vm.resetPassword());
        ClickThrottle.setOnClickListener(binding.accountResetApplyRecovery, v -> vm.onApplyRecoveryClick());
        vm.getResetDone().observe(this, done -> {
            setResult(Activity.RESULT_OK);
            finish();
        });
        vm.getNavigate().observe(this, route -> {
            Bundle extras = new Bundle();
            extras.putString(RouterPath.EXTRA_PHONE, vm.phoneDigits());
            Router.open(this, route, extras);
        });
    }
}
