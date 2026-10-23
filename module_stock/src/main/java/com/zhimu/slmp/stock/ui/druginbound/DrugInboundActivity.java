package com.zhimu.slmp.stock.ui.druginbound;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
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
import com.zhimu.slmp.stock.databinding.StockActivityDrugInboundBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.ui.common.ChangeLogActivity;
import com.zhimu.slmp.stock.ui.common.FormMode;
import com.zhimu.slmp.stock.ui.common.PickerData;
import com.zhimu.slmp.stock.ui.common.PickerHost;
import com.zhimu.slmp.stock.ui.common.PickerSheet;
import com.zhimu.slmp.stock.ui.common.RecordMenu;
import com.zhimu.slmp.stock.ui.common.StockFormViewModel;
import com.zhimu.slmp.stock.ui.common.StockNav;
import com.zhimu.slmp.stock.ui.common.StockUi;

import java.time.LocalDate;

/**
 * M08 兽药与疫苗入库 / 入库单（只读）/ 修改入库单。药品面板“新建药品档案”以 Activity Result 打开 M09，返回 drug_id 后自动选中。
 */
public class DrugInboundActivity extends BaseActivity implements PickerHost, RecordMenu.Callback {

    private static final String REQ_DISCARD = "stock_m08_discard";
    private static final String REQ_VOID = "stock_m08_void";
    private static final String REQ_NEED_REQUEST = "stock_m08_need_request";
    private static final String REQ_DATE = "stock_m08_date";
    private static final String EXTRA_FIELD = "field";

