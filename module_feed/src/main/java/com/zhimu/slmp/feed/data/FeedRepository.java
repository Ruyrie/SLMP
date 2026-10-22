package com.zhimu.slmp.feed.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.feed.model.request.FeedExecQuery;
import com.zhimu.slmp.feed.model.request.FeedPlanSaveRequest;
import com.zhimu.slmp.feed.model.request.FeedRecordCreateRequest;
import com.zhimu.slmp.feed.model.request.FeedRecordUpdateRequest;
import com.zhimu.slmp.feed.model.request.FeedRecordVoidRequest;
import com.zhimu.slmp.feed.model.request.FeedTaskQuery;
import com.zhimu.slmp.feed.model.vo.FeedAttentionItemVO;
import com.zhimu.slmp.feed.model.vo.FeedExecDayVO;
import com.zhimu.slmp.feed.model.vo.FeedExecSummaryVO;
import com.zhimu.slmp.feed.model.vo.FeedPlanBatchOptionVO;
import com.zhimu.slmp.feed.model.vo.FeedPlanVO;
import com.zhimu.slmp.feed.model.vo.FeedRecordDetailVO;
import com.zhimu.slmp.feed.model.vo.FeedRecordLookupVO;
import com.zhimu.slmp.feed.model.vo.FeedStdApplyVO;
import com.zhimu.slmp.feed.model.vo.FeedTaskFormVO;
import com.zhimu.slmp.feed.model.vo.FeedTaskItemVO;
import com.zhimu.slmp.feed.model.vo.FeedTodaySummaryVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * 饲喂任务、投喂登记、饲喂方案与执行情况（API-G01–G16）。写接口返回 ApiResponse 以取服务端 message；
 * 分页源（FeedTaskPagingSource、FeedAttentionPagingSource）为 lib_network 的 PagedSource，按 has_more 判断末页。
 */
@FeatureScope
public class FeedRepository {

    private final FeedApi api;

    @Inject
    public FeedRepository(@NonNull FeedApi api) {
        this.api = api;
    }

    // ===== 今日任务与投喂 =====

    /** API-G01 */
    @NonNull
    public Single<FeedTodaySummaryVO> getTodaySummary() {
        return api.getTodaySummary().compose(RxResult.unwrap());
    }

    /** API-G02 */
    @NonNull
    public Single<PageVO<FeedTaskItemVO>> getTodayTasks(@Nullable String status, @Nullable Long penId, int page,
                                                        int size) {
        return api.getTodayTasks(status, penId, page, size).compose(RxResult.unwrap());
    }

    /** API-G02 分页源 */
    @NonNull
    public PagedSource<FeedTaskItemVO> todayTaskSource(@Nullable FeedTaskQuery query) {
        String status = query == null ? null : query.status;
        Long penId = query == null ? null : query.penId;
        return new PagedSource<>((page, size) -> getTodayTasks(status, penId, page, size));
    }

    /** API-G03 */
    @NonNull
    public Single<FeedTaskFormVO> getTaskForm(long taskId) {
        return api.getTaskForm(taskId).compose(RxResult.unwrap());
    }

    /** API-G04（Idempotency-Key） */
    @NonNull
    public Single<ApiResponse<FeedRecordDetailVO>> createRecord(@NonNull FeedRecordCreateRequest request,
                                                               @NonNull String idemKey) {
        return api.createRecord(request, idemKey).compose(FeedRx.checked());
    }

    /** API-G05 */
    @NonNull
    public Single<FeedRecordDetailVO> getRecord(long recordId) {
        return api.getRecord(recordId).compose(RxResult.unwrap());
    }

    /** API-G06 */
    @NonNull
    public Single<ApiResponse<FeedRecordDetailVO>> updateRecord(long recordId,
                                                               @NonNull FeedRecordUpdateRequest request) {
        return api.updateRecord(recordId, request).compose(FeedRx.checked());
    }

    /** API-G07 */
    @NonNull
    public Single<ApiResponse<FeedRecordDetailVO>> voidRecord(long recordId, @NonNull FeedRecordVoidRequest request) {
        return api.voidRecord(recordId, request).compose(FeedRx.checked());
    }

    /** API-G08 */
    @NonNull
    public Single<FeedRecordLookupVO> lookupRecord(@NonNull String requestId) {
        return api.lookupRecord(requestId).compose(RxResult.unwrap());
    }

    // ===== 饲喂方案 =====

    /** API-G09 */
    @NonNull
    public Single<List<FeedPlanBatchOptionVO>> getPlanBatchOptions() {
        return api.getPlanBatchOptions().compose(RxResult.unwrap());
    }

    /** API-G10 */
    @NonNull
    public Single<FeedPlanVO> getFeedPlan(long batchId) {
        return api.getFeedPlan(batchId).compose(RxResult.unwrap());
    }

    /** API-G11 */
    @NonNull
    public Single<FeedStdApplyVO> getFeedStd(long batchId) {
        return api.getFeedStd(batchId).compose(RxResult.unwrap());
    }

    /** API-G12 */
    @NonNull
    public Single<ApiResponse<FeedPlanVO>> saveFeedPlan(long batchId, @NonNull FeedPlanSaveRequest request) {
        return api.saveFeedPlan(batchId, request).compose(FeedRx.checked());
    }

    // ===== 执行情况 =====

    /** API-G14 */
    @NonNull
    public Single<FeedExecSummaryVO> getExecSummary(@NonNull FeedExecQuery query) {
        return api.getExecSummary(query.toMap()).compose(RxResult.unwrap());
    }

    /** API-G15 */
    @NonNull
    public Single<PageVO<FeedAttentionItemVO>> getExecAttention(@NonNull FeedExecQuery query, int page, int size) {
        return api.getExecAttention(query.toMap(), page, size).compose(RxResult.unwrap());
    }

    /** API-G15 分页源 */
    @NonNull
    public PagedSource<FeedAttentionItemVO> attentionSource(@NonNull FeedExecQuery query) {
        return new PagedSource<>((page, size) -> getExecAttention(query, page, size));
    }

    /** API-G16 */
    @NonNull
    public Single<FeedExecDayVO> getExecDay(@NonNull FeedExecQuery query, @NonNull String date) {
        return api.getExecDay(query.toMap(), date).compose(RxResult.unwrap());
    }
}
