package com.zhimu.slmp.health.ui.immuneregister;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.databinding.HealthActivityImmuneRegisterBinding;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.model.vo.DrugLotOptionVO;
import com.zhimu.slmp.health.model.vo.ImmunePlanOptionVO;
import com.zhimu.slmp.health.model.vo.ImmuneRecordVO;
import com.zhimu.slmp.health.model.vo.ImmuneRegisterFormVO;
import com.zhimu.slmp.health.model.vo.MemberItemVO;
import com.zhimu.slmp.health.model.vo.VaccineOptionVO;
import com.zhimu.slmp.health.ui.common.HealthForms;
import com.zhimu.slmp.health.ui.common.HealthNav;
import com.zhimu.slmp.health.ui.common.LotSheet;
import com.zhimu.slmp.health.ui.common.OptionAdapter;
import com.zhimu.slmp.health.ui.common.RecordLogBottomSheet;
import com.zhimu.slmp.health.util.HealthFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * D03 登记接种（ImmuneRegisterActivity）：入口 T04“去登记”、T02 快捷登记、D01“登记接种”“补录”（参数 plan_id、backfill），
 * D01 已接种卡片（record_id，查看状态）。成功后通知待办刷新并返回来源页，全局提示“已登记接种，待办已自动完成”。
 */
public class ImmuneRegisterActivity extends BaseActivity {

    private static final String REQ_LOT = "health_register_lot";
    private static final String REQ_DATE = "health_register_date";
    private static final String REQ_DISCARD = "health_register_discard";
    private static final String REQ_CHANGE = "health_register_change";
    private static final long DEBOUNCE_MS = 500;

