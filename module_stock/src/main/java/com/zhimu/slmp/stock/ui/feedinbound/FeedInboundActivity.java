package com.zhimu.slmp.stock.ui.feedinbound;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.databinding.StockActivityFeedInboundBinding;
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
import java.util.ArrayList;

/**
 * M03 饲料入库 / 入库单（只读）/ 修改入库单。成功后带全局提示 RESULT_OK 返回来源页。
 */
public class FeedInboundActivity extends BaseActivity implements PickerHost, RecordMenu.Callback {

    private static final String REQ_DISCARD = "stock_m03_discard";
    private static final String REQ_VOID = "stock_m03_void";
    private static final String REQ_NEED_REQUEST = "stock_m03_need_request";
    private static final String REQ_DATE = "stock_m03_date";
    private static final String EXTRA_FIELD = "field";

    private StockActivityFeedInboundBinding binding;
    private FeedInboundViewModel vm;
    private ArrayAdapter<String> supplierAdapter;
    private ActivityResultLauncher<Intent> archiveLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.stock_activity_feed_inbound);
        vm = obtainViewModel(StockInjector.factory(), FeedInboundViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        archiveLauncher = Router.registerForResult(this, r -> {
            if (r.getResultCode() == Activity.RESULT_OK) {
                vm.reloadOptions();
            }
        });

        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar, getString(R.string.stock_inbound_title));
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

        ClickThrottle.setOnClickListener(binding.stockFeedInput, v -> showFeedPicker());
        binding.stockFeedLayout.setEndIconOnClickListener(v -> showFeedPicker());
        bindDate(binding.stockProductionInput, binding.stockProductionLayout, FeedInboundViewModel.F_PRODUCTION);
        bindDate(binding.stockShelfInput, binding.stockShelfLayout, FeedInboundViewModel.F_SHELF);
        bindDate(binding.stockDateInput, binding.stockDateLayout, FeedInboundViewModel.F_DATE);
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this, (k, r) -> {
            Bundle extras = DateTimePicker.getExtras(r);
            vm.onDatePicked(extras == null ? FeedInboundViewModel.F_DATE : extras.getString(EXTRA_FIELD,
                    FeedInboundViewModel.F_DATE), DateTimePicker.getDate(r));
        });
        binding.stockSync.stockSyncCard.setOnClickListener(v -> vm.toggleSync());

        supplierAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        binding.stockSupplierInput.setAdapter(supplierAdapter);
        binding.stockSupplierInput.setOnFocusChangeListener((v, focus) -> {
            if (focus) {
                vm.querySuppliers(binding.stockSupplierInput.getText() == null ? ""
                        : binding.stockSupplierInput.getText().toString());
            }
        });
        vm.getSupplierName().observe(this, t -> {
            if (binding.stockSupplierInput.hasFocus()) {
                vm.querySuppliers(t);
            }
        });
        vm.getSupplierSuggest().observe(this, list -> {
            supplierAdapter.clear();
            if (list != null) {
                supplierAdapter.addAll(list);
            }
            supplierAdapter.notifyDataSetChanged();
            if (binding.stockSupplierInput.hasFocus() && list != null && !list.isEmpty()) {
                binding.stockSupplierInput.showDropDown();
            }
        });

        binding.stockActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getSubmitting().observe(this, s -> binding.stockActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getFocusField().observe(this, this::focusField);

        getSupportFragmentManager().setFragmentResultListener(FeedInboundViewModel.PICKER_FEED, this, (k, r) -> {
            if (PickerSheet.EVENT_FOOTER.equals(PickerSheet.eventOf(r))) {
                StockNav.open(this, archiveLauncher, RouterPath.M02, null);
            } else if (PickerSheet.valueOf(r) != null) {
                vm.onFeedPicked(Long.parseLong(PickerSheet.valueOf(r)));
            }
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
        vm.getVoided().observe(this, msg -> StockNav.finishWithMessage(this,
                com.zhimu.slmp.base.mvvm.UiMessage.of(msg)));
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
        return vm.getFeedPicker();
    }

    @Override
    public void onPickerSearch(@NonNull String pickerKey, @NonNull String keyword) {
    }

    private void renderMode() {
        String mode = vm.getMode().getValue();
        UiState state = vm.getUiState().getValue();
        binding.stockState.render(state);
        boolean content = state != null && state.is(UiState.Status.CONTENT);
        boolean view = FormMode.VIEW.equals(mode);
        boolean edit = FormMode.EDIT.equals(mode);
        binding.stockTopBar.baseToolbar.setTitle(view ? R.string.stock_inbound_record_title
                : edit ? R.string.stock_inbound_edit_title : R.string.stock_inbound_title);
        binding.stockTopBar.baseToolbar.getMenu().findItem(R.id.stock_menu_more)
                .setVisible(content && view && RecordMenu.hasItems(vm.actions(), true));
        binding.stockActionBar.setVisibility(content && !view ? View.VISIBLE : View.GONE);
        binding.stockActionBar.setPrimary(getString(edit ? R.string.stock_action_save_edit
                : R.string.stock_inbound_confirm), com.zhimu.slmp.base.R.drawable.ic_check);
    }

    private void showFeedPicker() {
        if (!FormMode.NEW.equals(vm.getMode().getValue())) {
            return;
        }
        PickerSheet.builder(FeedInboundViewModel.PICKER_FEED)
                .title(getString(R.string.stock_pick_feed_title))
                .footer(getString(R.string.stock_feed_add))
                .build().showSafely(getSupportFragmentManager());
    }

    private void bindDate(@NonNull View input, @NonNull com.google.android.material.textfield.TextInputLayout layout,
                          @NonNull String field) {
        ClickThrottle.setOnClickListener(input, v -> pickDate(field));
        layout.setEndIconOnClickListener(v -> pickDate(field));
    }

    private void pickDate(@NonNull String field) {
        String current = FeedInboundViewModel.F_PRODUCTION.equals(field) ? vm.getProductionDate().getValue()
                : FeedInboundViewModel.F_SHELF.equals(field) ? vm.getShelfLifeDate().getValue()
                : vm.getInboundDate().getValue();
        LocalDate d = DateFormatter.parseDate(current);
        Bundle extras = new Bundle();
        extras.putString(EXTRA_FIELD, field);
        DateTimePicker.date(REQ_DATE)
                .selection(d != null ? d : DateFormatter.today())
                .bounds(null, FeedInboundViewModel.F_SHELF.equals(field) ? null : DateFormatter.today())
                .extras(extras)
                .show(getSupportFragmentManager());
    }

    /** 第一个错误字段滚动到可见并获得焦点 */
    private void focusField(@Nullable String field) {
        View target = null;
        if (FeedInboundViewModel.F_QTY.equals(field)) {
            target = binding.stockQtyInput;
        } else if (FeedInboundViewModel.F_PRICE.equals(field)) {
            target = binding.stockPriceInput;
        } else if (FeedInboundViewModel.F_SUPPLIER.equals(field)) {
            target = binding.stockSupplierInput;
        } else if (FeedInboundViewModel.F_FEED.equals(field)) {
            target = binding.stockFeedLayout;
        } else if (FeedInboundViewModel.F_PRODUCTION.equals(field)) {
            target = binding.stockProductionLayout;
        } else if (FeedInboundViewModel.F_SHELF.equals(field)) {
            target = binding.stockShelfLayout;
        } else if (FeedInboundViewModel.F_DATE.equals(field)) {
            target = binding.stockDateLayout;
        }
        StockUi.scrollTo(binding.stockScroll, target);
    }
}
