package com.zhimu.slmp.stock.ui.drugoutbound;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.databinding.StockActivityDrugOutboundBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.CostAllocationVO;
import com.zhimu.slmp.stock.model.vo.StockBatchOptionVO;
import com.zhimu.slmp.stock.model.vo.StockMemberVO;
import com.zhimu.slmp.stock.model.vo.StockPenOptionVO;
import com.zhimu.slmp.common.ui.recordlog.RecordLogSheet;
import com.zhimu.slmp.stock.ui.common.FormMode;
import com.zhimu.slmp.stock.ui.common.PickerData;
import com.zhimu.slmp.stock.ui.common.PickerHost;
import com.zhimu.slmp.stock.ui.common.PickerSheet;
import com.zhimu.slmp.stock.ui.common.RecordMenu;
import com.zhimu.slmp.stock.ui.common.StockFormViewModel;
import com.zhimu.slmp.stock.ui.common.StockNav;
import com.zhimu.slmp.stock.ui.common.StockUi;
import com.zhimu.slmp.stock.util.StockCalc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * M11 兽药与疫苗出库 / 领用记录 / 退货记录 / 修改。成功后带全局提示 RESULT_OK 返回来源页（M06 或 M05）。
 */
public class DrugOutboundActivity extends BaseActivity implements PickerHost, RecordMenu.Callback {

    private static final String REQ_DISCARD = "stock_m11_discard";
    private static final String REQ_SCRAP_DISCARD = "stock_m11_scrap_discard";
    private static final String REQ_VOID = "stock_m11_void";
    private static final String REQ_NOT_TODAY = "stock_m11_not_today";
    private static final String REQ_DATE = "stock_m11_date";
    private static final String REQ_TARGET = "stock_m11_target";
    private static final String REQ_RECEIVER = "stock_m11_receiver";
    private static final String KEY_LOT = "lot_id";

