package com.zhimu.slmp.herd.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.Pager;
import androidx.paging.PagingData;
import androidx.paging.rxjava3.PagingRx;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.herd.data.paging.BatchPagingSource;
import com.zhimu.slmp.herd.data.paging.TimelinePagingSource;
import com.zhimu.slmp.herd.model.request.BatchCreateRequest;
import com.zhimu.slmp.herd.model.request.DeathCreateRequest;
import com.zhimu.slmp.herd.model.request.DeathUpdateRequest;
import com.zhimu.slmp.herd.model.request.EntryUpdateRequest;
import com.zhimu.slmp.herd.model.request.TransferCreateRequest;
import com.zhimu.slmp.herd.model.request.TransferUpdateRequest;
import com.zhimu.slmp.herd.model.vo.BatchDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchEntryResultVO;
import com.zhimu.slmp.herd.model.vo.BatchEventDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;
import com.zhimu.slmp.herd.model.vo.BatchLookupVO;
import com.zhimu.slmp.herd.model.vo.BatchTraceVO;
import com.zhimu.slmp.herd.model.vo.DeathResultVO;
import com.zhimu.slmp.herd.model.vo.PenStockVO;
import com.zhimu.slmp.herd.model.vo.RequestCheckVO;
import com.zhimu.slmp.herd.model.vo.TimelineItemVO;
import com.zhimu.slmp.herd.model.vo.TransferResultVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 批次查询与登记（API-L06、L14–L24、L26、L28）。
 */
@FeatureScope
public class BatchRepository {

    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 下拉一次取 100 条（服务端分页上限） */
    public static final int OPTIONS_SIZE = 100;

    private final HerdApi api;

    @Inject
    public BatchRepository(@NonNull HerdApi api) {
        this.api = api;
    }

    /** API-L06 分页 */
    @NonNull
    public Flowable<PagingData<BatchListItemVO>> pageBatches(@Nullable String status, @Nullable String keyword,
                                                             @Nullable String sort) {
        return pageBatches(status, keyword, sort, null);
    }

    /** API-L06 分页；onFirstPage 在第 1 页到达时回调（io 线程） */
    @NonNull
    public Flowable<PagingData<BatchListItemVO>> pageBatches(@Nullable String status, @Nullable String keyword,
                                                             @Nullable String sort,
                                                             @Nullable Consumer<PageVO<BatchListItemVO>> onFirstPage) {
        Pager<Integer, BatchListItemVO> pager = new Pager<>(StockRepository.pagingConfig(),
                () -> new BatchPagingSource(api, status, StockRepository.emptyToNull(keyword), sort, onFirstPage));
        return PagingRx.getFlowable(pager);
    }

    /** API-L06（ACTIVE，size 100）：L10、L11 批次下拉 */
    @NonNull
    public Single<PageVO<BatchListItemVO>> getActiveBatches() {
        return api.getBatches(STATUS_ACTIVE, null, null, 1, OPTIONS_SIZE).compose(RxResult.unwrap());
    }

    /** API-L14 扫码原文 */
    @NonNull
    public Single<BatchLookupVO> lookupByQr(@NonNull String qrContent) {
        return api.lookup(qrContent, null).compose(RxResult.unwrap());
    }

    /** API-L14 批次号 */
    @NonNull
    public Single<BatchLookupVO> lookupByNo(@NonNull String batchNo) {
        return api.lookup(null, batchNo).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<BatchTraceVO> getPublicTrace(@NonNull String token) {
        return api.getPublicTrace(token).compose(RxResult.unwrap());
    }

    /** API-L15 */
    @NonNull
    public Single<BatchDetailVO> getBatchDetail(long batchId) {
        return api.getBatch(batchId).compose(RxResult.unwrap());
    }

    /** API-L17 */
    @NonNull
    public Single<List<PenStockVO>> getPenStocks(long batchId) {
        return api.getBatchPenStocks(batchId).compose(RxResult.unwrap());
    }

    /** API-L16 */
    @NonNull
    public Flowable<PagingData<TimelineItemVO>> pageTimeline(long batchId, @Nullable String type) {
        Pager<Integer, TimelineItemVO> pager = new Pager<>(StockRepository.pagingConfig(),
                () -> new TimelinePagingSource(api, batchId, type));
        return PagingRx.getFlowable(pager);
    }

    /** API-L18 */
    @NonNull
    public Single<BatchEntryResultVO> createBatch(@NonNull BatchCreateRequest req, @NonNull String idemKey) {
        return api.createBatch(req, idemKey).compose(RxResult.unwrap());
    }

    /** API-L20 */
    @NonNull
    public Single<BatchDetailVO> updateEntry(long batchId, @NonNull EntryUpdateRequest req) {
        return api.updateEntry(batchId, req).compose(RxResult.unwrap());
    }

    /** API-L19 */
    @NonNull
    public Single<BatchEventDetailVO> getEventDetail(long eventId) {
        return api.getEventDetail(eventId).compose(RxResult.unwrap());
    }

    /** API-L21 */
    @NonNull
    public Single<TransferResultVO> createTransfer(@NonNull TransferCreateRequest req, @NonNull String idemKey) {
        return api.createTransfer(req, idemKey).compose(RxResult.unwrap());
    }

    /** API-L22 */
    @NonNull
    public Single<TransferResultVO> updateTransfer(long eventId, @NonNull TransferUpdateRequest req) {
        return api.updateTransfer(eventId, req).compose(RxResult.unwrap());
    }

    /** API-L23 */
    @NonNull
    public Single<DeathResultVO> createDeath(@NonNull DeathCreateRequest req, @NonNull String idemKey) {
        return api.createDeath(req, idemKey).compose(RxResult.unwrap());
    }

    /** API-L24 */
    @NonNull
    public Single<DeathResultVO> updateDeath(long eventId, @NonNull DeathUpdateRequest req) {
        return api.updateDeath(eventId, req).compose(RxResult.unwrap());
    }

    /** API-L26 */
    @NonNull
    public Single<RequestCheckVO> checkByRequest(@NonNull String requestKey) {
        return api.checkByRequest(requestKey).compose(RxResult.unwrap());
    }

    /** API-L28 */
    @NonNull
    public Completable deleteBatch(long batchId, int version) {
        return RxResult.completable(api.deleteBatch(batchId, version));
    }
}
