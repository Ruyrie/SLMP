package com.zhimu.slmp.finance.ui.salelist;

import android.content.Intent;
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
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.finance.FinanceExtras;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivitySaleListBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.SaleOrderItemVO;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.finance.ui.common.ListStateBinder;
import com.zhimu.slmp.finance.ui.common.SaleOrderCardAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * E14 出栏单列表（原型未绘制，按 UI 设计文档 2.2 补充设计）。
 */
public class SaleOrderListActivity extends FinancePageActivity {

    static final long HIGHLIGHT_MS = 1500;

    private FinanceActivitySaleListBinding binding;
    private SaleOrderListViewModel vm;
    private SaleOrderCardAdapter adapter;
    private final List<Chip> chips = new ArrayList<>();
    private boolean renderingChips;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FinanceActivitySaleListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), SaleOrderListViewModel.class);
        observeBase(vm);
        setupTopBar(binding.financeTopBar, getString(R.string.finance_e14_title), binding.financeList);
        TopAppBarHelper.inflateMenu(binding.financeTopBar.baseToolbar, R.menu.finance_menu_search);
        binding.financeTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            vm.enterSearch();
            return true;
        });
        observeNav(vm.getOpenPage());

        adapter = new SaleOrderCardAdapter(SaleOrderCardAdapter.MODE_LIST, new SaleOrderCardAdapter.Listener() {
            @Override
            public void onClick(@NonNull SaleOrderItemVO o) {
                vm.openDetail(o.getId());
            }

            @Override
            public void onReceive(@NonNull SaleOrderItemVO o) {
            }
        });
        binding.financeList.setLayoutManager(new LinearLayoutManager(this));
        binding.financeList.setAdapter(adapter.withLoadStateFooter(new FooterLoadStateAdapter(adapter::retry, true)));
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (positionStart > 0) {
                    binding.financeList.post(() -> adapter.notifyItemChanged(positionStart - 1));
                } else {
                    binding.financeList.scrollToPosition(0);
                }
                scrollToHighlight();
            }
        });
        ListStateBinder.bind(this, adapter, binding.financeState, binding.financeRefresh, vm,
                () -> SaleOrderListViewModel.emptyText(String.valueOf(vm.getStatus().getValue()), vm.currentKeyword()));
        adapter.addLoadStateListener(s -> {
            boolean empty = s.getRefresh() instanceof androidx.paging.LoadState.NotLoading && adapter.getItemCount() == 0;
            String kw = vm.currentKeyword();
            binding.financeSearchAll.setVisibility(empty && kw != null
                    && !FinanceConst.TYPE_ALL.equals(vm.getStatus().getValue()) ? View.VISIBLE : View.GONE);
            Map<String, Integer> counts = vm.getTabCounts().getValue();
            Integer all = counts == null ? null : counts.get(FinanceConst.TYPE_ALL);
            boolean noOrders = empty && kw == null && FinanceConst.TYPE_ALL.equals(vm.getStatus().getValue())
                    && (all == null || all == 0);
            binding.financeChipsScroll.setVisibility(noOrders ? View.GONE : View.VISIBLE);
            return kotlin.Unit.INSTANCE;
        });
        vm.getPagingData().observe(this, d -> adapter.submitData(getLifecycle(), d));
        vm.getMonthGroups().observe(this, adapter::setGroups);
        vm.getHighlightId().observe(this, id -> scrollToHighlight());
        ClickThrottle.setOnClickListener(binding.financeFab, v -> vm.openCreate());
        binding.financeSearchAll.setOnClickListener(v -> vm.searchInAll());
        binding.financeList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 4) {
                    binding.financeFab.shrink();
                } else if (dy < -4) {
                    binding.financeFab.extend();
                }
            }
        });

        for (String s : SaleOrderListViewModel.STATUSES) {
            Chip c = FinanceViews.filterChip(this, binding.financeStatusChips, SaleOrderListViewModel.chipText(s, null), s);
            c.setOnCheckedChangeListener((b, checked) -> {
                if (checked && !renderingChips) {
                    vm.setStatus(s);
                    binding.financeChipsScroll.post(() -> binding.financeChipsScroll.smoothScrollTo(
                            Math.max(0, c.getLeft() - 48), 0));
                }
            });
            chips.add(c);
        }
        vm.getStatus().observe(this, s -> renderChips());
        vm.getTabCounts().observe(this, m -> renderChips());
        setupSearch();
    }

    @Override
    protected void onChildResult(@NonNull ActivityResult result) {
        Intent data = result.getData();
        long created = data == null ? 0 : data.getLongExtra(FinanceExtras.RESULT_CREATED_ID, 0);
        if (created > 0) {
            vm.onCreated(created);
        } else {
            vm.onDetailReturned();
        }
    }

    private void renderChips() {
        renderingChips = true;
        String cur = vm.getStatus().getValue();
        Map<String, Integer> counts = vm.getTabCounts().getValue();
        for (Chip c : chips) {
            String code = String.valueOf(c.getTag());
            c.setText(SaleOrderListViewModel.chipText(code, counts));
            c.setChecked(code.equals(cur));
        }
        renderingChips = false;
    }

    private void setupSearch() {
        binding.financeSearch.financeSearchInput.setHint(R.string.finance_e14_search_hint);
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
                adapter.setKeyword(s.toString().trim().isEmpty() ? null : s.toString().trim());
                vm.search(s.toString());
            }
        });
        vm.getSearchMode().observe(this, on -> {
            boolean searching = Boolean.TRUE.equals(on);
            binding.financeSearch.getRoot().setVisibility(searching ? View.VISIBLE : View.GONE);
            binding.financeTopBar.getRoot().setVisibility(searching ? View.GONE : View.VISIBLE);
            if (searching) {
                binding.financeFab.hide();
                binding.financeSearch.financeSearchInput.requestFocus();
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                        .showSoftInput(binding.financeSearch.financeSearchInput, InputMethodManager.SHOW_IMPLICIT);
            } else {
                binding.financeFab.show();
                binding.financeSearch.financeSearchInput.setText("");
                adapter.setKeyword(null);
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
