package com.zhimu.slmp.finance.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.finance.model.request.SupplementConfirmRequest;
import com.zhimu.slmp.finance.model.vo.BatchSettlementVO;
import com.zhimu.slmp.finance.model.vo.SettlementOptionPageVO;
import com.zhimu.slmp.network.RxResult;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * 批次核算仓库：API-E26–E28。
 */
@FeatureScope
public class SettlementRepository {

    /** E03 关联批次、E13 按批次一次取 100 条 */
    public static final int OPTION_SIZE = 100;

    private final FinanceApi api;

    @Inject
    public SettlementRepository(@NonNull FinanceApi api) {
        this.api = api;
    }

    /** API-E26：在养在前、已结束在后 */
    @NonNull
    public Single<SettlementOptionPageVO> getOptions(@Nullable String keyword, int page, int size) {
        return api.getSettlementOptions(keyword, page, size).compose(RxResult.unwrap());
    }

    /** API-E27 */
    @NonNull
    public Single<BatchSettlementVO> getSettlement(long batchId) {
        return api.getSettlement(batchId).compose(RxResult.unwrap());
    }

    /** API-E28 */
    @NonNull
    public Single<BatchSettlementVO> confirmSupplements(long batchId, @NonNull SupplementConfirmRequest r) {
        return api.confirmSupplements(batchId, r).compose(RxResult.unwrap());
    }
}
