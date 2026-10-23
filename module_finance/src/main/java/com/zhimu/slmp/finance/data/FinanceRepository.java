package com.zhimu.slmp.finance.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.finance.model.request.FinanceRecordQuery;
import com.zhimu.slmp.finance.model.request.FinanceRecordRequest;
import com.zhimu.slmp.finance.model.request.SubcategoryRequest;
import com.zhimu.slmp.finance.model.request.VersionRequest;
import com.zhimu.slmp.finance.model.vo.BusinessOverviewVO;
import com.zhimu.slmp.finance.model.vo.DuplicateCheckVO;
import com.zhimu.slmp.finance.model.vo.FinanceCategoryVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordDetailVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordPageVO;
import com.zhimu.slmp.finance.model.vo.FinanceRecordVO;
import com.zhimu.slmp.finance.model.vo.FinanceSummaryVO;
import com.zhimu.slmp.finance.model.vo.SubcategoryVO;
import com.zhimu.slmp.finance.model.vo.SubmissionVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.math.BigDecimal;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 收支仓库：经营概览 E01、月度汇总 E02、流水与记录 E03–E07、疑似重复 E09、分类与子类 E10–E13、提交查询 E43。
 */
@FeatureScope
public class FinanceRepository {

    private final FinanceApi api;

    @Inject
    public FinanceRepository(@NonNull FinanceApi api) {
        this.api = api;
    }

    /** API-E01 */
    @NonNull
    public Single<BusinessOverviewVO> getOverview() {
        return api.getOverview().compose(RxResult.unwrap());
    }

    /** API-E02；scope 为空或 ALL */
    @NonNull
    public Single<FinanceSummaryVO> getMonthlySummary(@NonNull String month, @Nullable String scope) {
        return api.getMonthlySummary(month, scope).compose(RxResult.unwrap());
    }

    /** API-E03 一页 */
    @NonNull
    public Single<FinanceRecordPageVO> loadRecordPage(@NonNull FinanceRecordQuery q, int page, int size) {
        return api.listRecords(q.getMonth(), q.getType(), q.getCategoryIds(), q.getBatchId(), q.getHasVoucher(),
                q.getKeyword(), q.getScope(), page, size).compose(RxResult.unwrap());
    }

    /** API-E03 分页数据源；每页到达时回调（读取 totals），在 io 线程调用 */
    @NonNull
    public PagedSource<FinanceRecordVO> pagingRecords(@NonNull FinanceRecordQuery q,
                                                      @Nullable Consumer<FinanceRecordPageVO> onPage) {
        return new PagedSource<>((page, size) -> loadRecordPage(q, page, size)
                .doOnSuccess(p -> {
                    if (onPage != null && page == 1) {
                        onPage.accept(p);
                    }
                })
                .map(p -> (PageVO<FinanceRecordVO>) p));
    }

    /** API-E04 */
    @NonNull
    public Single<FinanceRecordDetailVO> getRecordDetail(long id) {
        return api.getRecord(id).compose(RxResult.unwrap());
    }

    /** API-E05 */
    @NonNull
    public Single<FinanceRecordVO> createRecord(@NonNull FinanceRecordRequest r, @NonNull String idemKey) {
        return api.createRecord(r, idemKey).compose(RxResult.unwrap());
    }

    /** API-E06 */
    @NonNull
    public Single<FinanceRecordVO> updateRecord(long id, @NonNull FinanceRecordRequest r) {
        return api.updateRecord(id, r).compose(RxResult.unwrap());
    }

    /** API-E07 */
    @NonNull
    public Single<FinanceRecordVO> voidRecord(long id, @NonNull VersionRequest r) {
        return api.voidRecord(id, r).compose(RxResult.unwrap());
    }

    /** API-E09 */
    @NonNull
    public Single<DuplicateCheckVO> checkDuplicate(@NonNull String recordType, long categoryId,
                                                   @NonNull BigDecimal amount, @NonNull String recordDate,
                                                   @Nullable Long excludeId) {
        return api.checkDuplicate(recordType, categoryId, amount, recordDate, excludeId).compose(RxResult.unwrap());
    }

    /** API-E10；direction 为空时返回全部 */
    @NonNull
    public Single<List<FinanceCategoryVO>> getCategories(@Nullable String direction) {
        return api.getCategories(direction).compose(RxResult.unwrap());
    }

    /** API-E11 */
    @NonNull
    public Single<SubcategoryVO> addSubcategory(@NonNull SubcategoryRequest r, @NonNull String idemKey) {
        return api.addSubcategory(r, idemKey).compose(RxResult.unwrap());
    }

    /** API-E12 */
    @NonNull
    public Single<SubcategoryVO> renameSubcategory(long id, @NonNull SubcategoryRequest r) {
        return api.renameSubcategory(id, r).compose(RxResult.unwrap());
    }

    /** API-E13（返回 null） */
    @NonNull
    public Completable deleteSubcategory(long id) {
        return RxResult.completable(api.deleteSubcategory(id));
    }

    /** API-E43：提交超时后按幂等键确认是否已保存 */
    @NonNull
    public Single<SubmissionVO> getSubmission(@NonNull String clientRequestId) {
        return api.getSubmission(clientRequestId).compose(RxResult.unwrap());
    }
}
