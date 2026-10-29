package com.zhimu.slmp.health.ui.medicationregister;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.data.MedicationRepository;
import com.zhimu.slmp.health.databinding.HealthActivityMedicationRegisterBinding;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.model.vo.DrugLotOptionVO;
import com.zhimu.slmp.health.model.vo.LinkableReportVO;
import com.zhimu.slmp.health.model.vo.MedicationCreateResultVO;
import com.zhimu.slmp.health.model.vo.MedicationDrugOptionVO;
import com.zhimu.slmp.health.model.vo.MedicationRecordVO;
import com.zhimu.slmp.health.model.vo.MedicationTargetVO;
import com.zhimu.slmp.health.model.vo.MemberItemVO;
import com.zhimu.slmp.health.ui.common.HealthForms;
import com.zhimu.slmp.health.ui.common.HealthNav;
import com.zhimu.slmp.health.ui.common.LotSheet;
import com.zhimu.slmp.health.ui.common.OptionAdapter;
import com.zhimu.slmp.health.util.HealthFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * D04 登记 / 修改用药（MedicationRegisterActivity）：入口 D05 FAB、T02、I07“转为用药记录”（batch_id 或 pen_id、report_id、diagnosis_id），
 * D05 菜单“修改用药记录”（record_id）。登记成功后进入（或返回）D05，提示新的可出栏日期。
 */
public class MedicationRegisterActivity extends BaseActivity {

    private static final String REQ_LOT = "health_med_lot";
    private static final String REQ_DATE = "health_med_date";
    private static final String REQ_REPORT = "health_med_report";
    private static final String REQ_DISCARD = "health_med_discard";
    private static final String REQ_DRAFT = "health_med_draft";
    private static final String REQ_CHANGE = "health_med_change";
    private static final String REQ_STAFF = "health_med_staff";
    private static final long PREVIEW_DEBOUNCE_MS = 300;
    private static final long SEARCH_DEBOUNCE_MS = 300;

