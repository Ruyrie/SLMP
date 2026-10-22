package com.zhimu.slmp.health.data;

import androidx.annotation.Nullable;

import com.zhimu.slmp.health.model.request.MedicationCreateRequest;
import com.zhimu.slmp.health.model.request.MedicationPreviewRequest;
import com.zhimu.slmp.health.model.request.MedicationUpdateRequest;
import com.zhimu.slmp.health.model.request.NotifyManagerRequest;
import com.zhimu.slmp.health.model.vo.DeleteImpactVO;
import com.zhimu.slmp.health.model.vo.MedicationCreateResultVO;
import com.zhimu.slmp.health.model.vo.MedicationDrugOptionVO;
import com.zhimu.slmp.health.model.vo.MedicationRecordVO;
import com.zhimu.slmp.health.model.vo.MedicationTargetVO;
import com.zhimu.slmp.health.model.vo.NotifyResultVO;
import com.zhimu.slmp.health.model.vo.ReleaseDatePreviewVO;
import com.zhimu.slmp.health.model.vo.ToastVO;
import com.zhimu.slmp.health.model.vo.WithdrawalVO;
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
 * 用药与休药接口（服务端模块08 文档 API-D17–D27）。API-D22 带 Idempotency-Key（写入每条记录 client_request_id）。
 */
public interface MedicationApi {

    /** API-D17 休药状态与按药品计算 */
    @GET("api/v1/batches/{batch_id}/withdrawal")
    Single<ApiResponse<WithdrawalVO>> getWithdrawal(@Path("batch_id") long batchId);

    /** API-D18 用药记录列表（按首次用药日期倒序） */
    @GET("api/v1/batches/{batch_id}/medication-records")
    Single<ApiResponse<PageVO<MedicationRecordVO>>> getRecords(@Path("batch_id") long batchId,
                                                               @Query("page") int page, @Query("size") int size);

    /** API-D19 用药对象下拉；target_type：BATCH / PEN */
    @GET("api/v1/medication/targets")
    Single<ApiResponse<List<MedicationTargetVO>>> getTargets(@Query("target_type") String targetType,
                                                             @Nullable @Query("keyword") String keyword);

    /** API-D20 可选兽药（batch_id 或 pen_id 必填其一）；关键词命中禁用药时返回 banned_hint */
    @GET("api/v1/medication/drug-options")
    Single<ApiResponse<MedicationDrugOptionVO.ListResult>> getDrugOptions(@Nullable @Query("batch_id") Long batchId,
                                                                          @Nullable @Query("pen_id") Long penId,
                                                                          @Nullable @Query("keyword") String keyword);

    /** API-D21 预估可出栏日期与预计用量（纯计算，失败静默） */
    @POST("api/v1/medication-records/preview")
    Single<ApiResponse<ReleaseDatePreviewVO>> preview(@Body MedicationPreviewRequest body);

    /** API-D22 登记用药 */
    @POST("api/v1/medication-records")
    Single<ApiResponse<MedicationCreateResultVO>> create(@Body MedicationCreateRequest body,
                                                         @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-D23 用药记录详情 */
    @GET("api/v1/medication-records/{id}")
    Single<ApiResponse<MedicationRecordVO>> getDetail(@Path("id") long recordId);

    /** API-D24 修改用药记录 */
    @PUT("api/v1/medication-records/{id}")
    Single<ApiResponse<MedicationRecordVO>> update(@Path("id") long recordId, @Body MedicationUpdateRequest body);

    /** API-D25 删除影响预判 */
    @GET("api/v1/medication-records/{id}/delete-impact")
    Single<ApiResponse<DeleteImpactVO>> getDeleteImpact(@Path("id") long recordId);

    /** API-D26 直接删除（只限所有者、场长，且影响为 NO_CHANGE_TODAY） */
    @DELETE("api/v1/medication-records/{id}")
    Single<ApiResponse<ToastVO>> delete(@Path("id") long recordId, @Query("version") int version);

    /** API-D27 员工通知场长 */
    @POST("api/v1/medication-records/{id}/notify-manager")
    Single<ApiResponse<NotifyResultVO>> notifyManager(@Path("id") long recordId, @Body NotifyManagerRequest body);
}
