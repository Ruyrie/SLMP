package com.zhimu.slmp.herd.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.rxjava3.PagingRx;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.herd.data.paging.PenStockPagingSource;
import com.zhimu.slmp.herd.model.vo.CategoryStockVO;
import com.zhimu.slmp.herd.model.vo.MyPensVO;
import com.zhimu.slmp.herd.model.vo.PenStockItemVO;
import com.zhimu.slmp.herd.model.vo.ProductionOverviewVO;
import com.zhimu.slmp.herd.model.vo.StockSummaryVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 生产页与存栏查询（API-L01–L05）。Retrofit 已在 io 线程执行。
 */
@FeatureScope
public class StockRepository {

    private final HerdApi api;

    @Inject
    public StockRepository(@NonNull HerdApi api) {
        this.api = api;
    }

    /** API-L01 */
    @NonNull
    public Single<ProductionOverviewVO> getOverview() {
        return api.getOverview().compose(RxResult.unwrap());
    }

    /** API-L02 */
    @NonNull
    public Single<MyPensVO> getMyPens() {
        return api.getMyPens().compose(RxResult.unwrap());
    }

    /** API-L03 */
    @NonNull
    public Single<StockSummaryVO> getStockSummary() {
        return api.getStockSummary().compose(RxResult.unwrap());
    }

    /** API-L04 */
    @NonNull
    public Flowable<PagingData<PenStockItemVO>> pagePenStocks(@Nullable String sort, @Nullable String keyword) {
        return pagePenStocks(sort, keyword, null);
    }

    /** API-L04；onFirstPage 在第 1 页到达时回调（io 线程），读取 total */
    @NonNull
    public Flowable<PagingData<PenStockItemVO>> pagePenStocks(@Nullable String sort, @Nullable String keyword,
                                                              @Nullable Consumer<PageVO<PenStockItemVO>> onFirstPage) {
        Pager<Integer, PenStockItemVO> pager = new Pager<>(pagingConfig(),
                () -> new PenStockPagingSource(api, sort, emptyToNull(keyword), onFirstPage));
        return PagingRx.getFlowable(pager);
    }

    /** API-L05 */
    @NonNull
    public Single<List<CategoryStockVO>> getCategoryStocks(@Nullable String keyword) {
        return api.getStockCategories(emptyToNull(keyword)).compose(RxResult.unwrap());
    }

    @NonNull
    static PagingConfig pagingConfig() {
        return new PagingConfig(PagedSource.PAGE_SIZE, 5, false, PagedSource.PAGE_SIZE);
    }

    @Nullable
    static String emptyToNull(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
