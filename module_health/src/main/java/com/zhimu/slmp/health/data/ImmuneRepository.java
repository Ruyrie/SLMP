package com.zhimu.slmp.health.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingSource;

import com.zhimu.slmp.common.di.FeatureScope;
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
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 免疫计划与接种记录（API-D01–D16、D41）。请求在 io 线程执行（BaseViewModel.launch 负责切换），这里不改 LiveData。
 */
@FeatureScope
public class ImmuneRepository {

    private final ImmuneApi api;

    @Inject
    public ImmuneRepository(@NonNull ImmuneApi api) {
        this.api = api;
    }

    @NonNull
    public Single<ImmunePlanOverviewVO> getOverview(long batchId) {
        return api.getOverview(batchId).compose(RxResult.unwrap());
    }

    /** API-D02 分页（每页 20 条） */
    @NonNull
    public PagingSource<Integer, ImmunePlanVO> planPager(long batchId, @NonNull String status,
                                                         @Nullable Consumer<PageVO<ImmunePlanVO>> onFirstPage) {
        return new PagedSource<>((page, size) -> api.getPlans(batchId, status, page, size).compose(RxResult.unwrap()),
                onFirstPage);
    }

    @NonNull
    public Single<ImmunePlanOverviewVO> confirmPreEntry(long planId, @NonNull VersionRequest body) {
        return api.confirmPreEntry(planId, body).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<ImmunePlanVO> joinPreEntry(long planId, @NonNull VersionRequest body) {
        return api.joinPreEntry(planId, body).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<ImmunePlanOverviewVO> undoPreEntry(long planId, @NonNull VersionRequest body) {
        return api.undoPreEntry(planId, body).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<ImmunePlanVO> postpone(long planId, @NonNull ImmunePlanPostponeRequest body) {
        return api.postpone(planId, body).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<ImmunePlanVO> createPlan(long batchId, @NonNull ImmunePlanCreateRequest body, @NonNull String idemKey) {
        return api.createPlan(batchId, body, idemKey).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<ImmunePlanVO> updatePlan(long planId, @NonNull ImmunePlanUpdateRequest body) {
        return api.updatePlan(planId, body).compose(RxResult.unwrap());
    }

    @NonNull
    public Completable deletePlan(long planId, int version) {
        return RxResult.completable(api.deletePlan(planId, version));
    }

    @NonNull
    public Single<PageVO<ImmunePlanOptionVO>> getPlanOptions(@Nullable String keyword, int page, int size) {
        return api.getPlanOptions(keyword, page, size).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<ImmuneRegisterFormVO> getRegisterForm(long planId, boolean backfill) {
        return api.getRegisterForm(planId, backfill ? Boolean.TRUE : null).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<List<VaccineOptionVO>> getVaccineOptions(@Nullable Long categoryId) {
        return api.getVaccineOptions(categoryId).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<ImmuneRecordVO> createRecord(@NonNull ImmuneRecordCreateRequest body, @NonNull String idemKey) {
        return api.createRecord(body, idemKey).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<ImmuneRecordVO> getRecord(long recordId) {
        return api.getRecord(recordId).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<ImmuneRecordVO> updateRecord(long recordId, @NonNull ImmuneRecordUpdateRequest body) {
        return api.updateRecord(recordId, body).compose(RxResult.unwrap());
    }

    /** API-D41：超时后按幂等键确认是否已保存 */
    @NonNull
    public Single<HealthRequestStatusVO> getRequestStatus(@NonNull String requestId, @NonNull String recordType) {
        return api.getRequestStatus(requestId, recordType).compose(RxResult.unwrap());
    }
}
