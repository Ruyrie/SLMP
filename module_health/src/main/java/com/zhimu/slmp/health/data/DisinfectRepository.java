package com.zhimu.slmp.health.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingSource;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.health.model.request.DisinfectPlanPauseRequest;
import com.zhimu.slmp.health.model.request.DisinfectPlanPreviewRequest;
import com.zhimu.slmp.health.model.request.DisinfectPlanSaveRequest;
import com.zhimu.slmp.health.model.request.DisinfectRecordCreateRequest;
import com.zhimu.slmp.health.model.request.DisinfectRecordUpdateRequest;
import com.zhimu.slmp.health.model.request.VersionRequest;
import com.zhimu.slmp.health.model.vo.DisinfectPlanDetailVO;
import com.zhimu.slmp.health.model.vo.DisinfectPlanPreviewVO;
import com.zhimu.slmp.health.model.vo.DisinfectPlanVO;
import com.zhimu.slmp.health.model.vo.DisinfectRecordVO;
import com.zhimu.slmp.health.model.vo.DisinfectTaskOptionVO;
import com.zhimu.slmp.health.model.vo.DisinfectTaskPageVO;
import com.zhimu.slmp.health.model.vo.DisinfectTaskVO;
import com.zhimu.slmp.health.model.vo.DisinfectantOptionVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 消毒计划、任务与记录（API-D28–D40）。
 */
@FeatureScope
public class DisinfectRepository {

    private final DisinfectApi api;

    @Inject
    public DisinfectRepository(@NonNull DisinfectApi api) {
        this.api = api;
    }

    /** API-D28 分页；第 1 页到达时回调（读取 tab_counts） */
    @NonNull
    public PagingSource<Integer, DisinfectPlanVO> planPager(@NonNull String status,
                                                            @Nullable Consumer<PageVO<DisinfectPlanVO>> onFirstPage) {
        return new PagedSource<>((page, size) -> api.getPlans(status, page, size).compose(RxResult.unwrap()),
                onFirstPage);
    }

    @NonNull
    public Single<DisinfectPlanDetailVO> getPlan(long planId) {
        return api.getPlan(planId).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<DisinfectPlanPreviewVO> previewPlan(@NonNull DisinfectPlanPreviewRequest body) {
        return api.previewPlan(body).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<DisinfectPlanVO> createPlan(@NonNull DisinfectPlanSaveRequest body, @NonNull String idemKey) {
        return api.createPlan(body, idemKey).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<DisinfectPlanVO> updatePlan(long planId, @NonNull DisinfectPlanSaveRequest body) {
        return api.updatePlan(planId, body).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<DisinfectPlanVO> pausePlan(long planId, @NonNull DisinfectPlanPauseRequest body) {
        return api.pausePlan(planId, body).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<DisinfectPlanVO> resumePlan(long planId, @NonNull VersionRequest body) {
        return api.resumePlan(planId, body).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<List<DisinfectantOptionVO>> getDisinfectants(@Nullable String keyword) {
        return api.getDisinfectants(keyword).compose(RxResult.unwrap());
    }

    /** API-D36 分页；第 1 页到达时回调（tab_counts 与 overdue_banner，实际类型为 DisinfectTaskPageVO） */
    @NonNull
    public PagingSource<Integer, DisinfectTaskVO> taskPager(@NonNull String status, @Nullable Long penId,
                                                            @Nullable Consumer<PageVO<DisinfectTaskVO>> onFirstPage) {
        return new PagedSource<>((page, size) -> api.getTasks(status, penId, page, size).compose(RxResult.unwrap())
                .map(p -> (PageVO<DisinfectTaskVO>) p), onFirstPage);
    }

    @NonNull
    public Single<DisinfectTaskPageVO> taskMeta(@NonNull String status, @Nullable Long penId) {
        return api.getTasks(status, penId, 1, PagedSource.PAGE_SIZE).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<List<DisinfectTaskOptionVO>> getTaskOptions() {
        return api.getTaskOptions().compose(RxResult.unwrap());
    }

    @NonNull
    public Single<DisinfectRecordVO> createRecord(@NonNull DisinfectRecordCreateRequest body, @NonNull String idemKey) {
        return api.createRecord(body, idemKey).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<DisinfectRecordVO> getRecord(long recordId) {
        return api.getRecord(recordId).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<DisinfectRecordVO> updateRecord(long recordId, @NonNull DisinfectRecordUpdateRequest body) {
        return api.updateRecord(recordId, body).compose(RxResult.unwrap());
    }
}
