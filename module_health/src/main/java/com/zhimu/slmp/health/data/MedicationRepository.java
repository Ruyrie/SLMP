package com.zhimu.slmp.health.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingSource;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.health.model.request.MedicationCreateRequest;
import com.zhimu.slmp.health.model.request.MedicationPreviewRequest;
import com.zhimu.slmp.health.model.request.MedicationUpdateRequest;
import com.zhimu.slmp.health.model.request.NotifyManagerRequest;
import com.zhimu.slmp.health.model.vo.DeleteImpactVO;
import com.zhimu.slmp.health.model.vo.MedicationCreateResultVO;
import com.zhimu.slmp.health.model.vo.MedicationDrugOptionVO;
import com.zhimu.slmp.health.model.vo.MedicationRecordVO;
import com.zhimu.slmp.health.model.vo.MedicationTargetVO;
import com.zhimu.slmp.health.model.vo.WithdrawalVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 用药与休药（API-D17–D27）。
 */
@FeatureScope
public class MedicationRepository {

    public static final String TARGET_BATCH = "BATCH";
    public static final String TARGET_PEN = "PEN";

    private final MedicationApi api;

    @Inject
    public MedicationRepository(@NonNull MedicationApi api) {
        this.api = api;
    }

    @NonNull
    public Single<WithdrawalVO> getWithdrawal(long batchId) {
        return api.getWithdrawal(batchId).compose(RxResult.unwrap());
    }

    /** API-D18 分页；第 1 页到达时回调（读取 total，io 线程） */
    @NonNull
    public PagingSource<Integer, MedicationRecordVO> recordPager(long batchId,
                                                                 @Nullable Consumer<PageVO<MedicationRecordVO>> onFirstPage) {
        return new PagedSource<>((page, size) -> api.getRecords(batchId, page, size).compose(RxResult.unwrap()),
                onFirstPage);
    }

    @NonNull
    public Single<List<MedicationTargetVO>> getTargets(@NonNull String targetType, @Nullable String keyword) {
        return api.getTargets(targetType, keyword).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<MedicationDrugOptionVO.ListResult> getDrugOptions(@Nullable Long batchId, @Nullable Long penId,
                                                                    @Nullable String keyword) {
        return api.getDrugOptions(batchId, penId, keyword).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<com.zhimu.slmp.health.model.vo.ReleaseDatePreviewVO> preview(@NonNull MedicationPreviewRequest body) {
        return api.preview(body).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<MedicationCreateResultVO> create(@NonNull MedicationCreateRequest body, @NonNull String idemKey) {
        return api.create(body, idemKey).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<MedicationRecordVO> getDetail(long recordId) {
        return api.getDetail(recordId).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<MedicationRecordVO> update(long recordId, @NonNull MedicationUpdateRequest body) {
        return api.update(recordId, body).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<DeleteImpactVO> getDeleteImpact(long recordId) {
        return api.getDeleteImpact(recordId).compose(RxResult.unwrap());
    }

    /** API-D26：返回 data.toast（“已删除用药记录”） */
    @NonNull
    public Single<String> delete(long recordId, int version) {
        return api.delete(recordId, version).compose(RxResult.unwrap())
                .map(vo -> vo.getToast() == null ? "" : vo.getToast());
    }

    /** API-D27：返回 data.notified_names（刘芳） */
    @NonNull
    public Single<List<String>> notifyManager(long recordId, @NonNull NotifyManagerRequest body) {
        return api.notifyManager(recordId, body).compose(RxResult.unwrap()).map(vo -> vo.getNotifiedNames());
    }
}
