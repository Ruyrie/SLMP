package com.zhimu.slmp.health.data;

import androidx.annotation.Nullable;

import com.zhimu.slmp.health.model.request.ImmunePlanCreateRequest;
import com.zhimu.slmp.health.model.request.ImmunePlanPostponeRequest;
import com.zhimu.slmp.health.model.request.ImmunePlanUpdateRequest;
import com.zhimu.slmp.health.model.request.ImmuneRecordCreateRequest;
import com.zhimu.slmp.health.model.request.ImmuneRecordUpdateRequest;
import com.zhimu.slmp.health.model.request.VersionRequest;
import com.zhimu.slmp.health.model.vo.HealthRequestStatusVO;
import com.zhimu.slmp.health.model.vo.ImmunePlanOptionVO;
import com.zhimu.slmp.health.model.vo.ImmunePlanOverviewVO;
import com.zhimu.slmp.health.model.vo.ImmunePlanVO;
import com.zhimu.slmp.health.model.vo.ImmuneRecordVO;
import com.zhimu.slmp.health.model.vo.ImmuneRegisterFormVO;
import com.zhimu.slmp.health.model.vo.VaccineOptionVO;
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;

import java.util.List;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 免疫计划与接种记录接口（服务端模块08 文档 2.5 节 API-D01–D16、D41）。
 * API-D07、D14 带 Idempotency-Key（D14 的键同时作为 client_request_id，超时后用 API-D41 确认）。
 */
public interface ImmuneApi {

    /** API-D01 批次免疫概况（D01、D02） */
    @GET("api/v1/batches/{batch_id}/immune-plans/overview")
    Single<ApiResponse<ImmunePlanOverviewVO>> getOverview(@Path("batch_id") long batchId);

    /** API-D02 免疫计划列表；status：ALL / PENDING / MISSED / POSTPONED / DONE / CANCELLED */
    @GET("api/v1/batches/{batch_id}/immune-plans")
    Single<ApiResponse<PageVO<ImmunePlanVO>>> getPlans(@Path("batch_id") long batchId,
                                                       @Query("status") String status,
                                                       @Query("page") int page, @Query("size") int size);

    /** API-D03 确认入栏前已接种 */
    @POST("api/v1/immune-plans/{id}/pre-entry-confirm")
    Single<ApiResponse<ImmunePlanOverviewVO>> confirmPreEntry(@Path("id") long planId, @Body VersionRequest body);

    /** API-D04 入栏前项目加入计划（计划日期为今天） */
    @POST("api/v1/immune-plans/{id}/pre-entry-join")
    Single<ApiResponse<ImmunePlanVO>> joinPreEntry(@Path("id") long planId, @Body VersionRequest body);

    /** API-D05 撤销确认（10 分钟内） */
    @POST("api/v1/immune-plans/{id}/pre-entry-undo")
    Single<ApiResponse<ImmunePlanOverviewVO>> undoPreEntry(@Path("id") long planId, @Body VersionRequest body);

    /** API-D06 调整计划日期 */
    @POST("api/v1/immune-plans/{id}/postpone")
    Single<ApiResponse<ImmunePlanVO>> postpone(@Path("id") long planId, @Body ImmunePlanPostponeRequest body);

    /** API-D07 添加免疫项目 */
    @POST("api/v1/batches/{batch_id}/immune-plans")
    Single<ApiResponse<ImmunePlanVO>> createPlan(@Path("batch_id") long batchId, @Body ImmunePlanCreateRequest body,
                                                 @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-D08 修改剂量、方式 */
    @PUT("api/v1/immune-plans/{id}")
    Single<ApiResponse<ImmunePlanVO>> updatePlan(@Path("id") long planId, @Body ImmunePlanUpdateRequest body);

    /** API-D09 删除项目（置为已取消），version 为查询参数 */
    @DELETE("api/v1/immune-plans/{id}")
    Single<ApiResponse<Object>> deletePlan(@Path("id") long planId, @Query("version") int version);

    /** API-D11 登记接种的计划下拉；keyword 为批次号或疫苗名 */
    @GET("api/v1/immune-plans/options")
    Single<ApiResponse<PageVO<ImmunePlanOptionVO>>> getPlanOptions(@Nullable @Query("keyword") String keyword,
                                                                   @Query("page") int page, @Query("size") int size);

    /** API-D12 登记接种预填；backfill 为补免 */
    @GET("api/v1/immune-plans/{id}/register-form")
    Single<ApiResponse<ImmuneRegisterFormVO>> getRegisterForm(@Path("id") long planId,
                                                              @Nullable @Query("backfill") Boolean backfill);

    /** API-D13 本场疫苗档案下拉；category_id 选填，默认取批次品类 */
    @GET("api/v1/vaccines/options")
    Single<ApiResponse<List<VaccineOptionVO>>> getVaccineOptions(@Nullable @Query("category_id") Long categoryId);

    /** API-D14 登记接种 / 补免 */
    @POST("api/v1/immune-records")
    Single<ApiResponse<ImmuneRecordVO>> createRecord(@Body ImmuneRecordCreateRequest body,
                                                     @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-D15 接种记录查看 */
    @GET("api/v1/immune-records/{id}")
    Single<ApiResponse<ImmuneRecordVO>> getRecord(@Path("id") long recordId);

    /** API-D16 修改接种记录 */
    @PUT("api/v1/immune-records/{id}")
    Single<ApiResponse<ImmuneRecordVO>> updateRecord(@Path("id") long recordId, @Body ImmuneRecordUpdateRequest body);

    /** API-D41 按请求编号查询登记结果；record_type：IMMUNE_RECORD / MEDICATION_RECORD / DISINFECT_RECORD */
    @GET("api/v1/health-records/by-request/{client_request_id}")
    Single<ApiResponse<HealthRequestStatusVO>> getRequestStatus(@Path("client_request_id") String requestId,
                                                                @Query("record_type") String recordType);
}
