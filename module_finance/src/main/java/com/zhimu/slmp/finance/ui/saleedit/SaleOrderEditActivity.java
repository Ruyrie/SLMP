package com.zhimu.slmp.finance.ui.saleedit;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.ButtonLoading;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.ZmtTextInputLayout;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivitySaleEditBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.SaleBatchOptionVO;
import com.zhimu.slmp.finance.model.vo.SalePrefillVO;
import com.zhimu.slmp.finance.ui.common.FinanceNav;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * E06 登记出栏单（新增 / 编辑，图 10-8、10-9）。
 */
public class SaleOrderEditActivity extends FinancePageActivity {

    static final String REQ_BATCH = "finance_e06_batch";
    static final String REQ_SALE_DATE = "finance_e06_sale_date";
    static final String REQ_DUE = "finance_e06_due";
    static final String REQ_DISCARD = "finance_e06_discard";

    private FinanceActivitySaleEditBinding binding;
    private SaleOrderEditViewModel vm;
    private final List<TextInputEditText> penInputs = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FinanceActivitySaleEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), SaleOrderEditViewModel.class);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);
        boolean edit = Boolean.TRUE.equals(vm.getEditMode().getValue());
        setupTopBar(binding.financeTopBar, getString(edit ? R.string.finance_e06_title_edit : R.string.finance_e06_title),
                binding.financeScroll);
        binding.financeSubmit.setText(edit ? R.string.finance_e06_save : R.string.finance_e06_submit);
        bindState(binding.financeState, vm);
        observeNav(vm.getOpenPage());

        View.OnClickListener pickBatch = v -> vm.openBatchSheet();
        binding.financeBatch.setOnClickListener(pickBatch);
        binding.financeBatchLayout.setEndIconOnClickListener(pickBatch);
        binding.financeBatchLayout.setEnabled(!edit);
        vm.getShowBatchSheet().observe(this, b -> showBatchSheet());
        getSupportFragmentManager().setFragmentResultListener(REQ_BATCH, this,
                (k, r) -> vm.selectBatch(Long.parseLong(OptionSheetDialogFragment.getValue(r))));
        binding.financeGoEntry.setOnClickListener(v -> vm.openEntry());

        View.OnClickListener pickSale = v -> {
            SaleBatchOptionVO b = vm.getSelectedBatch().getValue();
            DateTimePicker.date(REQ_SALE_DATE).selection(DateFormatter.parseDate(vm.saleDate.getValue()))
                    .bounds(b == null ? null : DateFormatter.parseDate(b.getEntryDate()), DateFormatter.today())
                    .show(getSupportFragmentManager());
        };
        binding.financeSaleDate.setOnClickListener(pickSale);
        binding.financeSaleDateLayout.setEndIconOnClickListener(pickSale);
        getSupportFragmentManager().setFragmentResultListener(REQ_SALE_DATE, this,
                (k, r) -> vm.changeSaleDate(DateTimePicker.getDate(r)));
        View.OnClickListener pickDue = v -> DateTimePicker.date(REQ_DUE)
                .selection(DateFormatter.parseDate(vm.dueDate.getValue() == null ? vm.saleDate.getValue()
                        : vm.dueDate.getValue()))
                .bounds(DateFormatter.parseDate(vm.saleDate.getValue()), null).show(getSupportFragmentManager());
        binding.financeDue.setOnClickListener(pickDue);
        binding.financeDueLayout.setEndIconOnClickListener(pickDue);
        getSupportFragmentManager().setFragmentResultListener(REQ_DUE, this,
                (k, r) -> vm.setDueDate(DateTimePicker.getDate(r)));

        binding.financePricingGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) {
                vm.switchPricing(id == R.id.finance_by_head ? FinanceConst.BY_HEAD : FinanceConst.BY_WEIGHT);
            }
        });
        ClickThrottle.setOnClickListener(binding.financeSubmit, v -> vm.submit());

        // 观察
        vm.getSelectedBatch().observe(this, b -> renderAll());
        vm.getInWithdrawal().observe(this, b -> renderAll());
        vm.getMaxQuantity().observe(this, b -> renderAll());
        vm.getNoBatches().observe(this, b -> renderAll());
        vm.getPrefill().observe(this, this::renderPrefill);
        vm.getAvgWeight().observe(this, a -> binding.financeWeightLayout.setHelperText(vm.weightHelper()));
        vm.getSaleAmount().observe(this, this::renderAmount);
        vm.quantityText.observe(this, q -> renderRule());
        vm.pricingMethod.observe(this, m -> {
            binding.financePricingGroup.check(FinanceConst.BY_HEAD.equals(m) ? R.id.finance_by_head : R.id.finance_by_weight);
            binding.financePriceLayout.setSuffixText(vm.priceUnit());
            renderAmount(vm.getSaleAmount().getValue());
        });
        vm.saleDate.observe(this, d -> binding.financeSaleDate.setText(DateFormatter.formatDate(d)));
        vm.dueDate.observe(this, d -> {
            binding.financeDue.setText(d == null ? null : DateFormatter.formatDate(d));
            binding.financeDueLayout.setHelperText(vm.dueHelper());
        });
        vm.getPenQuantities().observe(this, this::renderPens);
        vm.getFieldErrors().observe(this, this::renderErrors);
        vm.getCanSubmit().observe(this, c -> binding.financeSubmit.setEnabled(Boolean.TRUE.equals(c)));
        vm.getSubmitting().observe(this, s -> ButtonLoading.setLoading(binding.financeSubmit, Boolean.TRUE.equals(s)));
        vm.getOpenDetail().observe(this, t -> {
            Intent data = new Intent();
            Object id = t.get(RouterPath.EXTRA_SALE_ORDER_ID);
            if (id instanceof Long) {
                data.putExtra(FinanceExtras.RESULT_CREATED_ID, (Long) id);
            }
            setResult(RESULT_OK, data);
            FinanceNav.open(this, t, null);
        });
        vm.getFinishWithResult().observe(this, text -> FinanceNav.finishWithMessage(this, text));

        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                finish();
            }
        });
        vm.getConfirmDiscard().observe(this, b -> ConfirmDialogFragment.discardChanges(this, REQ_DISCARD)
                .showSafely(getSupportFragmentManager()));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBack()) {
                    finish();
                }
            }
        });
    }

    private void showBatchSheet() {
        List<OptionSheetDialogFragment.Option> options = new ArrayList<>();
        List<SaleBatchOptionVO> list = vm.getBatchOptions().getValue();
        if (list != null) {
            for (SaleBatchOptionVO b : list) {
                OptionSheetDialogFragment.Option o = new OptionSheetDialogFragment.Option(String.valueOf(b.getBatchId()),
                        b.getBatchNo() + " · " + b.getPenName(), "存栏 " + FinanceFormat.count(b.getCurrentCount(),
                        b.getCountUnitText()));
                o.tag(b.isInWithdrawal() ? (b.getWithdrawalDaysLeft() == null ? "休药中"
                        : "休药中 · 剩 " + b.getWithdrawalDaysLeft() + " 天") : "可出栏",
                        b.isInWithdrawal() ? StatusTagView.WARNING : StatusTagView.SUCCESS);
                options.add(o);
            }
        }
        SaleBatchOptionVO cur = vm.getSelectedBatch().getValue();
        OptionSheetDialogFragment.builder(REQ_BATCH).title(getString(R.string.finance_e06_batch_sheet)).options(options)
                .selected(cur == null ? null : String.valueOf(cur.getBatchId())).build()
                .showSafely(getSupportFragmentManager());
    }

    private void renderAll() {
        SaleBatchOptionVO b = vm.getSelectedBatch().getValue();
        boolean withdrawal = Boolean.TRUE.equals(vm.getInWithdrawal().getValue());
        binding.financeBatch.setText(b == null ? null : b.getBatchNo());
        binding.financeBatchLayout.setHelperText(vm.batchHelper());
        String tag = vm.batchTag();
        binding.financeBatchTag.setVisibility(tag == null ? View.GONE : View.VISIBLE);
        if (tag != null) {
            binding.financeBatchTag.setStatus(tag, withdrawal ? StatusTagView.WARNING : StatusTagView.SUCCESS);
            binding.financeBatchTag.setTagIcon(withdrawal ? 0 : com.zhimu.slmp.base.R.drawable.ic_check_circle);
        }
        binding.financeGoEntry.setVisibility(Boolean.TRUE.equals(vm.getNoBatches().getValue()) ? View.VISIBLE
                : View.GONE);
        binding.financeWithdrawalNotice.setVisibility(withdrawal ? View.VISIBLE : View.GONE);
        if (withdrawal) {
            binding.financeWithdrawalNotice.setText(vm.withdrawalNotice());
        }
        binding.financeQuantityLayout.setSuffixText(vm.unitText());
        binding.financeQuantityLayout.setHelperText(vm.quantityHelper());
        binding.financePriceLayout.setSuffixText(vm.priceUnit());
        binding.financeAmountBox.setVisibility(withdrawal ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams lp = binding.financeSubmit.getLayoutParams();
        lp.width = withdrawal ? ViewGroup.LayoutParams.MATCH_PARENT : (int) (200 * getResources().getDisplayMetrics().density);
        binding.financeSubmit.setLayoutParams(lp);
        renderRule();
    }

    private void renderRule() {
        boolean show = vm.getSelectedBatch().getValue() != null && !Boolean.TRUE.equals(vm.getInWithdrawal().getValue());
        binding.financeRuleNotice.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.financeRuleNotice.setText(vm.ruleNotice());
        }
    }

    /** “去入栏”（L08）返回：重拉批次选项 */
    @Override
    protected void onChildResult(@NonNull ActivityResult result) {
        vm.reloadBatchOptions();
    }

    private void renderPrefill(@Nullable SalePrefillVO p) {
        binding.financePriceLayout.setHelperText(vm.priceHelper());
        if (p != null) {
            binding.financeBuyer.setAdapter(new ArrayAdapter<>(this, R.layout.finance_item_dropdown,
                    p.getRecentBuyers()));
        }
    }

    private void renderAmount(@Nullable BigDecimal amount) {
        binding.financeAmount.setText(amount == null ? FinanceFormat.DASH
                : FinanceViews.amountWithUnit(binding.financeAmount, FinanceFormat.num(amount), "元", .64f));
    }

    private void renderPens(@Nullable List<SaleOrderEditViewModel.PenRow> rows) {
        boolean multi = rows != null && !rows.isEmpty();
        binding.financePensTitle.setVisibility(multi ? View.VISIBLE : View.GONE);
        if (!multi) {
            binding.financePens.removeAllViews();
            penInputs.clear();
            return;
        }
        if (penInputs.size() == rows.size()) {
            return;
        }
        binding.financePens.removeAllViews();
        penInputs.clear();
        for (int i = 0; i < rows.size(); i++) {
            SaleOrderEditViewModel.PenRow r = rows.get(i);
            ZmtTextInputLayout layout = new ZmtTextInputLayout(this, null,
                    com.google.android.material.R.attr.textInputOutlinedStyle);
            layout.setHint(r.penName);
            layout.setHelperText("本批次在栏 " + FinanceFormat.count(r.stockCount, vm.unitText()));
            layout.setSuffixText(vm.unitText());
            TextInputEditText input = new TextInputEditText(layout.getContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setText(r.quantityText);
            layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
            binding.financePens.addView(layout, lp);
            final int index = i;
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    vm.setPenQuantity(index, s.toString());
                }
            });
            penInputs.add(input);
        }
    }

    private void renderErrors(@Nullable Map<String, String> errors) {
        FinanceViews.setError(binding.financeBatchLayout, errors, SaleOrderEditViewModel.F_BATCH);
        if (errors == null || !errors.containsKey(SaleOrderEditViewModel.F_BATCH)) {
            binding.financeBatchLayout.setHelperText(vm.batchHelper());
        }
        FinanceViews.setError(binding.financeSaleDateLayout, errors, SaleOrderEditViewModel.F_SALE_DATE);
        FinanceViews.setError(binding.financeQuantityLayout, errors, SaleOrderEditViewModel.F_QUANTITY);
        if (errors == null || !errors.containsKey(SaleOrderEditViewModel.F_QUANTITY)) {
            binding.financeQuantityLayout.setHelperText(vm.quantityHelper());
        }
        FinanceViews.setError(binding.financeWeightLayout, errors, SaleOrderEditViewModel.F_WEIGHT);
        if (errors == null || !errors.containsKey(SaleOrderEditViewModel.F_WEIGHT)) {
            binding.financeWeightLayout.setHelperText(vm.weightHelper());
        }
        FinanceViews.setError(binding.financePriceLayout, errors, SaleOrderEditViewModel.F_PRICE);
        if (errors == null || !errors.containsKey(SaleOrderEditViewModel.F_PRICE)) {
            binding.financePriceLayout.setHelperText(vm.priceHelper());
        }
        FinanceViews.setError(binding.financeBuyerLayout, errors, SaleOrderEditViewModel.F_BUYER);
        FinanceViews.setError(binding.financeDueLayout, errors, SaleOrderEditViewModel.F_DUE);
        if (errors == null || !errors.containsKey(SaleOrderEditViewModel.F_DUE)) {
            binding.financeDueLayout.setHelperText(vm.dueHelper());
        }
        String pens = errors == null ? null : errors.get(SaleOrderEditViewModel.F_PENS);
        binding.financePensError.setText(pens);
        binding.financePensError.setVisibility(pens == null ? View.GONE : View.VISIBLE);
        if (errors != null && !errors.isEmpty() && !errors.containsKey(SaleOrderEditViewModel.F_QUANTITY)
                || errors != null && errors.size() > 1) {
            View first = errors.containsKey(SaleOrderEditViewModel.F_BATCH) ? binding.financeBatchLayout
                    : errors.containsKey(SaleOrderEditViewModel.F_SALE_DATE) ? binding.financeSaleDateLayout
                    : errors.containsKey(SaleOrderEditViewModel.F_QUANTITY) ? binding.financeQuantityLayout
                    : errors.containsKey(SaleOrderEditViewModel.F_WEIGHT) ? binding.financeWeightLayout
                    : errors.containsKey(SaleOrderEditViewModel.F_PRICE) ? binding.financePriceLayout
                    : binding.financeBuyerLayout;
            binding.financeScroll.smoothScrollTo(0, Math.max(0, first.getTop() - 48));
        }
    }
}
