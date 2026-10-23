package com.zhimu.slmp.stock.ui.flow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.StateLayout;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.stock.R;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.databinding.StockActivityListBinding;
import com.zhimu.slmp.stock.di.StockInjector;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.stock.ui.common.RowPagingAdapter;
import com.zhimu.slmp.stock.ui.common.StockNav;

import kotlin.Unit;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * 出入库流水列表（M01“全部流水”、饲料卡“流水”、M05 菜单“全部出入库流水”）：顶部单选 Chip，按日期分组，每页 20 条。
 * 本组件内页面（没有页面编号），显式 Intent 打开；点记录按 FlowRouter 跳转，返回 RESULT_OK 时刷新并回传给上一页。
 */
public class StockFlowListActivity extends BaseActivity {

    private StockActivityListBinding binding;
    private StockFlowListViewModel vm;
    private FlowPagingAdapter adapter;
    private ActivityResultLauncher<Intent> recordLauncher;

    @NonNull
    public static Intent intent(@NonNull Context context, @NonNull String materialType, @Nullable Long filterId,
                                @Nullable String title) {
        Intent i = new Intent(context, StockFlowListActivity.class)
                .putExtra(RouterPath.EXTRA_MATERIAL_TYPE, materialType)
                .putExtra(RouterPath.EXTRA_TITLE, title);
        if (filterId != null) {
            i.putExtra(StockEnums.MATERIAL_DRUG.equals(materialType) ? RouterPath.EXTRA_DRUG_ID
                    : RouterPath.EXTRA_FEED_ID, filterId.longValue());
        }
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = StockActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(StockInjector.factory(), StockFlowListViewModel.class);
        observeBase(vm);
        String title = getIntent().getStringExtra(RouterPath.EXTRA_TITLE);
        TopAppBarHelper.setupBack(binding.stockTopBar.baseToolbar,
                title == null || title.isEmpty() ? getString(R.string.stock_flow_all_title) : title);
        recordLauncher = Router.registerForResult(this, r -> {
            if (r.getResultCode() == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK);
                adapter.refresh();
            }
        });

        buildChips();
        adapter = new FlowPagingAdapter(false, vm::onItemClick);
        binding.stockList.setLayoutManager(new LinearLayoutManager(this));
        binding.stockList.addItemDecoration(new RowPagingAdapter.Decoration(this, adapter, 0));
        binding.stockList.setAdapter(adapter.withLoadStateFooter(new FooterLoadStateAdapter(adapter::retry)));
        adapter.addLoadStateListener(this::onLoadStates);
        vm.getPagingData().observe(this, data -> adapter.submitData(getLifecycle(), data));
        vm.getOpenRecord().observe(this, f -> StockNav.openFlow(this, recordLauncher, f));

        binding.stockRefresh.setOnChildScrollUpCallback((p, c) -> binding.stockList.canScrollVertically(-1));
        binding.stockRefresh.setOnRefreshListener(adapter::refresh);
        binding.stockState.setOnRetryListener(v -> adapter.retry());
    }

    private void buildChips() {
        binding.stockChipScroll.setVisibility(android.view.View.VISIBLE);
        binding.stockChips.removeAllViews();
        String current = vm.currentFilter();
        for (String[] g : vm.groups()) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.stock_item_filter_chip, binding.stockChips, false);
            chip.setId(android.view.View.generateViewId());
            chip.setText(g[1]);
            chip.setTag(g[0]);
            chip.setCheckable(true);
            chip.setChecked(g[0].equals(current));
            binding.stockChips.addView(chip);
        }
        binding.stockChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            Object tag = group.findViewById(ids.get(0)).getTag();
            if (tag instanceof String) {
                vm.setGroup((String) tag);
                binding.stockList.scrollToPosition(0);
            }
        });
    }

    private Unit onLoadStates(@NonNull CombinedLoadStates states) {
        LoadState refresh = states.getRefresh();
        binding.stockRefresh.setRefreshing(refresh instanceof LoadState.Loading && adapter.getItemCount() > 0);
        StateLayout state = binding.stockState;
        if (refresh instanceof LoadState.Error && adapter.getItemCount() == 0) {
            Throwable t = ((LoadState.Error) refresh).getError();
            state.showError(t.getMessage(), null);
        } else if (refresh instanceof LoadState.Loading && adapter.getItemCount() == 0) {
            state.showLoading();
        } else if (refresh instanceof LoadState.NotLoading && adapter.getItemCount() == 0) {
            boolean all = StockEnums.GROUP_ALL.equals(vm.currentFilter());
            state.showEmpty(all ? getString(R.string.stock_flow_empty) : getString(R.string.stock_flow_empty_group),
                    null);
        } else {
            state.showContent();
        }
        return Unit.INSTANCE;
    }
}
