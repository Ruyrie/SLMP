package com.zhimu.slmp.stock.data;

import androidx.annotation.Nullable;

import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.model.ApiResponse;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.stock.model.request.DrugInboundRequest;
import com.zhimu.slmp.stock.model.request.DrugOutboundRequest;
import com.zhimu.slmp.stock.model.request.DrugSaveRequest;
import com.zhimu.slmp.stock.model.request.DrugScrapRequest;
import com.zhimu.slmp.stock.model.request.FeedInboundRequest;
import com.zhimu.slmp.stock.model.request.FeedLossRequest;
import com.zhimu.slmp.stock.model.request.FeedOtherOutRequest;
import com.zhimu.slmp.stock.model.request.FeedSaveRequest;
import com.zhimu.slmp.stock.model.request.InboundUpdateRequest;
import com.zhimu.slmp.stock.model.request.OutboundUpdateRequest;
import com.zhimu.slmp.stock.model.request.StockSettingRequest;
import com.zhimu.slmp.stock.model.request.StocktakeConfirmRequest;
import com.zhimu.slmp.stock.model.request.VoidRequest;
import com.zhimu.slmp.stock.model.vo.CatalogResultVO;
import com.zhimu.slmp.stock.model.vo.CostAllocationVO;
import com.zhimu.slmp.stock.model.vo.DrugArchiveListResult;
import com.zhimu.slmp.stock.model.vo.DrugDetailVO;
import com.zhimu.slmp.stock.model.vo.DrugLotDetailVO;
import com.zhimu.slmp.stock.model.vo.DrugLotItemVO;
import com.zhimu.slmp.stock.model.vo.DrugLotOptionsVO;
import com.zhimu.slmp.stock.model.vo.DrugOptionListResult;
import com.zhimu.slmp.stock.model.vo.DrugStockOverviewVO;
import com.zhimu.slmp.stock.model.vo.FeedDetailVO;
import com.zhimu.slmp.stock.model.vo.FeedOptionVO;
import com.zhimu.slmp.stock.model.vo.FeedStockItemVO;
import com.zhimu.slmp.stock.model.vo.FeedStockOverviewVO;
import com.zhimu.slmp.stock.model.vo.RequestStatusVO;
import com.zhimu.slmp.stock.model.vo.StockBatchOptionVO;
import com.zhimu.slmp.stock.model.vo.StockFlowItemVO;
import com.zhimu.slmp.stock.model.vo.StockInboundVO;
import com.zhimu.slmp.stock.model.vo.StockLossVO;
import com.zhimu.slmp.stock.model.vo.StockMemberVO;
import com.zhimu.slmp.stock.model.vo.StockOutboundVO;
import com.zhimu.slmp.stock.model.vo.StockPenOptionVO;
import com.zhimu.slmp.stock.model.vo.StockSettingVO;
import com.zhimu.slmp.stock.model.vo.StocktakeSheetVO;
import com.zhimu.slmp.stock.model.vo.StocktakeVO;

import java.math.BigDecimal;
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
 * 模块09 接口 API-M01–M42（服务端模块09 文档 2.5 节），另有 M09 用到的 API-U11 目录搜索，
 * 以及 M11 下拉借用的 API-L06、L07、F16（待定事项 2）。全部带 X-Farm-Id（拦截器添加），登记类 POST 带幂等键。
 */
public interface StockApi {

    // ===== 饲料 =====

    /** API-M01 饲料库存概览 */
    @GET("api/v1/feed-stock/overview")
    Single<ApiResponse<FeedStockOverviewVO>> getFeedStockOverview();

    /** API-M02 饲料结存列表（预警在前） */
    @GET("api/v1/feeds")
    Single<ApiResponse<PageVO<FeedStockItemVO>>> pageFeeds(@Query("page") int page, @Query("size") int size);

    /** API-M03 饲料下拉：purpose INBOUND / OTHER_OUT */
    @GET("api/v1/feeds/options")
    Single<ApiResponse<List<FeedOptionVO>>> listFeedOptions(@Query("purpose") String purpose,
                                                            @Nullable @Query("keyword") String keyword);

    /** API-M04 饲料档案详情 */
    @GET("api/v1/feeds/{id}")
    Single<ApiResponse<FeedDetailVO>> getFeed(@Path("id") long id);

    /** API-M05 新增饲料 */
    @POST("api/v1/feeds")
    Single<ApiResponse<FeedDetailVO>> createFeed(@Body FeedSaveRequest body);

    /** API-M06 修改饲料档案 */
    @PUT("api/v1/feeds/{id}")
    Single<ApiResponse<FeedDetailVO>> updateFeed(@Path("id") long id, @Body FeedSaveRequest body);

