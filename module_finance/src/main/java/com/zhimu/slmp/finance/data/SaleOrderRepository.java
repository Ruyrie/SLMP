package com.zhimu.slmp.finance.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.finance.model.request.SaleChangePreviewRequest;
import com.zhimu.slmp.finance.model.request.SaleOrderRequest;
import com.zhimu.slmp.finance.model.request.SaleOrderVoidRequest;
import com.zhimu.slmp.finance.model.request.SaleReceiptRequest;
import com.zhimu.slmp.finance.model.vo.ReceivableVO;
import com.zhimu.slmp.finance.model.vo.SaleBatchOptionVO;
import com.zhimu.slmp.finance.model.vo.SaleChangePreviewVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderCreatedVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderItemVO;
import com.zhimu.slmp.finance.model.vo.SaleOrderPageVO;
import com.zhimu.slmp.finance.model.vo.SalePrefillVO;
import com.zhimu.slmp.finance.model.vo.SubmissionVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 出栏与收款仓库：API-E14–E23（含提交超时查询 E43）。
 */
@FeatureScope
public class SaleOrderRepository {

    private final FinanceApi api;

    @Inject
    public SaleOrderRepository(@NonNull FinanceApi api) {
        this.api = api;
    }

    /** API-E14 */
    @NonNull
    public Single<List<SaleBatchOptionVO>> getBatchOptions() {
        return api.getBatchOptions().compose(RxResult.unwrap());
    }

    /** API-E15；收款面板只要 last_payment_method 时 batchId、saleDate 都传 null */
    @NonNull
    public Single<SalePrefillVO> getPrefill(@Nullable Long batchId, @Nullable String saleDate) {
        return api.getPrefill(batchId, saleDate).compose(RxResult.unwrap());
    }

    /** API-E16 */
    @NonNull
    public Single<SaleOrderCreatedVO> createOrder(@NonNull SaleOrderRequest r, @NonNull String idemKey) {
        return api.createOrder(r, idemKey).compose(RxResult.unwrap());
    }

    /** API-E17 一页 */
    @NonNull
    public Single<SaleOrderPageVO> loadOrderPage(@NonNull String status, @Nullable String keyword, int page,
                                                 int size) {
        return api.listOrders(status, keyword, page, size).compose(RxResult.unwrap());
    }

    /** API-E17 分页数据源；每一页到达时回调（月份分组逐页合并、第 1 页的 tab_counts），在 io 线程调用 */
    @NonNull
    public PagedSource<SaleOrderItemVO> pagingOrders(@NonNull String status, @Nullable String keyword,
                                                     @Nullable Consumer<SaleOrderPageVO> onPage) {
        return new PagedSource<>((page, size) -> loadOrderPage(status, keyword, page, size)
                .doOnSuccess(p -> {
                    if (onPage != null) {
                        onPage.accept(p);
                    }
                })
                .map(p -> (PageVO<SaleOrderItemVO>) p));
    }

    /** API-E18 一页 */
    @NonNull
    public Single<ReceivableVO> loadReceivablePage(@NonNull String dueFilter, int page, int size) {
        return api.listReceivables(dueFilter, page, size).compose(RxResult.unwrap());
    }

    /** API-E18 分页数据源；第 1 页到达时回调（tab_counts、summary、remind_rule_text） */
    @NonNull
    public PagedSource<SaleOrderItemVO> pagingReceivables(@NonNull String dueFilter,
                                                          @Nullable Consumer<ReceivableVO> onFirstPage) {
        return new PagedSource<>((page, size) -> loadReceivablePage(dueFilter, page, size)
                .doOnSuccess(p -> {
                    if (onFirstPage != null && page == 1) {
                        onFirstPage.accept(p);
                    }
                })
                .map(p -> (PageVO<SaleOrderItemVO>) p));
    }

    /** API-E19 */
    @NonNull
    public Single<SaleOrderDetailVO> getOrderDetail(long id) {
        return api.getOrder(id).compose(RxResult.unwrap());
    }

    /** API-E20 */
    @NonNull
    public Single<SaleOrderDetailVO> updateOrder(long id, @NonNull SaleOrderRequest r) {
        return api.updateOrder(id, r).compose(RxResult.unwrap());
    }

    /** API-E21 */
    @NonNull
    public Single<SaleOrderDetailVO> voidOrder(long id, @NonNull SaleOrderVoidRequest r) {
        return api.voidOrder(id, r).compose(RxResult.unwrap());
    }

    /** API-E22 */
    @NonNull
    public Single<SaleOrderDetailVO> addReceipt(long id, @NonNull SaleReceiptRequest r, @NonNull String idemKey) {
        return api.addReceipt(id, r, idemKey).compose(RxResult.unwrap());
    }

    /** API-E23 */
    @NonNull
    public Single<SaleChangePreviewVO> previewChange(long id, @NonNull SaleChangePreviewRequest r) {
        return api.previewChange(id, r).compose(RxResult.unwrap());
    }

    /** API-E43 */
    @NonNull
    public Single<SubmissionVO> getSubmission(@NonNull String clientRequestId) {
        return api.getSubmission(clientRequestId).compose(RxResult.unwrap());
    }
}
