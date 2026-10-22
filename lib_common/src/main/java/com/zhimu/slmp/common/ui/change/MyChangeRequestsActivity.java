package com.zhimu.slmp.common.ui.change;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.paging.LoadStateAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.StateLayout;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.R;
import com.zhimu.slmp.common.databinding.CommonChangeActivityListBinding;
import com.zhimu.slmp.common.databinding.CommonChangeItemFooterBinding;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.ui.change.model.vo.ChangeRequestItemVO;

import java.util.Map;

import kotlin.Unit;

/**
 * U02 我的修改申请（RouterPath.U02）：筛选 Chip、下拉刷新、滚动到底自动加载下一页；空状态“去栏舍与存栏”；
 * 筛选后为空“没有已驳回的修改申请”+“查看全部”；点卡片进入原记录详情（目前按批次打开 L06），“重新提交”进入 U01。
 */
public class MyChangeRequestsActivity extends BaseActivity {

    private MyChangeRequestsViewModel vm;
    private CommonChangeActivityListBinding binding;
    private ChangeRequestAdapter adapter;
    private FooterAdapter footer;
    private boolean sawLoading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(ChangeComponent.Injector.factory(this), MyChangeRequestsViewModel.class);
        binding = CommonChangeActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.commonChangeListTopBar.baseToolbar, getString(R.string.common_change_list_title));
        TopAppBarHelper.bindLiftOnScroll(binding.commonChangeListTopBar.baseAppBar, binding.commonChangeList);

        adapter = new ChangeRequestAdapter(vm::onItemClick, vm::onResubmitClick);
        footer = new FooterAdapter();
        binding.commonChangeList.setLayoutManager(new LinearLayoutManager(this));
        binding.commonChangeList.addItemDecoration(new GroupedListDecoration(this));
        binding.commonChangeList.setAdapter(adapter.withLoadStateFooter(footer));
        vm.getPagingData().observe(this, data -> adapter.submitData(getLifecycle(), data));
        adapter.addLoadStateListener(this::onLoadStates);

        binding.commonChangeRefresh.setOnRefreshListener(adapter::refresh);
        // 列表包在 StateLayout 中，下拉刷新按列表本身是否还能上滑判断
        binding.commonChangeRefresh.setOnChildScrollUpCallback((parent, child) ->
                binding.commonChangeList.canScrollVertically(-1));
        binding.commonChangeListState.render(UiState.loading());
        binding.commonChangeListState.setOnRetryListener(v -> adapter.retry());
        binding.commonChangeFilters.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.common_change_filter_all : ids.get(0);
            vm.selectStatus(statusOf(id));
            binding.commonChangeList.scrollToPosition(0);
        });
        vm.getTabCounts().observe(this, this::renderCounts);
        vm.getOpenRecord().observe(this, this::openRecord);
        vm.getOpenResubmit().observe(this, id -> {
            Bundle extras = new Bundle();
            extras.putLong(RouterPath.EXTRA_REQUEST_ID, id);
            Router.open(this, RouterPath.U01, extras);
        });
    }

    @Nullable
    private static String statusOf(int chipId) {
        if (chipId == R.id.common_change_filter_pending) {
            return ChangeRequestItemVO.STATUS_PENDING;
        }
        if (chipId == R.id.common_change_filter_approved) {
            return ChangeRequestItemVO.STATUS_APPROVED;
        }
        if (chipId == R.id.common_change_filter_rejected) {
            return ChangeRequestItemVO.STATUS_REJECTED;
        }
        return null;
    }

    private void renderCounts(@Nullable Map<String, Integer> counts) {
        setChip(binding.commonChangeFilterPending, R.string.common_change_filter_pending, counts, ChangeRequestItemVO.STATUS_PENDING);
        setChip(binding.commonChangeFilterApproved, R.string.common_change_filter_approved, counts, ChangeRequestItemVO.STATUS_APPROVED);
        setChip(binding.commonChangeFilterRejected, R.string.common_change_filter_rejected, counts, ChangeRequestItemVO.STATUS_REJECTED);
    }

    private void setChip(@NonNull Chip chip, int labelRes, @Nullable Map<String, Integer> counts, @NonNull String key) {
        Integer n = counts == null ? null : counts.get(key);
        String label = getString(labelRes);
        chip.setText(n == null ? label : getString(R.string.common_change_filter_count, label, n));
    }

    private Unit onLoadStates(@NonNull CombinedLoadStates states) {
        LoadState refresh = states.getRefresh();
        StateLayout state = binding.commonChangeListState;
        boolean empty = adapter.getItemCount() == 0;
        if (refresh instanceof LoadState.Loading) {
            sawLoading = true;
            if (empty) {
                state.render(UiState.loading());
            }
            return Unit.INSTANCE;
        }
        if (!sawLoading) {
            // 首次请求开始前的初始状态，保持骨架屏
            return Unit.INSTANCE;
        }
        binding.commonChangeRefresh.setRefreshing(false);
        if (refresh instanceof LoadState.Error) {
            ApiException e = ApiException.from(((LoadState.Error) refresh).getError());
            if (empty) {
                state.render(UiState.error(getString(R.string.common_change_list_error_message), e.getTraceId(), e.getCode()));
            } else {
                showMessage(e.getMessage());
            }
            return Unit.INSTANCE;
        }
        if (empty) {
            String status = vm.currentFilter();
            if (status == null) {
                binding.commonChangeFilterBar.setVisibility(View.GONE);
                state.setEmpty(com.zhimu.slmp.base.R.drawable.ic_edit_note, getString(R.string.common_change_empty_title),
                        getString(R.string.common_change_empty_message), getString(R.string.common_change_empty_action),
                        com.zhimu.slmp.base.R.drawable.ic_fence, StateLayout.EMPTY_ACTION_TONAL,
                        v -> Router.open(this, RouterPath.L03));
            } else {
                binding.commonChangeFilterBar.setVisibility(View.VISIBLE);
                state.setEmpty(com.zhimu.slmp.base.R.drawable.ic_edit_note,
                        getString(R.string.common_change_filter_empty, labelOf(status)), null,
                        getString(R.string.common_change_view_all), 0, StateLayout.EMPTY_ACTION_TEXT,
                        v -> binding.commonChangeFilters.check(R.id.common_change_filter_all));
            }
            state.render(UiState.empty(null, null));
        } else {
            binding.commonChangeFilterBar.setVisibility(View.VISIBLE);
            state.render(UiState.content());
        }
        footer.setTotal(vm.getTotal().getValue() == null ? adapter.getItemCount() : vm.getTotal().getValue());
        return Unit.INSTANCE;
    }

    @NonNull
    private String labelOf(@NonNull String status) {
        switch (status) {
            case ChangeRequestItemVO.STATUS_PENDING:
                return getString(R.string.common_change_filter_pending);
            case ChangeRequestItemVO.STATUS_APPROVED:
                return getString(R.string.common_change_filter_approved);
            default:
                return getString(R.string.common_change_filter_rejected);
        }
    }

    /** 进入原记录：有批次时打开 L06 批次档案（各记录类型的详情页路由待各模块约定） */
    private void openRecord(@NonNull ChangeRequestItemVO item) {
        Bundle extras = new Bundle();
        if (item.getBatchId() != null) {
            extras.putLong(RouterPath.EXTRA_BATCH_ID, item.getBatchId());
            Router.open(this, RouterPath.L06, extras);
        } else {
            extras.putString(RouterPath.EXTRA_BIZ_TYPE, item.getBizType());
            extras.putLong(RouterPath.EXTRA_BIZ_ID, item.getBizId());
            Router.openPage(this, null, extras);
        }
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (MyChangeRequestsViewModel.ACTION_SWITCH_BACK.equals(actionId)) {
            vm.switchBack();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从 U01 重新提交返回后刷新
        if (adapter.getItemCount() > 0) {
            adapter.refresh();
        }
    }

    /** 列表底部：加载中 / 已显示全部 N 条 / 加载失败，点按重试 */
    private final class FooterAdapter extends LoadStateAdapter<FooterAdapter.Holder> {

        private int total;

        void setTotal(int total) {
            this.total = total;
        }

        @Override
        public boolean displayLoadStateAsItem(@NonNull LoadState loadState) {
            return loadState instanceof LoadState.Loading || loadState instanceof LoadState.Error
                    || (loadState instanceof LoadState.NotLoading && loadState.getEndOfPaginationReached()
                    && adapter.getItemCount() > 0);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, @NonNull LoadState loadState) {
            return new Holder(CommonChangeItemFooterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, @NonNull LoadState loadState) {
            boolean loading = loadState instanceof LoadState.Loading;
            holder.b.commonChangeFooterProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loadState instanceof LoadState.Error) {
                holder.b.commonChangeFooterText.setText(R.string.common_change_footer_retry);
                holder.itemView.setOnClickListener(v -> adapter.retry());
            } else if (!loading) {
                holder.b.commonChangeFooterText.setText(getString(R.string.common_change_footer_all,
                        Math.max(total, adapter.getItemCount())));
                holder.itemView.setOnClickListener(null);
            } else {
                holder.b.commonChangeFooterText.setText(null);
                holder.itemView.setOnClickListener(null);
            }
        }

        final class Holder extends RecyclerView.ViewHolder {
            final CommonChangeItemFooterBinding b;

            Holder(@NonNull CommonChangeItemFooterBinding binding) {
                super(binding.getRoot());
                this.b = binding;
            }
        }
    }
}
