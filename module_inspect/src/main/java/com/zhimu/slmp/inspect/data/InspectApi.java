package com.zhimu.slmp.inspect.data;

import com.zhimu.slmp.inspect.model.request.AbnormalReportCreateRequest;
import com.zhimu.slmp.inspect.model.request.HandleSaveRequest;
import com.zhimu.slmp.inspect.model.request.InspectionBackfillRequest;
import com.zhimu.slmp.inspect.model.request.InspectionSettingRequest;
import com.zhimu.slmp.inspect.model.request.InspectionSubmitRequest;
import com.zhimu.slmp.inspect.model.vo.DiagnosisDetailVO;
import com.zhimu.slmp.inspect.model.vo.DiagnosisStateVO;
import com.zhimu.slmp.inspect.model.vo.HandleSaveResultVO;
import com.zhimu.slmp.inspect.model.vo.InspectionDetailVO;
import com.zhimu.slmp.inspect.model.vo.InspectionFormVO;
import com.zhimu.slmp.inspect.model.vo.InspectionListItemVO;
import com.zhimu.slmp.inspect.model.vo.InspectionSettingVO;
import com.zhimu.slmp.inspect.model.vo.InspectionSubmitResultVO;
import com.zhimu.slmp.inspect.model.vo.InspectionSummaryVO;
import com.zhimu.slmp.inspect.model.vo.PenInspectionOverviewVO;
import com.zhimu.slmp.inspect.model.vo.PenInspectionRowVO;
import com.zhimu.slmp.inspect.model.vo.RecentIssueVO;
import com.zhimu.slmp.inspect.model.vo.ReportCreateResultVO;
import com.zhimu.slmp.inspect.model.vo.ReportFormVO;
import com.zhimu.slmp.inspect.model.vo.SymptomVO;
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
 * 模块07 日常巡检与异常上报 AI 诊断接口（服务端文档 2.5.1 节），路径为相对路径。
 */
public interface InspectApi {

    // ===== 巡检 =====

    /** API-I01 巡检统计与截止时间（I01） */
    @GET("api/v1/inspections/summary")
    Single<ApiResponse<InspectionSummaryVO>> getSummary(@Query("date") String date);

    /** API-I02 某日栏舍清单（I01）：status 为 ALL / PENDING / ABNORMAL / DONE，data.tab_counts 为各状态数量 */
    @GET("api/v1/inspections")
    Single<ApiResponse<PageVO<InspectionListItemVO>>> getDayList(@Query("date") String date,
                                                               @Query("status") String status,
                                                               @Query("page") int page, @Query("size") int size);

    /** API-I03 前 3 天超时、补录、漏巡（所有者、场长） */
    @GET("api/v1/inspections/recent-issues")
    Single<ApiResponse<List<RecentIssueVO>>> getRecentIssues();

    /** API-I04 截止时间与提醒规则 */
    @GET("api/v1/inspection-settings/current")
    Single<ApiResponse<InspectionSettingVO>> getSetting();

    /** API-I05 修改截止时间（所有者、场长；不带幂等键） */
    @PUT("api/v1/inspection-settings/current")
    Single<ApiResponse<InspectionSettingVO>> updateSetting(@Body InspectionSettingRequest request);

    /** API-I06 登记 / 修改 / 补录表单上下文（I02、I03） */
    @GET("api/v1/inspections/form-context")
    Single<ApiResponse<InspectionFormVO>> getFormContext(@Query("pen_id") long penId, @Query("mode") String mode,
                                                         @Query("inspect_date") String inspectDate,
                                                         @Query("inspection_id") Long inspectionId);