    private StockActivityDrugOutboundBinding binding;
    private DrugOutboundViewModel vm;
    private ArrayAdapter<String> returnToAdapter;
    private View allocForm;
    private View allocView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.stock_activity_drug_outbound);
        vm = obtainViewModel(StockInjector.factory(), DrugOutboundViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        allocForm = binding.getRoot().findViewById(R.id.stock_alloc_form);
        allocView = binding.getRoot().findViewById(R.id.stock_alloc_view);

        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar, getString(R.string.stock_outbound_title));
        binding.stockTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.stockTopBar.baseToolbar.inflateMenu(R.menu.stock_menu_more);
        TopAppBarHelper.tintMenu(binding.stockTopBar.baseToolbar);
        binding.stockTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            View anchor = binding.stockTopBar.baseToolbar.findViewById(R.id.stock_menu_more);
            RecordMenu.show(anchor != null ? anchor : binding.stockTopBar.baseToolbar, vm.actions(), true, this);
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (vm.onBack()) {
                    return;
                }
                if (!vm.cancelEdit()) {
                    finish();
                }
            }
        });

        binding.stockState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, s -> renderMode());
        vm.getMode().observe(this, m -> renderMode());
        vm.getOutType().observe(this, t -> {
            int id = StockEnums.OUT_RETURN.equals(t) ? R.id.stock_type_return : R.id.stock_type_use;
            if (binding.stockTypeGroup.getCheckedButtonId() != id) {
                binding.stockTypeGroup.check(id);
            }
            renderMode();
        });
        binding.stockTypeGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) {
                vm.setOutType(id == R.id.stock_type_return ? StockEnums.OUT_RETURN : StockEnums.OUT_USE);
            }
        });
        vm.getTargetType().observe(this, t -> {
            int id = StockEnums.TARGET_PEN.equals(t) ? R.id.stock_target_pen : R.id.stock_target_batch;
            if (binding.stockTargetGroup.getCheckedButtonId() != id) {
                binding.stockTargetGroup.check(id);
            }
        });
        binding.stockTargetGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) {
                vm.setTargetType(id == R.id.stock_target_pen ? StockEnums.TARGET_PEN : StockEnums.TARGET_BATCH);
            }
        });
        vm.getReadonlyRows().observe(this, rows -> StockUi.renderKv(binding.stockReadonlyRows, rows));
        vm.getAllocation().observe(this, a -> renderAllocation());
        vm.getReturnAmount().observe(this, t -> renderAllocation());
        buildReasons();
        vm.getReturnReason().observe(this, this::syncReason);

        ClickThrottle.setOnClickListener(binding.stockDrugInput, v -> showDrugPicker());
        binding.stockDrugLayout.setEndIconOnClickListener(v -> showDrugPicker());
        ClickThrottle.setOnClickListener(binding.stockLotInput, v -> showLotPicker());
        binding.stockLotLayout.setEndIconOnClickListener(v -> showLotPicker());
        ClickThrottle.setOnClickListener(binding.stockTargetInput, v -> showTargets());
        binding.stockTargetLayout.setEndIconOnClickListener(v -> showTargets());
        ClickThrottle.setOnClickListener(binding.stockReceiverInput, v -> showReceivers());
        binding.stockReceiverLayout.setEndIconOnClickListener(v -> showReceivers());
        ClickThrottle.setOnClickListener(binding.stockDateInput, v -> pickDate());
        binding.stockDateLayout.setEndIconOnClickListener(v -> pickDate());
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (k, r) -> vm.onDatePicked(DateTimePicker.getDate(r)));
        getSupportFragmentManager().setFragmentResultListener(REQ_TARGET, this, (k, r) -> {
            String v = OptionSheetDialogFragment.getValue(r);
            if (v == null) {
                return;
            }
            if (StockEnums.TARGET_PEN.equals(vm.getTargetType().getValue())) {
                vm.onPenPicked(Long.parseLong(v));
            } else {
                vm.onBatchPicked(Long.parseLong(v));
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_RECEIVER, this, (k, r) -> {
            String v = OptionSheetDialogFragment.getValue(r);
            if (v != null) {
                vm.onReceiverPicked(Long.parseLong(v));
            }
        });
        getSupportFragmentManager().setFragmentResultListener(DrugOutboundViewModel.PICKER_DRUG, this, (k, r) -> {
            String v = PickerSheet.valueOf(r);
            if (v != null) {
                vm.onDrugPicked(Long.parseLong(v));
            }
        });
        getSupportFragmentManager().setFragmentResultListener(DrugOutboundViewModel.PICKER_LOT, this, (k, r) -> {
            String v = PickerSheet.valueOf(r);
            if (v == null) {
                return;
            }
            if (PickerSheet.EVENT_ACTION.equals(PickerSheet.eventOf(r))) {
                vm.onGoScrap(Long.parseLong(v));
            } else {
                vm.onLotPicked(Long.parseLong(v));
            }
        });

        returnToAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        binding.stockReturnToInput.setAdapter(returnToAdapter);
        binding.stockReturnToInput.setOnFocusChangeListener((v, focus) -> {
            if (focus) {
                vm.queryReturnTo(String.valueOf(binding.stockReturnToInput.getText()));
            }
        });
        vm.getReturnTo().observe(this, t -> {
            if (binding.stockReturnToInput.hasFocus()) {
                vm.queryReturnTo(t);
            }
        });
        vm.getReturnToSuggest().observe(this, list -> {
            returnToAdapter.clear();
            if (list != null) {
                returnToAdapter.addAll(list);
            }
            returnToAdapter.notifyDataSetChanged();
            if (binding.stockReturnToInput.hasFocus() && list != null && !list.isEmpty()) {
                binding.stockReturnToInput.showDropDown();
            }
        });

        binding.stockActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getSubmitting().observe(this, s -> binding.stockActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getFocusField().observe(this, this::focusField);

        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.onDiscardConfirmed();
                if (!vm.cancelEdit()) {
                    finish();
                }
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_SCRAP_DISCARD, this, (k, r) -> {
            Bundle extras = ConfirmDialogFragment.getExtras(r);
            if (ConfirmDialogFragment.isConfirmed(r) && extras != null) {
                vm.onDiscardForScrap(extras.getLong(KEY_LOT));
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_VOID, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.voidRecord();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_NOT_TODAY, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.onRequestChange();
            }
        });
        vm.getShowDiscard().observe(this, v -> StockUi.showDiscard(this, REQ_DISCARD));
        vm.getConfirmDiscard().observe(this, lotId -> {
            Bundle extras = new Bundle();
            extras.putLong(KEY_LOT, lotId);
            ConfirmDialogFragment.builder(REQ_SCRAP_DISCARD)
                    .title(getString(R.string.stock_discard_title))
                    .message(getString(R.string.stock_discard_message))
                    .negative(getString(R.string.stock_action_keep_editing))
                    .positive(getString(R.string.stock_action_discard))
                    .extras(extras)
                    .build().showSafely(getSupportFragmentManager());
        });
        vm.getOpenScrap().observe(this, lotId -> {
            Router.open(this, RouterPath.M07, StockNav.idArgs(RouterPath.EXTRA_LOT_ID, lotId));
            finish();
        });
        vm.getSubmitted().observe(this, msg -> StockNav.finishWithMessage(this, UiMessage.of(msg)));
        vm.getVoided().observe(this, msg -> StockNav.finishWithMessage(this, UiMessage.of(msg)));
        vm.getEdited().observe(this, v -> setResult(Activity.RESULT_OK));
        vm.getConfirmVoid().observe(this, text -> ConfirmDialogFragment.builder(REQ_VOID)
                .title(getString(R.string.stock_out_void_title))
                .message(text)
                .negative(getString(R.string.stock_action_cancel))
                .positive(getString(R.string.stock_action_void))
                .danger(true)
                .build().showSafely(getSupportFragmentManager()));
        vm.getNotToday().observe(this, text -> ConfirmDialogFragment.builder(REQ_NOT_TODAY)
                .title(text == null || text.isEmpty() ? getString(R.string.stock_not_today_title) : text)
                .message(getString(R.string.stock_need_request_message))
                .negative(getString(R.string.stock_action_cancel))
                .positive(getString(R.string.stock_menu_request_change))
                .build().showSafely(getSupportFragmentManager()));
        vm.getOpenChangeRequest().observe(this, id -> Router.open(this, RouterPath.U01,
                StockNav.changeArgs(StockEnums.BIZ_OUTBOUND, id)));
        vm.getOpenRequest().observe(this, id -> Router.open(this, RouterPath.U02, StockNav.requestArgs(id)));
        vm.getOpenChangeLog().observe(this, id -> RecordLogSheet.show(getSupportFragmentManager(),
                StockEnums.BIZ_OUTBOUND, id, null));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            vm.saveDraft();
        }
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        switch (actionId) {
            case StockFormViewModel.ACTION_RESTORE_DRAFT:
                vm.restoreDraft();
                break;
            case StockFormViewModel.ACTION_RETRY:
                vm.retrySubmit();
                break;
            case StockFormViewModel.ACTION_REFRESH:
                vm.reloadRecord();
                break;
            default:
                break;
        }
    }

    @Override
    public void onRecordMenu(int id) {
        switch (id) {
            case RecordMenu.EDIT:
                vm.startEdit();
                break;
            case RecordMenu.VOID:
                vm.onVoidMenu();
                break;
            case RecordMenu.REQUEST:
                vm.onRequestChange();
                break;
            case RecordMenu.VIEW_REQUEST:
                vm.onViewRequest();
                break;
            default:
                vm.onChangeLog();
                break;
        }
    }

    @Nullable
    @Override
    public LiveData<PickerData> pickerData(@NonNull String pickerKey) {
        return DrugOutboundViewModel.PICKER_LOT.equals(pickerKey) ? vm.getLotPicker() : vm.getDrugPicker();
    }

    @Override
    public void onPickerSearch(@NonNull String pickerKey, @NonNull String keyword) {
        if (DrugOutboundViewModel.PICKER_DRUG.equals(pickerKey)) {
            vm.searchDrugs(keyword);
        }
    }

    private void renderMode() {
        String mode = vm.getMode().getValue();
        UiState state = vm.getUiState().getValue();
        binding.stockState.render(state);
        boolean content = state != null && state.is(UiState.Status.CONTENT);
        boolean view = FormMode.VIEW.equals(mode);
        boolean edit = FormMode.EDIT.equals(mode);
        boolean use = vm.isUse();
        int title = view ? (use ? R.string.stock_outbound_use_record : R.string.stock_outbound_return_record)
                : edit ? (use ? R.string.stock_outbound_use_edit : R.string.stock_outbound_return_edit)
                : R.string.stock_outbound_title;
        binding.stockTopBar.baseToolbar.setTitle(title);
        binding.stockTopBar.baseToolbar.getMenu().findItem(R.id.stock_menu_more)
                .setVisible(content && view && RecordMenu.hasItems(vm.actions(), true));
        binding.stockActionBar.setVisibility(content && !view ? View.VISIBLE : View.GONE);
        if (edit) {
            binding.stockActionBar.setPrimary(getString(R.string.stock_action_save_edit),
                    com.zhimu.slmp.base.R.drawable.ic_check);
        } else if (use) {
            binding.stockActionBar.setPrimary(getString(R.string.stock_outbound_use_confirm), R.drawable.stock_ic_output);
        } else {
            binding.stockActionBar.setPrimary(getString(R.string.stock_outbound_return_confirm),
                    R.drawable.stock_ic_assignment_return);
        }
        binding.stockReturnAmount.getRoot().setVisibility(use ? View.GONE : View.VISIBLE);
        renderAllocation();
    }

    /** 成本分摊卡：领用时显示；数量为空时金额“—”，不显示分摊行 */
    private void renderAllocation() {
        boolean use = vm.isUse();
        boolean view = FormMode.VIEW.equals(vm.getMode().getValue());
        CostAllocationVO a = vm.getAllocation().getValue();
        bindAllocation(allocForm, a, use && !view);
        bindAllocation(allocView, a, use && view && a != null && a.rows != null && !a.rows.isEmpty());
    }

    private void bindAllocation(@Nullable View card, @Nullable CostAllocationVO a, boolean visible) {
        if (card == null) {
            return;
        }
        card.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            return;
        }
        TextView total = card.findViewById(R.id.stock_alloc_total);
        LinearLayout rows = card.findViewById(R.id.stock_alloc_rows);
        View divider = card.findViewById(R.id.stock_alloc_divider);
        boolean hasRows = a != null && a.rows != null && !a.rows.isEmpty();
        total.setText(hasRows && a.totalAmount != null ? StockCalc.money(a.totalAmount)
                : StockCalc.money(vm.localAmount()));
        divider.setVisibility(hasRows ? View.VISIBLE : View.GONE);
        rows.removeAllViews();
        if (!hasRows) {
            return;
        }
        LayoutInflater inf = getLayoutInflater();
        for (String[] r : DrugOutboundViewModel.allocationRows(a)) {
            LinearLayout line = (LinearLayout) inf.inflate(R.layout.stock_item_alloc_row, rows, false);
            ((TextView) line.findViewById(R.id.stock_alloc_left)).setText(r[0]);
            ((TextView) line.findViewById(R.id.stock_alloc_right)).setText(r[1]);
            line.setAlpha(0f);
            rows.addView(line, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            line.animate().alpha(1f).setDuration(150).start();
        }
    }

    private void buildReasons() {
        binding.stockReasonChips.removeAllViews();
        for (String[] r : DrugOutboundViewModel.RETURN_REASONS) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.stock_item_filter_chip, binding.stockReasonChips,
                    false);
            chip.setId(View.generateViewId());
            chip.setText(r[1]);
            chip.setTag(r[0]);
            chip.setCheckable(true);
            binding.stockReasonChips.addView(chip);
        }
        binding.stockReasonChips.setOnCheckedStateChangeListener((group, ids) -> {
            String reason = ids.isEmpty() ? null : (String) group.findViewById(ids.get(0)).getTag();
            String cur = vm.getReturnReason().getValue();
            if (reason == null ? cur != null : !reason.equals(cur)) {
                vm.onReturnReasonPicked(reason);
            }
        });
    }

    private void syncReason(@Nullable String reason) {
        for (int i = 0; i < binding.stockReasonChips.getChildCount(); i++) {
            Chip c = (Chip) binding.stockReasonChips.getChildAt(i);
            boolean want = c.getTag() != null && c.getTag().equals(reason);
            if (c.isChecked() != want) {
                c.setChecked(want);
            }
        }
    }

    private void showDrugPicker() {
        if (!FormMode.NEW.equals(vm.getMode().getValue())) {
            return;
        }
        PickerSheet.builder(DrugOutboundViewModel.PICKER_DRUG)
                .title(getString(R.string.stock_pick_drug_title))
                .search(getString(R.string.stock_search_name_hint), vm.drugKeyword())
                .build().showSafely(getSupportFragmentManager());
    }

    private void showLotPicker() {
        if (!FormMode.NEW.equals(vm.getMode().getValue())) {
            return;
        }
        if (vm.getSelectedDrug().getValue() == null) {
            showMessage(getString(R.string.stock_pick_drug_first));
            return;
        }
        PickerSheet.builder(DrugOutboundViewModel.PICKER_LOT)
                .title(getString(R.string.stock_pick_lot_title))
                .description(vm.lotPickerSubtitle())
                .build().showSafely(getSupportFragmentManager());
    }

    private void showTargets() {
        List<OptionSheetDialogFragment.Option> options = new ArrayList<>();
        boolean pen = StockEnums.TARGET_PEN.equals(vm.getTargetType().getValue());
        if (pen) {
            List<StockPenOptionVO> list = vm.getPens().getValue();
            if (list != null) {
                for (StockPenOptionVO p : list) {
                    boolean empty = p.stockCount <= 0 || p.disabled;
                    int n = vm.batchCountInPen(p.penId);
                    String unit = p.unitText == null ? "头" : p.unitText;
                    String hint = empty ? getString(R.string.stock_pen_empty)
                            : "在栏 " + p.stockCount + " " + unit + (n > 0 ? " · " + n + " 个批次" : "");
                    options.add(new OptionSheetDialogFragment.Option(String.valueOf(p.penId), p.penName, hint)
                            .disabled(empty));
                }
            }
        } else {
            List<StockBatchOptionVO> list = vm.getBatches().getValue();
            if (list != null) {
                for (StockBatchOptionVO b : list) {
                    if (b.currentCount <= 0) {
                        continue;
                    }
                    options.add(new OptionSheetDialogFragment.Option(String.valueOf(b.batchId),
                            DrugOutboundViewModel.batchLabel(b),
                            "存栏 " + b.currentCount + " " + (b.unitText == null ? "头" : b.unitText)));
                }
            }
        }
        Long sel = pen ? vm.getPenId().getValue() : vm.getBatchId().getValue();
        OptionSheetDialogFragment.builder(REQ_TARGET)
                .title(getString(pen ? R.string.stock_target_pen : R.string.stock_target_batch))
                .options(options)
                .selected(sel == null ? null : String.valueOf(sel))
                .build().showSafely(getSupportFragmentManager());
    }

    private void showReceivers() {
        List<OptionSheetDialogFragment.Option> options = new ArrayList<>();
        List<StockMemberVO> list = vm.getMembers().getValue();
        if (list != null) {
            for (StockMemberVO m : list) {
                options.add(new OptionSheetDialogFragment.Option(String.valueOf(m.userId), m.name, m.roleText));
            }
        }
        Long sel = vm.getReceiverId().getValue();
        OptionSheetDialogFragment.builder(REQ_RECEIVER)
                .title(getString(R.string.stock_field_receiver))
                .options(options)
                .selected(sel == null ? null : String.valueOf(sel))
                .build().showSafely(getSupportFragmentManager());
    }

    private void pickDate() {
        LocalDate d = DateFormatter.parseDate(vm.getOutDate().getValue());
        DateTimePicker.date(REQ_DATE)
                .selection(d != null ? d : DateFormatter.today())
                .bounds(null, DateFormatter.today())
                .show(getSupportFragmentManager());
    }

    private void focusField(@Nullable String field) {
        View target = DrugOutboundViewModel.F_DRUG.equals(field) ? binding.stockDrugLayout
                : DrugOutboundViewModel.F_LOT.equals(field) ? binding.stockLotLayout
                : DrugOutboundViewModel.F_QTY.equals(field) ? binding.stockQtyInput
                : DrugOutboundViewModel.F_BATCH.equals(field) || DrugOutboundViewModel.F_PEN.equals(field)
                ? binding.stockTargetLayout
                : DrugOutboundViewModel.F_RECEIVER.equals(field) ? binding.stockReceiverLayout
                : DrugOutboundViewModel.F_RETURN_TO.equals(field) ? binding.stockReturnToInput
                : DrugOutboundViewModel.F_REASON.equals(field) ? binding.stockReasonChips
                : DrugOutboundViewModel.F_REMARK.equals(field) ? binding.stockRemarkInput : binding.stockDateLayout;
        StockUi.scrollTo(binding.stockScroll, target);
    }
}