    private StockActivityDrugInboundBinding binding;
    private DrugInboundViewModel vm;
    private ActivityResultLauncher<Intent> archiveLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.stock_activity_drug_inbound);
        vm = obtainViewModel(StockInjector.factory(), DrugInboundViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        archiveLauncher = Router.registerForResult(this, r -> {
            if (r.getResultCode() == Activity.RESULT_OK && r.getData() != null) {
                long id = r.getData().getLongExtra(StockExtras.RESULT_DRUG_ID, 0);
                if (id > 0) {
                    vm.onArchiveCreated(id);
                }
            }
        });

        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar, getString(R.string.stock_drug_inbound_title));
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
        vm.getRecord().observe(this, r -> renderMode());
        vm.getReadonlyRows().observe(this, rows -> StockUi.renderKv(binding.stockReadonlyRows, rows));
        binding.stockFinanceRow.setOnClickListener(v -> Router.open(this, RouterPath.E04));
        binding.stockFinanceRow.setShowChevron(true);
        vm.getExpireHint().observe(this, hint -> binding.stockExpireLayout.setHelperTextColor(ColorStateList.valueOf(
                MaterialColors.getColor(binding.stockExpireLayout, hint != null
                        ? com.zhimu.slmp.base.R.attr.colorOnWarningContainer
                        : com.google.android.material.R.attr.colorOnSurfaceVariant))));

        ClickThrottle.setOnClickListener(binding.stockDrugInput, v -> showDrugPicker());
        binding.stockDrugLayout.setEndIconOnClickListener(v -> showDrugPicker());
        ClickThrottle.setOnClickListener(binding.stockDateInput, v -> pickDate(DrugInboundViewModel.F_DATE));
        binding.stockDateLayout.setEndIconOnClickListener(v -> pickDate(DrugInboundViewModel.F_DATE));
        ClickThrottle.setOnClickListener(binding.stockExpireInput, v -> pickDate(DrugInboundViewModel.F_EXPIRE));
        binding.stockExpireLayout.setEndIconOnClickListener(v -> pickDate(DrugInboundViewModel.F_EXPIRE));
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this, (k, r) -> {
            Bundle extras = DateTimePicker.getExtras(r);
            vm.onDatePicked(extras == null ? DrugInboundViewModel.F_DATE
                    : extras.getString(EXTRA_FIELD, DrugInboundViewModel.F_DATE), DateTimePicker.getDate(r));
        });
        binding.stockSync.stockSyncCard.setOnClickListener(v -> vm.toggleSync());

        binding.stockActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getSubmitting().observe(this, s -> binding.stockActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getFocusField().observe(this, this::focusField);

        getSupportFragmentManager().setFragmentResultListener(DrugInboundViewModel.PICKER_DRUG, this, (k, r) -> {
            if (PickerSheet.EVENT_FOOTER.equals(PickerSheet.eventOf(r))) {
                vm.onArchiveNew();
            } else if (PickerSheet.valueOf(r) != null) {
                vm.onDrugPicked(Long.parseLong(PickerSheet.valueOf(r)));
            }
        });
        vm.getOpenArchiveNew().observe(this, kw -> {
            Bundle b = new Bundle();
            b.putString(RouterPath.EXTRA_KEYWORD, kw);
            StockNav.open(this, archiveLauncher, RouterPath.M09, b);
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.onDiscardConfirmed();
                if (!vm.cancelEdit()) {
                    finish();
                }
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_VOID, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.voidRecord();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_NEED_REQUEST, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.onRequestChange();
            }
        });
        vm.getShowDiscard().observe(this, v -> StockUi.showDiscard(this, REQ_DISCARD));
        vm.getSubmitted().observe(this, msg -> StockNav.finishWithMessage(this, msg));
        vm.getVoided().observe(this, msg -> StockNav.finishWithMessage(this, UiMessage.of(msg)));
        vm.getConfirmVoid().observe(this, text -> ConfirmDialogFragment.builder(REQ_VOID)
                .title(getString(R.string.stock_inbound_void_title))
                .message(text)
                .negative(getString(R.string.stock_action_cancel))
                .positive(getString(R.string.stock_action_void))
                .danger(true)
                .build().showSafely(getSupportFragmentManager()));
        vm.getNeedRequest().observe(this, text -> ConfirmDialogFragment.builder(REQ_NEED_REQUEST)
                .title(text)
                .message(getString(R.string.stock_need_request_message))
                .negative(getString(R.string.stock_action_cancel))
                .positive(getString(R.string.stock_menu_request_change))
                .build().showSafely(getSupportFragmentManager()));
        vm.getOpenChangeRequest().observe(this, id -> Router.open(this, RouterPath.U01,
                StockNav.changeArgs(StockEnums.BIZ_INBOUND, id)));
        vm.getOpenRequest().observe(this, id -> Router.open(this, RouterPath.U02, StockNav.requestArgs(id)));
        vm.getOpenChangeLog().observe(this, id -> startActivity(ChangeLogActivity.intent(this,
                StockEnums.BIZ_INBOUND, id)));
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
        return vm.getDrugPicker();
    }

    @Override
    public void onPickerSearch(@NonNull String pickerKey, @NonNull String keyword) {
        vm.searchDrugs(keyword);
    }

    private void renderMode() {
        String mode = vm.getMode().getValue();
        UiState state = vm.getUiState().getValue();
        binding.stockState.render(state);
        boolean content = state != null && state.is(UiState.Status.CONTENT);
        boolean view = FormMode.VIEW.equals(mode);
        boolean edit = FormMode.EDIT.equals(mode);
        binding.stockTopBar.baseToolbar.setTitle(view ? R.string.stock_inbound_record_title
                : edit ? R.string.stock_inbound_edit_title : R.string.stock_drug_inbound_title);
        binding.stockTopBar.baseToolbar.getMenu().findItem(R.id.stock_menu_more)
                .setVisible(content && view && RecordMenu.hasItems(vm.actions(), true));
        binding.stockActionBar.setVisibility(content && !view ? View.VISIBLE : View.GONE);
        binding.stockActionBar.setPrimary(getString(edit ? R.string.stock_action_save_edit
                : R.string.stock_inbound_confirm), com.zhimu.slmp.base.R.drawable.ic_check);
    }

    private void showDrugPicker() {
        if (!FormMode.NEW.equals(vm.getMode().getValue())) {
            return;
        }
        PickerSheet.builder(DrugInboundViewModel.PICKER_DRUG)
                .title(getString(R.string.stock_pick_drug_title))
                .search(getString(R.string.stock_search_name_hint), vm.keyword())
                .footer(getString(R.string.stock_drug_archive_create))
                .build().showSafely(getSupportFragmentManager());
    }

    private void pickDate(@NonNull String field) {
        String current = DrugInboundViewModel.F_EXPIRE.equals(field) ? vm.getExpireDate().getValue()
                : vm.getInboundDate().getValue();
        LocalDate d = DateFormatter.parseDate(current);
        Bundle extras = new Bundle();
        extras.putString(EXTRA_FIELD, field);
        DateTimePicker.date(REQ_DATE)
                .selection(d != null ? d : DateFormatter.today())
                .bounds(null, DrugInboundViewModel.F_EXPIRE.equals(field) ? null : DateFormatter.today())
                .extras(extras)
                .show(getSupportFragmentManager());
    }

    private void focusField(@Nullable String field) {
        View target = DrugInboundViewModel.F_DRUG.equals(field) ? binding.stockDrugLayout
                : DrugInboundViewModel.F_LOT.equals(field) ? binding.stockLotInput
                : DrugInboundViewModel.F_DATE.equals(field) ? binding.stockDateLayout
                : DrugInboundViewModel.F_EXPIRE.equals(field) ? binding.stockExpireLayout
                : DrugInboundViewModel.F_QTY.equals(field) ? binding.stockQtyInput
                : DrugInboundViewModel.F_PRICE.equals(field) ? binding.stockPriceInput : null;
        StockUi.scrollTo(binding.stockScroll, target);
    }
}
