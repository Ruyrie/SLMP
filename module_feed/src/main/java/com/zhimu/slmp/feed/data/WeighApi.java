package com.zhimu.slmp.feed.data;

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
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;

import java.util.List;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** 称重与生长曲线接口（API-G18–G26）。 */
public interface WeighApi {

    /** API-G18 称重批次选择（G05 传 false、G06 传 true） */
    @GET("api/v1/weigh-records/batch-options")
    Single<ApiResponse<List<WeighBatchOptionVO>>> getBatchOptions(@Query("include_ended") boolean includeEnded);

    /** API-G19 称重表单上下文（G05）：date 为空时默认今天 */
    @GET("api/v1/batches/{batch_id}/weigh-form")
    Single<ApiResponse<WeighFormVO>> getWeighForm(@Path("batch_id") long batchId, @Query("date") String date);

    /** API-G20 提交称重（要求幂等键） */
    @POST("api/v1/batches/{batch_id}/weigh-records")
    Single<ApiResponse<WeighRecordVO>> createWeighRecord(@Path("batch_id") long batchId,
                                                         @Body WeighRecordCreateRequest body,
                                                         @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-G21 称重记录详情（G05 修改） */
    @GET("api/v1/weigh-records/{id}")
    Single<ApiResponse<WeighRecordVO>> getWeighRecord(@Path("id") long id);

    /** API-G22 修改称重记录 */
    @PUT("api/v1/weigh-records/{id}")
    Single<ApiResponse<WeighRecordVO>> updateWeighRecord(@Path("id") long id, @Body WeighRecordUpdateRequest body);

    /** API-G23 修改 / 恢复称重周期（所有者、场长） */
    @PUT("api/v1/batches/{batch_id}/weigh-cycle")
    Single<ApiResponse<WeighCycleVO>> updateWeighCycle(@Path("batch_id") long batchId, @Body WeighCycleRequest body);

    /** API-G24 生长曲线（G06）：compare_batch_ids 逗号分隔，最多 3 个 */
    @GET("api/v1/batches/{batch_id}/growth-curve")
    Single<ApiResponse<GrowthCurveVO>> getGrowthCurve(@Path("batch_id") long batchId,
                                                      @Query("compare_batch_ids") String compareBatchIds);

    /** API-G25 称重记录列表（G06） */
    @GET("api/v1/batches/{batch_id}/weigh-records")
    Single<ApiResponse<PageVO<WeighRecordItemVO>>> getWeighRecords(@Path("batch_id") long batchId,
                                                                   @Query("page") int page,
                                                                   @Query("size") int size);

    /** API-G26 对比批次选项（G06） */
    @GET("api/v1/batches/{batch_id}/growth-compare-options")
    Single<ApiResponse<List<GrowthCompareOptionVO>>> getCompareOptions(@Path("batch_id") long batchId);
}
