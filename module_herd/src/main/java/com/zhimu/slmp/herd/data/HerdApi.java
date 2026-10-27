package com.zhimu.slmp.herd.data;

import androidx.annotation.Nullable;

import com.zhimu.slmp.herd.model.request.BatchCreateRequest;
import com.zhimu.slmp.herd.model.request.DeathCreateRequest;
import com.zhimu.slmp.herd.model.request.DeathUpdateRequest;
import com.zhimu.slmp.herd.model.request.EntryUpdateRequest;
import com.zhimu.slmp.herd.model.request.PenCreateRequest;
import com.zhimu.slmp.herd.model.request.PenStatusRequest;
import com.zhimu.slmp.herd.model.request.PenUpdateRequest;
import com.zhimu.slmp.herd.model.request.TransferCreateRequest;
import com.zhimu.slmp.herd.model.request.TransferUpdateRequest;
import com.zhimu.slmp.herd.model.vo.BatchDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchEntryResultVO;
import com.zhimu.slmp.herd.model.vo.BatchEventDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;
import com.zhimu.slmp.herd.model.vo.BatchLookupVO;
import com.zhimu.slmp.herd.model.vo.BatchTraceVO;
import com.zhimu.slmp.herd.model.vo.CategoryStockVO;
import com.zhimu.slmp.herd.model.vo.DeathResultVO;
import com.zhimu.slmp.herd.model.vo.ExecutorOptionVO;
import com.zhimu.slmp.herd.model.vo.MyPensVO;
import com.zhimu.slmp.herd.model.vo.PenDetailVO;
import com.zhimu.slmp.herd.model.vo.PenFormOptionsVO;
import com.zhimu.slmp.herd.model.vo.PenOptionVO;
import com.zhimu.slmp.herd.model.vo.PenStockItemVO;
import com.zhimu.slmp.herd.model.vo.PenStockVO;
import com.zhimu.slmp.herd.model.vo.ProductionOverviewVO;
import com.zhimu.slmp.herd.model.vo.RequestCheckVO;
import com.zhimu.slmp.herd.model.vo.StockSummaryVO;
import com.zhimu.slmp.herd.model.vo.TimelineItemVO;
import com.zhimu.slmp.herd.model.vo.TransferResultVO;
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
 * 模块04 栏舍与存栏接口（服务端模块04 文档 2.5 节，前缀 /api/v1）。Authorization、X-Farm-Id 由拦截器添加；
 * API-L11、L18、L21、L23 另带 Idempotency-Key（即 API-L26 的 request_key）。
 */
public interface HerdApi {

    /** Anonymous, read-only traceability; this endpoint never returns the management archive. */
    @GET("api/v1/public/batch-traces/{token}")
    Single<ApiResponse<BatchTraceVO>> getPublicTrace(@Path("token") String token);

    // ===== 生产页与存栏 =====

    /** API-L01 生产页概况（L01） */
    @GET("api/v1/production/overview")
    Single<ApiResponse<ProductionOverviewVO>> getOverview();

    /** API-L02 我的栏舍（L02，员工） */
    @GET("api/v1/my-pens")
    Single<ApiResponse<MyPensVO>> getMyPens();

    /** API-L03 存栏汇总（L03） */
    @GET("api/v1/stock/summary")
    Single<ApiResponse<StockSummaryVO>> getStockSummary();

    /** API-L04 按栏舍存栏（L03 栏舍视图），sort：CODE / STOCK / OCCUPANCY */
    @GET("api/v1/stock/pens")
    Single<ApiResponse<PageVO<PenStockItemVO>>> getStockPens(@Nullable @Query("sort") String sort,
                                                             @Nullable @Query("keyword") String keyword,
                                                             @Query("page") int page, @Query("size") int size);

    /** API-L05 按品类存栏（L03 品类视图，不分页） */
    @GET("api/v1/stock/categories")
    Single<ApiResponse<List<CategoryStockVO>>> getStockCategories(@Nullable @Query("keyword") String keyword);

    // ===== 栏舍 =====

    /** API-L07 栏舍下拉（usage = OPTIONS，不分页） */
    @GET("api/v1/pens")
    Single<ApiResponse<List<PenOptionVO>>> getPenOptions(@Query("usage") String usage,
                                                         @Nullable @Query("category_id") Long categoryId,
                                                         @Nullable @Query("exclude_pen_id") Long excludePenId);

    /** API-L10 栏舍表单选项（L05） */
    @GET("api/v1/pens/form-options")
    Single<ApiResponse<PenFormOptionsVO>> getFormOptions();

    /** API-L08 栏舍详情（L04、L05 编辑） */
    @GET("api/v1/pens/{id}")
    Single<ApiResponse<PenDetailVO>> getPenDetail(@Path("id") long penId);

