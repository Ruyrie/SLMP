package com.zhimu.slmp.finance.ui.record;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivityRecordEditBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.DuplicateCheckVO;
import com.zhimu.slmp.finance.model.vo.FinanceCategoryVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordDetailVO;
import com.zhimu.slmp.finance.model.vo.SettlementOptionVO;
import com.zhimu.slmp.finance.model.vo.SubcategoryVO;
import com.zhimu.slmp.finance.ui.common.FinanceNav;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.ui.common.NameInputBottomSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * E03 记一笔（新增 / 编辑 / 只读，图 10-4、10-5）。
 */
public class FinanceRecordEditActivity extends FinancePageActivity {

    static final String REQ_DATE = "finance_e03_date";
    static final String REQ_SUB = "finance_e03_sub";
    static final String REQ_DISCARD = "finance_e03_discard";

    private FinanceActivityRecordEditBinding binding;
    private FinanceRecordEditViewModel vm;
    private final List<SettlementOptionVO> batchList = new ArrayList<>();
    private boolean renderingChips;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FinanceActivityRecordEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), FinanceRecordEditViewModel.class);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);
        boolean edit = Boolean.TRUE.equals(vm.getEditMode().getValue());
        boolean readOnly = Boolean.TRUE.equals(vm.getReadOnly().getValue());
        setupTopBar(binding.financeTopBar, getString(readOnly ? R.string.finance_e03_title_view
                : edit ? R.string.finance_e03_title_edit : R.string.finance_e03_title), binding.financeScroll);
        bindState(binding.financeState, vm);
        observeNav(vm.getOpenPage());
        if (vm.getPhotos() != null) {
            binding.financeVouchers.bind(this, vm.getPhotos());
        }

        binding.financeEditNote.setVisibility(edit && !readOnly ? View.VISIBLE : View.GONE);
        binding.financeTypeGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) {
                vm.switchType(id == R.id.finance_type_income ? FinanceConst.INCOME : FinanceConst.EXPENSE);
            }
        });
        binding.financeDuplicate.setAction(getString(R.string.finance_view), v -> vm.viewDuplicate());
        View.OnClickListener pickDate = v -> {
            if (Boolean.TRUE.equals(vm.getReadOnly().getValue())) {
                return;
            }
            DateTimePicker.date(REQ_DATE).selection(DateFormatter.parseDate(vm.recordDate.getValue()))
                    .bounds(null, DateFormatter.today()).show(getSupportFragmentManager());
        };
        binding.financeDate.setOnClickListener(pickDate);
        binding.financeDateLayout.setEndIconOnClickListener(pickDate);
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (k, r) -> vm.setRecordDate(DateTimePicker.getDate(r)));
        binding.financeAmount.setOnFocusChangeListener((v, focus) -> {
            if (!focus) {
                formatAmount();
            }
        });
        if (!edit && !readOnly && savedInstanceState == null) {
            binding.financeAmount.requestFocus();
        }
        binding.financeBatch.setOnItemClickListener((p, v, pos, id) ->
                vm.selectBatch(pos == 0 ? null : batchList.get(pos - 1).getBatchId()));
        binding.financeBottomBar.setOnPrimaryClickListener(v -> vm.save());

        // 子类面板
        getSupportFragmentManager().setFragmentResultListener(REQ_SUB, this,
                (k, r) -> vm.addSubcategory(NameInputBottomSheet.nameOf(r)));
        vm.getShowSubcategorySheet().observe(this, id -> NameInputBottomSheet.show(getSupportFragmentManager(),
                REQ_SUB, getString(R.string.finance_sub_add_title), getString(R.string.finance_sub_name), null,
                getString(R.string.finance_sub_add), null));
        vm.getSubcategoryError().observe(this, e -> NameInputBottomSheet.setError(getSupportFragmentManager(), e));
        vm.getSubcategorySubmitting().observe(this, s -> NameInputBottomSheet.setLoading(getSupportFragmentManager(),
                Boolean.TRUE.equals(s)));
        vm.getDismissSubcategorySheet().observe(this, b -> NameInputBottomSheet.dismiss(getSupportFragmentManager()));

        // 放弃填写
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (k, r) -> {
            if (ConfirmDialogFragment.isConfirmed(r)) {
                finish();
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBack()) {
                    finish();
                }
            }
        });
        vm.getConfirmDiscard().observe(this, b -> ConfirmDialogFragment.discardChanges(this, REQ_DISCARD)
                .showSafely(getSupportFragmentManager()));

        vm.recordType.observe(this, this::renderType);
        vm.getCategories().observe(this, l -> renderCategories());
        vm.categoryId.observe(this, id -> renderCategories());
        vm.getSubcategories().observe(this, l -> renderSubcategories());
        vm.subcategoryId.observe(this, id -> renderSubcategories());
        vm.recordDate.observe(this, d -> binding.financeDate.setText(DateFormatter.formatDate(d)));
        vm.getBatchOptions().observe(this, this::renderBatches);
        vm.batchId.observe(this, id -> renderBatchValue());
        vm.getBatchHelper().observe(this, binding.financeBatchLayout::setHelperText);
        vm.getDuplicate().observe(this, this::renderDuplicate);
        vm.getFieldErrors().observe(this, this::renderErrors);
        vm.getCanSubmit().observe(this, c -> binding.financeBottomBar.setPrimaryEnabled(Boolean.TRUE.equals(c)));
        vm.getSubmitting().observe(this, s -> binding.financeBottomBar.setPrimaryLoading(Boolean.TRUE.equals(s)));
        vm.getFinishWithResult().observe(this, text -> FinanceNav.finishWithMessage(this, text));
        vm.getDetail().observe(this, this::renderReadOnly);
        if (readOnly) {
            binding.financeBottomBar.setVisibility(View.GONE);
        }
    }

    private void formatAmount() {
        java.math.BigDecimal v = com.zhimu.slmp.finance.util.SaleAmountCalculator.parseDecimal(vm.amountText.getValue());
        if (v != null && v.signum() > 0 && v.stripTrailingZeros().scale() <= 2) {
            // 数字键盘输入框不接受千分位逗号，失焦时只补齐两位小数
            vm.amountText.setValue(v.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString());
        }
    }

    private void renderType(@Nullable String type) {
        boolean income = FinanceConst.INCOME.equals(type);
        binding.financeTypeGroup.check(income ? R.id.finance_type_income : R.id.finance_type_expense);
        boolean lock = Boolean.TRUE.equals(vm.getEditMode().getValue());
        binding.financeTypeExpense.setEnabled(!lock);
        binding.financeTypeIncome.setEnabled(!lock);
        binding.financeIncomeNote.setVisibility(income ? View.VISIBLE : View.GONE);
        binding.financeNoteLayout.setVisibility(income ? View.VISIBLE : View.GONE);
    }

    private void renderCategories() {
        renderingChips = true;
        binding.financeCategories.removeAllViews();
        Long selected = vm.categoryId.getValue();
        boolean readOnly = Boolean.TRUE.equals(vm.getReadOnly().getValue());
        for (FinanceCategoryVO c : vm.getCategories().getValue()) {
            Chip chip = FinanceViews.filterChip(this, binding.financeCategories, c.getName(), String.valueOf(c.getId()));
            chip.setChecked(Objects.equals(selected, c.getId()));
            chip.setEnabled(!readOnly || chip.isChecked());
            chip.setOnClickListener(v -> {
                if (!renderingChips) {
                    vm.selectCategory(((Chip) v).isChecked() ? c.getId() : null);
                }
            });
        }
        renderingChips = false;
        FinanceCategoryVO c = vm.selectedCategory();
        boolean showSub = vm.isExpense() && c != null;
        binding.financeSubTitle.setVisibility(showSub ? View.VISIBLE : View.GONE);
        binding.financeSubcategories.setVisibility(showSub ? View.VISIBLE : View.GONE);
        if (c != null) {
            binding.financeSubTitle.setText(getString(R.string.finance_e03_sub_title, c.getName()));
        }
    }

    private void renderSubcategories() {
        binding.financeSubcategories.removeAllViews();
        Long selected = vm.subcategoryId.getValue();
        boolean readOnly = Boolean.TRUE.equals(vm.getReadOnly().getValue());
        for (SubcategoryVO s : vm.getSubcategories().getValue()) {
            Chip chip = FinanceViews.filterChip(this, binding.financeSubcategories, s.getName(), String.valueOf(s.getId()));
            chip.setChecked(Objects.equals(selected, s.getId()));
            chip.setEnabled(!readOnly || chip.isChecked());
            chip.setOnClickListener(v -> vm.selectSubcategory(s.getId()));
        }
        if (!readOnly) {
            Chip add = FinanceViews.filterChip(this, binding.financeSubcategories, getString(R.string.finance_e03_add_sub),
                    "add");
            add.setCheckable(false);
            add.setChipIconResource(com.zhimu.slmp.base.R.drawable.ic_add);
            add.setChipIconVisible(true);
            add.setChipIconTint(FinanceViews.csl(add, androidx.appcompat.R.attr.colorPrimary));
            ClickThrottle.setOnClickListener(add, v -> vm.openSubcategorySheet());
        }
    }

    private void renderBatches(@Nullable List<SettlementOptionVO> list) {
        batchList.clear();
        if (list != null) {
            batchList.addAll(list);
        }
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.finance_e03_batch_none));
        for (SettlementOptionVO o : batchList) {
            labels.add(o.getBatchNo() + (o.getPenName() == null ? "" : " · " + o.getPenName()));
        }
        binding.financeBatch.setAdapter(new ArrayAdapter<>(this, com.zhimu.slmp.finance.R.layout.finance_item_dropdown,
                labels));
        renderBatchValue();
    }

    private void renderBatchValue() {
        Long id = vm.batchId.getValue();
        String text = getString(R.string.finance_e03_batch_none);
        for (SettlementOptionVO o : batchList) {
            if (Objects.equals(id, o.getBatchId())) {
                text = o.getBatchNo() + (o.getPenName() == null ? "" : " · " + o.getPenName());
            }
        }
        binding.financeBatch.setText(text, false);
    }

    private void renderDuplicate(@Nullable DuplicateCheckVO d) {
        binding.financeDuplicate.setVisibility(d == null ? View.GONE : View.VISIBLE);
        if (d != null) {
            binding.financeDuplicate.setNotice(getString(R.string.finance_e03_duplicate_title),
                    FinanceRecordEditViewModel.duplicateText(d));
        }
    }

    private void renderErrors(@Nullable Map<String, String> errors) {
        String amount = errors == null ? null : errors.get(FinanceRecordEditViewModel.F_AMOUNT);
        binding.financeAmountLayout.setError(amount);
        if (amount == null) {
            binding.financeAmountLayout.setErrorEnabled(false);
            binding.financeAmountLayout.setHelperText(getString(R.string.finance_e03_amount_helper));
        }
        String cat = errors == null ? null : errors.get(FinanceRecordEditViewModel.F_CATEGORY);
        binding.financeCategoryError.setText(cat);
        binding.financeCategoryError.setVisibility(cat == null ? View.GONE : View.VISIBLE);
        FinanceViews.setError(binding.financeDateLayout, errors, FinanceRecordEditViewModel.F_DATE);
        if (errors == null || !errors.containsKey(FinanceRecordEditViewModel.F_DATE)) {
            binding.financeDateLayout.setHelperText(getString(R.string.finance_e03_date_helper));
        }
        FinanceViews.setError(binding.financeNoteLayout, errors, FinanceRecordEditViewModel.F_NOTE);
        if (errors != null && !errors.isEmpty()) {
            View first = errors.containsKey(FinanceRecordEditViewModel.F_AMOUNT) ? binding.financeAmountLayout
                    : errors.containsKey(FinanceRecordEditViewModel.F_CATEGORY) ? binding.financeCategories
                    : binding.financeDateLayout;
            binding.financeScroll.smoothScrollTo(0, Math.max(0, first.getTop() - 48));
        }
    }

    /** 只读查看：禁用全部输入，凭证只读大图 */
    private void renderReadOnly(@Nullable FinanceRecordDetailVO d) {
        if (d == null || !Boolean.TRUE.equals(vm.getReadOnly().getValue())) {
            return;
        }
        binding.financeAmount.setEnabled(false);
        binding.financeNote.setEnabled(false);
        binding.financeDateLayout.setEnabled(false);
        binding.financeBatchLayout.setEnabled(false);
        binding.financeTypeExpense.setEnabled(false);
        binding.financeTypeIncome.setEnabled(false);
        binding.financeVouchers.setReadOnlyFiles(d.getVouchers(), null);
    }
}
