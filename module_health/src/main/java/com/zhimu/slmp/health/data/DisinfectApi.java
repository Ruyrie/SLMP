package com.zhimu.slmp.health.data;

import androidx.annotation.Nullable;

import com.zhimu.slmp.health.model.request.DisinfectPlanPauseRequest;
import com.zhimu.slmp.health.model.request.DisinfectPlanPreviewRequest;
import com.zhimu.slmp.health.model.request.DisinfectPlanSaveRequest;
import com.zhimu.slmp.health.model.request.DisinfectRecordCreateRequest;
import com.zhimu.slmp.health.model.request.DisinfectRecordUpdateRequest;
import com.zhimu.slmp.health.model.request.VersionRequest;
import com.zhimu.slmp.health.model.vo.DisinfectPlanDetailVO;
import com.zhimu.slmp.health.model.vo.DisinfectPlanPreviewVO;
import com.zhimu.slmp.health.model.vo.DisinfectPlanVO;
import com.zhimu.slmp.health.model.vo.DisinfectRecordVO;
import com.zhimu.slmp.health.model.vo.DisinfectTaskOptionVO;
import com.zhimu.slmp.health.model.vo.DisinfectTaskPageVO;
import com.zhimu.slmp.health.model.vo.DisinfectantOptionVO;
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

/**
 * 消毒计划、任务与记录接口（服务端模块08 文档 API-D28–D40）。API-D31、D38 带 Idempotency-Key。
 */
public interface DisinfectApi {

    /** API-D28 消毒计划列表；status：ALL / ACTIVE / PAUSED；tab_counts 含 all、active、paused */
    @GET("api/v1/disinfect-plans")
    Single<ApiResponse<PageVO<DisinfectPlanVO>>> getPlans(@Query("status") String status,
                                                          @Query("page") int page, @Query("size") int size);

    /** API-D29 计划详情 */
    @GET("api/v1/disinfect-plans/{id}")
    Single<ApiResponse<DisinfectPlanDetailVO>> getPlan(@Path("id") long planId);

    /** API-D30 任务数与提醒预估 */
    @POST("api/v1/disinfect-plans/preview")
    Single<ApiResponse<DisinfectPlanPreviewVO>> previewPlan(@Body DisinfectPlanPreviewRequest body);

    /** API-D31 新建并启用 */
    @POST("api/v1/disinfect-plans")
    Single<ApiResponse<DisinfectPlanVO>> createPlan(@Body DisinfectPlanSaveRequest body,
                                                    @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-D32 修改计划 */
    @PUT("api/v1/disinfect-plans/{id}")
    Single<ApiResponse<DisinfectPlanVO>> updatePlan(@Path("id") long planId, @Body DisinfectPlanSaveRequest body);

    /** API-D33 暂停计划 */
    @POST("api/v1/disinfect-plans/{id}/pause")
    Single<ApiResponse<DisinfectPlanVO>> pausePlan(@Path("id") long planId, @Body DisinfectPlanPauseRequest body);

    /** API-D34 启用计划 */
    @POST("api/v1/disinfect-plans/{id}/resume")
    Single<ApiResponse<DisinfectPlanVO>> resumePlan(@Path("id") long planId, @Body VersionRequest body);

    /** API-D35 消毒剂下拉与库存提示 */
    @GET("api/v1/disinfectants/options")
    Single<ApiResponse<List<DisinfectantOptionVO>>> getDisinfectants(@Nullable @Query("keyword") String keyword);

    /** API-D36 消毒任务列表；status：ALL / OVERDUE / PENDING / DONE */
    @GET("api/v1/disinfect-tasks")
    Single<ApiResponse<DisinfectTaskPageVO>> getTasks(@Query("status") String status,
                                                      @Nullable @Query("pen_id") Long penId,
                                                      @Query("page") int page, @Query("size") int size);

    /** API-D37 登记消毒的任务下拉 */
    @GET("api/v1/disinfect-tasks/options")
    Single<ApiResponse<List<DisinfectTaskOptionVO>>> getTaskOptions();

    /** API-D38 登记消毒 */
    @POST("api/v1/disinfect-records")
    Single<ApiResponse<DisinfectRecordVO>> createRecord(@Body DisinfectRecordCreateRequest body,
                                                        @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-D39 消毒记录查看 */
    @GET("api/v1/disinfect-records/{id}")
    Single<ApiResponse<DisinfectRecordVO>> getRecord(@Path("id") long recordId);

    /** API-D40 修改消毒记录 */
    @PUT("api/v1/disinfect-records/{id}")
    Single<ApiResponse<DisinfectRecordVO>> updateRecord(@Path("id") long recordId,
                                                        @Body DisinfectRecordUpdateRequest body);
}
