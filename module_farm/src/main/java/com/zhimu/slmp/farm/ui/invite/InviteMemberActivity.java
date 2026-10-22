package com.zhimu.slmp.farm.ui.invite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.radiobutton.MaterialRadioButton;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.databinding.FarmActivityInviteBinding;
import com.zhimu.slmp.farm.di.FarmInjector;
import com.zhimu.slmp.farm.ui.FarmUi;
import com.zhimu.slmp.farm.ui.PenChips;
import com.zhimu.slmp.farm.util.PhoneTextWatcher;

import java.util.Map;

/**
 * F08 邀请成员：手机号进入页面即获得焦点（重新邀请时不自动获得焦点）；选“场长”时负责栏舍区收起；
 * 成功后 setResult 带“已发送邀请，对方同意后加入”并关闭；40903 关闭后 F07 刷新并提示。
 */
public class InviteMemberActivity extends BaseActivity {

    private FarmActivityInviteBinding binding;
    private InviteMemberViewModel vm;
    private MaterialRadioButton managerRadio;
    private MaterialRadioButton staffRadio;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.farm_activity_invite);
        binding.setLifecycleOwner(this);
        vm = obtainViewModel(FarmInjector.factory(), InviteMemberViewModel.class);
        observeBase(vm);
        binding.setVm(vm);

        FarmUi.setupTopBar(binding.topBar, getString(R.string.farm_invite_title));
        FarmContextVO ctx = CoreInjector.get(this).farmContext().current();
        String farmName = ctx == null ? getString(R.string.farm_this_farm) : ctx.getName();
        FarmUi.setupNoPermission(binding.state, ctx == null ? null : ctx.getName());
        binding.noPens.setText(getString(R.string.farm_invite_no_pens, farmName));
        binding.state.setOnRetryListener(v -> vm.retry());
        PhoneTextWatcher.attach(binding.etPhone);
        binding.etPhone.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                vm.onPhoneFocusLost();
            }
        });

        View root = binding.getRoot();
        managerRadio = root.findViewById(R.id.role_manager_radio);
        staffRadio = root.findViewById(R.id.role_staff_radio);
        root.findViewById(R.id.role_manager).setOnClickListener(v -> vm.onRoleSelected(InviteMemberViewModel.ROLE_MANAGER));
        root.findViewById(R.id.role_staff).setOnClickListener(v -> vm.onRoleSelected(InviteMemberViewModel.ROLE_STAFF));
        ClickThrottle.setOnClickListener(binding.actionBar.getPrimaryButton(), v -> {
            hideKeyboard(null);
            vm.submit();
        });
        if (savedInstanceState == null && !vm.isReinvite()) {
            binding.etPhone.requestFocus();
        }

        vm.getUiState().observe(this, binding.state::render);
        vm.getRole().observe(this, role -> {
            boolean staff = !InviteMemberViewModel.ROLE_MANAGER.equals(role);
            managerRadio.setChecked(!staff);
            staffRadio.setChecked(staff);
            binding.pensSection.setVisibility(staff ? View.VISIBLE : View.GONE);
        });
        vm.getPenOptions().observe(this, list -> renderChips());
        vm.getSelectedPenIds().observe(this, ids -> renderChips());
        vm.getFieldErrors().observe(this, this::renderErrors);
        vm.getSubmitting().observe(this, s -> binding.actionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getCanSubmit().observe(this, ok -> binding.actionBar.setPrimaryEnabled(Boolean.TRUE.equals(ok)));
        vm.getDone().observe(this, msg -> finishWith(msg));
        vm.getStale().observe(this, msg -> finishWith(msg));
    }

    private void renderChips() {
        PenChips.render(binding.penChips, vm.getPenOptions().getValue(), vm.getSelectedPenIds().getValue(),
                vm::onPenToggled);
    }

    private void renderErrors(@Nullable Map<String, String> errors) {
        FarmUi.setError(binding.tilPhone, errors, InviteMemberViewModel.F_PHONE);
        if (!binding.tilPhone.isErrorEnabled()) {
            binding.tilPhone.setHelperText(getString(R.string.farm_invite_phone_helper));
        }
    }

    private void finishWith(@Nullable String message) {
        setResult(RESULT_OK, new Intent().putExtra(FarmExtras.RESULT_MESSAGE, message));
        finish();
    }
}
