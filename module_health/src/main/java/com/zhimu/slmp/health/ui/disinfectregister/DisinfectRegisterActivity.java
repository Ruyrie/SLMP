package com.zhimu.slmp.health.ui.disinfectregister;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.ui.recordlog.RecordLogSheet;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.databinding.HealthActivityDisinfectRegisterBinding;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.model.vo.DisinfectRecordVO;
import com.zhimu.slmp.health.model.vo.DisinfectTaskOptionVO;
import com.zhimu.slmp.health.model.vo.DisinfectantOptionVO;
import com.zhimu.slmp.health.model.vo.MemberItemVO;
import com.zhimu.slmp.health.model.vo.PenOptionVO;
import com.zhimu.slmp.health.ui.common.HealthForms;
import com.zhimu.slmp.health.ui.common.HealthNav;
import com.zhimu.slmp.health.ui.common.LotSheet;
import com.zhimu.slmp.health.ui.common.OptionAdapter;
import com.zhimu.slmp.health.util.HealthFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * D08 登记消毒（DisinfectRegisterActivity）：入口 D09 待执行任务 / “补录”（task_id、backfill）、T04“去登记”、T02、
 * D09 FAB“登记临时消毒”（record_type = EMPTY_PEN）、D09 已执行任务（record_id，查看状态）。
 */
public class DisinfectRegisterActivity extends BaseActivity {

    private static final String REQ_LOT = "health_dis_lot";
    private static final String REQ_DATE = "health_dis_date";
    private static final String REQ_TIME = "health_dis_time";
    private static final String REQ_DISCARD = "health_dis_discard";
    private static final String REQ_DRAFT = "health_dis_draft";
    private static final String REQ_CHANGE = "health_dis_change";
    private static final String STATE_PICKED_DATE = "health_picked_date";

