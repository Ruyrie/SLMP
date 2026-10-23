package com.zhimu.slmp.finance.ui.category;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.ButtonLoading;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivityCategoryBinding;
import com.zhimu.slmp.finance.databinding.FinanceItemSubcategoryAddBinding;
import com.zhimu.slmp.finance.databinding.FinanceItemSubcategoryBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.vo.FinanceCategoryVO;
import com.zhimu.slmp.finance.model.vo.SubcategoryVO;
import com.zhimu.slmp.finance.ui.common.FinanceIcons;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.NameInputBottomSheet;

import java.util.List;
import java.util.Objects;

/**
 * E05 支出分类（图 10-7）。
 */
public class ExpenseCategoryActivity extends FinancePageActivity {

    static final String REQ_RENAME = "finance_e05_rename";
    static final String REQ_DELETE = "finance_e05_delete";
    static final String EXTRA_SUB_ID = "sub_id";
    static final String EXTRA_CATEGORY_ID = "category_id";

    private FinanceActivityCategoryBinding binding;
    private ExpenseCategoryViewModel vm;
    @Nullable
    private FinanceItemSubcategoryAddBinding addRow;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FinanceActivityCategoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), ExpenseCategoryViewModel.class);
        observeBase(vm);
        setupTopBar(binding.financeTopBar, getString(R.string.finance_e05_title), binding.financeScroll);
        bindState(binding.financeState, vm);
        vm.getCategories().observe(this, l -> render());
        vm.getSubs().observe(this, m -> render());
        vm.getExpandedId().observe(this, id -> render());
        vm.getNameError().observe(this, e -> {
            if (addRow != null) {
                addRow.financeNewLayout.setError(e);
                addRow.financeNewLayout.setErrorEnabled(e != null);
            }
        });
        vm.getAdding().observe(this, a -> {
            if (addRow != null) {
                ButtonLoading.setLoading(addRow.financeNewAdd, Boolean.TRUE.equals(a));
            }
        });
        vm.newName.observe(this, n -> {
            if (addRow != null && !Objects.equals(String.valueOf(addRow.financeNewInput.getText()), n)) {
                addRow.financeNewInput.setText(n);
            }
            if (addRow != null) {
                addRow.financeNewAdd.setEnabled(n != null && !n.trim().isEmpty());
            }
        });

        // 改名面板
        vm.getShowRenameSheet().observe(this, sub -> {
            Long cid = vm.categoryOf(sub.getId());
            Bundle extras = new Bundle();
            extras.putLong(EXTRA_SUB_ID, sub.getId());
            extras.putLong(EXTRA_CATEGORY_ID, cid == null ? 0 : cid);
            NameInputBottomSheet.show(getSupportFragmentManager(), REQ_RENAME, getString(R.string.finance_sub_rename_title),
                    getString(R.string.finance_sub_name), sub.getName(), getString(R.string.finance_sub_save), extras);
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_RENAME, this, (k, r) -> {
            Bundle extras = r.getBundle(NameInputBottomSheet.RESULT_EXTRAS);
            if (extras != null) {
                vm.rename(extras.getLong(EXTRA_CATEGORY_ID), extras.getLong(EXTRA_SUB_ID), NameInputBottomSheet.nameOf(r));
            }
        });
        vm.getRenameError().observe(this, e -> NameInputBottomSheet.setError(getSupportFragmentManager(), e));
        vm.getRenaming().observe(this, s -> NameInputBottomSheet.setLoading(getSupportFragmentManager(),
                Boolean.TRUE.equals(s)));
        vm.getDismissRenameSheet().observe(this, b -> NameInputBottomSheet.dismiss(getSupportFragmentManager()));

        // 删除确认
        vm.getConfirmDelete().observe(this, sub -> {
            Long cid = vm.categoryOf(sub.getId());
            Bundle extras = new Bundle();
            extras.putLong(EXTRA_SUB_ID, sub.getId());
            extras.putLong(EXTRA_CATEGORY_ID, cid == null ? 0 : cid);
            ConfirmDialogFragment.builder(REQ_DELETE).title(getString(R.string.finance_e05_delete_title, sub.getName()))
                    .message(getString(R.string.finance_e05_delete_message))
                    .positive(getString(R.string.finance_e05_delete)).negative(getString(R.string.finance_cancel))
                    .danger(true).extras(extras).build().showSafely(getSupportFragmentManager());
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DELETE, this, (k, r) -> {
            Bundle extras = ConfirmDialogFragment.getExtras(r);
            if (ConfirmDialogFragment.isConfirmed(r) && extras != null) {
                vm.delete(extras.getLong(EXTRA_CATEGORY_ID), extras.getLong(EXTRA_SUB_ID));
            }
        });
    }

    private void render() {
        List<FinanceCategoryVO> list = vm.getCategories().getValue();
        binding.financeGroupTitle.setTitle(getString(R.string.finance_e05_group, list == null ? 0 : list.size()));
        binding.financeCategories.removeAllViews();
        addRow = null;
        if (list == null) {
            return;
        }
        Long expanded = vm.getExpandedId().getValue();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (FinanceCategoryVO c : list) {
            boolean open = Objects.equals(expanded, c.getId());
            ListRowView row = new ListRowView(this);
            row.setLeadingIcon(FinanceIcons.of(c.getIcon()), ListRowView.TONE_SECONDARY);
            row.setTitle(c.getName());
            row.setSubtitle(vm.subtitleOf(c.getId()));
            row.setTrailingIcon(open ? com.zhimu.slmp.base.R.drawable.ic_expand_less
                    : com.zhimu.slmp.base.R.drawable.ic_expand_more);
            ClickThrottle.setOnClickListener(row, v -> vm.toggle(c.getId()));
            binding.financeCategories.addView(row);
            if (!open) {
                continue;
            }
            for (SubcategoryVO s : vm.subsOf(c.getId())) {
                FinanceItemSubcategoryBinding sub = FinanceItemSubcategoryBinding.inflate(inflater,
                        binding.financeCategories, false);
                sub.financeSubName.setText(s.getName());
                ClickThrottle.setOnClickListener(sub.financeSubEdit, v -> vm.requestRename(s));
                ClickThrottle.setOnClickListener(sub.financeSubDelete, v -> vm.requestDelete(s));
                binding.financeCategories.addView(sub.getRoot());
            }
            FinanceItemSubcategoryAddBinding add = FinanceItemSubcategoryAddBinding.inflate(inflater,
                    binding.financeCategories, false);
            add.financeNewInput.setText(vm.newName.getValue());
            add.financeNewAdd.setEnabled(vm.newName.getValue() != null && !vm.newName.getValue().trim().isEmpty());
            add.financeNewInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (!Objects.equals(vm.newName.getValue(), s.toString())) {
                        vm.newName.setValue(s.toString());
                    }
                }
            });
            ClickThrottle.setOnClickListener(add.financeNewAdd, v -> vm.add());
            String err = vm.getNameError().getValue();
            add.financeNewLayout.setError(err);
            add.financeNewLayout.setErrorEnabled(err != null);
            addRow = add;
            binding.financeCategories.addView(add.getRoot());
        }
        binding.financeCategories.updatePositions();
    }
}
