package com.zhimu.slmp.finance.ui.advice;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.finance.R;
import com.zhimu.slmp.finance.databinding.FinanceActivityListPageBinding;
import com.zhimu.slmp.finance.databinding.FinanceViewAdviceHeaderBinding;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.vo.SaleBatchOptionVO;
import com.zhimu.slmp.finance.ui.common.FinancePageActivity;
import com.zhimu.slmp.finance.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.finance.ui.common.SingleViewAdapter;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;

/**
 * E12 AI 出栏建议（图 10-16）。
 */
public class SaleAdviceActivity extends FinancePageActivity {

    static final String REQ_BATCH = "finance_e12_batch";

    private FinanceActivityListPageBinding binding;
    private FinanceViewAdviceHeaderBinding header;
    private SaleAdviceViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FinanceActivityListPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FinanceInjector.factory(), SaleAdviceViewModel.class);
        observeBase(vm);
        setupTopBar(binding.financeTopBar, getString(R.string.finance_e12_title), binding.financeList);
        bindState(binding.financeState, vm);
        observeNav(vm.getOpenPage());

        binding.financeList.setLayoutManager(new LinearLayoutManager(this));
        header = FinanceViewAdviceHeaderBinding.inflate(getLayoutInflater(), binding.financeList, false);
        AdviceHistoryAdapter history = new AdviceHistoryAdapter(a -> vm.openHistory(a.getId()));
        binding.financeList.setAdapter(new ConcatAdapter(new SingleViewAdapter(header.getRoot()),
                history.withLoadStateFooter(new FooterLoadStateAdapter(history::retry, false))));
        vm.getPagingData().observe(this, d -> history.submitData(getLifecycle(), d));
        history.addLoadStateListener(s -> {
            boolean done = s.getRefresh() instanceof LoadState.NotLoading;
            header.financeHistoryEmpty.setVisibility(done && history.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            if (!(s.getRefresh() instanceof LoadState.Loading)) {
                binding.financeRefresh.setRefreshing(false);
            }
            return Unit.INSTANCE;
        });
        binding.financeRefresh.setOnRefreshListener(history::refresh);

        View.OnClickListener pick = v -> showBatchSheet();
        header.financeBatch.setOnClickListener(pick);
        header.financeBatchLayout.setEndIconOnClickListener(pick);
        getSupportFragmentManager().setFragmentResultListener(REQ_BATCH, this,
                (k, r) -> vm.selectBatch(Long.parseLong(OptionSheetDialogFragment.getValue(r))));
        ClickThrottle.setOnClickListener(header.financeCard.financeAiRegenerate, v -> vm.regenerate());
        ClickThrottle.setOnClickListener(header.financeCard.financeAiRegister, v -> vm.openRegister());

        vm.getSelectedBatchId().observe(this, id -> renderBatch());
        vm.getBatchOptions().observe(this, l -> renderBatch());
        vm.getNoBatches().observe(this, none -> {
            boolean n = Boolean.TRUE.equals(none);
            header.financeNoBatch.setVisibility(n ? View.VISIBLE : View.GONE);
            header.financeCard.getRoot().setVisibility(n ? View.GONE : View.VISIBLE);
            header.financeBatchLayout.setVisibility(n ? View.GONE : View.VISIBLE);
        });
        vm.getAdvice().observe(this, a -> renderCard());
        vm.getGenerating().observe(this, g -> renderCard());
        vm.getFailed().observe(this, f -> renderCard());
        vm.getShowHistorySheet().observe(this, a -> AdviceHistoryBottomSheet.show(getSupportFragmentManager(), a));
    }

    private void renderBatch() {
        SaleBatchOptionVO b = vm.selectedBatch();
        header.financeBatch.setText(b == null ? null : b.getBatchNo() + " · " + b.getPenName());
        header.financeBatchLayout.setHelperText(b == null ? null : SaleAdviceViewModel.batchHelper(b));
    }

    private void renderCard() {
        AdviceCardBinder.bind(header.financeCard, vm.getAdvice().getValue(), false,
                Boolean.TRUE.equals(vm.getGenerating().getValue()), Boolean.TRUE.equals(vm.getFailed().getValue()));
    }

    private void showBatchSheet() {
        List<OptionSheetDialogFragment.Option> options = new ArrayList<>();
        List<SaleBatchOptionVO> list = vm.getBatchOptions().getValue();
        if (list != null) {
            for (SaleBatchOptionVO b : list) {
                OptionSheetDialogFragment.Option o = new OptionSheetDialogFragment.Option(String.valueOf(b.getBatchId()),
                        b.getBatchNo() + " · " + b.getPenName(), SaleAdviceViewModel.batchHelper(b));
                if (b.isInWithdrawal()) {
                    o.tag(b.getWithdrawalDaysLeft() == null ? "休药中" : "休药中 · 剩 " + b.getWithdrawalDaysLeft() + " 天",
                            StatusTagView.WARNING);
                }
                options.add(o);
            }
        }
        Long cur = vm.getSelectedBatchId().getValue();
        OptionSheetDialogFragment.builder(REQ_BATCH).title(getString(R.string.finance_e12_batch)).options(options)
                .selected(cur == null ? null : String.valueOf(cur)).build().showSafely(getSupportFragmentManager());
    }
}