    private HealthActivityDisinfectRegisterBinding binding;
    private DisinfectRegisterViewModel vm;
    private OptionAdapter taskAdapter;
    private OptionAdapter penAdapter;
    private OptionAdapter disinfectantAdapter;
    private OptionAdapter executorAdapter;
    @Nullable
    private String pickedDate;
    @NonNull
    private String primaryAction = "SUBMIT";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.health_activity_disinfect_register);
        vm = obtainViewModel(HealthInjector.factory(), DisinfectRegisterViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        // 照片网格：必须在页面 STARTED 之前 bind
        binding.healthPhotos.bind(this, vm.getPhotos());
        HealthNav.finishOnFarmSwitch(this);
        if (savedInstanceState != null) {
            pickedDate = savedInstanceState.getString(STATE_PICKED_DATE);
        }

        TopAppBarHelper.setupBack(binding.healthTopBar.baseToolbar, getString(R.string.health_d08_title));
        binding.healthTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.health_menu_history) {
                RecordLogSheet.show(getSupportFragmentManager(), HealthExtras.BIZ_DISINFECT_RECORD,
                        vm.getRecordId() == null ? 0L : vm.getRecordId(), null);
                return true;
            }
            return false;
        });
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

        // 登记类型
        binding.healthTypeGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) {
                vm.onRecordTypeChanged(id == R.id.health_type_empty ? HealthExtras.RECORD_EMPTY_PEN
                        : id == R.id.health_type_emergency ? HealthExtras.RECORD_EMERGENCY : HealthExtras.RECORD_PLAN_TASK);
            }
        });
        vm.getRecordType().observe(this, type -> renderType());
        vm.getMode().observe(this, m -> renderMode());

        taskAdapter = HealthForms.bindDropdown(binding.healthTaskInput, vm::onTaskSelected);
        penAdapter = HealthForms.bindDropdown(binding.healthPenInput, vm::onPenSelected);
        disinfectantAdapter = HealthForms.bindDropdown(binding.healthDisinfectantInput, vm::onDisinfectantSelected);
        executorAdapter = HealthForms.bindDropdown(binding.healthExecutorInput, vm::onExecutorSelected);
        vm.getTaskOptions().observe(this, this::renderTasks);
        vm.getSelectedTask().observe(this, t -> renderTask());
        vm.getTaskId().observe(this, id -> renderTask());
        vm.getPens().observe(this, this::renderPens);
        vm.getPenId().observe(this, id -> renderPenField());
        vm.getDisinfectants().observe(this, this::renderDisinfectants);
        vm.getDrugId().observe(this, id -> {
            HealthForms.showSelected(binding.healthDisinfectantInput, disinfectantAdapter, vm.currentCatalogId());
            renderLot();
        });
        vm.getExecutors().observe(this, this::renderExecutors);
        vm.getExecutorId().observe(this, id -> HealthForms.showSelected(binding.healthExecutorInput, executorAdapter, id));
        vm.getMethods().observe(this, this::renderMethods);
        vm.getMethodCode().observe(this, code -> HealthForms.checkChip(binding.healthMethodChips, code));
        vm.getRecord().observe(this, this::renderRecord);

        // 实际时间：日期 → 24 小时制时间
        vm.getActualTime().observe(this, t -> binding.healthTimeInput.setText(HealthFormat.dateTime(t)));
        ClickThrottle.setOnClickListener(binding.healthTimeInput, v -> pickDate());
        binding.healthTimeLayout.setEndIconOnClickListener(v -> pickDate());
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this, (key, result) -> {
            pickedDate = DateTimePicker.getDate(result);
            String current = vm.getActualTime().getValue();
            DateTimePicker.time(REQ_TIME).time(current != null && current.length() >= 16 ? current.substring(11, 16)
                    : null).show(getSupportFragmentManager());
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_TIME, this, (key, result) -> {
            vm.onActualDateTimeSelected(pickedDate, DateTimePicker.getTime(result));
            pickedDate = null;
        });

        // 批号
        ClickThrottle.setOnClickListener(binding.healthLotInput, v -> vm.onLotFieldClick());
        binding.healthLotLayout.setEndIconOnClickListener(v -> vm.onLotFieldClick());
        vm.getShowLotSheet().observe(this, v -> LotSheet.show(getSupportFragmentManager(), REQ_LOT,
                getString(R.string.health_pick_disinfectant_lot), vm.disinfectantName(), vm.getLotOptions().getValue(),
                vm.getDrugLotId().getValue(), vm.usageUnit(), !vm.isStaff(), null,
                getString(R.string.health_purpose_disinfect)));
        getSupportFragmentManager().setFragmentResultListener(REQ_LOT, this, (key, result) -> {
            Long id = LotSheet.resultLotId(result);
            if (id != null) {
                vm.selectLot(id);
            }
        });
        vm.getDrugLotId().observe(this, id -> renderLot());
        vm.getLotOptions().observe(this, o -> renderLot());
        vm.getFieldErrors().observe(this, this::renderErrors);

        // 浓度与确认横幅
        binding.healthConcentrationInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (binding.healthConcentrationInput.hasFocus()) {
                    vm.onConcentrationChanged();
                }
            }
        });
        vm.getConcentrationMismatch().observe(this, m -> renderConcentration());
        vm.getConcentrationConfirmed().observe(this, c -> renderConcentration());
        vm.getConcentration().observe(this, c -> renderConcentration());
        binding.healthConfirmNotice.setAction(getString(R.string.health_confirm), v -> vm.confirmConcentration());
        vm.getScrollToNotice().observe(this, v -> {
            HealthForms.scrollTo(binding.healthScroll, binding.healthConfirmNotice);
            flashNotice();
        });

        // 登记人
        AccountUserVO me = CoreInjector.get(this).sessionManager().currentUser();
        String myName = me == null || me.getName() == null ? "" : me.getName();
        binding.healthExecutorLayout.setHelperText(getString(R.string.health_creator_owner, myName));
        binding.healthCreator.setText(getString(R.string.health_creator_staff, myName));

        // 提交
        binding.healthActionBar.setOnPrimaryClickListener(v -> onPrimaryClick());
        vm.getSubmitting().observe(this, s -> binding.healthActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getPhotos().getItems().observe(this, items -> renderActions());
        vm.getScrollToField().observe(this, this::scrollTo);
        vm.getFinishWithSuccess().observe(this, msg -> HealthNav.finishWithMessage(this, msg));
        vm.getBackToTasks().observe(this, msg -> HealthNav.finishWithMessage(this, msg));
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
        vm.getOpenChangeRequest().observe(this, id -> {
            Bundle extras = new Bundle();
            extras.putString(RouterPath.EXTRA_BIZ_TYPE, HealthExtras.BIZ_DISINFECT_RECORD);
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
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PICKED_DATE, pickedDate);
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
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

    private void renderMode() {
        String mode = vm.getMode().getValue();
        boolean view = DisinfectRegisterViewModel.MODE_VIEW.equals(mode);
        boolean edit = DisinfectRegisterViewModel.MODE_EDIT.equals(mode);
        binding.healthTopBar.baseToolbar.setTitle(view ? R.string.health_d08_title_view
                : edit ? R.string.health_d08_title_edit : R.string.health_d08_title);
        binding.healthTopBar.baseToolbar.getMenu().clear();
        if (view || edit) {
            binding.healthTopBar.baseToolbar.inflateMenu(R.menu.health_menu_history);
            TopAppBarHelper.tintMenu(binding.healthTopBar.baseToolbar);
        }
        boolean editable = vm.isEditable();
        for (int i = 0; i < binding.healthTypeGroup.getChildCount(); i++) {
            binding.healthTypeGroup.getChildAt(i).setEnabled(editable && !edit);
        }
        binding.healthTaskLayout.setEnabled(editable && !edit);
        binding.healthPenLayout.setEnabled(editable && !edit);
        binding.healthDisinfectantLayout.setEnabled(editable && !edit);
        binding.healthTimeLayout.setEnabled(editable);
        binding.healthExecutorLayout.setEnabled(editable);
        binding.healthLotLayout.setEnabled(editable);
        binding.healthUsageLayout.setEnabled(editable);
        binding.healthConcentrationLayout.setEnabled(editable);
        for (int i = 0; i < binding.healthMethodChips.getChildCount(); i++) {
            binding.healthMethodChips.getChildAt(i).setEnabled(editable);
        }
        boolean staff = vm.isStaff();
        binding.healthExecutorLayout.setVisibility(!staff && !view ? View.VISIBLE : View.GONE);
        binding.healthCreator.setVisibility(staff && !view ? View.VISIBLE : View.GONE);
        binding.healthPhotos.setVisibility(view ? View.GONE : View.VISIBLE);
        binding.healthPhotosView.setVisibility(view ? View.VISIBLE : View.GONE);
        renderActions();
        renderType();
        renderConcentration();
    }

    private void renderType() {
        String type = vm.getRecordType().getValue();
        boolean plan = vm.isPlanTask();
        binding.healthTypeGroup.check(HealthExtras.RECORD_EMPTY_PEN.equals(type) ? R.id.health_type_empty
                : HealthExtras.RECORD_EMERGENCY.equals(type) ? R.id.health_type_emergency : R.id.health_type_plan);
        binding.healthTaskBox.setVisibility(plan ? View.VISIBLE : View.GONE);
        binding.healthPenLayout.setVisibility(plan ? View.GONE : View.VISIBLE);
        boolean view = DisinfectRegisterViewModel.MODE_VIEW.equals(vm.getMode().getValue());
        binding.healthDisinfectantLayout.setVisibility(plan || view || vm.isEditMode() ? View.GONE : View.VISIBLE);
        binding.healthPenLayout.setHelperText(getString(HealthExtras.RECORD_EMERGENCY.equals(type)
                ? R.string.health_emergency_hint : R.string.health_empty_pen_hint));
        renderTask();
        renderPenField();
        renderConcentration();
    }

    private void renderTasks(@Nullable List<DisinfectTaskOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (DisinfectTaskOptionVO t : list) {
                OptionAdapter.Item item = new OptionAdapter.Item(t.getTaskId(), taskTitle(t), t.getPlanText(), false);
                if (t.isOverdue()) {
                    item.tag(getString(R.string.health_overdue), StatusTagView.ERROR);
                }
                items.add(item);
            }
        }
        taskAdapter.setItems(items);
        renderTask();
    }

    @NonNull
    private static String taskTitle(@NonNull DisinfectTaskOptionVO t) {
        return HealthFormat.nonNull(t.getPenName()) + HealthFormat.DOT + HealthFormat.nonNull(t.getDateText() != null
                ? t.getDateText() : HealthFormat.date(t.getTaskDate()));
    }

    private void renderTask() {
        DisinfectRecordVO r = vm.getRecord().getValue();
        DisinfectTaskOptionVO t = vm.getSelectedTask().getValue();
        if (t != null) {
            binding.healthTaskInput.setText(taskTitle(t), false);
        } else if (r != null && vm.isPlanTask()) {
            binding.healthTaskInput.setText(HealthFormat.nonNull(r.getPenName()) + HealthFormat.DOT
                    + HealthFormat.date(r.getTaskDate()), false);
        } else {
            HealthForms.showSelected(binding.healthTaskInput, taskAdapter, vm.getTaskId().getValue());
        }
        binding.healthTaskTag.setVisibility(t != null && t.isOverdue() ? View.VISIBLE : View.GONE);
        renderErrors(vm.getFieldErrors().getValue());
    }

    private void renderPens(@Nullable List<PenOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (PenOptionVO p : list) {
                items.add(new OptionAdapter.Item(p.getPenId(), HealthFormat.nonNull(p.getPenName()),
                        p.getStockCount() == 0 ? getString(R.string.health_pen_empty) : null, p.isDisabled()));
            }
        }
        penAdapter.setItems(items);
        renderPenField();
    }

    private void renderPenField() {
        DisinfectRecordVO r = vm.getRecord().getValue();
        if (r != null && penAdapter.findById(vm.getPenId().getValue()) == null) {
            binding.healthPenInput.setText(HealthFormat.nonNull(r.getPenName()), false);
        } else {
            HealthForms.showSelected(binding.healthPenInput, penAdapter, vm.getPenId().getValue());
        }
    }

    private void renderDisinfectants(@Nullable List<DisinfectantOptionVO> list) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (list != null) {
            for (DisinfectantOptionVO o : list) {
                items.add(new OptionAdapter.Item(o.getCatalogId(), HealthFormat.nonNull(o.getName()), o.getStockHintText(),
                        false));
            }
        }
        disinfectantAdapter.setItems(items);
        HealthForms.showSelected(binding.healthDisinfectantInput, disinfectantAdapter, vm.currentCatalogId());
        renderLot();
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

    private void renderMethods(@Nullable List<DictItemVO> list) {
        HealthForms.renderChoiceChips(binding.healthMethodChips, list, null, vm.getMethodCode().getValue(),
                vm::onMethodSelected);
        boolean editable = vm.isEditable();
        for (int i = 0; i < binding.healthMethodChips.getChildCount(); i++) {
            binding.healthMethodChips.getChildAt(i).setEnabled(editable);
        }
    }

    private void renderLot() {
        binding.healthLotInput.setText(vm.lotText());
        binding.healthUsageLayout.setSuffixText(vm.usageUnit());
        renderErrors(vm.getFieldErrors().getValue());
    }

    private void renderErrors(@Nullable Map<String, String> errors) {
        setError(binding.healthTaskLayout, errors == null ? null : errors.get(DisinfectRegisterViewModel.F_TASK),
                planHelper());
        setError(binding.healthPenLayout, errors == null ? null : errors.get(DisinfectRegisterViewModel.F_PEN),
                getString(HealthExtras.RECORD_EMERGENCY.equals(vm.getRecordType().getValue())
                        ? R.string.health_emergency_hint : R.string.health_empty_pen_hint));
        setError(binding.healthLotLayout, errors == null ? null : errors.get(DisinfectRegisterViewModel.F_LOT),
                vm.lotHelper());
        String concentrationError = errors == null ? null : errors.get(DisinfectRegisterViewModel.F_CONCENTRATION);
        binding.healthConcentrationLayout.setError(concentrationError);
        if (concentrationError == null) {
            binding.healthConcentrationLayout.setErrorEnabled(false);
            renderConcentration();
        }
    }

    @Nullable
    private String planHelper() {
        DisinfectTaskOptionVO t = vm.getSelectedTask().getValue();
        if (t != null) {
            return t.getPlanText();
        }
        DisinfectRecordVO r = vm.getRecord().getValue();
        if (r != null) {
            return r.getPlanText();
        }
        List<DisinfectTaskOptionVO> list = vm.getTaskOptions().getValue();
        return list != null && list.isEmpty() && vm.isEditable() ? getString(R.string.health_no_task_today) : null;
    }

    private static void setError(@NonNull TextInputLayout layout, @Nullable String error, @Nullable String helper) {
        layout.setError(error);
        if (error == null) {
            layout.setErrorEnabled(false);
            layout.setHelperText(TextUtils.isEmpty(helper) ? null : helper);
        }
    }

    /** 浓度与计划不一致：框内 warning 图标、辅助文字“计划浓度 1:300”（warning 色）、确认横幅；确认后改为已确认状态 */
    private void renderConcentration() {
        boolean mismatch = Boolean.TRUE.equals(vm.getConcentrationMismatch().getValue());
        boolean confirmed = Boolean.TRUE.equals(vm.getConcentrationConfirmed().getValue());
        String planned = vm.plannedConcentration();
        TextInputLayout layout = binding.healthConcentrationLayout;
        if (mismatch) {
            layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            layout.setEndIconDrawable(com.zhimu.slmp.base.R.drawable.ic_warning);
            int warning = MaterialColors.getColor(layout, com.zhimu.slmp.base.R.attr.colorWarning);
            layout.setEndIconTintList(ColorStateList.valueOf(warning));
            layout.setHelperText(getString(R.string.health_planned_concentration, HealthFormat.nonNull(planned)));
            layout.setHelperTextColor(ColorStateList.valueOf(warning));
        } else {
            layout.setEndIconMode(TextInputLayout.END_ICON_NONE);
            layout.setHelperText(null);
        }
        boolean show = mismatch && vm.isEditable();
        binding.healthConfirmNotice.setVisibility(show ? View.VISIBLE : View.GONE);
        String c = HealthFormat.nonNull(vm.getConcentration().getValue());
        if (confirmed) {
            binding.healthConfirmNotice.setIcon(com.zhimu.slmp.base.R.drawable.ic_check_circle);
            binding.healthConfirmNotice.setNotice(null, getString(R.string.health_concentration_confirmed, c));
            binding.healthConfirmNotice.setAction(null, null);
        } else {
            binding.healthConfirmNotice.setIcon(com.zhimu.slmp.base.R.drawable.ic_warning);
            binding.healthConfirmNotice.setNotice(getString(R.string.health_concentration_mismatch_title),
                    getString(R.string.health_concentration_mismatch_text, c));
            binding.healthConfirmNotice.setAction(getString(R.string.health_confirm), v -> vm.confirmConcentration());
        }
    }

    /** 未确认浓度就提交：横幅描边闪烁一次（300 毫秒） */
    private void flashNotice() {
        View notice = binding.healthConfirmNotice;
        notice.animate().alpha(0.4f).setDuration(150).withEndAction(() ->
                notice.animate().alpha(1f).setDuration(150).start()).start();
    }

    private void renderRecord(@Nullable DisinfectRecordVO r) {
        if (r == null) {
            return;
        }
        binding.healthPhotosView.setReadOnlyFiles(r.getPhotos(), getString(R.string.health_d08_title_view));
        renderMode();
        renderLot();
    }

    /** 底部：登记时“登记消毒”（照片上传中“等待照片上传”）；查看时按 actions */
    private void renderActions() {
        String mode = vm.getMode().getValue();
        if (DisinfectRegisterViewModel.MODE_VIEW.equals(mode)) {
            DisinfectRecordVO r = vm.getRecord().getValue();
            List<String> actions = r == null ? new ArrayList<>() : r.getActions();
            if (actions.contains(HealthExtras.ACTION_VIEW_REQUEST)) {
                primaryAction = HealthExtras.ACTION_VIEW_REQUEST;
                binding.healthActionBar.setPrimary(getString(R.string.health_view_change_request), 0);
            } else if (actions.contains(HealthExtras.ACTION_EDIT)) {
                primaryAction = HealthExtras.ACTION_EDIT;
                binding.healthActionBar.setPrimary(getString(R.string.health_edit_record),
                        com.zhimu.slmp.base.R.drawable.ic_edit);
            } else if (actions.contains(HealthExtras.ACTION_CHANGE_REQUEST) && !vm.isStaff()) {
                primaryAction = HealthExtras.ACTION_CHANGE_REQUEST;
                binding.healthActionBar.setPrimary(getString(R.string.health_change_request_review),
                        com.zhimu.slmp.base.R.drawable.ic_edit_note);
            } else {
                primaryAction = "";
            }
            binding.healthActionBar.setVisibility(primaryAction.isEmpty() ? View.GONE : View.VISIBLE);
            binding.healthActionBar.setPrimaryEnabled(true);
            return;
        }
        primaryAction = "SUBMIT";
        binding.healthActionBar.setVisibility(View.VISIBLE);
        boolean uploading = vm.getPhotos().hasUploading();
        if (uploading) {
            binding.healthActionBar.setPrimary(getString(R.string.health_wait_photos), 0);
        } else if (vm.isEditMode()) {
            binding.healthActionBar.setPrimary(getString(R.string.health_save_changes), 0);
        } else {
            binding.healthActionBar.setPrimary(getString(R.string.health_register_disinfect),
                    com.zhimu.slmp.base.R.drawable.ic_sanitizer);
        }
        binding.healthActionBar.setPrimaryEnabled(!uploading);
    }

    private void onPrimaryClick() {
        if ("SUBMIT".equals(primaryAction)) {
            vm.submit();
        } else if (!primaryAction.isEmpty()) {
            vm.onViewAction(primaryAction);
        }
    }

    private void pickDate() {
        LocalDateTime current = DateFormatter.parseDateTime(vm.getActualTime().getValue());
        LocalDate today = DateFormatter.today();
        LocalDate min = DateFormatter.parseDate(vm.minDate());
        DateTimePicker.date(REQ_DATE)
                .selection(current != null ? current.toLocalDate() : today)
                .bounds(min, today)
                .show(getSupportFragmentManager());
    }

    private void scrollTo(@Nullable String field) {
        View target;
        if (DisinfectRegisterViewModel.F_TASK.equals(field)) {
            target = binding.healthTaskBox;
        } else if (DisinfectRegisterViewModel.F_PEN.equals(field)) {
            target = binding.healthPenLayout;
        } else if (DisinfectRegisterViewModel.F_DRUG.equals(field)) {
            target = binding.healthDisinfectantLayout;
        } else if (DisinfectRegisterViewModel.F_TIME.equals(field)) {
            target = binding.healthTimeLayout;
        } else if (DisinfectRegisterViewModel.F_LOT.equals(field)) {
            target = binding.healthLotLayout;
        } else if (DisinfectRegisterViewModel.F_USAGE.equals(field)
                || DisinfectRegisterViewModel.F_CONCENTRATION.equals(field)) {
            target = binding.healthUsageLayout;
        } else if (DisinfectRegisterViewModel.F_CONFIRMED.equals(field)) {
            target = binding.healthConfirmNotice;
        } else {
            target = binding.healthMethodRow;
        }
        HealthForms.scrollTo(binding.healthScroll, target);
    }
}
