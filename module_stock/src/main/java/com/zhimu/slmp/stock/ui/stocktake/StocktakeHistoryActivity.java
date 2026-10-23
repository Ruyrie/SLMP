package com.zhimu.slmp.stock.ui.stocktake;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.databinding.StockActivityListBinding;
import com.zhimu.slmp.stock.databinding.StockItemRowBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.vo.StocktakeVO;
import com.zhimu.slmp.stock.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.stock.ui.common.RowPagingAdapter;
import com.zhimu.slmp.stock.ui.common.StockUi;
import com.zhimu.slmp.stock.ui.record.StockRecordActivity;

import kotlin.Unit;
import com.zhimu.slmp.common.router.RouterPath;

/** 盘点记录列表（M04 history）：本组件内页面，显式 Intent 打开。 */
public class StocktakeHistoryActivity extends BaseActivity {

    private StockActivityListBinding binding;
    private RowPagingAdapter<StocktakeVO> adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = StockActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        StocktakeHistoryViewModel vm = obtainViewModel(StockInjector.factory(), StocktakeHistoryViewModel.class);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar, getString(R.string.stock_stocktake_history_title));
        adapter = new RowPagingAdapter<StocktakeVO>(vm::onItemClick) {
            @Override
            protected void bindItem(@NonNull StockItemRowBinding b, @NonNull StocktakeVO item) {
                StockUi.bindRow(b, com.zhimu.slmp.base.R.drawable.ic_receipt_long, ListRowView.TONE_NEUTRAL,
                        StocktakeHistoryViewModel.title(item), StocktakeHistoryViewModel.subtitle(item),
                        item.itemCount + " 种", null, StatusTagView.NEUTRAL, 0);
            }
        };
        binding.stockList.setLayoutManager(new LinearLayoutManager(this));
        binding.stockList.addItemDecoration(new RowPagingAdapter.Decoration(this, adapter, 0));
        binding.stockList.setAdapter(adapter.withLoadStateFooter(new FooterLoadStateAdapter(adapter::retry)));
        adapter.addLoadStateListener(this::onLoadStates);
        vm.getPagingData().observe(this, d -> adapter.submitData(getLifecycle(), d));
        vm.getOpenRecord().observe(this, id -> startActivity(new Intent(this, StockRecordActivity.class)
                .putExtra(RouterPath.EXTRA_RECORD_KIND, StockExtras.KIND_STOCKTAKE)
                .putExtra(RouterPath.EXTRA_RECORD_ID, id.longValue())));
        binding.stockRefresh.setOnChildScrollUpCallback((p, c) -> binding.stockList.canScrollVertically(-1));
        binding.stockRefresh.setOnRefreshListener(adapter::refresh);
        binding.stockState.setOnRetryListener(v -> adapter.retry());
    }

    private Unit onLoadStates(@NonNull CombinedLoadStates states) {
        LoadState refresh = states.getRefresh();
        binding.stockRefresh.setRefreshing(refresh instanceof LoadState.Loading && adapter.getItemCount() > 0);
        if (refresh instanceof LoadState.Error && adapter.getItemCount() == 0) {
            binding.stockState.showError(((LoadState.Error) refresh).getError().getMessage(), null);
        } else if (refresh instanceof LoadState.Loading && adapter.getItemCount() == 0) {
            binding.stockState.showLoading();
        } else if (refresh instanceof LoadState.NotLoading && adapter.getItemCount() == 0) {
            binding.stockState.showEmpty(getString(R.string.stock_stocktake_history_empty), null);
        } else {
            binding.stockState.showContent();
        }
        return Unit.INSTANCE;
    }
}
