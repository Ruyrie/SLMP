package com.zhimu.slmp.health.ui.disinfectplanedit;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.databinding.HealthActivityDisinfectPlanEditBinding;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.model.vo.DisinfectantOptionVO;
import com.zhimu.slmp.health.model.vo.PenOptionVO;
import com.zhimu.slmp.health.ui.common.HealthForms;
import com.zhimu.slmp.health.ui.common.HealthNav;
import com.zhimu.slmp.health.ui.common.OptionAdapter;
import com.zhimu.slmp.health.util.HealthFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * D07 新建 / 修改消毒计划（DisinfectPlanEditActivity）：入口 D06 FAB、卡片“修改计划”（plan_id）。保存后返回 D06 刷新。
 */
public class DisinfectPlanEditActivity extends BaseActivity {

    private static final String REQ_START = "health_plan_start";
    private static final String REQ_END = "health_plan_end";
    private static final String REQ_DISCARD = "health_plan_discard";
    private static final long PREVIEW_DEBOUNCE_MS = 300;

    private HealthActivityDisinfectPlanEditBinding binding;
    private DisinfectPlanEditViewModel vm;
    private OptionAdapter disinfectantAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.health_activity_disinfect_plan_edit);
        vm = obtainViewModel(HealthInjector.factory(), DisinfectPlanEditViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        HealthNav.finishOnFarmSwitch(this);

        binding.healthTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (vm.isDirty()) {
                    HealthForms.showDiscard(DisinfectPlanEditActivity.this, REQ_DISCARD);
                } else {
                    finish();
                }
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                finish();
            }
        });
        binding.healthState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, state -> {
            binding.healthState.render(state);
            binding.healthActionBar.setVisibility(state != null && state.is(com.zhimu.slmp.base.mvvm.UiState.Status.CONTENT)
                    ? View.VISIBLE : View.GONE);
        });

        vm.getPens().observe(this, this::renderPens);
        vm.getPenIds().observe(this, ids -> checkPens());
        vm.getPreview().observe(this, p -> renderSummary());
        vm.getEditMode().observe(this, e -> binding.healthActionBar.setPrimary(getString(Boolean.TRUE.equals(e)
                ? R.string.health_save_changes : R.string.health_save_and_enable), Boolean.TRUE.equals(e) ? 0
                : com.zhimu.slmp.base.R.drawable.ic_check));
        HealthForms.afterTextIdle(binding.healthCycleInput, PREVIEW_DEBOUNCE_MS, vm::requestPreview);

        ClickThrottle.setOnClickListener(binding.healthStartInput, v -> pickDate(REQ_START, vm.getStartDate().getValue()));
        binding.healthStartLayout.setEndIconOnClickListener(v -> pickDate(REQ_START, vm.getStartDate().getValue()));
        ClickThrottle.setOnClickListener(binding.healthEndInput, v -> pickDate(REQ_END, vm.getEndDate().getValue()));
        binding.healthEndLayout.setEndIconOnClickListener(v -> pickDate(REQ_END, vm.getEndDate().getValue()));
        getSupportFragmentManager().setFragmentResultListener(REQ_START, this,
                (key, result) -> vm.onStartDateSelected(DateTimePicker.getDate(result)));
        getSupportFragmentManager().setFragmentResultListener(REQ_END, this,
                (key, result) -> vm.onEndDateSelected(DateTimePicker.getDate(result)));

        disinfectantAdapter = HealthForms.bindDropdown(binding.healthDisinfectantInput, vm::selectDisinfectant);
        vm.getDisinfectants().observe(this, this::renderDisinfectants);
        vm.getCatalogId().observe(this, id -> HealthForms.showSelected(binding.healthDisinfectantInput,
                disinfectantAdapter, id));
        vm.getStockHint().observe(this, this::renderStockHint);
        vm.getFieldErrors().observe(this, e -> renderStockHint(vm.getStockHint().getValue()));
        vm.getMethods().observe(this, this::renderMethods);
        vm.getMethodCode().observe(this, code -> HealthForms.checkChip(binding.healthMethodChips, code));

        binding.healthActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getSubmitting().observe(this, s -> binding.healthActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getScrollToField().observe(this, this::scrollTo);
        vm.getFinishWithResult().observe(this, vo -> HealthNav.finishWithMessage(this, vm.successMessage(vo)));
    }

    private void renderPens(@Nullable List<PenOptionVO> pens) {
        binding.healthPenChips.removeAllViews();
        if (pens == null) {
            return;
        }
        LayoutInflater inflater = getLayoutInflater();
        for (PenOptionVO p : pens) {
            Chip chip = (Chip) inflater.inflate(R.layout.health_item_choice_chip, binding.healthPenChips, false);
            chip.setText(p.getPenName());
            chip.setTag(p.getPenId());
            chip.setEnabled(!p.isDisabled());
            chip.setChecked(vm.isPenSelected(p.getPenId()));
            chip.setOnClickListener(v -> vm.togglePen(p.getPenId()));
            binding.healthPenChips.addView(chip);
        }
    }

    private void checkPens() {
        for (int i = 0; i < binding.healthPenChips.getChildCount(); i++) {
            Chip chip = (Chip) binding.healthPenChips.getChildAt(i);
            Object tag = chip.getTag();
            chip.setChecked(tag instanceof Long && vm.isPenSelected((Long) tag));
        }
        renderSummary();
    }

    private void renderSummary() {
        String penText = vm.penText();
        binding.healthPenText.setText(penText);
        binding.healthPenText.setVisibility(TextUtils.isEmpty(penText) ? View.GONE : View.VISIBLE);
        binding.healthSummaryTitle.setText(vm.summaryTitle());
        String text = vm.summaryText();
        binding.healthSummaryText.setText(text);
        binding.healthSummaryText.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    private void renderDisinfectants(@Nullable List<DisinfectantOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (DisinfectantOptionVO o : list) {
                items.add(new OptionAdapter.Item(o.getCatalogId(), HealthFormat.nonNull(o.getName()),
                        o.getRecommendedConcentration() == null ? null : "推荐 " + o.getRecommendedConcentration(), false));
            }
        }
        disinfectantAdapter.setItems(items);
        HealthForms.showSelected(binding.healthDisinfectantInput, disinfectantAdapter, vm.getCatalogId().getValue());
    }

    /** 消毒剂辅助文字：临期、无库存用 warning 色，库存正常“结存 n 升”用 on-surface-variant */
    private void renderStockHint(@Nullable String hint) {
        java.util.Map<String, String> errors = vm.getFieldErrors().getValue();
        String error = errors == null ? null : errors.get(DisinfectPlanEditViewModel.F_CATALOG);
        binding.healthDisinfectantLayout.setError(error);
        if (error != null) {
            return;
        }
        binding.healthDisinfectantLayout.setErrorEnabled(false);
        binding.healthDisinfectantLayout.setHelperText(hint);
        int color = MaterialColors.getColor(binding.healthDisinfectantLayout, vm.isStockWarning()
                ? com.zhimu.slmp.base.R.attr.colorWarning : com.google.android.material.R.attr.colorOnSurfaceVariant);
        binding.healthDisinfectantLayout.setHelperTextColor(android.content.res.ColorStateList.valueOf(color));
    }

    private void renderMethods(@Nullable List<DictItemVO> list) {
        HealthForms.renderChoiceChips(binding.healthMethodChips, list, null, vm.getMethodCode().getValue(),
                vm::onMethodSelected);
    }

    private void pickDate(String key, @Nullable String current) {
        LocalDate selected = DateFormatter.parseDate(current);
        LocalDate today = DateFormatter.today();
        DateTimePicker.date(key)
                .selection(selected != null ? selected : today)
                .bounds(REQ_END.equals(key) ? DateFormatter.parseDate(vm.getStartDate().getValue()) : today, null)
                .show(getSupportFragmentManager());
    }

    private void scrollTo(@Nullable String field) {
        View target;
        if (DisinfectPlanEditViewModel.F_PENS.equals(field)) {
            target = binding.healthPenLabel;
        } else if (DisinfectPlanEditViewModel.F_CYCLE.equals(field)) {
            target = binding.healthCycleLayout;
        } else if (DisinfectPlanEditViewModel.F_START.equals(field) || DisinfectPlanEditViewModel.F_END.equals(field)) {
            target = binding.healthStartLayout;
        } else if (DisinfectPlanEditViewModel.F_METHOD.equals(field)) {
            target = binding.healthMethodLabel;
        } else {
            target = binding.healthDisinfectantLayout;
        }
        HealthForms.scrollTo(binding.healthScroll, target);
    }
}