    private HealthActivityMedicationRegisterBinding binding;
    private MedicationRegisterViewModel vm;
    private OptionAdapter targetAdapter;
    private OptionAdapter drugAdapter;
    private OptionAdapter executorAdapter;
    private boolean settingDrugText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.health_activity_medication_register);
        vm = obtainViewModel(HealthInjector.factory(), MedicationRegisterViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        HealthNav.finishOnFarmSwitch(this);

        // 底部操作栏：左侧“提交后可出栏日期”，右侧主按钮
        View box = binding.healthReleaseBox;
        binding.healthActionBar.removeView(box);
        binding.healthActionBar.addView(box, 0);
        binding.healthActionBar.setPrimary(getString(R.string.health_register_medication),
                com.zhimu.slmp.base.R.drawable.ic_medication);
        vm.getReleaseText().observe(this, t -> box.setVisibility(TextUtils.isEmpty(t) ? View.INVISIBLE : View.VISIBLE));

        binding.healthTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBackPressed()) {
                    finish();
                }
            }
        });
        vm.getDiscardEvent().observe(this, v -> HealthForms.showDiscard(this, REQ_DISCARD));
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.onDiscardConfirmed();
                finish();
            }
        });
        vm.getRestoreDraftEvent().observe(this, v -> ConfirmDialogFragment.builder(REQ_DRAFT)
                .title(getString(R.string.health_draft_title))
                .message(getString(R.string.health_draft_message))
                .negative(getString(R.string.health_draft_discard))
                .positive(getString(R.string.health_draft_restore))
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_DRAFT, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.restoreDraft();
            } else {
                vm.discardDraft();
            }
        });
        binding.healthState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, binding.healthState::render);

        // 用药对象
        binding.healthTargetGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) {
                vm.onTargetTypeChanged(id == R.id.health_target_pen ? MedicationRepository.TARGET_PEN
                        : MedicationRepository.TARGET_BATCH);
            }
        });
        vm.getTargetType().observe(this, type -> {
            boolean batch = !MedicationRepository.TARGET_PEN.equals(type);
            binding.healthTargetGroup.check(batch ? R.id.health_target_batch : R.id.health_target_pen);
            binding.healthTargetLayout.setHint(getString(batch ? R.string.health_batch : R.string.health_pen));
        });
        targetAdapter = HealthForms.bindDropdown(binding.healthTargetInput, vm::onTargetSelected);
        vm.getTargets().observe(this, this::renderTargets);
        vm.getBatchId().observe(this, id -> renderTarget());
        vm.getPenId().observe(this, id -> renderTarget());
        vm.getEditMode().observe(this, edit -> {
            boolean e = Boolean.TRUE.equals(edit);
            binding.healthTargetLayout.setEnabled(!e);
            for (int i = 0; i < binding.healthTargetGroup.getChildCount(); i++) {
                binding.healthTargetGroup.getChildAt(i).setEnabled(!e);
            }
            binding.healthActionBar.setPrimary(getString(e ? R.string.health_save_changes
                    : R.string.health_register_medication), e ? 0 : com.zhimu.slmp.base.R.drawable.ic_medication);
        });
        vm.getRecord().observe(this, this::renderRecord);

        // 药品（可输入搜索）
        drugAdapter = new OptionAdapter(this);
        binding.healthDrugInput.setAdapter(drugAdapter);
        binding.healthDrugInput.setOnItemClickListener((p, v, position, id) -> {
            OptionAdapter.Item item = drugAdapter.getItem(position);
            if (item != null) {
                vm.onDrugSelected(item.id);
            }
        });
        HealthForms.afterTextIdle(binding.healthDrugInput, SEARCH_DEBOUNCE_MS, () -> {
            if (!settingDrugText) {
                vm.searchDrug(binding.healthDrugInput.getText() == null ? null
                        : binding.healthDrugInput.getText().toString());
            }
        });
        vm.getDrugs().observe(this, this::renderDrugs);
        vm.getSelectedDrug().observe(this, this::renderDrug);
        vm.getBannedHint().observe(this, h -> renderDrugError());

        // 批号
        ClickThrottle.setOnClickListener(binding.healthLotInput, v -> showLotSheet());
        binding.healthLotLayout.setEndIconOnClickListener(v -> showLotSheet());
        getSupportFragmentManager().setFragmentResultListener(REQ_LOT, this, (key, result) -> {
            Long id = LotSheet.resultLotId(result);
            if (id != null) {
                vm.selectLot(id);
            }
        });
        vm.getDrugLotId().observe(this, id -> renderLot());
        vm.getLotOptions().observe(this, o -> renderLot());
        vm.getFieldErrors().observe(this, this::renderErrors);

        // 剂量、疗程、预估
        HealthForms.afterTextIdle(binding.healthDoseInput, PREVIEW_DEBOUNCE_MS, vm::requestPreview);
        HealthForms.afterTextIdle(binding.healthCourseInput, PREVIEW_DEBOUNCE_MS, vm::requestPreview);
        vm.getOverDose().observe(this, over -> binding.healthDoseLayout.setEndIconMode(
                Boolean.TRUE.equals(over) ? com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM
                        : com.google.android.material.textfield.TextInputLayout.END_ICON_NONE));
        binding.healthDoseLayout.setEndIconDrawable(com.zhimu.slmp.base.R.drawable.ic_warning);
        binding.healthDoseLayout.setEndIconTintList(android.content.res.ColorStateList.valueOf(
                com.google.android.material.color.MaterialColors.getColor(binding.healthDoseLayout,
                        com.zhimu.slmp.base.R.attr.colorWarning)));
        vm.getPreview().observe(this, p -> binding.healthUsedLayout.setHelperText(vm.usedHelper()));
        FarmContextVO farm = CoreInjector.get(this).farmContext().current();
        String species = farm == null || farm.getSpeciesText() == null ? "该畜种" : farm.getSpeciesText();
        binding.healthMismatchNotice.setNotice(getString(R.string.health_mismatch_title, species),
                getString(R.string.health_mismatch_text));

        // 日期与执行人
        ClickThrottle.setOnClickListener(binding.healthDateInput, v -> pickDate());
        binding.healthDateLayout.setEndIconOnClickListener(v -> pickDate());
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> vm.onFirstDateSelected(DateTimePicker.getDate(result)));
        executorAdapter = HealthForms.bindDropdown(binding.healthExecutorInput, vm::onExecutorSelected);
        vm.getExecutors().observe(this, this::renderExecutors);
        vm.getExecutorId().observe(this, id -> HealthForms.showSelected(binding.healthExecutorInput, executorAdapter, id));
        AccountUserVO me = CoreInjector.get(this).sessionManager().currentUser();
        String myName = me == null || me.getName() == null ? "" : me.getName();
        boolean staff = vm.isStaff();
        binding.healthExecutorLayout.setVisibility(staff ? View.GONE : View.VISIBLE);
        binding.healthExecutorLayout.setHelperText(getString(R.string.health_creator_owner, myName));
        binding.healthCreator.setVisibility(staff ? View.VISIBLE : View.GONE);
        binding.healthCreator.setText(getString(R.string.health_creator_staff, myName));

        // 关联诊断
        vm.getLinkedReport().observe(this, this::renderReport);
        vm.getShowReportSheet().observe(this, v -> {
            LinkableReportVO current = vm.getLinkedReport().getValue();
            LinkReportBottomSheet.show(getSupportFragmentManager(), REQ_REPORT, vm.getReports().getValue(),
                    current == null ? null : current.getReportId());
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_REPORT, this, (key, result) -> {
            Long id = MedicationRegisterViewModel.asLong(OptionSheetDialogFragment.getValue(result));
            if (id != null) {
                vm.onReportSelected(id);
            }
        });

        // 提交
        binding.healthActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getSubmitting().observe(this, s -> binding.healthActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getScrollToField().observe(this, this::scrollTo);
        vm.getFinishWithSuccess().observe(this, this::onSuccess);
        vm.getFinishWithMessage().observe(this, msg -> HealthNav.finishWithMessage(this, msg));
        vm.getNeedChangeRequest().observe(this, msg -> ConfirmDialogFragment.builder(REQ_CHANGE)
                .icon(com.zhimu.slmp.base.R.drawable.ic_edit_note)
                .title(getString(R.string.health_change_submit_title))
                .message(msg)
                .negative(getString(com.zhimu.slmp.base.R.string.base_action_cancel))
                .positive(getString(R.string.health_submit_change_request))
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_CHANGE, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.onChangeRequestConfirmed();
            }
        });
        vm.getStaffRestricted().observe(this, msg -> ConfirmDialogFragment.builder(REQ_STAFF)
                .icon(com.zhimu.slmp.base.R.drawable.ic_lock)
                .title(getString(R.string.health_staff_cannot_edit))
                .message(msg)
                .negative(getString(R.string.health_got_it))
                .positive(getString(R.string.health_notify_manager))
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_STAFF, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.notifyManager();
            }
        });
        vm.getOpenChangeRequest().observe(this, id -> {
            Bundle extras = new Bundle();
            extras.putString(RouterPath.EXTRA_BIZ_TYPE, HealthExtras.BIZ_MEDICATION_RECORD);
            extras.putLong(RouterPath.EXTRA_BIZ_ID, id);
            Router.open(this, RouterPath.U01, extras);
        });
    }

    @Override
    protected void onMessageAction(@androidx.annotation.NonNull String actionId) {
        vm.onMessageAction(actionId);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            vm.saveDraft();
        }
    }

    // ===== 渲染 =====

    private void renderTargets(@Nullable List<MedicationTargetVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        String unit = HealthFormat.countUnit(CoreInjector.get(this).farmContext());
        if (list != null) {
            for (MedicationTargetVO t : list) {
                if (vm.isBatchTarget() && t.getBatchId() != null) {
                    items.add(new OptionAdapter.Item(t.getBatchId(), HealthFormat.nonNull(t.getBatchNo())
                            + HealthFormat.DOT + HealthFormat.nonNull(t.getPenName()),
                            "存栏 " + HealthFormat.qty(t.getCurrentCount(), unit), false));
                } else if (!vm.isBatchTarget() && t.getPenId() != null) {
                    boolean noBatch = Boolean.FALSE.equals(t.getHasBatch());
                    items.add(new OptionAdapter.Item(t.getPenId(), HealthFormat.nonNull(t.getPenName()),
                            noBatch ? getString(R.string.health_pen_no_batch) : t.getBatches().size() + " 个在栏批次",
                            false));
                }
            }
        }
        targetAdapter.setItems(items);
        renderTarget();
    }

    private void renderTarget() {
        Long id = vm.isBatchTarget() ? vm.getBatchId().getValue() : vm.getPenId().getValue();
        MedicationRecordVO r = vm.getRecord().getValue();
        if (r != null) {
            binding.healthTargetInput.setText(HealthFormat.nonNull(r.getBatchNo()) + HealthFormat.DOT
                    + HealthFormat.nonNull(r.getPenName()), false);
        } else {
            HealthForms.showSelected(binding.healthTargetInput, targetAdapter, id);
        }
        renderErrors(vm.getFieldErrors().getValue());
    }

    private void renderRecord(@Nullable MedicationRecordVO r) {
        if (r == null) {
            return;
        }
        renderTarget();
        setDrugText(r.getDrugName());
    }

    private void renderDrugs(@Nullable List<MedicationDrugOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (MedicationDrugOptionVO d : list) {
                items.add(new OptionAdapter.Item(d.getDrugId(), HealthFormat.nonNull(d.getDrugName()),
                        MedicationRegisterViewModel.drugHint(d.getHintText()), false));
            }
        }
        drugAdapter.setItems(items);
        if (binding.healthDrugInput.hasFocus() && !items.isEmpty()) {
            binding.healthDrugInput.showDropDown();
        }
    }

    private void renderDrug(@Nullable MedicationDrugOptionVO d) {
        if (d != null) {
            setDrugText(d.getDrugName());
        }
        binding.healthDoseLayout.setSuffixText(vm.doseUnit());
        String doseHelper = vm.doseHelper();
        binding.healthDoseLayout.setHelperText(doseHelper);
        binding.healthUsedLayout.setSuffixText(vm.stockUnit());
        binding.healthUsedLayout.setHelperText(vm.usedHelper());
        renderDrugError();
    }

    private void setDrugText(@Nullable String text) {
        settingDrugText = true;
        binding.healthDrugInput.setText(HealthFormat.nonNull(text), false);
        binding.healthDrugInput.dismissDropDown();
        binding.healthDrugInput.post(() -> settingDrugText = false);
    }

    /** 药品字段：错误（请选择药品 / 42203）优先，其次禁用药提示（warning），否则辅助文字 */
    private void renderDrugError() {
        Map<String, String> errors = vm.getFieldErrors().getValue();
        String error = errors == null ? null : errors.get(MedicationRegisterViewModel.F_DRUG);
        String banned = vm.getBannedHint().getValue();
        binding.healthDrugLayout.setError(error != null ? error : banned);
        if (error == null && banned == null) {
            binding.healthDrugLayout.setErrorEnabled(false);
            binding.healthDrugLayout.setHelperText(vm.drugHelper());
        }
    }

    private void renderLot() {
        DrugLotOptionVO lot = vm.selectedLot();
        MedicationRecordVO r = vm.getRecord().getValue();
        binding.healthLotInput.setText(lot != null ? lot.getLotNo() : r != null && vm.getLotOptions().getValue() == null
                ? r.getLotNo() : "");
        renderErrors(vm.getFieldErrors().getValue());
    }

    private void renderErrors(@Nullable Map<String, String> errors) {
        String targetError = errors == null ? null : errors.get(vm.isBatchTarget()
                ? MedicationRegisterViewModel.F_BATCH : MedicationRegisterViewModel.F_PEN);
        binding.healthTargetLayout.setError(targetError);
        if (targetError == null) {
            binding.healthTargetLayout.setErrorEnabled(false);
            String helper = vm.targetHelper();
            binding.healthTargetLayout.setHelperText(TextUtils.isEmpty(helper) ? null : helper);
        }
        String lotError = errors == null ? null : errors.get(MedicationRegisterViewModel.F_LOT);
        binding.healthLotLayout.setError(lotError);
        if (lotError == null) {
            binding.healthLotLayout.setErrorEnabled(false);
            binding.healthLotLayout.setHelperText(vm.lotHelper());
        }
        renderDrugError();
    }

    private void renderExecutors(@Nullable List<MemberItemVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (MemberItemVO m : list) {
                items.add(new OptionAdapter.Item(m.getUserId(), HealthFormat.nonNull(m.getName()), null, false));
            }
        }
        executorAdapter.setItems(items);
        HealthForms.showSelected(binding.healthExecutorInput, executorAdapter, vm.getExecutorId().getValue());
    }

    /** 关联 Chip：已关联“已关联 10月18日 AI 诊断 · 1 号保育舍”（close 取消）；未关联“关联异常上报或诊断” */
    private void renderReport(@Nullable LinkableReportVO r) {
        if (r != null) {
            binding.healthReportChip.setText(getString(R.string.health_report_linked, HealthFormat.nonNull(r.getLabel())));
            binding.healthReportChip.setChipIconResource(com.zhimu.slmp.base.R.drawable.ic_link);
            binding.healthReportChip.setCloseIconVisible(true);
            binding.healthReportChip.setOnCloseIconClickListener(v -> vm.unlinkReport());
            binding.healthReportChip.setOnClickListener(null);
        } else {
            binding.healthReportChip.setText(R.string.health_report_link);
            binding.healthReportChip.setChipIconResource(R.drawable.health_ic_add_link);
            binding.healthReportChip.setCloseIconVisible(false);
            ClickThrottle.setOnClickListener(binding.healthReportChip, v -> vm.loadReports());
        }
    }

    private void showLotSheet() {
        if (vm.getLotOptions().getValue() == null) {
            return;
        }
        MedicationDrugOptionVO d = vm.getSelectedDrug().getValue();
        LotSheet.show(getSupportFragmentManager(), REQ_LOT, getString(R.string.health_pick_drug_lot),
                d == null ? null : d.getDrugName(), vm.getLotOptions().getValue(), vm.getDrugLotId().getValue(),
                vm.stockUnit(), !vm.isStaff(), null, getString(R.string.health_purpose_medicate));
    }

    private void pickDate() {
        LocalDate selected = DateFormatter.parseDate(vm.getFirstDate().getValue());
        DateTimePicker.date(REQ_DATE)
                .selection(selected != null ? selected : DateFormatter.today())
                .bounds(null, DateFormatter.today())
                .show(getSupportFragmentManager());
    }

    /** 登记成功：从 D05 进入时返回刷新，否则进入该批次的 D05；Snackbar 提示新的可出栏日期 */
    private void onSuccess(@Nullable MedicationCreateResultVO result) {
        String toast = result == null || result.getToast() == null ? getString(R.string.health_medication_saved)
                : result.getToast();
        if (getCallingActivity() == null) {
            Long batch = vm.archiveBatchId();
            if (batch != null) {
                Bundle extras = new Bundle();
                extras.putLong(RouterPath.EXTRA_BATCH_ID, batch);
                Router.open(this, RouterPath.D05, extras);
            }
        }
        setResult(RESULT_OK);
        finish();
        GlobalEvents.postMessage(toast);
    }

    private void scrollTo(@Nullable String field) {
        View target;
        if (MedicationRegisterViewModel.F_BATCH.equals(field) || MedicationRegisterViewModel.F_PEN.equals(field)) {
            target = binding.healthTargetLayout;
        } else if (MedicationRegisterViewModel.F_DRUG.equals(field) || MedicationRegisterViewModel.F_LOT.equals(field)) {
            target = binding.healthDrugLayout;
        } else if (MedicationRegisterViewModel.F_DOSE.equals(field) || MedicationRegisterViewModel.F_COURSE.equals(field)) {
            target = binding.healthDoseLayout;
        } else if (MedicationRegisterViewModel.F_OVER_NOTE.equals(field)) {
            target = binding.healthOverNoteLayout;
        } else if (MedicationRegisterViewModel.F_USAGE_NOTE.equals(field)) {
            target = binding.healthUsageNoteLayout;
        } else if (MedicationRegisterViewModel.F_USED.equals(field)) {
            target = binding.healthUsedLayout;
        } else if (MedicationRegisterViewModel.F_FIRST_DATE.equals(field)) {
            target = binding.healthDateLayout;
        } else {
            target = binding.healthReasonLayout;
        }
        HealthForms.scrollTo(binding.healthScroll, target);
    }
}
