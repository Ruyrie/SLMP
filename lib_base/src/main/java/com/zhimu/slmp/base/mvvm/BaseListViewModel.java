package com.zhimu.slmp.base.mvvm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingLiveData;
import androidx.paging.PagingSource;

import java.util.Objects;

/**
 * 分页列表 ViewModel 基类（Paging 3，数据源为 lib_network 的 PagedSource 等 RxPagingSource）。
 * 筛选条件 F 放在 ViewModel 中，条件变化时重新创建分页数据；每页 20 条，到底部前 5 项预取。
 *
 * @param <F> 筛选条件（建议不可变对象并实现 equals）
 * @param <T> 列表项
 */
public abstract class BaseListViewModel<F, T> extends BaseViewModel {

    public static final int PAGE_SIZE = 20;
    public static final int PREFETCH_DISTANCE = 5;

    private final MutableLiveData<F> filter;
    private final LiveData<PagingData<T>> pagingData;
    @Nullable
    private PagingSource<Integer, T> currentSource;

    protected BaseListViewModel(@Nullable F initialFilter) {
        filter = new MutableLiveData<>(initialFilter);
        pagingData = Transformations.switchMap(filter, f -> {
            Pager<Integer, T> pager = new Pager<>(pagingConfig(), () -> {
                currentSource = createPagingSource(f);
                return currentSource;
            });
            return PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), ViewModelKt.getViewModelScope(this));
        });
    }

    /** 按筛选条件创建数据源（通常为 Repository 返回的 PagedSource） */
    @NonNull
    protected abstract PagingSource<Integer, T> createPagingSource(@Nullable F filter);

    @NonNull
    protected PagingConfig pagingConfig() {
        return new PagingConfig(PAGE_SIZE, PREFETCH_DISTANCE, false, PAGE_SIZE);
    }

    /** 分页数据，交给 PagingDataAdapter.submitData(lifecycle, data) */
    public LiveData<PagingData<T>> getPagingData() {
        return pagingData;
    }

    public LiveData<F> getFilter() {
        return filter;
    }

    @Nullable
    public F currentFilter() {
        return filter.getValue();
    }

    /** 修改筛选条件；与当前条件相同时不重建 */
    public void setFilter(@Nullable F newFilter) {
        if (!Objects.equals(filter.getValue(), newFilter)) {
            filter.setValue(newFilter);
        }
    }

    /** 下拉刷新：使当前数据源失效，从第 1 页重新加载 */
    public void refresh() {
        if (currentSource != null) {
            currentSource.invalidate();
        }
    }

    @Override
    public void retry() {
        refresh();
    }
}
