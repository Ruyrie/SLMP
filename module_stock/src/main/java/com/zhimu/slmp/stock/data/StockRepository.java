package com.zhimu.slmp.stock.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.network.RxResult;
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
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * 模块09 数据仓库（技术准备文档 2.3.3）：Retrofit 已在 io 线程执行，这里只拆 ApiResponse（RxResult.unwrap）。
 * 分页接口返回 PageVO，由页面的 PagingSource 逐页调用。
 */
@FeatureScope
public class StockRepository {

    /** M01 饲料卡一次取全（page 1，size 100） */
    public static final int FEED_PAGE_SIZE = 100;

    private final StockApi api;
    private final FarmContext farmContext;

    @Inject
    public StockRepository(@NonNull StockApi api, @NonNull FarmContext farmContext) {
        this.api = api;
        this.farmContext = farmContext;
    }

    // ===== 饲料 =====

    public Single<FeedStockOverviewVO> getFeedStockOverview() {
        return api.getFeedStockOverview().compose(RxResult.unwrap());
    }

    public Single<PageVO<FeedStockItemVO>> pageFeeds(int page, int size) {
        return api.pageFeeds(page, size).compose(RxResult.unwrap());
    }

    public Single<List<FeedOptionVO>> listFeedOptions(@NonNull String purpose, @Nullable String keyword) {
        return api.listFeedOptions(purpose, keyword).compose(RxResult.unwrap());
    }

    public Single<FeedDetailVO> getFeed(long id) {
        return api.getFeed(id).compose(RxResult.unwrap());
    }

    public Single<FeedDetailVO> createFeed(@NonNull FeedSaveRequest req) {
        return api.createFeed(req).compose(RxResult.unwrap());
    }

    public Single<FeedDetailVO> updateFeed(long id, @NonNull FeedSaveRequest req) {
        return api.updateFeed(id, req).compose(RxResult.unwrap());
    }

    public Single<StockInboundVO> createFeedInbound(@NonNull FeedInboundRequest req, @NonNull String idemKey) {
        return api.createFeedInbound(req, idemKey).compose(RxResult.unwrap());
    }

    public Single<StockInboundVO> getInbound(long id) {
        return api.getInbound(id).compose(RxResult.unwrap());
    }

    public Single<StockInboundVO> updateInbound(long id, @NonNull InboundUpdateRequest req) {
        return api.updateInbound(id, req).compose(RxResult.unwrap());
    }

    public Completable voidInbound(long id, @NonNull VoidRequest req) {
        return RxResult.completable(api.voidInbound(id, req));
    }

    /** API-M12：失败时返回空列表（建议项不影响填写） */
    public Single<List<String>> recentPartners(@NonNull String kind, @Nullable String keyword) {
        return api.recentPartners(kind, keyword).compose(RxResult.unwrap())
                .onErrorReturnItem(Collections.emptyList());
    }

    public Single<PageVO<StockFlowItemVO>> pageFeedFlows(@Nullable Long feedId, @NonNull String group,
                                                         int page, int size) {
        return api.pageFeedFlows(feedId, group, page, size).compose(RxResult.unwrap());
    }

    public Single<StocktakeSheetVO> getStocktakeSheet() {
        return api.getStocktakeSheet().compose(RxResult.unwrap());
    }

    public Single<StocktakeVO> confirmStocktake(@NonNull StocktakeConfirmRequest req, @NonNull String idemKey) {
        return api.confirmStocktake(req, idemKey).compose(RxResult.unwrap());
    }

    public Single<PageVO<StocktakeVO>> pageStocktakes(int page, int size) {
        return api.pageStocktakes(page, size).compose(RxResult.unwrap());
    }

    public Single<StocktakeVO> getStocktake(long id) {
        return api.getStocktake(id).compose(RxResult.unwrap());
    }

    public Single<StockLossVO> createFeedLoss(@NonNull FeedLossRequest req, @NonNull String idemKey) {
        return api.createFeedLoss(req, idemKey).compose(RxResult.unwrap());
    }

    public Single<StockLossVO> getLoss(long id) {
        return api.getLoss(id).compose(RxResult.unwrap());
    }

    public Single<StockOutboundVO> createFeedOtherOut(@NonNull FeedOtherOutRequest req, @NonNull String idemKey) {
        return api.createFeedOtherOut(req, idemKey).compose(RxResult.unwrap());
    }

    public Single<StockOutboundVO> getOutbound(long id) {
        return api.getOutbound(id).compose(RxResult.unwrap());
    }

    public Single<StockOutboundVO> registerReturnRefund(long id,
            @NonNull com.zhimu.slmp.stock.model.request.ReturnRefundRequest request, @NonNull String key) {
        return api.registerReturnRefund(id, request, key).compose(RxResult.unwrap());
    }

    public Single<StockOutboundVO> updateReturnRefund(long id,
            @NonNull com.zhimu.slmp.stock.model.request.ReturnRefundRequest request, @NonNull String key) {
        return api.updateReturnRefund(id, request, key).compose(RxResult.unwrap());
    }

