package com.zhimu.slmp.network.paging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingState;
import androidx.paging.rxjava3.RxPagingSource;

import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 通用 PageVO 分页数据源（Paging 3 + RxJava3）：页码从 1 开始，每页 20 条，has_more 为 false 时结束。
 * <pre>
 * new PagedSource&lt;&gt;((page, size) -&gt; api.listMembers(farmId, status, page, size).compose(RxResult.unwrap()));
 * </pre>
 */
public class PagedSource<T> extends RxPagingSource<Integer, T> {

    public static final int PAGE_SIZE = 20;

    /** 按页码加载一页 */
    public interface Loader<T> {
        @NonNull
        Single<PageVO<T>> load(int page, int size);
    }

    private final Loader<T> loader;
    @Nullable
    private final Consumer<PageVO<T>> onFirstPage;

    public PagedSource(@NonNull Loader<T> loader) {
        this(loader, null);
    }

    /**
     * @param onFirstPage 第 1 页到达时回调（读取 total、tab_counts），在 io 线程调用
     */
    public PagedSource(@NonNull Loader<T> loader, @Nullable Consumer<PageVO<T>> onFirstPage) {
        this.loader = loader;
        this.onFirstPage = onFirstPage;
    }

    @NonNull
    @Override
    public Single<LoadResult<Integer, T>> loadSingle(@NonNull LoadParams<Integer> params) {
        final int page = params.getKey() == null ? 1 : params.getKey();
        return loader.load(page, PAGE_SIZE)
                .subscribeOn(Schedulers.io())
                .map(vo -> {
                    if (page == 1 && onFirstPage != null) {
                        onFirstPage.accept(vo);
                    }
                    return toResult(vo, page);
                })
                .onErrorReturn(t -> new LoadResult.Error<>(RxResult.toApiException(t)));
    }

    @NonNull
    private LoadResult<Integer, T> toResult(@NonNull PageVO<T> vo, int page) {
        return new LoadResult.Page<>(vo.getList(), page == 1 ? null : page - 1, vo.isHasMore() ? page + 1 : null);
    }

    @Nullable
    @Override
    public Integer getRefreshKey(@NonNull PagingState<Integer, T> state) {
        return null;
    }
}
