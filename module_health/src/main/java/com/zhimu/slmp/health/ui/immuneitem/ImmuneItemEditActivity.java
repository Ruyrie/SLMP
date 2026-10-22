package com.zhimu.slmp.health.ui.immuneitem;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.databinding.HealthActivityImmuneItemEditBinding;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.model.vo.ImmunePlanOverviewVO;
import com.zhimu.slmp.health.model.vo.VaccineOptionVO;
import com.zhimu.slmp.health.ui.common.HealthForms;
import com.zhimu.slmp.health.ui.common.HealthNav;
import com.zhimu.slmp.health.ui.common.OptionAdapter;
import com.zhimu.slmp.health.util.HealthFormat;
import com.zhimu.slmp.health.util.ImmuneRules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * D02 添加免疫项目（ImmuneItemEditActivity）：入口 D01 FAB、D01 无模板空状态按钮；修改状态（D01 菜单“修改剂量和方式”）
 * 标题“修改免疫项目”，疫苗与计划时间只读，底部只有“保存修改”。成功后回传 RESULT_PLAN_ID，D01 高亮该项。
 */
public class ImmuneItemEditActivity extends BaseActivity {

    private static final String REQ_DATE = "health_item_date";
    private static final String REQ_DISCARD = "health_item_discard";

    private HealthActivityImmuneItemEditBinding binding;
    private ImmuneItemEditViewModel vm;
    private OptionAdapter vaccineAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.health_activity_immune_item_edit);
        vm = obtainViewModel(HealthInjector.factory(), ImmuneItemEditViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        HealthNav.finishOnFarmSwitch(this);

        binding.healthTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (vm.isDirty()) {
                    HealthForms.showDiscard(ImmuneItemEditActivity.this, REQ_DISCARD);
                } else {
                    finishWithLastCreated();
                }
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                finishWithLastCreated();
            }
        });
        binding.healthState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, binding.healthState::render);

        vaccineAdapter = HealthForms.bindDropdown(binding.healthVaccineInput, vm::onVaccineSelected);
        vm.getVaccines().observe(this, this::renderVaccines);
        vm.getDrugId().observe(this, id -> {
            if (Boolean.TRUE.equals(vm.getEditMode().getValue())) {
                binding.healthVaccineInput.setText(HealthFormat.nonNull(vm.getEditVaccineName()), false);
            } else {
                HealthForms.showSelected(binding.healthVaccineInput, vaccineAdapter, id);
            }
        });
        vm.getOverview().observe(this, this::renderOverview);
        vm.getMethods().observe(this, this::renderMethods);
        vm.getMethodCode().observe(this, code -> HealthForms.checkChip(binding.healthMethodChips, code));

        vm.getPlanMode().observe(this, mode -> binding.healthModeGroup.check(ImmuneRules.MODE_BY_DATE.equals(mode)
                ? R.id.health_mode_date : R.id.health_mode_age));
        binding.healthModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                vm.onPlanModeChanged(checkedId == R.id.health_mode_date ? ImmuneRules.MODE_BY_DATE
                        : ImmuneRules.MODE_BY_DAY_AGE);
            }
        });
        vm.getEditMode().observe(this, edit -> {
            boolean e = Boolean.TRUE.equals(edit);
            for (int i = 0; i < binding.healthModeGroup.getChildCount(); i++) {
                binding.healthModeGroup.getChildAt(i).setEnabled(!e);
            }
            if (e) {
                binding.healthActionBar.setPrimary(getString(R.string.health_save_changes), 0);
                binding.healthActionBar.setSecondary(null, 0);
            } else {
                binding.healthActionBar.setPrimary(getString(R.string.health_d02_submit), com.zhimu.slmp.base.R.drawable.ic_add);
                binding.healthActionBar.setSecondary(getString(R.string.health_d02_continue), 0);
            }
        });

        ClickThrottle.setOnClickListener(binding.healthDateInput, v -> pickDate());
        binding.healthDateLayout.setEndIconOnClickListener(v -> pickDate());
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> vm.onPlanDateSelected(DateTimePicker.getDate(result)));
        binding.healthAgeInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                vm.clearError(ImmuneItemEditViewModel.F_AGE);
            }
        });

        binding.healthActionBar.setOnPrimaryClickListener(v -> vm.submit(false));
        binding.healthActionBar.setOnSecondaryClickListener(v -> vm.submit(true));
        vm.getSubmitting().observe(this, s -> binding.healthActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getScrollToField().observe(this, this::scrollTo);
        vm.getResetForm().observe(this, v -> {
            binding.healthVaccineInput.setText("", false);
            binding.healthScroll.smoothScrollTo(0, 0);
        });
        vm.getFinishWithResult().observe(this, id -> {
            Intent data = new Intent().putExtra(HealthExtras.RESULT_PLAN_ID, id == null ? 0L : id);
            setResult(RESULT_OK, data);
            finish();
            com.zhimu.slmp.base.event.GlobalEvents.postMessage(getString(Boolean.TRUE.equals(vm.getEditMode().getValue())
                    ? R.string.health_d02_saved : R.string.health_d02_added));
        });
        vm.getBackWithMessage().observe(this, msg -> HealthNav.finishWithMessage(this, msg));
    }

    /** 继续添加过项目后返回，D01 刷新并高亮最后一项 */
    private void finishWithLastCreated() {
        Long last = vm.getLastCreatedId();
        if (last != null) {
            setResult(RESULT_OK, new Intent().putExtra(HealthExtras.RESULT_PLAN_ID, last));
        }
        finish();
    }

    private void renderOverview(@Nullable ImmunePlanOverviewVO o) {
        if (o == null) {
            return;
        }
        binding.healthNoTemplate.setVisibility(o.isHasTemplate() ? View.GONE : View.VISIBLE);
        if (!o.isHasTemplate()) {
            binding.healthNoTemplate.setNotice(getString(R.string.health_no_template_title),
                    getString(R.string.health_no_template_detail, HealthFormat.nonNull(o.getBreedName())));
        }
        String farm = vm.farmName();
        binding.healthFarmTag.setText(farm);
        binding.healthFarmTag.setVisibility(farm.isEmpty() ? View.GONE : View.VISIBLE);
        binding.healthBatchSummary.setText(vm.batchSummary());
        binding.healthRemind.setText(vm.remindText());
    }

    private void renderVaccines(@Nullable List<VaccineOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (VaccineOptionVO v : list) {
                items.add(new OptionAdapter.Item(v.getDrugId(), HealthFormat.nonNull(v.getName()),
                        v.isNoStock() ? getString(R.string.health_no_stock) : null, false));
            }
        }
        vaccineAdapter.setItems(items);
        if (!Boolean.TRUE.equals(vm.getEditMode().getValue())) {
            HealthForms.showSelected(binding.healthVaccineInput, vaccineAdapter, vm.getDrugId().getValue());
        } else {
            binding.healthVaccineInput.setText(HealthFormat.nonNull(vm.getEditVaccineName()), false);
        }
    }

    private void renderMethods(@Nullable List<DictItemVO> list) {
        HealthForms.renderChoiceChips(binding.healthMethodChips, list, null, vm.getMethodCode().getValue(),
                vm::onMethodSelected);
    }

    private void pickDate() {
        LocalDate selected = DateFormatter.parseDate(vm.getPlanDate().getValue());
        LocalDate today = DateFormatter.today();
        DateTimePicker.date(REQ_DATE)
                .selection(selected != null ? selected : today)
                .bounds(today, null)
                .show(getSupportFragmentManager());
    }

    private void scrollTo(@Nullable String field) {
        View target;
        if (ImmuneItemEditViewModel.F_DRUG.equals(field)) {
            target = binding.healthVaccineLayout;
        } else if (ImmuneItemEditViewModel.F_AGE.equals(field)) {
            target = binding.healthAgeLayout;
        } else if (ImmuneItemEditViewModel.F_DATE.equals(field)) {
            target = binding.healthDateLayout;
        } else if (ImmuneItemEditViewModel.F_DOSE.equals(field)) {
            target = binding.healthDoseLayout;
        } else {
            target = binding.healthMethodLabel;
        }
        HealthForms.scrollTo(binding.healthScroll, target);
    }
}
