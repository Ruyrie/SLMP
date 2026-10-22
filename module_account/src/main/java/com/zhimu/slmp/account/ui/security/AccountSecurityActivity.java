package com.zhimu.slmp.account.ui.security;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountActivitySecurityBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * A08 账号与安全：修改密码页、A04 用 Activity Result 打开；A09 普通打开；重新生成恢复码用底部面板验证。
 */
public class AccountSecurityActivity extends BaseActivity {

    private static final String TAG_REGENERATE = "account_regenerate";

    private AccountSecurityViewModel vm;
    private ActivityResultLauncher<Intent> passwordLauncher;
    private ActivityResultLauncher<Intent> codeLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(AccountInjector.factory(), AccountSecurityViewModel.class);
        AccountActivitySecurityBinding binding = DataBindingUtil.setContentView(this, R.layout.account_activity_security);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.accountSecurityTopBar.baseToolbar, getString(R.string.account_security_title));
        TopAppBarHelper.bindLiftOnScroll(binding.accountSecurityTopBar.baseAppBar, binding.accountSecurityScroll);
        binding.accountSecurityState.setOnRetryListener(v -> vm.retry());

        SpannableString danger = new SpannableString(getString(R.string.account_security_cancel));
        danger.setSpan(new ForegroundColorSpan(MaterialColors.getColor(binding.getRoot(),
                androidx.appcompat.R.attr.colorError)), 0, danger.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.accountSecurityCancel.setTitle(danger);

        passwordLauncher = Router.registerForResult(this, r -> {
            if (r.getResultCode() == Activity.RESULT_OK) {
                vm.onPasswordChanged();
            }
        });
        codeLauncher = Router.registerForResult(this, r -> {
            if (r.getResultCode() == Activity.RESULT_OK) {
                vm.onCodeConfirmed();
            }
        });

        ClickThrottle.setOnClickListener(binding.accountSecurityPassword, v -> vm.openPage(RouterPath.A08_PASSWORD));
        ClickThrottle.setOnClickListener(binding.accountSecurityRegenerate, v -> vm.openRegenerate());
        ClickThrottle.setOnClickListener(binding.accountSecurityCancel, v -> vm.openPage(RouterPath.A09));
        vm.getShowSheet().observe(this, s -> {
            if (getSupportFragmentManager().findFragmentByTag(TAG_REGENERATE) == null) {
                new RegenerateCodeBottomSheet().showSafely(getSupportFragmentManager(), TAG_REGENERATE);
            }
        });
        vm.getNavigate().observe(this, this::navigate);
    }

    private void navigate(@NonNull String route) {
        if (RouterPath.A08_PASSWORD.equals(route)) {
            Router.openForResult(this, passwordLauncher, route, null);
        } else if (RouterPath.A04.equals(route)) {
            Router.openForResult(this, codeLauncher, route, null);
        } else {
            Router.open(this, route);
        }
    }
}
