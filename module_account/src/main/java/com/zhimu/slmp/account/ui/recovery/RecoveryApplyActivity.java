package com.zhimu.slmp.account.ui.recovery;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountActivityRecoveryApplyBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.ui.base.FormBinder;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.TopAppBarHelper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A05 找回账号：提交成功 setResult 带 new_phone_masked 返回；已填写内容时返回先确认“放弃这次找回申请？”；
 * 客服卡片打开系统拨号盘（ACTION_DIAL，不直接拨出）。
 */
public class RecoveryApplyActivity extends BaseActivity {

    /** 返回 A01 的结果：新手机号打码（String） */
    public static final String RESULT_NEW_PHONE_MASKED = "new_phone_masked";
    private static final String REQ_DISCARD = "account_recovery_discard";

    private RecoveryApplyViewModel vm;
    private OnBackPressedCallback backCallback;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(AccountInjector.factory(), RecoveryApplyViewModel.class);
        AccountActivityRecoveryApplyBinding binding =
                DataBindingUtil.setContentView(this, R.layout.account_activity_recovery_apply);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.accountRecoveryTopBar.baseToolbar, getString(R.string.account_recovery_title));
        TopAppBarHelper.bindLiftOnScroll(binding.accountRecoveryTopBar.baseAppBar, binding.accountRecoveryScroll);

        FormBinder.setupPhone(binding.accountRecoveryOldPhone);
        FormBinder.setupPhone(binding.accountRecoveryNewPhone);
        FormBinder.setupRecoveryCode(binding.accountRecoveryCode);
        Map<String, TextInputLayout> fields = new LinkedHashMap<>();
        fields.put(RecoveryApplyViewModel.FIELD_OLD_PHONE, binding.accountRecoveryOldPhone);
        fields.put(RecoveryApplyViewModel.FIELD_RECOVERY_CODE, binding.accountRecoveryCode);
        fields.put(RecoveryApplyViewModel.FIELD_NEW_PHONE, binding.accountRecoveryNewPhone);
        fields.put(RecoveryApplyViewModel.FIELD_NEW_PHONE_SMS_CODE, binding.accountRecoverySms);
        fields.put(RecoveryApplyViewModel.FIELD_DESCRIPTION, binding.accountRecoveryDescription);
        FormBinder.bindFieldErrors(this, vm.getFieldErrors(), fields);
        FormBinder.bindFocus(this, vm.getFocusField(), fields);
        FormBinder.bindSmsField(binding.accountRecoverySms, this, vm.getSmsCountdown(), vm.getSmsSentHint(),
                vm::sendNewPhoneSms);

        vm.getServicePhone().observe(this, phone ->
                binding.accountRecoveryService.setSubtitle(getString(R.string.account_recovery_service_desc, phone)));
        ClickThrottle.setOnClickListener(binding.accountRecoveryService, v -> vm.onServiceClick());
        binding.accountRecoveryBar.setOnPrimaryClickListener(v -> vm.submit());

        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                vm.onBack();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
        vm.getIsDirty().observe(this, dirty -> backCallback.setEnabled(Boolean.TRUE.equals(dirty)));
        vm.getConfirmDiscard().observe(this, b -> ConfirmDialogFragment.builder(REQ_DISCARD)
                .title(getString(R.string.account_recovery_discard_title))
                .message(getString(R.string.account_recovery_discard_message))
                .positive(getString(R.string.account_recovery_discard))
                .negative(getString(R.string.account_recovery_keep))
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                backCallback.setEnabled(false);
                finish();
            }
        });
        vm.getDial().observe(this, phone -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
            } catch (RuntimeException ignored) {
                // 没有拨号应用
            }
        });
        vm.getSubmitted().observe(this, vo -> {
            Intent data = new Intent().putExtra(RESULT_NEW_PHONE_MASKED, vo.getNewPhoneMasked());
            setResult(Activity.RESULT_OK, data);
            backCallback.setEnabled(false);
            finish();
        });
    }
}
