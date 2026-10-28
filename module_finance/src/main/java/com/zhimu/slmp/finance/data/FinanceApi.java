package com.zhimu.slmp.finance.data;

import androidx.annotation.Nullable;

import com.zhimu.slmp.finance.model.request.FinanceRecordRequest;
import com.zhimu.slmp.finance.model.request.ReportQuery;
import com.zhimu.slmp.finance.model.request.SaleAdviceRequest;
import com.zhimu.slmp.finance.model.request.SaleChangePreviewRequest;
import com.zhimu.slmp.finance.model.request.SaleOrderRequest;
import com.zhimu.slmp.finance.model.request.SaleOrderVoidRequest;
import com.zhimu.slmp.finance.model.request.SaleReceiptRequest;
import com.zhimu.slmp.finance.model.request.SubcategoryRequest;
import com.zhimu.slmp.finance.model.request.SupplementConfirmRequest;
import com.zhimu.slmp.finance.model.request.VersionRequest;
import com.zhimu.slmp.finance.model.vo.BatchSettlementVO;
import com.zhimu.slmp.finance.model.vo.BusinessOverviewVO;
import com.zhimu.slmp.finance.model.vo.BusinessReportVO;
import com.zhimu.slmp.finance.model.vo.DailyPriceVO;
import com.zhimu.slmp.finance.model.vo.DuplicateCheckVO;
import com.zhimu.slmp.finance.model.vo.FinanceCategoryVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordDetailVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordPageVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordVO;
import com.zhimu.slmp.finance.model.vo.FinanceSummaryVO;
import com.zhimu.slmp.finance.model.vo.MarketFilterVO;
import com.zhimu.slmp.finance.model.vo.MarketTrendVO;
import com.zhimu.slmp.finance.model.vo.ReceivableVO;
import com.zhimu.slmp.finance.model.vo.ReportPeriodVO;
import com.zhimu.slmp.finance.model.vo.ReportSummaryVO;
import com.zhimu.slmp.finance.model.vo.ReportTrendVO;
import com.zhimu.slmp.finance.model.vo.SaleAdviceVO;
import com.zhimu.slmp.finance.model.vo.SaleBatchOptionVO;
import com.zhimu.slmp.finance.model.vo.SaleChangePreviewVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderCreatedVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderPageVO;
import com.zhimu.slmp.finance.model.vo.SalePrefillVO;
import com.zhimu.slmp.finance.model.vo.SettlementOptionPageVO;
import com.zhimu.slmp.finance.model.vo.SubcategoryVO;
import com.zhimu.slmp.finance.model.vo.SubmissionVO;
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;

import java.math.BigDecimal;
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
 * 模块10 接口 API-E01–E41、E43（服务端模块10 文档 2.5 节；E42 导出见 {@link ReportExportApi}）。
 */
public interface FinanceApi {

    // ===== 收支 =====

    /** API-E01 经营概览 */
    @GET("api/v1/business-overview")
    Single<ApiResponse<BusinessOverviewVO>> getOverview();

    /** API-E02 月度汇总 */
    @GET("api/v1/finance-records/monthly-summary")
    Single<ApiResponse<FinanceSummaryVO>> getMonthlySummary(@Query("month") String month,
                                                           @Nullable @Query("scope") String scope);

    /** API-E03 收支流水 */
    @GET("api/v1/finance-records")
    Single<ApiResponse<FinanceRecordPageVO>> listRecords(@Nullable @Query("month") String month,
                                                         @Query("type") String type,
                                                         @Nullable @Query("category_ids") String categoryIds,
                                                         @Nullable @Query("batch_id") Long batchId,
                                                         @Nullable @Query("has_voucher") Boolean hasVoucher,
                                                         @Nullable @Query("keyword") String keyword,
                                                         @Nullable @Query("scope") String scope,
                                                         @Query("page") int page, @Query("size") int size);

    /** API-E04 收支详情 */
    @GET("api/v1/finance-records/{id}")
    Single<ApiResponse<FinanceRecordDetailVO>> getRecord(@Path("id") long id);

