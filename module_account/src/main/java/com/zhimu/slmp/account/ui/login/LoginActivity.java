package com.zhimu.slmp.account.ui.login;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountActivityLoginBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.ui.base.FormBinder;
import com.zhimu.slmp.account.ui.recovery.RecoveryApplyActivity;
import com.zhimu.slmp.account.util.NextStepRouter;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A01 登录：密码登录 / 验证码登录；登录失效横幅；成功后按 next_step 分发并清空返回栈。
 * A03 重置、A05 找回用 Activity Result 返回。
 */
public class LoginActivity extends BaseActivity {

    private LoginViewModel vm;
    private AccountActivityLoginBinding binding;
    private ActivityResultLauncher<Intent> resetLauncher;
    private ActivityResultLauncher<Intent> recoveryLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(AccountInjector.factory(), LoginViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.account_activity_login);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);

        resetLauncher = Router.registerForResult(this, r -> {
            if (r.getResultCode() == Activity.RESULT_OK) {
                vm.onPasswordReset();
            }
        });
        recoveryLauncher = Router.registerForResult(this, r -> {
            if (r.getResultCode() == Activity.RESULT_OK) {
                Intent data = r.getData();
                vm.onRecoverySubmitted(data == null ? null
                        : data.getStringExtra(RecoveryApplyActivity.RESULT_NEW_PHONE_MASKED));
            }
        });

        FormBinder.setupPhone(binding.accountLoginPhone);
        FormBinder.setupPassword(binding.accountLoginPassword);
        Map<String, TextInputLayout> fields = new LinkedHashMap<>();
        fields.put(LoginViewModel.FIELD_PHONE, binding.accountLoginPhone);
        fields.put(LoginViewModel.FIELD_PASSWORD, binding.accountLoginPassword);
        fields.put(LoginViewModel.FIELD_SMS_CODE, binding.accountLoginSms);
        FormBinder.bindFieldErrors(this, vm.getFieldErrors(), fields);
        FormBinder.bindFocus(this, vm.getFocusField(), fields);
        FormBinder.bindSmsField(binding.accountLoginSms, this, vm.getSmsCountdown(), vm.getSmsSentHint(),
                vm::sendSmsCode);
        FormBinder.onImeDone(binding.accountLoginPassword, vm::login);
        FormBinder.onImeDone(binding.accountLoginSms, vm::login);

        vm.getLoginMode().observe(this, mode -> {
            int id = LoginViewModel.MODE_SMS.equals(mode) ? R.id.account_login_mode_sms : R.id.account_login_mode_password;
            if (binding.accountLoginMode.getCheckedButtonId() != id) {
                binding.accountLoginMode.check(id);
            }
        });
        binding.accountLoginMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                vm.switchMode(checkedId == R.id.account_login_mode_sms
                        ? LoginViewModel.MODE_SMS : LoginViewModel.MODE_PASSWORD);
            }
        });

        ClickThrottle.setOnClickListener(binding.accountLoginSubmit, v -> vm.login());
        ClickThrottle.setOnClickListener(binding.accountLoginForgot, v -> vm.openPage(RouterPath.A03));
        ClickThrottle.setOnClickListener(binding.accountLoginUnusable, v -> vm.openPage(RouterPath.A05));
        ClickThrottle.setOnClickListener(binding.accountLoginRegister, v -> vm.openPage(RouterPath.A02));

        vm.getNavigate().observe(this, this::navigate);
        vm.getLoginSuccess().observe(this, token -> NextStepRouter.dispatch(this, token.getNextStep()));
    }

    private void navigate(@NonNull String route) {
        Bundle extras = new Bundle();
        extras.putString(RouterPath.EXTRA_PHONE, vm.phoneDigits());
        if (RouterPath.A03.equals(route)) {
            Router.openForResult(this, resetLauncher, route, extras);
        } else if (RouterPath.A05.equals(route)) {
            Router.openForResult(this, recoveryLauncher, route, extras);
        } else {
            Router.open(this, route, extras);
        }
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (LoginViewModel.ACTION_REGISTER.equals(actionId)) {
            vm.openPage(RouterPath.A02);
        }
    }
}
