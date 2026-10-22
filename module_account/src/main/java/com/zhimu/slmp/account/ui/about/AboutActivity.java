package com.zhimu.slmp.account.ui.about;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.account.BuildConfig;
import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountActivityAboutBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.TopAppBarHelper;

/**
 * 关于页（RouterPath.A07_ABOUT）：版本号与协议入口。
 */
public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AboutViewModel vm = obtainViewModel(AccountInjector.factory(), AboutViewModel.class);
        AccountActivityAboutBinding binding = DataBindingUtil.setContentView(this, R.layout.account_activity_about);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.accountAboutTopBar.baseToolbar, getString(R.string.account_about_title));
        vm.setVersionName(versionName());
        ClickThrottle.setOnClickListener(binding.accountAboutUserAgreement, v -> vm.openAgreement(AboutViewModel.TYPE_USER));
        ClickThrottle.setOnClickListener(binding.accountAboutPrivacy, v -> vm.openAgreement(AboutViewModel.TYPE_PRIVACY));
        vm.getOpenAgreement().observe(this, item -> AgreementActivity.open(this, item));
    }

    private String versionName() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v != null ? v : BuildConfig.APP_VERSION_NAME;
        } catch (Exception e) {
            return BuildConfig.APP_VERSION_NAME;
        }
    }
}
