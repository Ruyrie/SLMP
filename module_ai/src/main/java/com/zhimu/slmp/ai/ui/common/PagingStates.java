package com.zhimu.slmp.ai.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.mvvm.UiState;

/**
 * 分页加载状态 → 页面状态：首屏加载中骨架、空、失败；已有内容时刷新失败只提示（返回 null，由调用方 Snackbar）。
 */
public final class PagingStates {

    private PagingStates() {
    }

    /**
     * @param sawLoading 是否已经出现过一次 Loading（首次请求开始前保持骨架）
     * @return 需要渲染的状态；null 表示保持现状
     */
    @Nullable
    public static UiState of(@NonNull CombinedLoadStates states, int itemCount, boolean sawLoading,
                             @Nullable String emptyTitle, @Nullable String emptyMessage) {
        LoadState refresh = states.getRefresh();
        boolean empty = itemCount == 0;
        if (refresh instanceof LoadState.Loading) {
            return empty ? UiState.loading() : null;
        }
        if (!sawLoading) {
            return null;
        }
        if (refresh instanceof LoadState.Error) {
            if (!empty) {
                return null;
            }
            ApiException e = ApiException.from(((LoadState.Error) refresh).getError());
            return UiState.error(e.getMessage(), e.getTraceId(), e.getCode());
        }
        if (empty && states.getAppend().getEndOfPaginationReached()) {
            return UiState.empty(emptyTitle, emptyMessage);
        }
        return empty ? UiState.empty(emptyTitle, emptyMessage) : UiState.content();
    }

    /** 已有内容时刷新失败的提示 */
    @Nullable
    public static String refreshError(@NonNull CombinedLoadStates states, int itemCount) {
        LoadState refresh = states.getRefresh();
        if (refresh instanceof LoadState.Error && itemCount > 0) {
            ApiException e = ApiException.from(((LoadState.Error) refresh).getError());
            return e.isNetwork() ? ApiException.MSG_NETWORK : e.getMessage();
        }
        return null;
    }
}
