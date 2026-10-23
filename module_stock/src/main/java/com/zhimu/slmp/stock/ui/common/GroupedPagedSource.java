package com.zhimu.slmp.stock.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingState;
import androidx.paging.rxjava3.RxPagingSource;

import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 按分组键插入标题行的分页源（页码从 1 开始，每页 20 条，has_more 为 false 时结束）：
 * 例如流水按 biz_date 分组“10月19日 周一 · 今天”。只向后加载，记住上一页最后一项的分组键，跨页同组不重复插标题。
 */
public class GroupedPagedSource<T> extends RxPagingSource<Integer, ListRow<T>> {

    private final PagedSource.Loader<T> loader;
    @Nullable
    private final Function<T, String> groupOf;
    private final Function<T, String> keyOf;
    @Nullable
    private String lastGroup;

    /**
     * @param groupOf 分组标题；为 null 时不插标题
     * @param keyOf   项的稳定键
     */
    public GroupedPagedSource(@NonNull PagedSource.Loader<T> loader, @Nullable Function<T, String> groupOf,
                              @NonNull Function<T, String> keyOf) {
        this.loader = loader;
        this.groupOf = groupOf;
        this.keyOf = keyOf;
    }

    @NonNull
    @Override
    public Single<LoadResult<Integer, ListRow<T>>> loadSingle(@NonNull LoadParams<Integer> params) {
        final int page = params.getKey() == null ? 1 : params.getKey();
        return loader.load(page, PagedSource.PAGE_SIZE)
                .subscribeOn(Schedulers.io())
                .map(vo -> toResult(vo, page))
                .onErrorReturn(t -> new LoadResult.Error<>(RxResult.toApiException(t)));
    }

    @NonNull
    private LoadResult<Integer, ListRow<T>> toResult(@NonNull PageVO<T> vo, int page) {
        if (page == 1) {
            lastGroup = null;
        }
        List<ListRow<T>> rows = new ArrayList<>();
        if (vo.getList() != null) {
            for (T t : vo.getList()) {
                if (groupOf != null) {
                    String g = groupOf.apply(t);
                    if (g != null && !g.equals(lastGroup)) {
                        rows.add(ListRow.header(g, g));
                        lastGroup = g;
                    }
                }
                rows.add(ListRow.item(t, keyOf.apply(t)));
            }
        }
        return new LoadResult.Page<>(rows, null, vo.isHasMore() ? page + 1 : null);
    }

    @Nullable
    @Override
    public Integer getRefreshKey(@NonNull PagingState<Integer, ListRow<T>> state) {
        return null;
    }
}
