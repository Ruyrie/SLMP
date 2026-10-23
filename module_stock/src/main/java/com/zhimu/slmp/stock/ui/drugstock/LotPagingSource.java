package com.zhimu.slmp.stock.ui.drugstock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingState;
import androidx.paging.rxjava3.RxPagingSource;

import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.StockEnums;
import com.zhimu.slmp.stock.model.vo.DrugLotItemVO;
import com.zhimu.slmp.stock.ui.common.ListRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * M05 批号分页（技术准备文档 2.6.4）：无搜索时先按 group = ATTENTION 逐页加载，取完后切 NORMAL，
 * 每组第 1 页前插入标题“需要处理 · 3 个批号”“正常 · 4 个批号”（数量取 tab_counts）；搜索时 group = ALL，不分组。
 * 页键 = 阶段 × 100000 + 页码。
 */
public class LotPagingSource extends RxPagingSource<Integer, ListRow<DrugLotItemVO>> {

    static final int PHASE_ATTENTION = 1;
    static final int PHASE_NORMAL = 2;
    static final int PHASE_ALL = 3;
    private static final int STRIDE = 100000;

    /** 第 1 页到达后回传分组数量（io 线程） */
    public interface CountsListener {
        void onCounts(@Nullable Map<String, Integer> tabCounts);
    }

    private final StockRepository repository;
    @Nullable
    private final String category;
    @Nullable
    private final String keyword;
    @Nullable
    private final CountsListener countsListener;
    @Nullable
    private Map<String, Integer> counts;

    public LotPagingSource(@NonNull StockRepository repository, @Nullable String category, @Nullable String keyword,
                           @Nullable CountsListener countsListener) {
        this.repository = repository;
        this.category = category;
        this.keyword = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        this.countsListener = countsListener;
    }

    @NonNull
    @Override
    public Single<LoadResult<Integer, ListRow<DrugLotItemVO>>> loadSingle(@NonNull LoadParams<Integer> params) {
        int key = params.getKey() == null ? firstKey() : params.getKey();
        return load(key).subscribeOn(Schedulers.io())
                .onErrorReturn(t -> new LoadResult.Error<>(RxResult.toApiException(t)));
    }

    int firstKey() {
        return (keyword != null ? PHASE_ALL : PHASE_ATTENTION) * STRIDE + 1;
    }

    @NonNull
    private Single<LoadResult<Integer, ListRow<DrugLotItemVO>>> load(int key) {
        int phase = key / STRIDE;
        int page = key % STRIDE;
        String group = phase == PHASE_ATTENTION ? StockEnums.LOT_GROUP_ATTENTION
                : phase == PHASE_NORMAL ? StockEnums.LOT_GROUP_NORMAL : StockEnums.LOT_GROUP_ALL;
        return repository.pageDrugLots(group, category, keyword, page, PagedSource.PAGE_SIZE)
                .flatMap(vo -> {
                    if (page == 1 && vo.getTabCounts() != null) {
                        counts = vo.getTabCounts();
                        if (countsListener != null && phase != PHASE_NORMAL) {
                            countsListener.onCounts(counts);
                        }
                    }
                    List<DrugLotItemVO> list = vo.getList() == null ? new ArrayList<>() : vo.getList();
                    if (phase == PHASE_ATTENTION && page == 1 && list.isEmpty() && !vo.isHasMore()) {
                        return load(PHASE_NORMAL * STRIDE + 1);
                    }
                    return Single.just(toResult(vo, list, phase, page));
                });
    }

    @NonNull
    private LoadResult<Integer, ListRow<DrugLotItemVO>> toResult(@NonNull PageVO<DrugLotItemVO> vo,
                                                                 @NonNull List<DrugLotItemVO> list, int phase,
                                                                 int page) {
        List<ListRow<DrugLotItemVO>> rows = new ArrayList<>();
        if (page == 1 && phase != PHASE_ALL && !list.isEmpty()) {
            String group = phase == PHASE_ATTENTION ? StockEnums.LOT_GROUP_ATTENTION : StockEnums.LOT_GROUP_NORMAL;
            Integer n = counts == null ? null : counts.get(group);
            int count = n == null ? (int) vo.getTotal() : n;
            if (count <= 0) {
                count = list.size();
            }
            String title = (phase == PHASE_ATTENTION ? "需要处理" : "正常") + " · " + count + " 个批号";
            rows.add(ListRow.header(title, group));
        }
        for (DrugLotItemVO lot : list) {
            rows.add(ListRow.item(lot, String.valueOf(lot.lotId)));
        }
        Integer next;
        if (vo.isHasMore()) {
            next = phase * STRIDE + page + 1;
        } else if (phase == PHASE_ATTENTION) {
            next = PHASE_NORMAL * STRIDE + 1;
        } else {
            next = null;
        }
        return new LoadResult.Page<>(rows, null, next);
    }

    @Nullable
    @Override
    public Integer getRefreshKey(@NonNull PagingState<Integer, ListRow<DrugLotItemVO>> state) {
        return null;
    }
}
