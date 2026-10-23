package com.zhimu.slmp.finance.ui.receivable;

import android.os.Bundle;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.widget.NoticeView;
import com.zhimu.slmp.base.widget.StatCardView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivityReceivableBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.SaleOrderItemVO;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.FinanceViews;
import com.zhimu.slmp.finance.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.finance.ui.common.ListStateBinder;
import com.zhimu.slmp.finance.ui.common.ReceiptBottomSheet;
import com.zhimu.slmp.finance.ui.common.ReceiptHost;
import com.zhimu.slmp.finance.ui.common.SaleOrderCardAdapter;
import com.zhimu.slmp.finance.ui.common.SingleViewAdapter;
import com.zhimu.slmp.finance.util.FinanceFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * E09 待收款（图 10-13）。
 */
public class ReceivableListActivity extends FinancePageActivity implements ReceiptBottomSheet.Provider {

    private FinanceActivityReceivableBinding binding;
    private ReceivableListViewModel vm;
    private final List<Chip> chips = new ArrayList<>();
    private boolean renderingChips;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FinanceActivityReceivableBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), ReceivableListViewModel.class);
        observeBase(vm);
        setupTopBar(binding.financeTopBar, getString(R.string.finance_e09_title), binding.financeList);
        TopAppBarHelper.inflateMenu(binding.financeTopBar.baseToolbar, R.menu.finance_menu_receivable);
        binding.financeTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            vm.openAllOrders();
            return true;
        });
        observeNav(vm.getOpenPage());

        SaleOrderCardAdapter adapter = new SaleOrderCardAdapter(SaleOrderCardAdapter.MODE_RECEIVABLE,
                new SaleOrderCardAdapter.Listener() {
                    @Override
                    public void onClick(@NonNull SaleOrderItemVO o) {
                        vm.openDetail(o.getId());
                    }

                    @Override
                    public void onReceive(@NonNull SaleOrderItemVO o) {
                        vm.openReceipt(o);
                    }
                });
        NoticeView remind = new NoticeView(this);
        remind.setIcon(com.zhimu.slmp.base.R.drawable.ic_notifications);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        remind.setLayoutParams(lp);
        android.widget.FrameLayout remindBox = new android.widget.FrameLayout(this);
        int pad = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_page_margin);
        remindBox.setPadding(pad, 0, pad, pad);
        remindBox.addView(remind);
        SingleViewAdapter footer = new SingleViewAdapter(remindBox);
        binding.financeList.setLayoutManager(new LinearLayoutManager(this));
        binding.financeList.setAdapter(new ConcatAdapter(adapter.withLoadStateFooter(
                new FooterLoadStateAdapter(adapter::retry, false)), footer));
        ListStateBinder.bind(this, adapter, binding.financeState, binding.financeRefresh, vm, this::emptyText);
        vm.getPagingData().observe(this, d -> adapter.submitData(getLifecycle(), d));
        vm.getRemindRuleText().observe(this, remind::setText);
        vm.getSummary().observe(this, s -> {
            binding.financeTotalCard.setValue(s == null ? "0" : FinanceFormat.num(s.getTotalUnpaidAmount()));
            binding.financeTotalCard.setTrend(ReceivableListViewModel.summaryNote(s), StatCardView.TREND_NONE,
                    StatCardView.DIRECTION_NONE);
        });

        String[][] filters = {{FinanceConst.DUE_FILTER_ALL, "全部"}, {FinanceConst.DUE_FILTER_OVERDUE, "已逾期"},
                {FinanceConst.DUE_FILTER_7D, "7 天内到期"}};
        for (String[] f : filters) {
            Chip c = FinanceViews.filterChip(this, binding.financeFilterChips, f[1], f[0]);
            c.setOnCheckedChangeListener((b, checked) -> {
                if (checked && !renderingChips) {
                    vm.selectFilter(f[0]);
                }
            });
            chips.add(c);
        }
        vm.getDueFilter().observe(this, f -> renderChips());
        vm.getTabCounts().observe(this, m -> renderChips());

        vm.getReceipt().getShowSheet().observe(this, show -> {
            if (Boolean.TRUE.equals(show)) {
                ReceiptBottomSheet.show(getSupportFragmentManager());
            }
        });
    }

    @NonNull
    @Override
    public ReceiptHost receiptHost() {
        return vm;
    }

    @Override
    protected void onChildResult(@NonNull ActivityResult result) {
        vm.onChildResult();
    }

    @NonNull
    private CharSequence emptyText() {
        String f = vm.getDueFilter().getValue();
        if (FinanceConst.DUE_FILTER_OVERDUE.equals(f)) {
            return getString(R.string.finance_e09_empty_overdue);
        }
        if (FinanceConst.DUE_FILTER_7D.equals(f)) {
            return getString(R.string.finance_e09_empty_7d);
        }
        return getString(R.string.finance_e09_empty);
    }

    private void renderChips() {
        renderingChips = true;
        String cur = vm.getDueFilter().getValue();
        Map<String, Integer> counts = vm.getTabCounts().getValue();
        String[] names = {"全部", "已逾期", "7 天内到期"};
        for (int i = 0; i < chips.size(); i++) {
            Chip c = chips.get(i);
            Integer n = counts == null ? null : counts.get(String.valueOf(c.getTag()));
            c.setText(n == null ? names[i] : names[i] + " " + n);
            c.setChecked(String.valueOf(c.getTag()).equals(cur));
        }
        renderingChips = false;
    }
}