    public Single<StockOutboundVO> updateOutbound(long id, @NonNull OutboundUpdateRequest req) {
        return api.updateOutbound(id, req).compose(RxResult.unwrap());
    }

    public Completable voidOutbound(long id, @NonNull VoidRequest req) {
        return RxResult.completable(api.voidOutbound(id, req));
    }

    // ===== 兽药疫苗 =====

    public Single<DrugStockOverviewVO> getDrugStockOverview() {
        return api.getDrugStockOverview().compose(RxResult.unwrap());
    }

    public Single<PageVO<DrugLotItemVO>> pageDrugLots(@NonNull String group, @Nullable String category,
                                                      @Nullable String keyword, int page, int size) {
        return api.pageDrugLots(group, category, keyword, page, size).compose(RxResult.unwrap());
    }

    public Single<DrugLotDetailVO> getDrugLot(long id) {
        return api.getDrugLot(id).compose(RxResult.unwrap());
    }

    public Single<DrugLotOptionsVO> listDrugLotOptions(long drugId, @NonNull String purpose,
                                                       @Nullable BigDecimal needQty) {
        return api.listDrugLotOptions(drugId, purpose, needQty).compose(RxResult.unwrap());
    }

    public Single<PageVO<StockFlowItemVO>> pageDrugFlows(@Nullable Long lotId, @Nullable Long drugId,
                                                         @NonNull String group, int page, int size) {
        return api.pageDrugFlows(lotId, drugId, group, page, size).compose(RxResult.unwrap());
    }

    public Single<StockInboundVO> createDrugInbound(@NonNull DrugInboundRequest req, @NonNull String idemKey) {
        return api.createDrugInbound(req, idemKey).compose(RxResult.unwrap());
    }

    public Single<StockLossVO> createDrugScrap(@NonNull DrugScrapRequest req, @NonNull String idemKey) {
        return api.createDrugScrap(req, idemKey).compose(RxResult.unwrap());
    }

    public Single<StockOutboundVO> createDrugOutbound(@NonNull DrugOutboundRequest req, @NonNull String idemKey) {
        return api.createDrugOutbound(req, idemKey).compose(RxResult.unwrap());
    }

    public Single<CostAllocationVO> previewAllocation(long lotId, @NonNull BigDecimal qty, @NonNull String inputUnit,
                                                      @NonNull String targetType, @Nullable Long batchId,
                                                      @Nullable Long penId) {
        return api.previewAllocation(lotId, qty, inputUnit, targetType, batchId, penId).compose(RxResult.unwrap());
    }

    public Single<DrugArchiveListResult> listDrugs(@Nullable String keyword, @Nullable String category) {
        return api.listDrugs(keyword, category).compose(RxResult.unwrap());
    }

    public Single<DrugOptionListResult> listDrugOptions(@NonNull String purpose, @Nullable String keyword) {
        return api.listDrugOptions(purpose, keyword).compose(RxResult.unwrap());
    }

    public Single<DrugDetailVO> getDrug(long id) {
        return api.getDrug(id).compose(RxResult.unwrap());
    }

    public Single<DrugDetailVO> createDrug(@NonNull DrugSaveRequest req) {
        return api.createDrug(req).compose(RxResult.unwrap());
    }

    public Single<DrugDetailVO> updateDrug(long id, @NonNull DrugSaveRequest req) {
        return api.updateDrug(id, req).compose(RxResult.unwrap());
    }

    public Single<StockSettingVO> getStockSetting() {
        return api.getStockSetting().compose(RxResult.unwrap());
    }

    public Completable saveStockSetting(@NonNull StockSettingRequest req) {
        return RxResult.completable(api.saveStockSetting(req));
    }

    public Single<RequestStatusVO> getRequestStatus(@NonNull String requestId) {
        return api.getRequestStatus(requestId).compose(RxResult.unwrap());
    }

    // ===== 借用接口 =====

    public Single<CatalogResultVO> searchCatalog(@NonNull String category, @Nullable String keyword) {
        return api.searchCatalog(category, keyword, 50).compose(RxResult.unwrap());
    }

    public Single<List<StockPenOptionVO>> getPenOptions() {
        return api.getPenOptions("OPTIONS").compose(RxResult.unwrap());
    }

    public Single<List<StockBatchOptionVO>> getActiveBatches() {
        return api.getActiveBatches("ACTIVE", 1, 100).compose(RxResult.unwrap())
                .map(p -> p.getList() == null ? Collections.<StockBatchOptionVO>emptyList() : p.getList());
    }

    public Single<List<StockMemberVO>> getMembers() {
        return api.getMembers(farmContext.farmIdOrZero(), "ALL", 1, 50).compose(RxResult.unwrap())
                .map(p -> p.getList() == null ? Collections.<StockMemberVO>emptyList() : p.getList());
    }
}