    /** API-M08 饲料入库 */
    @POST("api/v1/feed-inbounds")
    Single<ApiResponse<StockInboundVO>> createFeedInbound(@Body FeedInboundRequest body,
                                                          @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-M09 入库单详情 */
    @GET("api/v1/stock-inbounds/{id}")
    Single<ApiResponse<StockInboundVO>> getInbound(@Path("id") long id);

    /** API-M10 修改未出库入库单 */
    @PUT("api/v1/stock-inbounds/{id}")
    Single<ApiResponse<StockInboundVO>> updateInbound(@Path("id") long id, @Body InboundUpdateRequest body);

    /** API-M11 作废入库单 */
    @POST("api/v1/stock-inbounds/{id}/void")
    Single<ApiResponse<Object>> voidInbound(@Path("id") long id, @Body VoidRequest body);

    /** API-M12 最近供应商 / 退回对象（最多 5 个） */
    @GET("api/v1/stock-partners/recent")
    Single<ApiResponse<List<String>>> recentPartners(@Query("kind") String kind,
                                                     @Nullable @Query("keyword") String keyword);

    /** API-M13 饲料流水 */
    @GET("api/v1/feed-flows")
    Single<ApiResponse<PageVO<StockFlowItemVO>>> pageFeedFlows(@Nullable @Query("feed_id") Long feedId,
                                                               @Query("flow_group") String flowGroup,
                                                               @Query("page") int page, @Query("size") int size);

    /** API-M14 盘点表 */
    @GET("api/v1/stocktakes/sheet")
    Single<ApiResponse<StocktakeSheetVO>> getStocktakeSheet();

    /** API-M15 确认盘点 */
    @POST("api/v1/stocktakes")
    Single<ApiResponse<StocktakeVO>> confirmStocktake(@Body StocktakeConfirmRequest body,
                                                      @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-M16 盘点记录 */
    @GET("api/v1/stocktakes")
    Single<ApiResponse<PageVO<StocktakeVO>>> pageStocktakes(@Query("page") int page, @Query("size") int size);

    /** API-M17 盘点详情 */
    @GET("api/v1/stocktakes/{id}")
    Single<ApiResponse<StocktakeVO>> getStocktake(@Path("id") long id);

    /** API-M18 登记报损 */
    @POST("api/v1/feed-losses")
    Single<ApiResponse<StockLossVO>> createFeedLoss(@Body FeedLossRequest body,
                                                    @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-M19 报损 / 报废详情 */
    @GET("api/v1/stock-losses/{id}")
    Single<ApiResponse<StockLossVO>> getLoss(@Path("id") long id);

    /** API-M20 饲料其他出库 */
    @POST("api/v1/feed-other-outbounds")
    Single<ApiResponse<StockOutboundVO>> createFeedOtherOut(@Body FeedOtherOutRequest body,
                                                            @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-M21 出库单详情 */
    @GET("api/v1/stock-outbounds/{id}")
    Single<ApiResponse<StockOutboundVO>> getOutbound(@Path("id") long id);

    @POST("api/v1/stock-outbounds/{id}/refund")
    Single<ApiResponse<StockOutboundVO>> registerReturnRefund(@Path("id") long id,
            @Body com.zhimu.slmp.stock.model.request.ReturnRefundRequest body,
            @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    @PUT("api/v1/stock-outbounds/{id}/refund")
    Single<ApiResponse<StockOutboundVO>> updateReturnRefund(@Path("id") long id,
            @Body com.zhimu.slmp.stock.model.request.ReturnRefundRequest body,
            @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-M22 当天修改出库单 */
    @PUT("api/v1/stock-outbounds/{id}")
    Single<ApiResponse<StockOutboundVO>> updateOutbound(@Path("id") long id, @Body OutboundUpdateRequest body);

    /** API-M23 当天作废出库单 */
    @POST("api/v1/stock-outbounds/{id}/void")
    Single<ApiResponse<Object>> voidOutbound(@Path("id") long id, @Body VoidRequest body);

    // ===== 兽药疫苗 =====

    /** API-M24 兽药疫苗概览 */
    @GET("api/v1/drug-stock/overview")
    Single<ApiResponse<DrugStockOverviewVO>> getDrugStockOverview();

    /** API-M25 批号列表：group ATTENTION / NORMAL / ALL，tab_counts 带 ATTENTION、NORMAL */
    @GET("api/v1/drug-lots")
    Single<ApiResponse<PageVO<DrugLotItemVO>>> pageDrugLots(@Query("group") String group,
                                                            @Nullable @Query("category") String category,
                                                            @Nullable @Query("keyword") String keyword,
                                                            @Query("page") int page, @Query("size") int size);

    /** API-M26 批号详情 */
    @GET("api/v1/drug-lots/{id}")
    Single<ApiResponse<DrugLotDetailVO>> getDrugLot(@Path("id") long id);

    /** API-M27 选择批号：purpose USE / RETURN */
    @GET("api/v1/drug-lots/options")
    Single<ApiResponse<DrugLotOptionsVO>> listDrugLotOptions(@Query("drug_id") long drugId,
                                                             @Query("purpose") String purpose,
                                                             @Nullable @Query("need_qty") BigDecimal needQty);

    /** API-M28 兽药疫苗流水 */
    @GET("api/v1/drug-flows")
    Single<ApiResponse<PageVO<StockFlowItemVO>>> pageDrugFlows(@Nullable @Query("drug_lot_id") Long drugLotId,
                                                               @Nullable @Query("drug_id") Long drugId,
                                                               @Query("flow_group") String flowGroup,
                                                               @Query("page") int page, @Query("size") int size);

    /** API-M30 兽药疫苗入库 */
    @POST("api/v1/drug-inbounds")
    Single<ApiResponse<StockInboundVO>> createDrugInbound(@Body DrugInboundRequest body,
                                                          @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-M31 登记报废 */
    @POST("api/v1/drug-scraps")
    Single<ApiResponse<StockLossVO>> createDrugScrap(@Body DrugScrapRequest body,
                                                     @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-M32 领用 / 退货 */
    @POST("api/v1/drug-outbounds")
    Single<ApiResponse<StockOutboundVO>> createDrugOutbound(@Body DrugOutboundRequest body,
                                                            @Header(Headers.IDEMPOTENCY_KEY) String idemKey);

    /** API-M33 领用分摊预览（batch_id 或 pen_id 二选一） */
    @GET("api/v1/drug-outbounds/allocation-preview")
    Single<ApiResponse<CostAllocationVO>> previewAllocation(@Query("drug_lot_id") long drugLotId,
                                                           @Query("input_qty") BigDecimal inputQty,
                                                           @Query("input_unit") String inputUnit,
                                                           @Query("target_type") String targetType,
                                                           @Nullable @Query("batch_id") Long batchId,
                                                           @Nullable @Query("pen_id") Long penId);

    /** API-M34 药品档案列表（不分页） */
    @GET("api/v1/drugs")
    Single<ApiResponse<DrugArchiveListResult>> listDrugs(@Nullable @Query("keyword") String keyword,
                                                         @Nullable @Query("category") String category);

    /** API-M35 药品下拉：purpose INBOUND / OUTBOUND */
    @GET("api/v1/drugs/options")
    Single<ApiResponse<DrugOptionListResult>> listDrugOptions(@Query("purpose") String purpose,
                                                              @Nullable @Query("keyword") String keyword);

    /** API-M36 药品档案详情 */
    @GET("api/v1/drugs/{id}")
    Single<ApiResponse<DrugDetailVO>> getDrug(@Path("id") long id);

    /** API-M37 新增药品档案 */
    @POST("api/v1/drugs")
    Single<ApiResponse<DrugDetailVO>> createDrug(@Body DrugSaveRequest body);

    /** API-M38 修改药品档案 */
    @PUT("api/v1/drugs/{id}")
    Single<ApiResponse<DrugDetailVO>> updateDrug(@Path("id") long id, @Body DrugSaveRequest body);

    /** API-M40 读取临期天数 */
    @GET("api/v1/stock-settings")
    Single<ApiResponse<StockSettingVO>> getStockSetting();

    /** API-M41 保存临期天数 */
    @PUT("api/v1/stock-settings")
    Single<ApiResponse<StockSettingVO>> saveStockSetting(@Body StockSettingRequest body);

    /** API-M42 查询提交结果（request_id 即提交时的幂等键） */
    @GET("api/v1/stock-requests/{request_id}")
    Single<ApiResponse<RequestStatusVO>> getRequestStatus(@Path("request_id") String requestId);

    // ===== 借用的公共 / 其他模块接口 =====

    /** API-U11 兽药疫苗目录搜索（M09 名称面板）；结果不含停用与禁用药，命中禁用药时 banned_hit 有值 */
    @GET("api/v1/base/drug-catalog")
    Single<ApiResponse<CatalogResultVO>> searchCatalog(@Query("drug_category") String drugCategory,
                                                       @Nullable @Query("keyword") String keyword,
                                                       @Query("size") int size);

    /** API-L07 栏舍下拉（usage = OPTIONS） */
    @GET("api/v1/pens")
    Single<ApiResponse<List<StockPenOptionVO>>> getPenOptions(@Query("usage") String usage);

    /** API-L06 在栏批次（status = ACTIVE） */
    @GET("api/v1/batches")
    Single<ApiResponse<PageVO<StockBatchOptionVO>>> getActiveBatches(@Query("status") String status,
                                                                    @Query("page") int page,
                                                                    @Query("size") int size);

    /** API-F16 本场成员 */
    @GET("api/v1/farms/{farmId}/members")
    Single<ApiResponse<PageVO<StockMemberVO>>> getMembers(@Path("farmId") long farmId, @Query("role") String role,
                                                         @Query("page") int page, @Query("size") int size);
}
