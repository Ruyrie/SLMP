package com.zhimu.slmp.finance.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.request.MarketPriceQuery;
import com.zhimu.slmp.finance.model.request.SaleAdviceRequest;
import com.zhimu.slmp.finance.model.vo.DailyPriceVO;
import com.zhimu.slmp.finance.model.vo.MarketFilterVO;
import com.zhimu.slmp.finance.model.vo.MarketTrendVO;
import com.zhimu.slmp.finance.model.vo.SaleAdviceVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * 行情与 AI 出栏建议仓库：API-E30–E36。
 */
@FeatureScope
public class MarketRepository {

    /** 轮询间隔与上限（技术准备文档 2.6.3：每 2 秒，90 秒上限；服务端 60 秒置 FAILED） */
    public static final long POLL_INTERVAL_SEC = 2;
    public static final long POLL_MAX_SEC = 90;

    private final FinanceApi api;

    @Inject
    public MarketRepository(@NonNull FinanceApi api) {
        this.api = api;
    }

    /** API-E30 */
    @NonNull
    public Single<MarketFilterVO> getFilters() {
        return api.getMarketFilters().compose(RxResult.unwrap());
    }

    /** API-E31 */
    @NonNull
    public Single<MarketTrendVO> getTrend(@NonNull MarketPriceQuery q) {
        return api.getTrend(q.getProvinceCode(), q.getCategoryId(), q.getBreedId(), q.getPriceType(),
                q.getRangeDays()).compose(RxResult.unwrap());
    }

    /** API-E32 分页数据源 */
    @NonNull
    public PagedSource<DailyPriceVO> pagingDailyPrices(@NonNull MarketPriceQuery q) {
        return new PagedSource<>((page, size) -> api.getDailyPrices(q.getProvinceCode(), q.getCategoryId(),
                q.getBreedId(), q.getPriceType(), page, size).compose(RxResult.unwrap()));
    }

    /** API-E33 */
    @NonNull
    public Single<SaleAdviceVO> getLatestAdvice(long batchId) {
        return api.getLatestAdvice(batchId).compose(RxResult.unwrap());
    }

    /** API-E34 */
    @NonNull
    public Single<SaleAdviceVO> regenerateAdvice(@NonNull SaleAdviceRequest r) {
        return api.regenerateAdvice(r).compose(RxResult.unwrap());
    }

    /** API-E35 分页数据源（batchId 为空时为当前养殖场全部批次） */
    @NonNull
    public PagedSource<SaleAdviceVO> pagingAdviceHistory(@Nullable Long batchId) {
        return new PagedSource<>((page, size) -> api.listAdvices(batchId, page, size).compose(RxResult.unwrap()));
    }

    /** API-E36 */
    @NonNull
    public Single<SaleAdviceVO> getAdvice(long id) {
        return api.getAdvice(id).compose(RxResult.unwrap());
    }

    /**
     * API-E36 轮询：0 秒起每 2 秒一次，状态不为 GENERATING 时结束；90 秒后仍在生成则正常结束（最后一项仍为 GENERATING，
     * 由调用方按失败处理）。
     */
    @NonNull
    public Observable<SaleAdviceVO> pollAdvice(long id) {
        long times = POLL_MAX_SEC / POLL_INTERVAL_SEC + 1;
        return Observable.interval(0, POLL_INTERVAL_SEC, TimeUnit.SECONDS)
                .take(times)
                .concatMapSingle(t -> getAdvice(id))
                .takeUntil(v -> !FinanceConst.GEN_GENERATING.equals(v.getStatus()));
    }
}
