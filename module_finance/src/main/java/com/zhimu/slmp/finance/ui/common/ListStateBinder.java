package com.zhimu.slmp.finance.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.paging.PagingDataAdapter;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.widget.StateLayout;

import java.util.function.Supplier;

import kotlin.Unit;

/**
 * 分页列表的整页状态：首屏加载显示骨架屏、失败显示加载失败（40303 交给 ViewModel 切换无权查看）、
 * 为空显示空状态文案，下拉刷新结束时收起指示器。ViewModel 已是无权查看时不覆盖。
 */
public final class ListStateBinder {

    private ListStateBinder() {
    }

    public static void bind(@NonNull LifecycleOwner owner, @NonNull PagingDataAdapter<?, ?> adapter,
                            @NonNull StateLayout state, @Nullable SwipeRefreshLayout refresh,
                            @NonNull FinanceListViewModel<?, ?> vm, @NonNull Supplier<CharSequence> emptyText) {
        if (refresh != null) {
            refresh.setOnRefreshListener(adapter::refresh);
        }
        state.setOnRetryListener(v -> adapter.retry());
        state.setOnNoPermissionActionListener(v -> {
            if (state.getContext() instanceof android.app.Activity) {
                ((android.app.Activity) state.getContext()).finish();
            }
        });
        vm.getUiState().observe(owner, s -> {
            if (s != null && s.is(UiState.Status.NO_PERMISSION)) {
                state.render(s);
            }
        });
        adapter.addLoadStateListener((CombinedLoadStates states) -> {
            UiState cur = vm.getUiState().getValue();
            if (cur != null && cur.is(UiState.Status.NO_PERMISSION)) {
                return Unit.INSTANCE;
            }
            LoadState r = states.getRefresh();
            boolean empty = adapter.getItemCount() == 0;
            if (r instanceof LoadState.Loading) {
                if (empty && (refresh == null || !refresh.isRefreshing())) {
                    state.showLoading();
                }
            } else if (r instanceof LoadState.Error) {
                if (refresh != null) {
                    refresh.setRefreshing(false);
                }
                Throwable t = ((LoadState.Error) r).getError();
                if (!vm.onListError(t) && empty) {
                    ApiException e = ApiException.from(t);
                    state.showError(e.getMessage(), e.getTraceId());
                }
            } else {
                if (refresh != null) {
                    refresh.setRefreshing(false);
                }
                if (empty) {
                    state.showEmpty(null, emptyText.get());
                } else {
                    state.showContent();
                }
            }
            return Unit.INSTANCE;
        });
    }
}