    /** API-E05 记一笔 */
    @POST("api/v1/finance-records")
    Single<ApiResponse<FinanceRecordVO>> createRecord(@Body FinanceRecordRequest body,
                                                      @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-E06 修改手工记录 */
    @PUT("api/v1/finance-records/{id}")
    Single<ApiResponse<FinanceRecordVO>> updateRecord(@Path("id") long id, @Body FinanceRecordRequest body);

    /** API-E07 作废手工记录 */
    @POST("api/v1/finance-records/{id}/void")
    Single<ApiResponse<FinanceRecordVO>> voidRecord(@Path("id") long id, @Body VersionRequest body);

    /** API-E09 疑似重复检查 */
    @GET("api/v1/finance-records/duplicate-check")
    Single<ApiResponse<DuplicateCheckVO>> checkDuplicate(@Query("record_type") String recordType,
                                                         @Query("category_id") long categoryId,
                                                         @Query("amount") BigDecimal amount,
                                                         @Query("record_date") String recordDate,
                                                         @Nullable @Query("exclude_id") Long excludeId);

    /** API-E10 一级分类与本场子类 */
    @GET("api/v1/finance-categories")
    Single<ApiResponse<List<FinanceCategoryVO>>> getCategories(@Nullable @Query("direction") String direction);

    /** API-E11 添加子类 */
    @POST("api/v1/expense-subcategories")
    Single<ApiResponse<SubcategoryVO>> addSubcategory(@Body SubcategoryRequest body,
                                                      @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-E12 子类改名 */
    @PUT("api/v1/expense-subcategories/{id}")
    Single<ApiResponse<SubcategoryVO>> renameSubcategory(@Path("id") long id, @Body SubcategoryRequest body);

    /** API-E13 删除子类 */
    @DELETE("api/v1/expense-subcategories/{id}")
    Single<ApiResponse<Object>> deleteSubcategory(@Path("id") long id);

    /** API-E43 按请求编号查询（提交超时后确认是否已保存） */
    @GET("api/v1/business-submissions/{client_request_id}")
    Single<ApiResponse<SubmissionVO>> getSubmission(@Path("client_request_id") String clientRequestId);

    // ===== 出栏与收款 =====

    /** API-E14 可出栏批次与休药状态 */
    @GET("api/v1/sale-orders/batch-options")
    Single<ApiResponse<List<SaleBatchOptionVO>>> getBatchOptions();

    /** API-E15 登记预填 */
    @GET("api/v1/sale-orders/prefill")
    Single<ApiResponse<SalePrefillVO>> getPrefill(@Nullable @Query("batch_id") Long batchId,
                                                  @Nullable @Query("sale_date") String saleDate);

    /** API-E16 登记出栏单 */
    @POST("api/v1/sale-orders")
    Single<ApiResponse<SaleOrderCreatedVO>> createOrder(@Body SaleOrderRequest body,
                                                        @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-E17 出栏单列表（App 不传 month） */
    @GET("api/v1/sale-orders")
    Single<ApiResponse<SaleOrderPageVO>> listOrders(@Query("status") String status,
                                                    @Nullable @Query("keyword") String keyword,
                                                    @Query("page") int page, @Query("size") int size);

    /** API-E18 待收款 */
    @GET("api/v1/sale-orders/receivables")
    Single<ApiResponse<ReceivableVO>> listReceivables(@Query("due_filter") String dueFilter,
                                                      @Query("page") int page, @Query("size") int size);

    /** API-E19 出栏单详情 */
    @GET("api/v1/sale-orders/{id}")
    Single<ApiResponse<SaleOrderDetailVO>> getOrder(@Path("id") long id);

    /** API-E20 修改未收款出栏单 */
    @PUT("api/v1/sale-orders/{id}")
    Single<ApiResponse<SaleOrderDetailVO>> updateOrder(@Path("id") long id, @Body SaleOrderRequest body);

    /** API-E21 作废未收款出栏单 */
    @POST("api/v1/sale-orders/{id}/void")
    Single<ApiResponse<SaleOrderDetailVO>> voidOrder(@Path("id") long id, @Body SaleOrderVoidRequest body);

    /** API-E22 登记收款 */
    @POST("api/v1/sale-orders/{id}/receipts")
    Single<ApiResponse<SaleOrderDetailVO>> addReceipt(@Path("id") long id, @Body SaleReceiptRequest body,
                                                      @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-E23 修改申请预计结果 */
    @POST("api/v1/sale-orders/{id}/change-preview")
    Single<ApiResponse<SaleChangePreviewVO>> previewChange(@Path("id") long id,
                                                           @Body SaleChangePreviewRequest body);

    // ===== 批次核算 =====

    /** API-E26 批次选择与待确认补录数 */
    @GET("api/v1/batch-settlements")
    Single<ApiResponse<SettlementOptionPageVO>> getSettlementOptions(@Nullable @Query("keyword") String keyword,
                                                                     @Query("page") int page,
                                                                     @Query("size") int size);

    /** API-E27 批次核算 */
    @GET("api/v1/batch-settlements/{batchId}")
    Single<ApiResponse<BatchSettlementVO>> getSettlement(@Path("batchId") long batchId);

    /** API-E28 确认补录并重算 */
    @POST("api/v1/batch-settlements/{batchId}/confirm-supplements")
    Single<ApiResponse<BatchSettlementVO>> confirmSupplements(@Path("batchId") long batchId,
                                                              @Body SupplementConfirmRequest body);

    // ===== 行情与 AI 出栏建议 =====

    /** API-E30 行情筛选项与默认值 */
    @GET("api/v1/market-prices/filters")
    Single<ApiResponse<MarketFilterVO>> getMarketFilters();

    /** API-E31 最新价、涨跌、走势、区间统计 */
    @GET("api/v1/market-prices/trend")
    Single<ApiResponse<MarketTrendVO>> getTrend(@Query("province_code") String provinceCode,
                                                @Query("category_id") long categoryId,
                                                @Query("breed_id") long breedId,
                                                @Query("price_type") String priceType,
                                                @Query("range_days") int rangeDays);

    /** API-E32 每日价格 */
    @GET("api/v1/market-prices/daily")
    Single<ApiResponse<PageVO<DailyPriceVO>>> getDailyPrices(@Query("province_code") String provinceCode,
                                                             @Query("category_id") long categoryId,
                                                             @Query("breed_id") long breedId,
                                                             @Query("price_type") String priceType,
                                                             @Query("page") int page, @Query("size") int size);

    /** API-E33 批次最近一次建议 */
    @retrofit2.http.Headers("X-Zmt-Read-Timeout: 90")
    @GET("api/v1/sale-advices/latest")
    Single<ApiResponse<SaleAdviceVO>> getLatestAdvice(@Query("batch_id") long batchId);

    /** API-E34 重新生成出栏建议 */
    @retrofit2.http.Headers("X-Zmt-Read-Timeout: 90")
    @POST("api/v1/sale-advices")
    Single<ApiResponse<SaleAdviceVO>> regenerateAdvice(@Body SaleAdviceRequest body);

    /** API-E35 历史建议 */
    @GET("api/v1/sale-advices")
    Single<ApiResponse<PageVO<SaleAdviceVO>>> listAdvices(@Nullable @Query("batch_id") Long batchId,
                                                          @Query("page") int page, @Query("size") int size);

    /** API-E36 建议全文与状态轮询 */
    @GET("api/v1/sale-advices/{id}")
    Single<ApiResponse<SaleAdviceVO>> getAdvice(@Path("id") long id);

    // ===== 经营报表 =====

    /** API-E37 已生成期次 */
    @GET("api/v1/business-reports/periods")
    Single<ApiResponse<ReportPeriodVO>> getPeriods(@Query("report_type") String reportType,
                                                   @Nullable @Query("scope") String scope);

    /** API-E38 报表指标与对比 */
    @GET("api/v1/business-reports/view")
    Single<ApiResponse<BusinessReportVO>> getReport(@Query("report_type") String reportType,
                                                    @Nullable @Query("period_start") String periodStart,
                                                    @Nullable @Query("scope") String scope,
                                                    @Nullable @Query("range_type") String rangeType,
                                                    @Nullable @Query("range_ref_id") Long rangeRefId);

    /** API-E39 指标逐日趋势 */
    @GET("api/v1/business-reports/trend")
    Single<ApiResponse<ReportTrendVO>> getReportTrend(@Query("report_type") String reportType,
                                                      @Nullable @Query("period_start") String periodStart,
                                                      @Nullable @Query("scope") String scope,
                                                      @Nullable @Query("range_type") String rangeType,
                                                      @Nullable @Query("range_ref_id") Long rangeRefId,
                                                      @Query("metric") String metric);

    /** API-E40 读取 AI 摘要（无摘要时服务端创建 GENERATING，客户端轮询本接口） */
    @GET("api/v1/business-reports/ai-summary")
    Single<ApiResponse<ReportSummaryVO>> getSummary(@Query("report_type") String reportType,
                                                    @Nullable @Query("period_start") String periodStart,
                                                    @Nullable @Query("scope") String scope,
                                                    @Nullable @Query("range_type") String rangeType,
                                                    @Nullable @Query("range_ref_id") Long rangeRefId);

    /** API-E41 重新生成 AI 摘要 */
    @POST("api/v1/business-reports/ai-summary/regenerate")
    Single<ApiResponse<ReportSummaryVO>> regenerateSummary(@Body ReportQuery body);
}
