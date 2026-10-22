package com.zhimu.slmp.feed.data;

import com.zhimu.slmp.feed.model.request.FeedPlanSaveRequest;
import com.zhimu.slmp.feed.model.request.FeedRecordCreateRequest;
import com.zhimu.slmp.feed.model.request.FeedRecordUpdateRequest;
import com.zhimu.slmp.feed.model.request.FeedRecordVoidRequest;
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
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;

import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/** 饲喂任务、投喂登记、饲喂方案与执行情况接口（API-G01–G16）；请求头 X-Farm-Id 由拦截器添加。 */
public interface FeedApi {

    /** API-G01 今日饲喂指标与横幅（G01） */
    @GET("api/v1/feed-tasks/today/summary")
    Single<ApiResponse<FeedTodaySummaryVO>> getTodaySummary();

    /** API-G02 今日任务列表（G01）：status 为 PENDING / DONE / OVERDUE，可空 */
    @GET("api/v1/feed-tasks/today")
    Single<ApiResponse<PageVO<FeedTaskItemVO>>> getTodayTasks(@Query("status") String status,
                                                              @Query("pen_id") Long penId,
                                                              @Query("page") int page, @Query("size") int size);

    /** API-G03 登记表单上下文（G02） */
    @GET("api/v1/feed-tasks/{id}")
    Single<ApiResponse<FeedTaskFormVO>> getTaskForm(@Path("id") long taskId);

    /** API-G04 确认投喂（G02，要求幂等键） */
    @POST("api/v1/feed-records")
    Single<ApiResponse<FeedRecordDetailVO>> createRecord(@Body FeedRecordCreateRequest body,
                                                        @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-G05 投喂记录详情（G02 修改 / 只读） */
    @GET("api/v1/feed-records/{id}")
    Single<ApiResponse<FeedRecordDetailVO>> getRecord(@Path("id") long recordId);

    /** API-G06 修改当天投喂记录（所有者、场长） */
    @PUT("api/v1/feed-records/{id}")
    Single<ApiResponse<FeedRecordDetailVO>> updateRecord(@Path("id") long recordId,
                                                        @Body FeedRecordUpdateRequest body);

    /** API-G07 作废当天投喂记录（所有者、场长） */
    @POST("api/v1/feed-records/{id}/void")
    Single<ApiResponse<FeedRecordDetailVO>> voidRecord(@Path("id") long recordId, @Body FeedRecordVoidRequest body);

    /** API-G08 按请求编号查询投喂（G02 提交超时后） */
    @GET("api/v1/feed-records/lookup")
    Single<ApiResponse<FeedRecordLookupVO>> lookupRecord(@Query("request_id") String requestId);

    /** API-G09 方案批次选择（G01、G03、G04） */
    @GET("api/v1/feed-plans/batch-options")
    Single<ApiResponse<List<FeedPlanBatchOptionVO>>> getPlanBatchOptions();

    /** API-G10 饲喂方案（G03） */
    @GET("api/v1/batches/{batch_id}/feed-plan")
    Single<ApiResponse<FeedPlanVO>> getFeedPlan(@Path("batch_id") long batchId);

    /** API-G11 套用品种标准（G03，只读） */
    @GET("api/v1/batches/{batch_id}/feed-plan/standard")
    Single<ApiResponse<FeedStdApplyVO>> getFeedStd(@Path("batch_id") long batchId);

    /** API-G12 保存方案（G03） */
    @PUT("api/v1/batches/{batch_id}/feed-plan")
    Single<ApiResponse<FeedPlanVO>> saveFeedPlan(@Path("batch_id") long batchId, @Body FeedPlanSaveRequest body);

    /** API-G14 执行情况汇总（G04） */
    @GET("api/v1/feed-executions/summary")
    Single<ApiResponse<FeedExecSummaryVO>> getExecSummary(@QueryMap Map<String, String> query);

    /** API-G15 需要关注（G04） */
    @GET("api/v1/feed-executions/attention")
    Single<ApiResponse<PageVO<FeedAttentionItemVO>>> getExecAttention(@QueryMap Map<String, String> query,
                                                                      @Query("page") int page,
                                                                      @Query("size") int size);

    /** API-G16 单日各餐明细（G04 点柱） */
    @GET("api/v1/feed-executions/daily")
    Single<ApiResponse<FeedExecDayVO>> getExecDay(@QueryMap Map<String, String> query, @Query("date") String date);
}