    private HealthActivityImmuneRegisterBinding binding;
    private ImmuneRegisterViewModel vm;
    private OptionAdapter planAdapter;
    private OptionAdapter vaccineAdapter;
    private OptionAdapter executorAdapter;
    @NonNull
    private String primaryAction = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.health_activity_immune_register);
        vm = obtainViewModel(HealthInjector.factory(), ImmuneRegisterViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        HealthNav.finishOnFarmSwitch(this);

        TopAppBarHelper.setupBack(binding.healthTopBar.baseToolbar, getString(R.string.health_d03_title));
        binding.healthTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.health_menu_history) {
                vm.loadLogs();
                RecordLogBottomSheet.newInstance(ImmuneRegisterViewModel.class).showSafely(getSupportFragmentManager());
                return true;
            }
            return false;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (vm.isDirty()) {
                    HealthForms.showDiscard(ImmuneRegisterActivity.this, REQ_DISCARD);
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
        vm.getUiState().observe(this, binding.healthState::render);

        planAdapter = HealthForms.bindDropdown(binding.healthPlanInput, vm::selectPlan);
        vaccineAdapter = HealthForms.bindDropdown(binding.healthVaccineInput, vm::selectVaccine);
        executorAdapter = HealthForms.bindDropdown(binding.healthExecutorInput, vm::onExecutorSelected);
        vm.getPlanOptions().observe(this, this::renderPlans);
        vm.getVaccines().observe(this, this::renderVaccines);
        vm.getExecutors().observe(this, this::renderExecutors);
        vm.getPlanId().observe(this, id -> renderPlanField());
        vm.getFormCtx().observe(this, this::renderForm);
        vm.getDrugId().observe(this, id -> renderVaccineField());
        vm.getExecutorId().observe(this, id -> renderExecutorField());
        vm.getMethods().observe(this, this::renderMethods);
        vm.getMethodCode().observe(this, code -> HealthForms.checkChip(binding.healthMethodChips, code));
        vm.getSelectedLot().observe(this, lot -> renderLot());
        vm.getLotOptions().observe(this, o -> renderLot());
        vm.getLotError().observe(this, e -> renderLot());
        vm.getFieldErrors().observe(this, e -> renderLot());
        vm.getShowStockIn().observe(this, s -> binding.healthStockIn.setVisibility(Boolean.TRUE.equals(s)
                ? View.VISIBLE : View.GONE));
        vm.getDeductText().observe(this, text -> {
            binding.healthDeductNotice.setVisibility(text == null || !vm.isEditable() ? View.GONE : View.VISIBLE);
            binding.healthDeductNotice.setText(text);
        });
        vm.getRecord().observe(this, this::renderRecord);
        vm.getMode().observe(this, m -> renderMode());

        ClickThrottle.setOnClickListener(binding.healthLotInput, v -> vm.onLotFieldClick());
        binding.healthLotLayout.setEndIconOnClickListener(v -> vm.onLotFieldClick());
        vm.getShowLotSheet().observe(this, v -> showLotSheet());
        getSupportFragmentManager().setFragmentResultListener(REQ_LOT, this, (key, result) -> {
            Long id = LotSheet.resultLotId(result);
            if (id != null) {
                vm.selectLot(id);
            }
        });
        ClickThrottle.setOnClickListener(binding.healthStockIn, v -> vm.onStockInClick());
        vm.getGoStockIn().observe(this, v -> Router.open(this, RouterPath.M08));

        HealthForms.afterTextIdle(binding.healthDoseInput, DEBOUNCE_MS, vm::onDoseOrQuantityChanged);
        HealthForms.afterTextIdle(binding.healthQuantityInput, DEBOUNCE_MS, vm::onDoseOrQuantityChanged);

        ClickThrottle.setOnClickListener(binding.healthDateInput, v -> pickDate());
        binding.healthDateLayout.setEndIconOnClickListener(v -> pickDate());
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> vm.onDateSelected(DateTimePicker.getDate(result)));

        binding.healthActionBar.setOnPrimaryClickListener(v -> onPrimaryClick());
        vm.getSubmitting().observe(this, s -> binding.healthActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getScrollToField().observe(this, this::scrollTo);
        vm.getFinishWithSuccess().observe(this, msg -> HealthNav.finishWithMessage(this, msg));
        vm.getBackToPlan().observe(this, msg -> HealthNav.finishWithMessage(this, msg));
        vm.getOpenChangeRequest().observe(this, id -> {
            Bundle extras = new Bundle();
            extras.putString(RouterPath.EXTRA_BIZ_TYPE, HealthExtras.BIZ_IMMUNE_RECORD);
            extras.putLong(RouterPath.EXTRA_BIZ_ID, id);
            Router.open(this, RouterPath.U01, extras);
        });
        vm.getOpenRequest().observe(this, id -> {
            Bundle extras = new Bundle();
            if (id != null) {
                extras.putLong(RouterPath.EXTRA_REQUEST_ID, id);
            }
            Router.open(this, RouterPath.U02, extras);
        });
        vm.getNeedChangeRequest().observe(this, msg -> ConfirmDialogFragment.builder(REQ_CHANGE)
                .title(getString(R.string.health_change_request_title))
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
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        vm.onMessageAction(actionId);
    }

    // ===== 渲染 =====

    private void renderMode() {
        String mode = vm.getMode().getValue();
        boolean view = ImmuneRegisterViewModel.MODE_VIEW.equals(mode);
        boolean edit = ImmuneRegisterViewModel.MODE_EDIT.equals(mode);
        binding.healthTopBar.baseToolbar.setTitle(view ? R.string.health_d03_title_view
                : edit ? R.string.health_d03_title_edit : R.string.health_d03_title);
        binding.healthTopBar.baseToolbar.getMenu().clear();
        if (view || edit) {
            binding.healthTopBar.baseToolbar.inflateMenu(R.menu.health_menu_history);
            TopAppBarHelper.tintMenu(binding.healthTopBar.baseToolbar);
        }
        boolean editable = vm.isEditable();
        binding.healthPlanLayout.setEnabled(vm.isPlanChangeable());
        binding.healthVaccineLayout.setEnabled(editable && !edit);
        binding.healthLotLayout.setEnabled(editable);
        binding.healthDoseLayout.setEnabled(editable);
        binding.healthQuantityLayout.setEnabled(editable);
        binding.healthDateLayout.setEnabled(editable);
        binding.healthExecutorLayout.setEnabled(editable);
        for (int i = 0; i < binding.healthMethodChips.getChildCount(); i++) {
            binding.healthMethodChips.getChildAt(i).setEnabled(editable);
        }
        binding.healthBackfillTag.setVisibility(ImmuneRegisterViewModel.MODE_BACKFILL.equals(mode) ? View.VISIBLE
                : View.GONE);
        boolean staff = vm.isStaff();
        binding.healthExecutorLayout.setVisibility(!staff && !view ? View.VISIBLE : View.GONE);
        binding.healthExecutorLayout.setHelperText(getString(R.string.health_creator_owner, vm.creatorName()));
        renderCreator();
        renderActions();
        renderLot();
        renderPlanField();
    }

    private void renderCreator() {
        boolean view = ImmuneRegisterViewModel.MODE_VIEW.equals(vm.getMode().getValue());
        ImmuneRecordVO r = vm.getRecord().getValue();
        if (view && r != null) {
            binding.healthCreator.setVisibility(View.VISIBLE);
            binding.healthCreator.setText(getString(R.string.health_record_people, HealthFormat.nonNull(r.getExecutorName()),
                    HealthFormat.nonNull(r.getCreatorName())));
            return;
        }
        boolean staff = vm.isStaff();
        binding.healthCreator.setVisibility(staff ? View.VISIBLE : View.GONE);
        binding.healthCreator.setText(getString(R.string.health_creator_staff, vm.creatorName()));
    }

    /** 底部操作栏：登记 / 补录为“登记接种”；查看状态按 actions（EDIT“修改记录”，CHANGE_REQUEST“提交修改申请”，VIEW_REQUEST） */
    private void renderActions() {
        String mode = vm.getMode().getValue();
        if (ImmuneRegisterViewModel.MODE_VIEW.equals(mode)) {
            ImmuneRecordVO r = vm.getRecord().getValue();
            List<String> actions = r == null ? new ArrayList<>() : r.getActions();
            if (actions.contains(HealthExtras.ACTION_VIEW_REQUEST)) {
                primaryAction = HealthExtras.ACTION_VIEW_REQUEST;
                binding.healthActionBar.setPrimary(getString(R.string.health_view_change_request), 0);
            } else if (actions.contains(HealthExtras.ACTION_EDIT)) {
                primaryAction = HealthExtras.ACTION_EDIT;
                binding.healthActionBar.setPrimary(getString(R.string.health_edit_record), com.zhimu.slmp.base.R.drawable.ic_edit);
            } else if (actions.contains(HealthExtras.ACTION_CHANGE_REQUEST)) {
                primaryAction = HealthExtras.ACTION_CHANGE_REQUEST;
                binding.healthActionBar.setPrimary(getString(R.string.health_change_request_review),
                        com.zhimu.slmp.base.R.drawable.ic_edit_note);
            } else {
                primaryAction = "";
            }
            binding.healthActionBar.setVisibility(primaryAction.isEmpty() ? View.GONE : View.VISIBLE);
            return;
        }
        primaryAction = "SUBMIT";
        binding.healthActionBar.setVisibility(View.VISIBLE);
        if (ImmuneRegisterViewModel.MODE_EDIT.equals(mode)) {
            binding.healthActionBar.setPrimary(getString(R.string.health_save_changes), 0);
        } else {
            binding.healthActionBar.setPrimary(getString(R.string.health_register_vaccine),
                    com.zhimu.slmp.base.R.drawable.ic_vaccines);
        }
    }

    private void onPrimaryClick() {
        if ("SUBMIT".equals(primaryAction)) {
            vm.submit();
        } else if (!primaryAction.isEmpty()) {
            vm.onViewAction(primaryAction);
        }
    }

    private void renderPlans(@Nullable List<ImmunePlanOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (ImmunePlanOptionVO o : list) {
                OptionAdapter.Item item = new OptionAdapter.Item(o.getPlanId(), ImmuneRegisterViewModel.planTitle(o),
                        (o.getBatchNo() == null ? "" : o.getBatchNo() + HealthFormat.DOT) + "计划日期"
                                + (o.getPlanDateText() != null ? o.getPlanDateText() : HealthFormat.date(o.getPlanDate())),
                        false);
                String status = o.getStatus();
                if ("MISSED".equals(status)) {
                    item.tag(o.getStatusText() == null ? "漏免" : o.getStatusText(), StatusTagView.ERROR);
                } else if ("POSTPONED".equals(status)) {
                    item.tag(o.getStatusText() == null ? "已延期" : o.getStatusText(), StatusTagView.WARNING);
                } else {
                    item.tag(o.getStatusText() == null ? "待接种" : o.getStatusText(), StatusTagView.PRIMARY);
                }
                items.add(item);
            }
        }
        planAdapter.setItems(items);
        renderPlanField();
    }

    private void renderPlanField() {
        ImmuneRecordVO r = vm.getRecord().getValue();
        String mode = vm.getMode().getValue();
        if (r != null && (ImmuneRegisterViewModel.MODE_VIEW.equals(mode) || ImmuneRegisterViewModel.MODE_EDIT.equals(mode))) {
            binding.healthPlanInput.setText(HealthFormat.nonNull(r.getPenName()) + HealthFormat.DOT
                    + HealthFormat.nonNull(r.getVaccineName())
                    + (r.getImmuneRound() == null ? "" : " " + r.getImmuneRound()), false);
            binding.healthPlanLayout.setHelperText((r.getBatchNo() == null ? "" : r.getBatchNo() + HealthFormat.DOT)
                    + (r.isBackfill() ? "补免" : "接种日期 " + HealthFormat.date(r.getVaccinateDate())));
            return;
        }
        ImmuneRegisterFormVO f = vm.getFormCtx().getValue();
        if (f != null && f.getPlan() != null && planAdapter.findById(f.getPlan().getPlanId()) == null) {
            binding.healthPlanInput.setText(ImmuneRegisterViewModel.planTitle(f.getPlan()), false);
        } else {
            HealthForms.showSelected(binding.healthPlanInput, planAdapter, vm.getPlanId().getValue());
        }
        String helper = vm.planHelper();
        binding.healthPlanLayout.setHelperText(TextUtils.isEmpty(helper) ? null : helper);
    }

    private void renderForm(@Nullable ImmuneRegisterFormVO f) {
        if (f == null) {
            return;
        }
        String unit = vm.doseUnit();
        binding.healthDoseLayout.setSuffixText(unit);
        binding.healthDoseLayout.setHelperText(getString(R.string.health_template_dose,
                HealthFormat.qty(f.getTemplateDose(), HealthFormat.stockPart(unit))));
        binding.healthQuantityLayout.setSuffixText(vm.unit());
        binding.healthQuantityLayout.setHelperText(getString(R.string.health_quantity_limit,
                HealthFormat.qty(f.getCurrentCount(), vm.unit())));
        renderPlanField();
        renderVaccineField();
        renderCreator();
        binding.healthExecutorLayout.setHelperText(getString(R.string.health_creator_owner, vm.creatorName()));
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
        renderVaccineField();
    }

    private void renderVaccineField() {
        if (vaccineAdapter.findById(vm.getDrugId().getValue()) != null) {
            HealthForms.showSelected(binding.healthVaccineInput, vaccineAdapter, vm.getDrugId().getValue());
        } else {
            ImmuneRegisterFormVO f = vm.getFormCtx().getValue();
            ImmuneRecordVO r = vm.getRecord().getValue();
            String name = f != null ? f.getVaccineName() : r != null ? r.getVaccineName() : null;
            binding.healthVaccineInput.setText(HealthFormat.nonNull(name), false);
        }
        String unit = vm.doseUnit();
        if (!unit.isEmpty()) {
            binding.healthDoseLayout.setSuffixText(unit);
        }
    }

    private void renderExecutors(@Nullable List<MemberItemVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (MemberItemVO m : list) {
                items.add(new OptionAdapter.Item(m.getUserId(), HealthFormat.nonNull(m.getName()), null, false));
            }
        }
        executorAdapter.setItems(items);
        renderExecutorField();
    }

    private void renderExecutorField() {
        HealthForms.showSelected(binding.healthExecutorInput, executorAdapter, vm.getExecutorId().getValue());
    }

    private void renderMethods(@Nullable List<DictItemVO> list) {
        HealthForms.renderChoiceChips(binding.healthMethodChips, list, null, vm.getMethodCode().getValue(),
                vm::onMethodSelected);
        boolean editable = vm.isEditable();
        for (int i = 0; i < binding.healthMethodChips.getChildCount(); i++) {
            binding.healthMethodChips.getChildAt(i).setEnabled(editable);
        }
    }

    /** 批号字段：值、“先到期先出”标签、辅助文字或错误（无可用批号、结存不足、42201 / 42204） */
    private void renderLot() {
        DrugLotOptionVO lot = vm.getSelectedLot().getValue();
        ImmuneRecordVO r = vm.getRecord().getValue();
        String text = lot != null ? lot.getLotNo()
                : r != null && ImmuneRegisterViewModel.MODE_VIEW.equals(vm.getMode().getValue()) ? r.getLotNo() : "";
        binding.healthLotInput.setText(HealthFormat.nonNull(text));
        binding.healthLotTag.setVisibility(vm.isDefaultLotSelected() ? View.VISIBLE : View.GONE);
        Map<String, String> errors = vm.getFieldErrors().getValue();
        String error = errors == null ? null : errors.get(ImmuneRegisterViewModel.F_LOT);
        if (error == null) {
            error = vm.getLotError().getValue();
        }
        binding.healthLotLayout.setError(error);
        if (error == null) {
            binding.healthLotLayout.setErrorEnabled(false);
            String helper = vm.lotHelper();
            binding.healthLotLayout.setHelperText(TextUtils.isEmpty(helper) ? null : helper);
        }
    }

    private void renderRecord(@Nullable ImmuneRecordVO r) {
        if (r == null) {
            return;
        }
        binding.healthDoseLayout.setSuffixText(r.getDoseUnit());
        binding.healthQuantityLayout.setSuffixText(vm.unit());
        binding.healthEditHint.setVisibility(TextUtils.isEmpty(r.getEditHint()) ? View.GONE : View.VISIBLE);
        binding.healthEditHint.setText(r.getEditHint());
        renderMode();
        renderVaccineField();
    }

    private void showLotSheet() {
        DrugLotOptionVO lot = vm.getSelectedLot().getValue();
        LotSheet.show(getSupportFragmentManager(), REQ_LOT, getString(R.string.health_pick_vaccine_lot),
                vaccineName(), vm.getLotOptions().getValue(), lot == null ? null : lot.getLotId(), vm.stockUnit(),
                !vm.isStaff(), vm.needQty(), getString(R.string.health_purpose_vaccinate));
    }

    @Nullable
    private String vaccineName() {
        OptionAdapter.Item item = vaccineAdapter.findById(vm.getDrugId().getValue());
        if (item != null) {
            return item.title;
        }
        ImmuneRegisterFormVO f = vm.getFormCtx().getValue();
        return f == null ? null : f.getVaccineName();
    }

    private void pickDate() {
        ImmuneRegisterFormVO f = vm.getFormCtx().getValue();
        LocalDate selected = DateFormatter.parseDate(vm.getVaccinateDate().getValue());
        LocalDate min = f == null ? null : DateFormatter.parseDate(f.getDateMin());
        LocalDate max = f == null ? DateFormatter.today() : DateFormatter.parseDate(f.getDateMax());
        DateTimePicker.date(REQ_DATE)
                .selection(selected != null ? selected : DateFormatter.today())
                .bounds(min, max != null ? max : DateFormatter.today())
                .show(getSupportFragmentManager());
    }

    private void scrollTo(@Nullable String field) {
        View target;
        if (ImmuneRegisterViewModel.F_PLAN.equals(field)) {
            target = binding.healthPlanLayout;
        } else if (ImmuneRegisterViewModel.F_DRUG.equals(field)) {
            target = binding.healthVaccineLayout;
        } else if (ImmuneRegisterViewModel.F_LOT.equals(field)) {
            target = binding.healthLotLayout;
        } else if (ImmuneRegisterViewModel.F_DOSE.equals(field) || ImmuneRegisterViewModel.F_QUANTITY.equals(field)) {
            target = binding.healthDoseLayout;
        } else if (ImmuneRegisterViewModel.F_METHOD.equals(field)) {
            target = binding.healthMethodLabel;
        } else {
            target = binding.healthDateLayout;
        }
        HealthForms.scrollTo(binding.healthScroll, target);
    }
}
