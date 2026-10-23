package com.zhimu.slmp.stock.ui.otherout;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;

import com.google.android.material.chip.Chip;
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
import com.zhimu.slmp.stock.databinding.StockActivityOtherOutBinding;
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
 * M10 饲料其他出库 / 其他出库记录（只读）/ 修改其他出库。新增成功回 M01；修改成功回只读状态；作废成功回流水。
 */
public class FeedOtherOutActivity extends BaseActivity implements PickerHost, RecordMenu.Callback {

    private static final String REQ_DISCARD = "stock_m10_discard";
    private static final String REQ_VOID = "stock_m10_void";
    private static final String REQ_NOT_TODAY = "stock_m10_not_today";
    private static final String REQ_DATE = "stock_m10_date";

    private StockActivityOtherOutBinding binding;
    private FeedOtherOutViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.stock_activity_other_out);
        vm = obtainViewModel(StockInjector.factory(), FeedOtherOutViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar, getString(R.string.stock_other_out_title));
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
        buildDestinations();
        vm.getDestination().observe(this, this::syncDestination);

        ClickThrottle.setOnClickListener(binding.stockFeedInput, v -> showFeedPicker());
        binding.stockFeedLayout.setEndIconOnClickListener(v -> showFeedPicker());
        ClickThrottle.setOnClickListener(binding.stockDateInput, v -> pickDate());
        binding.stockDateLayout.setEndIconOnClickListener(v -> pickDate());
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (k, r) -> vm.onDatePicked(DateTimePicker.getDate(r)));
        getSupportFragmentManager().setFragmentResultListener(FeedOtherOutViewModel.PICKER_FEED, this, (k, r) -> {
            String v = PickerSheet.valueOf(r);
            if (v != null) {
                vm.onFeedPicked(Long.parseLong(v));
            }
        });

        binding.stockActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getSubmitting().observe(this, s -> binding.stockActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getFocusField().observe(this, f -> StockUi.scrollTo(binding.stockScroll,
                FeedOtherOutViewModel.F_FEED.equals(f) ? binding.stockFeedLayout
                        : FeedOtherOutViewModel.F_QTY.equals(f) ? binding.stockQtyInput
                        : FeedOtherOutViewModel.F_DEST.equals(f) ? binding.stockDestChips
                        : FeedOtherOutViewModel.F_REMARK.equals(f) ? binding.stockRemarkInput : binding.stockDateLayout));

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
        getSupportFragmentManager().setFragmentResultListener(REQ_NOT_TODAY, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.onRequestChange();
            }
        });
        vm.getShowDiscard().observe(this, v -> StockUi.showDiscard(this, REQ_DISCARD));
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
                .title(getString(R.string.stock_not_today_title))
                .message(getString(R.string.stock_need_request_message))
                .negative(getString(R.string.stock_action_cancel))
                .positive(getString(R.string.stock_menu_request_change))
                .build().showSafely(getSupportFragmentManager()));
        vm.getOpenChangeRequest().observe(this, id -> Router.open(this, RouterPath.U01,
                StockNav.changeArgs(StockEnums.BIZ_OUTBOUND, id)));
        vm.getOpenRequest().observe(this, id -> Router.open(this, RouterPath.U02, StockNav.requestArgs(id)));
        vm.getOpenChangeLog().observe(this, id -> startActivity(ChangeLogActivity.intent(this,
                StockEnums.BIZ_OUTBOUND, id)));
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
        binding.stockTopBar.baseToolbar.setTitle(view ? R.string.stock_other_out_record
                : edit ? R.string.stock_other_out_edit : R.string.stock_other_out_title);
        binding.stockTopBar.baseToolbar.getMenu().findItem(R.id.stock_menu_more)
                .setVisible(content && view && RecordMenu.hasItems(vm.actions(), true));
        binding.stockActionBar.setVisibility(content && !view ? View.VISIBLE : View.GONE);
        if (edit) {
            binding.stockActionBar.setPrimary(getString(R.string.stock_action_save_edit),
                    com.zhimu.slmp.base.R.drawable.ic_check);
        } else {
            binding.stockActionBar.setPrimary(getString(R.string.stock_other_out_confirm), R.drawable.stock_ic_output);
        }
    }

    private void buildDestinations() {
        binding.stockDestChips.removeAllViews();
        for (String[] d : FeedOtherOutViewModel.DESTINATIONS) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.stock_item_filter_chip, binding.stockDestChips,
                    false);
            chip.setId(View.generateViewId());
            chip.setText(d[1]);
            chip.setTag(d[0]);
            chip.setCheckable(true);
            binding.stockDestChips.addView(chip);
        }
        binding.stockDestChips.setOnCheckedStateChangeListener((group, ids) -> {
            String dest = ids.isEmpty() ? null : (String) group.findViewById(ids.get(0)).getTag();
            if (dest == null ? vm.getDestination().getValue() != null : !dest.equals(vm.getDestination().getValue())) {
                vm.onDestinationPicked(dest);
            }
        });
    }

    private void syncDestination(@Nullable String dest) {
        for (int i = 0; i < binding.stockDestChips.getChildCount(); i++) {
            Chip c = (Chip) binding.stockDestChips.getChildAt(i);
            boolean want = c.getTag() != null && c.getTag().equals(dest);
            if (c.isChecked() != want) {
                c.setChecked(want);
            }
        }
    }

    private void showFeedPicker() {
        if (!FormMode.NEW.equals(vm.getMode().getValue())) {
            return;
        }
        PickerSheet.builder(FeedOtherOutViewModel.PICKER_FEED)
                .title(getString(R.string.stock_pick_feed_title))
                .build().showSafely(getSupportFragmentManager());
    }

    private void pickDate() {
        LocalDate d = DateFormatter.parseDate(vm.getOutDate().getValue());
        DateTimePicker.date(REQ_DATE)
                .selection(d != null ? d : DateFormatter.today())
                .bounds(null, DateFormatter.today())
                .show(getSupportFragmentManager());
    }
}
