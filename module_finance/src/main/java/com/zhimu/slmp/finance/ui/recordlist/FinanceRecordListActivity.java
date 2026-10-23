package com.zhimu.slmp.finance.ui.recordlist;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivityRecordListBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.FinanceRecordQuery;
import com.zhimu.slmp.finance.model.vo.FinanceCategoryVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordVO;
import com.zhimu.slmp.finance.ui.common.FinanceNav;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.finance.ui.common.ListStateBinder;
import com.zhimu.slmp.finance.ui.common.RecordLogBottomSheet;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * E04 收支流水（图 10-6）。
 */
public class FinanceRecordListActivity extends FinancePageActivity {

    static final String REQ_MONTH = "finance_e04_month";
    static final String REQ_FILTER = "finance_e04_filter";
    static final String REQ_VOID = "finance_e04_void";
    static final int MENU_EDIT = 1;
    static final int MENU_LOG = 2;
    static final int MENU_VOID = 3;
    static final long HIGHLIGHT_MS = 1500;

    private FinanceActivityRecordListBinding binding;
    private FinanceRecordListViewModel vm;
    private FinanceRecordAdapter adapter;
    private boolean renderingChips;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FinanceActivityRecordListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), FinanceRecordListViewModel.class);
        observeBase(vm);
        setupTopBar(binding.financeTopBar, getString(R.string.finance_e04_title), binding.financeList);
        TopAppBarHelper.inflateMenu(binding.financeTopBar.baseToolbar, R.menu.finance_menu_record_list);
        binding.financeTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.finance_menu_search) {
                vm.enterSearch();
            } else if (item.getItemId() == R.id.finance_menu_filter) {
                showFilter();
            }
            return true;
        });
        observeNav(vm.getOpenPage());

        adapter = new FinanceRecordAdapter(new FinanceRecordAdapter.Listener() {
            @Override
            public void onClick(@NonNull FinanceRecordVO r) {
                vm.onItemClick(r);
            }

            @Override
            public void onSource(@NonNull FinanceRecordVO r) {
                vm.openSource(r);
            }

            @Override
            public void onMore(@NonNull View anchor, @NonNull FinanceRecordVO r) {
                showMenu(anchor, r);
            }
        });
        binding.financeList.setLayoutManager(new LinearLayoutManager(this));
        binding.financeList.setAdapter(adapter.withLoadStateFooter(new FooterLoadStateAdapter(adapter::retry, true)));
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (positionStart > 0) {
                    binding.financeList.post(() -> adapter.notifyItemChanged(positionStart - 1));
                }
                scrollToHighlight();
            }
        });
        ListStateBinder.bind(this, adapter, binding.financeState, binding.financeRefresh, vm, this::emptyText);
        vm.getPagingData().observe(this, d -> adapter.submitData(getLifecycle(), d));
        vm.getTotals().observe(this, t -> binding.financeTotals.setText(FinanceRecordListViewModel.totalsText(t)));
        vm.getFilter().observe(this, this::renderFilter);
        vm.getHighlightId().observe(this, id -> scrollToHighlight());
        adapter.setShowFarm(vm.currentFilter() != null && FinanceConst.SCOPE_ALL.equals(vm.currentFilter().getScope()));

        binding.financeMonthChip.setOnClickListener(v -> showMonthPicker());
        getSupportFragmentManager().setFragmentResultListener(REQ_MONTH, this,
                (k, r) -> vm.setMonth(OptionSheetDialogFragment.getValue(r)));
        getSupportFragmentManager().setFragmentResultListener(REQ_FILTER, this, (k, r) -> vm.applyFilter(
                r.getString(RecordFilterBottomSheet.RESULT_IDS), r.getString(RecordFilterBottomSheet.RESULT_LABEL),
                r.getBoolean(RecordFilterBottomSheet.RESULT_VOUCHER) ? Boolean.TRUE : null));
        getSupportFragmentManager().setFragmentResultListener(REQ_VOID, this, (k, r) -> {
            Bundle extras = ConfirmDialogFragment.getExtras(r);
            if (ConfirmDialogFragment.isConfirmed(r) && extras != null) {
                vm.voidRecord(extras.getLong("id"));
            }
        });
        vm.getConfirmVoid().observe(this, rec -> {
            Bundle extras = new Bundle();
            extras.putLong("id", rec.getId());
            ConfirmDialogFragment.builder(REQ_VOID).title(getString(R.string.finance_e04_void_title))
                    .message(getString(R.string.finance_e04_void_message))
                    .positive(getString(R.string.finance_e04_menu_void)).negative(getString(R.string.finance_cancel))
                    .danger(true).extras(extras).build().showSafely(getSupportFragmentManager());
        });
        vm.getShowLogSheet().observe(this, logs -> RecordLogBottomSheet.show(getSupportFragmentManager(), logs,
                getString(R.string.finance_log_title)));
        setupSearch();
    }

    @Override
    protected void onChildResult(@NonNull ActivityResult result) {
        vm.onChildResult(FinanceNav.messageOf(result));
    }

    @NonNull
    private CharSequence emptyText() {
        FinanceRecordQuery q = vm.currentFilter();
        if (q != null && q.isFiltered()) {
            return getString(R.string.finance_e04_empty_filter);
        }
        return getString(R.string.finance_e04_empty, FinanceFormat.monthLabel(q == null ? FinanceFormat.currentMonth()
                : q.getMonth()));
    }

    private void renderFilter(@Nullable FinanceRecordQuery q) {
        if (q == null) {
            return;
        }
        binding.financeMonthChip.setText(FinanceFormat.monthLabel(q.getMonth()));
        renderingChips = true;
        binding.financeTypeChips.removeAllViews();
        String[][] types = {{FinanceConst.TYPE_ALL, getString(R.string.finance_e04_type_all)},
                {FinanceConst.EXPENSE, getString(R.string.finance_e04_type_expense)},
                {FinanceConst.INCOME, getString(R.string.finance_e04_type_income)},
                {FinanceConst.TYPE_AUTO, getString(R.string.finance_e04_type_auto)},
                {FinanceConst.TYPE_MANUAL, getString(R.string.finance_e04_type_manual)}};
        for (int i = 0; i < types.length; i++) {
            String[] t = types[i];
            Chip c = FinanceViews.filterChip(this, binding.financeTypeChips, t[1], t[0]);
            c.setChecked(t[0].equals(q.getType()));
            c.setOnClickListener(v -> {
                if (!renderingChips) {
                    vm.setType(t[0]);
                }
            });
            if (i == 0) {
                addRemovableChip(q.getCategoryIds() == null ? null : q.getCategoryLabel() == null ? "已选分类"
                        : q.getCategoryLabel(), vm::clearCategory);
                addRemovableChip(q.getBatchId() == null ? null : q.getBatchLabel() == null ? "已选批次"
                        : q.getBatchLabel(), vm::clearBatch);
            }
        }
        renderingChips = false;
        boolean searching = Boolean.TRUE.equals(vm.getSearchMode().getValue());
        binding.financeSearch.getRoot().setVisibility(searching ? View.VISIBLE : View.GONE);
    }

    private void addRemovableChip(@Nullable String label, @NonNull Runnable onClose) {
        if (label == null) {
            return;
        }
        Chip c = FinanceViews.filterChip(this, binding.financeTypeChips, label, "x");
        c.setChecked(true);
        c.setCloseIconVisible(true);
        c.setOnCloseIconClickListener(v -> onClose.run());
        c.setOnClickListener(v -> c.setChecked(true));
    }

    private void showMenu(@NonNull View anchor, @NonNull FinanceRecordVO r) {
        MoreMenuHelper.with(anchor)
                .add(MENU_EDIT, getString(R.string.finance_e04_menu_edit), com.zhimu.slmp.base.R.drawable.ic_edit)
                .add(MENU_LOG, getString(R.string.finance_e04_menu_log), com.zhimu.slmp.base.R.drawable.ic_history)
                .addDanger(MENU_VOID, getString(R.string.finance_e04_menu_void), com.zhimu.slmp.base.R.drawable.ic_block)
                .setOnItemClickListener(id -> {
                    if (id == MENU_EDIT) {
                        vm.edit(r);
                    } else if (id == MENU_LOG) {
                        vm.showLog(r);
                    } else if (id == MENU_VOID) {
                        vm.requestVoid(r);
                    }
                })
                .show();
    }

    private void showMonthPicker() {
        List<OptionSheetDialogFragment.Option> options = new ArrayList<>();
        String m = FinanceFormat.currentMonth();
        for (int i = 0; i < 24; i++) {
            String ym = FinanceFormat.shiftMonth(m, -i);
            options.add(new OptionSheetDialogFragment.Option(ym, ym.substring(0, 4) + "年"
                    + Integer.parseInt(ym.substring(5)) + "月"));
        }
        FinanceRecordQuery q = vm.currentFilter();
        OptionSheetDialogFragment.builder(REQ_MONTH).title(getString(R.string.finance_month_title)).options(options)
                .selected(q == null ? m : q.getMonth()).build().showSafely(getSupportFragmentManager());
    }

    private void showFilter() {
        List<FinanceCategoryVO> cats = vm.getExpenseCategories().getValue();
        int n = cats == null ? 0 : cats.size();
        long[] ids = new long[n];
        String[] names = new String[n];
        for (int i = 0; i < n; i++) {
            ids[i] = cats.get(i).getId();
            names[i] = cats.get(i).getName();
        }
        FinanceRecordQuery q = vm.currentFilter();
        RecordFilterBottomSheet.show(getSupportFragmentManager(), REQ_FILTER, ids, names,
                q == null ? null : q.getCategoryIds(), q != null && Boolean.TRUE.equals(q.getHasVoucher()));
    }

    private void setupSearch() {
        binding.financeSearch.financeSearchInput.setHint(R.string.finance_e04_search_hint);
        binding.financeSearch.financeSearchBack.setOnClickListener(v -> vm.exitSearch());
        binding.financeSearch.financeSearchClear.setOnClickListener(v ->
                binding.financeSearch.financeSearchInput.setText(""));
        binding.financeSearch.financeSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.financeSearch.financeSearchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                vm.search(s.toString());
            }
        });
        vm.getSearchMode().observe(this, on -> {
            boolean searching = Boolean.TRUE.equals(on);
            binding.financeSearch.getRoot().setVisibility(searching ? View.VISIBLE : View.GONE);
            binding.financeTopBar.getRoot().setVisibility(searching ? View.GONE : View.VISIBLE);
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (searching) {
                binding.financeSearch.financeSearchInput.requestFocus();
                imm.showSoftInput(binding.financeSearch.financeSearchInput, InputMethodManager.SHOW_IMPLICIT);
            } else {
                binding.financeSearch.financeSearchInput.setText("");
                hideKeyboard(binding.financeSearch.financeSearchInput);
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (Boolean.TRUE.equals(vm.getSearchMode().getValue())) {
                    vm.exitSearch();
                } else {
                    finish();
                }
            }
        });
    }

    /** 定位记录：找到后滚动并高亮 1.5 秒，未找到继续等待下一页 */
    private void scrollToHighlight() {
        Long id = vm.getHighlightId().getValue();
        if (id == null) {
            return;
        }
        int pos = adapter.positionOf(id);
        if (pos < 0) {
            return;
        }
        binding.financeList.scrollToPosition(pos);
        adapter.setHighlight(id);
        vm.consumeHighlight();
        binding.financeList.postDelayed(() -> adapter.setHighlight(null), HIGHLIGHT_MS);
    }
}
