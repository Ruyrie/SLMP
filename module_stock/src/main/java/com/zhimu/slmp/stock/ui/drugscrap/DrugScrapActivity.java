package com.zhimu.slmp.stock.ui.drugscrap;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.databinding.StockActivityDrugScrapBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.vo.DrugLotDetailVO;
import com.zhimu.slmp.stock.ui.common.StockFormViewModel;
import com.zhimu.slmp.stock.ui.common.StockNav;
import com.zhimu.slmp.stock.ui.common.StockUi;
import com.zhimu.slmp.stock.util.StockTexts;

import java.time.LocalDate;
import java.util.List;

/**
 * M07 登记报废。确认对话框“报废”后保持打开并显示加载，成功后关闭并带全局提示 RESULT_OK 返回 M06。
 */
public class DrugScrapActivity extends BaseActivity {

    private static final String REQ_CONFIRM = "stock_m07_confirm";
    private static final String REQ_DATE = "stock_m07_date";
    private static final String REQ_DISCARD = "stock_m07_discard";

    private StockActivityDrugScrapBinding binding;
    private DrugScrapViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.stock_activity_drug_scrap);
        vm = obtainViewModel(StockInjector.factory(), DrugScrapViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar, getString(R.string.stock_scrap_action));
        binding.stockTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBack()) {
                    finish();
                }
            }
        });
        binding.stockState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, s -> {
            binding.stockState.render(s);
            binding.stockActionBar.setVisibility(s != null && s.is(UiState.Status.CONTENT) ? View.VISIBLE : View.GONE);
        });
        vm.getLot().observe(this, this::renderLot);
        vm.getReasons().observe(this, this::renderReasons);
        vm.getReason().observe(this, r -> binding.stockRemarkLayout.setHint(vm.remarkRequired()
                ? getString(R.string.stock_field_remark) : getString(R.string.stock_field_remark_optional)));

        ClickThrottle.setOnClickListener(binding.stockDateInput, v -> pickDate());
        binding.stockDateLayout.setEndIconOnClickListener(v -> pickDate());
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (k, r) -> vm.onDatePicked(DateTimePicker.getDate(r)));

        binding.stockActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getFocusField().observe(this, f -> StockUi.scrollTo(binding.stockScroll,
                DrugScrapViewModel.F_QTY.equals(f) ? binding.stockQtyInput
                        : DrugScrapViewModel.F_REMARK.equals(f) ? binding.stockRemarkInput
                        : DrugScrapViewModel.F_DATE.equals(f) ? binding.stockDateLayout : binding.stockReasonChips));
        vm.getShowConfirm().observe(this, t -> ConfirmDialogFragment.builder(REQ_CONFIRM)
                .icon(com.zhimu.slmp.base.R.drawable.ic_delete)
                .title(t[0])
                .message(t[1])
                .negative(getString(R.string.stock_action_cancel))
                .positive(getString(R.string.stock_scrap_confirm))
                .danger(true)
                .keepOpenOnConfirm()
                .build().showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_CONFIRM, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.doScrap();
            }
        });
        vm.getSubmitting().observe(this, s -> {
            boolean loading = Boolean.TRUE.equals(s);
            ConfirmDialogFragment.setLoading(getSupportFragmentManager(), REQ_CONFIRM, loading);
            binding.stockActionBar.setPrimaryEnabled(!loading);
        });
        vm.getCloseConfirm().observe(this, v -> ConfirmDialogFragment.dismiss(getSupportFragmentManager(), REQ_CONFIRM));
        vm.getScrapped().observe(this, msg -> {
            ConfirmDialogFragment.dismiss(getSupportFragmentManager(), REQ_CONFIRM);
            StockNav.finishWithMessage(this, UiMessage.of(msg));
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                vm.onDiscardConfirmed();
                finish();
            }
        });
        vm.getShowDiscard().observe(this, v -> StockUi.showDiscard(this, REQ_DISCARD));
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (StockFormViewModel.ACTION_RETRY.equals(actionId)) {
            vm.retrySubmit();
        }
    }

    private void renderLot(@Nullable DrugLotDetailVO d) {
        if (d == null) {
            return;
        }
        binding.stockScrapIcon.setImageResource(StockTexts.categoryIcon(d.category));
        StockUi.tone(binding.stockScrapLeading, binding.stockScrapIcon, StockTexts.lotTone(d.lotStatus));
        String tag = StockTexts.lotTag(d);
        binding.stockScrapTag.setVisibility(tag == null ? View.GONE : View.VISIBLE);
        binding.stockScrapTag.setStatus(tag, StockTexts.lotTagType(d.lotStatus));
        binding.stockScrapTag.setTagIcon(StockTexts.lotTagIcon(d.lotStatus));
    }

    private void renderReasons(@Nullable List<DictItemVO> list) {
        binding.stockReasonChips.setOnCheckedStateChangeListener(null);
        binding.stockReasonChips.removeAllViews();
        if (list == null) {
            return;
        }
        String current = vm.getReason().getValue();
        for (DictItemVO r : list) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.stock_item_filter_chip, binding.stockReasonChips,
                    false);
            chip.setId(View.generateViewId());
            chip.setText(r.getName());
            chip.setTag(r.getCode());
            chip.setCheckable(true);
            chip.setChecked(r.getCode() != null && r.getCode().equals(current));
            binding.stockReasonChips.addView(chip);
        }
        binding.stockReasonChips.setOnCheckedStateChangeListener((group, ids) -> vm.onReasonPicked(ids.isEmpty()
                ? null : (String) group.findViewById(ids.get(0)).getTag()));
    }

    private void pickDate() {
        LocalDate d = DateFormatter.parseDate(vm.getScrapDate().getValue());
        DateTimePicker.date(REQ_DATE)
                .selection(d != null ? d : DateFormatter.today())
                .bounds(null, DateFormatter.today())
                .show(getSupportFragmentManager());
    }
}
