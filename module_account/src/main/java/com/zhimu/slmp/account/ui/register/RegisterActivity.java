package com.zhimu.slmp.account.ui.register;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountActivityRegisterBinding;
import com.zhimu.slmp.account.di.AccountInjector;
import com.zhimu.slmp.account.model.vo.AgreementItemVO;
import com.zhimu.slmp.account.model.vo.AgreementVO;
import com.zhimu.slmp.account.ui.about.AgreementActivity;
import com.zhimu.slmp.account.ui.base.FormBinder;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A02 注册账号：成功后打开 A04（A01、A02 出栈）；协议链接打开协议全文页，返回后表单与勾选保留。
 */
public class RegisterActivity extends BaseActivity {

    private RegisterViewModel vm;
    private AccountActivityRegisterBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(AccountInjector.factory(), RegisterViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.account_activity_register);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.accountRegisterTopBar.baseToolbar, getString(R.string.account_register_title));
        TopAppBarHelper.bindLiftOnScroll(binding.accountRegisterTopBar.baseAppBar, binding.accountRegisterScroll);

        FormBinder.setupPhone(binding.accountRegisterPhone);
        FormBinder.setupPassword(binding.accountRegisterPassword);
        Map<String, TextInputLayout> fields = new LinkedHashMap<>();
        fields.put(RegisterViewModel.FIELD_PHONE, binding.accountRegisterPhone);
        fields.put(RegisterViewModel.FIELD_SMS_CODE, binding.accountRegisterSms);
        fields.put(RegisterViewModel.FIELD_PASSWORD, binding.accountRegisterPassword);
        FormBinder.bindFieldErrors(this, vm.getFieldErrors(), fields);
        FormBinder.bindFocus(this, vm.getFocusField(), fields);
        FormBinder.bindSmsField(binding.accountRegisterSms, this, vm.getSmsCountdown(), vm.getSmsSentHint(),
                vm::sendSmsCode);
        FormBinder.onImeDone(binding.accountRegisterPassword, vm::register);

        // 协议勾选的错误：复选框变 error 色，协议行下方显示文字
        vm.getFieldErrors().observe(this, map -> {
            String err = map == null ? null : map.get(RegisterViewModel.FIELD_AGREE_TERMS);
            binding.accountRegisterAgree.setErrorShown(err != null);
            binding.accountRegisterAgreeError.setText(err);
            binding.accountRegisterAgreeError.setVisibility(err == null ? View.GONE : View.VISIBLE);
        });
        vm.getAgreement().observe(this, this::renderAgreementText);

        binding.accountRegisterBar.setOnPrimaryClickListener(v -> vm.register());
        vm.getNavigate().observe(this, route -> {
            Router.openClearTask(this, route, null);
            finish();
        });
        vm.getOpenAgreement().observe(this, item -> AgreementActivity.open(this, item));
    }

    private void renderAgreementText(@Nullable AgreementVO a) {
        String user = titleOf(a == null ? null : a.getUserAgreement(), getString(R.string.account_register_user_agreement));
        String privacy = titleOf(a == null ? null : a.getPrivacyPolicy(), getString(R.string.account_register_privacy_policy));
        int primary = MaterialColors.getColor(binding.accountRegisterAgreeText, androidx.appcompat.R.attr.colorPrimary);
        SpannableStringBuilder sb = new SpannableStringBuilder(getString(R.string.account_register_agree_prefix));
        appendLink(sb, user, primary, () -> vm.onAgreementClick(RegisterViewModel.TYPE_USER));
        sb.append(getString(R.string.account_register_and));
        appendLink(sb, privacy, primary, () -> vm.onAgreementClick(RegisterViewModel.TYPE_PRIVACY));
        binding.accountRegisterAgreeText.setText(sb);
        binding.accountRegisterAgreeText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @NonNull
    private static String titleOf(@Nullable AgreementItemVO item, @NonNull String fallback) {
        String t = item == null ? null : item.getTitle();
        if (t == null || t.isEmpty()) {
            return fallback;
        }
        return t.startsWith("《") ? t : "《" + t + "》";
    }

    private static void appendLink(@NonNull SpannableStringBuilder sb, @NonNull String text, int color,
                                   @NonNull Runnable onClick) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                onClick.run();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(color);
                ds.setUnderlineText(false);
            }
        }, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (RegisterViewModel.ACTION_LOGIN.equals(actionId)) {
            Bundle extras = new Bundle();
            extras.putString(RouterPath.EXTRA_PHONE, vm.phoneDigits());
            Router.openClearTask(this, RouterPath.A01, extras);
            finish();
        }
    }
}
