package com.zhimu.slmp.feed.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.feed.model.request.GrowthCurveQuery;
import com.zhimu.slmp.feed.model.request.WeighCycleRequest;
import com.zhimu.slmp.feed.model.request.WeighRecordCreateRequest;
import com.zhimu.slmp.feed.model.request.WeighRecordUpdateRequest;
import com.zhimu.slmp.feed.model.vo.GrowthCompareOptionVO;
import com.zhimu.slmp.feed.model.vo.GrowthCurveVO;
import com.zhimu.slmp.feed.model.vo.WeighBatchOptionVO;
import com.zhimu.slmp.feed.model.vo.WeighCycleVO;
import com.zhimu.slmp.feed.model.vo.WeighFormVO;
import com.zhimu.slmp.feed.model.vo.WeighRecordItemVO;
import com.zhimu.slmp.feed.model.vo.WeighRecordVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/** 称重与生长曲线（API-G18–G26）；WeighRecordPagingSource 为 lib_network 的 PagedSource。 */
@FeatureScope
public class WeighRepository {

    private final WeighApi api;

    @Inject
    public WeighRepository(@NonNull WeighApi api) {
        this.api = api;
    }

    /** API-G18 */
    @NonNull
    public Single<List<WeighBatchOptionVO>> getBatchOptions(boolean includeEnded) {
        return api.getBatchOptions(includeEnded).compose(RxResult.unwrap());
    }

    /** API-G19 */
    @NonNull
    public Single<WeighFormVO> getWeighForm(long batchId, @Nullable String date) {
        return api.getWeighForm(batchId, date).compose(RxResult.unwrap());
    }

    /** API-G20（Idempotency-Key） */
    @NonNull
    public Single<ApiResponse<WeighRecordVO>> createWeighRecord(long batchId, @NonNull WeighRecordCreateRequest request,
                                                               @NonNull String idemKey) {
        return api.createWeighRecord(batchId, request, idemKey).compose(FeedRx.checked());
    }

    /** API-G21 */
    @NonNull
    public Single<WeighRecordVO> getWeighRecord(long id) {
        return api.getWeighRecord(id).compose(RxResult.unwrap());
    }

    /** API-G22 */
    @NonNull
    public Single<ApiResponse<WeighRecordVO>> updateWeighRecord(long id, @NonNull WeighRecordUpdateRequest request) {
        return api.updateWeighRecord(id, request).compose(FeedRx.checked());
    }

    /** API-G23 */
    @NonNull
    public Single<ApiResponse<WeighCycleVO>> updateWeighCycle(long batchId, @NonNull WeighCycleRequest request) {
        return api.updateWeighCycle(batchId, request).compose(FeedRx.checked());
    }

    /** API-G24 */
    @NonNull
    public Single<GrowthCurveVO> getGrowthCurve(long batchId, @Nullable List<Long> compareBatchIds) {
        return api.getGrowthCurve(batchId, new GrowthCurveQuery(compareBatchIds).joined())
                .compose(RxResult.unwrap());
    }

    /** API-G25 */
    @NonNull
    public Single<PageVO<WeighRecordItemVO>> getWeighRecords(long batchId, int page, int size) {
        return api.getWeighRecords(batchId, page, size).compose(RxResult.unwrap());
    }

    /** API-G25 分页源 */
    @NonNull
    public PagedSource<WeighRecordItemVO> weighRecordSource(long batchId) {
        return new PagedSource<>((page, size) -> getWeighRecords(batchId, page, size));
    }

    /** API-G26 */
    @NonNull
    public Single<List<GrowthCompareOptionVO>> getCompareOptions(long batchId) {
        return api.getCompareOptions(batchId).compose(RxResult.unwrap());
    }
}
