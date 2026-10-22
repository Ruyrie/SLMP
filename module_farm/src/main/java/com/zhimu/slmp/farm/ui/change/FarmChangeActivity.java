package com.zhimu.slmp.farm.ui.change;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.NoticeView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.R;
import com.zhimu.slmp.farm.databinding.FarmActivityChangeBinding;
import com.zhimu.slmp.farm.di.FarmInjector;
import com.zhimu.slmp.farm.model.vo.RegistrationVO;
import com.zhimu.slmp.farm.ui.FarmUi;
import com.zhimu.slmp.map.contract.PickLocationContract;
import com.zhimu.slmp.map.model.vo.LocationResult;
import com.zhimu.slmp.map.model.vo.PickLocationInput;

import java.util.Map;

/**
 * F06 资料变更：已修改的字段 2 dp primary 描边、标签 primary，辅助文字“已修改 · 当前：<旧值>”，未修改为“未修改”；
 * 资料变更审核中（API-F13 PENDING）时全部只读、隐藏底部操作栏。
 */
public class FarmChangeActivity extends BaseActivity {

    private FarmActivityChangeBinding binding;
    private FarmChangeViewModel vm;
    @Nullable
    private ColorStateList defaultHint;

    private final ActivityResultLauncher<PickLocationInput> mapLauncher =
            registerForActivityResult(new PickLocationContract(), result -> vm.onLocationPicked(result));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.farm_activity_change);
        binding.setLifecycleOwner(this);
        vm = obtainViewModel(FarmInjector.factory(), FarmChangeViewModel.class);
        observeBase(vm);
        binding.setVm(vm);

        FarmUi.setupTopBar(binding.topBar, getString(R.string.farm_change_title));
        FarmContextVO ctx = CoreInjector.get(this).farmContext().current();
        FarmUi.setupNoPermission(binding.state, ctx == null ? null : ctx.getName());
        binding.state.setOnRetryListener(v -> vm.retry());
        binding.qualFiles.bind(this, vm.getQualFiles());
        binding.qualModifiedTag.setStatus(getString(R.string.farm_change_modified_tag), StatusTagView.PRIMARY);
        defaultHint = binding.tilName.getDefaultHintTextColor();
        renderNotice(false);

        binding.etName.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                vm.onNameFocusLost();
            }
        });
        ClickThrottle.setOnClickListener(binding.etAddress, v -> vm.onAddressClick());
        binding.tilAddress.setEndIconOnClickListener(v -> vm.onAddressClick());
        ClickThrottle.setOnClickListener(binding.actionBar.getPrimaryButton(), v -> {
            hideKeyboard(null);
            vm.submit();
        });

        vm.getUiState().observe(this, binding.state::render);
        vm.getOrigin().observe(this, o -> renderAll());
        vm.getNameModified().observe(this, m -> renderAll());
        vm.getAddressModified().observe(this, m -> renderAll());
        vm.getLocation().observe(this, l -> renderAll());
        vm.getQualSummary().observe(this, s -> renderQualRule());
        vm.getReadOnly().observe(this, ro -> {
            renderNotice(Boolean.TRUE.equals(ro));
            RegistrationVO o = vm.getOrigin().getValue();
            if (Boolean.TRUE.equals(ro) && o != null) {
                binding.qualFilesReadonly.setReadOnlyFiles(o.getQualificationFiles(), getString(R.string.farm_qual_source));
            }
            binding.tilAddress.setEndIconVisible(!Boolean.TRUE.equals(ro));
        });
        vm.getFieldErrors().observe(this, this::renderErrors);
        vm.getSubmitting().observe(this, s -> binding.actionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getCanSubmit().observe(this, ok -> binding.actionBar.setPrimaryEnabled(Boolean.TRUE.equals(ok)));
        vm.getOpenMapPick().observe(this, mapLauncher::launch);
        vm.getScrollToField().observe(this, field -> binding.getRoot().post(() -> FarmUi.scrollTo(binding.scroll,
                FarmChangeViewModel.F_ADDRESS.equals(field) ? binding.tilAddress
                        : FarmChangeViewModel.F_QUAL.equals(field) ? binding.qualHeader
                        : FarmChangeViewModel.F_REMARK.equals(field) ? binding.tilRemark : binding.tilName)));
        vm.getSubmitted().observe(this, v -> {
            setResult(RESULT_OK, new Intent().putExtra(FarmExtras.RESULT_MESSAGE,
                    getString(R.string.farm_change_submitted)));
            finish();
        });
    }

    private void renderNotice(boolean pending) {
        NoticeView notice = binding.notice;
        if (pending) {
            notice.setNotice(null, getString(R.string.farm_change_pending_notice));
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(getString(R.string.farm_change_notice_head));
        int start = sb.length();
        sb.append(getString(R.string.farm_change_notice_bold));
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(getString(R.string.farm_change_notice_tail));
        notice.setNotice(null, sb);
    }

    private void renderAll() {
        RegistrationVO o = vm.getOrigin().getValue();
        LocationResult loc = vm.getLocation().getValue();
        binding.etAddress.setText(loc == null ? null : loc.getAddress());
        boolean nameMod = Boolean.TRUE.equals(vm.getNameModified().getValue());
        boolean addrMod = Boolean.TRUE.equals(vm.getAddressModified().getValue());
        styleModified(binding.tilName, nameMod, o == null ? null : o.getName());
        styleModified(binding.tilAddress, addrMod, o == null ? null : o.getAddress());
        renderQualRule();
    }

    /** 已修改：2 dp primary 描边、标签 primary、辅助文字“已修改 · 当前：旧值”；未修改恢复默认 */
    private void styleModified(@NonNull TextInputLayout til, boolean modified, @Nullable String oldValue) {
        if (til.isErrorEnabled()) {
            return;
        }
        int stroke = getResources().getDimensionPixelSize(modified ? com.zhimu.slmp.base.R.dimen.base_stroke_focused
                : com.zhimu.slmp.base.R.dimen.base_stroke);
        til.setBoxStrokeWidth(stroke);
        if (modified) {
            ColorStateList primary = ColorStateList.valueOf(MaterialColors.getColor(til,
                    androidx.appcompat.R.attr.colorPrimary));
            til.setBoxStrokeColorStateList(primary);
            til.setDefaultHintTextColor(primary);
            til.setHelperText(getString(R.string.farm_change_modified, oldValue == null ? "" : oldValue));
        } else {
            ColorStateList normal = ContextCompat.getColorStateList(this,
                    com.zhimu.slmp.base.R.color.base_color_text_field_stroke);
            if (normal != null) {
                til.setBoxStrokeColorStateList(normal);
            }
            if (defaultHint != null) {
                til.setDefaultHintTextColor(defaultHint);
            }
            til.setHelperText(getString(R.string.farm_change_unmodified));
        }
    }

    private void renderQualRule() {
        Map<String, String> errors = vm.getFieldErrors().getValue();
        String error = errors == null ? null : errors.get(FarmChangeViewModel.F_QUAL);
        String summary = vm.getQualSummary().getValue();
        String rule = getString(R.string.farm_qual_rule).replace("；", "，");
        binding.qualRule.setText(error != null ? error : TextUtils.isEmpty(summary) ? rule : summary + "；" + rule);
        binding.qualRule.setTextColor(MaterialColors.getColor(binding.qualRule, error != null
                ? androidx.appcompat.R.attr.colorError
                : com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    private void renderErrors(@Nullable Map<String, String> errors) {
        FarmUi.setError(binding.tilName, errors, FarmChangeViewModel.F_NAME);
        FarmUi.setError(binding.tilAddress, errors, FarmChangeViewModel.F_ADDRESS, "province", "city", "district",
                "township", "adcode", "longitude", "latitude");
        FarmUi.setError(binding.tilRemark, errors, FarmChangeViewModel.F_REMARK);
        renderAll();
        View focus = binding.tilName.isErrorEnabled() ? binding.tilName : null;
        if (focus != null) {
            focus.requestFocus();
        }
    }
}