    /** API-L11 新增栏舍 */
    @POST("api/v1/pens")
    Single<ApiResponse<PenDetailVO>> createPen(@Body PenCreateRequest req,
                                               @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-L12 编辑栏舍 */
    @PUT("api/v1/pens/{id}")
    Single<ApiResponse<PenDetailVO>> updatePen(@Path("id") long penId, @Body PenUpdateRequest req);

    /** API-L13 停用栏舍 */
    @POST("api/v1/pens/{id}/disable")
    Single<ApiResponse<PenDetailVO>> disablePen(@Path("id") long penId, @Body PenStatusRequest req);

    /** API-L27 启用栏舍 */
    @POST("api/v1/pens/{id}/enable")
    Single<ApiResponse<PenDetailVO>> enablePen(@Path("id") long penId, @Body PenStatusRequest req);

    /** API-L25 实际执行人选项（L11） */
    @GET("api/v1/pens/{id}/executors")
    Single<ApiResponse<List<ExecutorOptionVO>>> getExecutors(@Path("id") long penId);

    // ===== 批次查询 =====

    /** API-L06 批次列表 / 下拉；status：ACTIVE / ENDED / ALL，sort：ENTRY_DATE / DAY_AGE */
    @GET("api/v1/batches")
    Single<ApiResponse<PageVO<BatchListItemVO>>> getBatches(@Nullable @Query("status") String status,
                                                            @Nullable @Query("keyword") String keyword,
                                                            @Nullable @Query("sort") String sort,
                                                            @Query("page") int page, @Query("size") int size);

    /** API-L14 扫码 / 批次号解析：qr_content（扫码原文，待定事项 1）与 batch_no 二选一 */
    @GET("api/v1/batches/lookup")
    Single<ApiResponse<BatchLookupVO>> lookup(@Nullable @Query("qr_content") String qrContent,
                                              @Nullable @Query("batch_no") String batchNo);

    /** API-L15 批次档案 */
    @GET("api/v1/batches/{id}")
    Single<ApiResponse<BatchDetailVO>> getBatch(@Path("id") long batchId);

    /** API-L16 生产记录时间线；type 为 TimelineFilter，null 为全部 */
    @GET("api/v1/batches/{id}/timeline")
    Single<ApiResponse<PageVO<TimelineItemVO>>> getTimeline(@Path("id") long batchId,
                                                            @Nullable @Query("type") String type,
                                                            @Query("page") int page, @Query("size") int size);

    /** API-L17 批次各栏舍在栏数 */
    @GET("api/v1/batches/{id}/pen-stocks")
    Single<ApiResponse<List<PenStockVO>>> getBatchPenStocks(@Path("id") long batchId);

    /** API-L19 存栏变动记录详情 */
    @GET("api/v1/batch-events/{id}")
    Single<ApiResponse<BatchEventDetailVO>> getEventDetail(@Path("id") long eventId);

    /** API-L26 按请求编号查询（提交超时后确认是否已保存） */
    @GET("api/v1/batch-events/by-request/{request_key}")
    Single<ApiResponse<RequestCheckVO>> checkByRequest(@Path("request_key") String requestKey);

    // ===== 批次写操作 =====

    /** API-L18 批次入栏 */
    @POST("api/v1/batches")
    Single<ApiResponse<BatchEntryResultVO>> createBatch(@Body BatchCreateRequest req,
                                                        @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-L20 修改入栏记录 */
    @PUT("api/v1/batches/{id}/entry")
    Single<ApiResponse<BatchDetailVO>> updateEntry(@Path("id") long batchId, @Body EntryUpdateRequest req);

    /** API-L28 删除批次（version 查询串传递） */
    @DELETE("api/v1/batches/{id}")
    Single<ApiResponse<Object>> deleteBatch(@Path("id") long batchId, @Query("version") int version);

    /** API-L21 转栏登记 */
    @POST("api/v1/batch-transfers")
    Single<ApiResponse<TransferResultVO>> createTransfer(@Body TransferCreateRequest req,
                                                         @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-L22 修改转栏记录 */
    @PUT("api/v1/batch-transfers/{id}")
    Single<ApiResponse<TransferResultVO>> updateTransfer(@Path("id") long eventId, @Body TransferUpdateRequest req);

    /** API-L23 死淘登记 */
    @POST("api/v1/batch-deaths")
    Single<ApiResponse<DeathResultVO>> createDeath(@Body DeathCreateRequest req,
                                                   @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-L24 修改死淘记录 */
    @PUT("api/v1/batch-deaths/{id}")
    Single<ApiResponse<DeathResultVO>> updateDeath(@Path("id") long eventId, @Body DeathUpdateRequest req);
}