    /** API-I07 登记当天巡检（client_request_id 兼作幂等键） */
    @POST("api/v1/inspections")
    Single<ApiResponse<InspectionSubmitResultVO>> register(@Body InspectionSubmitRequest request,
                                                           @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-I08 修改巡检（带 version，不带幂等键） */
    @PUT("api/v1/inspections/{id}")
    Single<ApiResponse<InspectionSubmitResultVO>> modify(@Path("id") long inspectionId,
                                                         @Body InspectionSubmitRequest request);

    /** API-I09 补录漏巡（所有者、场长） */
    @POST("api/v1/inspections/backfills")
    Single<ApiResponse<InspectionSubmitResultVO>> backfill(@Body InspectionBackfillRequest request,
                                                           @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-I25 按 client_request_id 查询登记 / 补录是否已保存（超时复查；40401 表示未保存） */
    @GET("api/v1/inspections/by-request/{id}")
    Single<ApiResponse<InspectionSubmitResultVO>> findByRequest(@Path("id") String clientRequestId);

    /** API-I10 巡检详情 */
    @GET("api/v1/inspections/{id}")
    Single<ApiResponse<InspectionDetailVO>> getDetail(@Path("id") long inspectionId);

    /** API-I11 栏舍巡检历史（I04）：filter 为 ALL / ABNORMAL / OVERDUE / BACKFILL / OFF_SITE */
    @GET("api/v1/pens/{penId}/inspections")
    Single<ApiResponse<PageVO<PenInspectionRowVO>>> getPenHistory(@Path("penId") long penId,
                                                                @Query("filter") String filter,
                                                                @Query("start_date") String startDate,
                                                                @Query("end_date") String endDate,
                                                                @Query("locate_date") String locateDate,
                                                                @Query("page") int page, @Query("size") int size);

    /** API-I12 栏舍近 7 天概况（I04） */
    @GET("api/v1/pens/{penId}/inspection-overview")
    Single<ApiResponse<PenInspectionOverviewVO>> getPenOverview(@Path("penId") long penId, @Query("to") String to);

    // ===== 异常上报与诊断 =====

    /** API-I14 上报表单上下文（I05）：转上报带 inspection_id，直接上报可带 pen_id */
    @GET("api/v1/abnormal-reports/form-context")
    Single<ApiResponse<ReportFormVO>> getReportForm(@Query("inspection_id") Long inspectionId,
                                                    @Query("pen_id") Long penId);

    /** API-I15 按批次品类取症状 */
    @GET("api/v1/symptoms")
    Single<ApiResponse<List<SymptomVO>>> getSymptoms(@Query("batch_id") long batchId);

    /** API-I16 异常上报（client_request_id 兼作幂等键），之后异步 AI 诊断 */
    @POST("api/v1/abnormal-reports")
    Single<ApiResponse<ReportCreateResultVO>> createReport(@Body AbnormalReportCreateRequest request,
                                                           @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-I26 按 client_request_id 查询上报是否已保存（40401 表示未保存） */
    @GET("api/v1/abnormal-reports/by-request/{id}")
    Single<ApiResponse<ReportCreateResultVO>> findReportByRequest(@Path("id") String clientRequestId);

    /** API-I19 诊断详情（I06、I07） */
    @GET("api/v1/abnormal-reports/{id}")
    Single<ApiResponse<DiagnosisDetailVO>> getDiagnosis(@Path("id") long reportId);

    /** API-I20 诊断状态（I06 每 3 秒轮询） */
    @GET("api/v1/abnormal-reports/{id}/diagnosis-state")
    Single<ApiResponse<DiagnosisStateVO>> getDiagnosisState(@Path("id") long reportId);

    /** API-I21 重新诊断（无请求体、无幂等键） */
    @POST("api/v1/abnormal-reports/{id}/rediagnoses")
    Single<ApiResponse<DiagnosisStateVO>> rediagnose(@Path("id") long reportId);

    /** API-I22 登记 / 修改处理结果（所有者、场长） */
    @PUT("api/v1/abnormal-reports/{id}/handle")
    Single<ApiResponse<HandleSaveResultVO>> saveHandle(@Path("id") long reportId, @Body HandleSaveRequest request);
}
