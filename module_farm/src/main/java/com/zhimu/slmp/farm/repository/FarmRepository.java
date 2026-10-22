package com.zhimu.slmp.farm.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.rxjava3.RxPagingSource;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.farm.model.request.CreateFarmRequest;
import com.zhimu.slmp.farm.model.request.CreateTransferRequest;
import com.zhimu.slmp.farm.model.request.FarmVersionRequest;
import com.zhimu.slmp.farm.model.request.SubmitFarmChangeRequest;
import com.zhimu.slmp.farm.model.request.UpdateFarmBasicRequest;
import com.zhimu.slmp.farm.model.request.UpdateRegistrationRequest;
import com.zhimu.slmp.farm.model.vo.FarmAuditVO;
import com.zhimu.slmp.farm.model.vo.FarmBasicVO;
import com.zhimu.slmp.farm.model.vo.FarmChangeVO;
import com.zhimu.slmp.farm.model.vo.FarmDetailVO;
import com.zhimu.slmp.farm.model.vo.FarmListItemVO;
import com.zhimu.slmp.farm.model.vo.NameCheckVO;
import com.zhimu.slmp.farm.model.vo.TransferAcceptVO;
import com.zhimu.slmp.farm.model.vo.TransferPageVO;
import com.zhimu.slmp.farm.model.vo.TransferVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.Map;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 养殖场：列表、上下文与切换、新建与审核、养殖场信息、资料变更、停用启用、转让（API-F01–F14、F27–F32）。
 */
@FeatureScope
public class FarmRepository {

    /** F01 的 relation */
    public static final String RELATION_ALL = "ALL";

    private final FarmApi api;

    @Inject
    public FarmRepository(@NonNull FarmApi api) {
        this.api = api;
    }

    // ===== API-F01–F04 =====

    /** API-F01 分页数据源（每页 20 条，页码从 1）；onFirstPage 在 io 线程收到第 1 页（tab_counts） */
    @NonNull
    public RxPagingSource<Integer, FarmListItemVO> farmPagingSource(@NonNull String relation,
                                                                    @Nullable Consumer<PageVO<FarmListItemVO>> onFirstPage) {
        return new PagedSource<>((page, size) -> getFarms(relation, page, size), onFirstPage);
    }

    @NonNull
    public RxPagingSource<Integer, FarmListItemVO> farmPagingSource(@NonNull String relation) {
        return farmPagingSource(relation, null);
    }

    /** API-F01 一页（兜底切换时取第一页） */
    @NonNull
    public Single<PageVO<FarmListItemVO>> getFarms(@NonNull String relation, int page, int size) {
        return api.listFarms(relation, page, size).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<FarmContextVO> getContext(long farmId) {
        return api.getContext(farmId).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<FarmContextVO> switchFarm(long farmId) {
        return api.switchFarm(farmId).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<NameCheckVO> checkName(@NonNull String name, @Nullable Long excludeFarmId) {
        return api.checkName(name, excludeFarmId).compose(RxResult.unwrap());
    }

    // ===== API-F05–F08 =====

    @NonNull
    public Single<FarmAuditVO> createFarm(@NonNull CreateFarmRequest req, @NonNull String idemKey) {
        return api.createFarm(req, idemKey).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<FarmAuditVO> getAudit(long farmId) {
        return api.getAudit(farmId).compose(RxResult.unwrap());
    }

    @NonNull
    public Completable updateRegistration(long farmId, @NonNull UpdateRegistrationRequest req, @NonNull String idemKey) {
        return RxResult.completable(api.updateRegistration(farmId, req, idemKey));
    }

    @NonNull
    public Completable deleteFarm(long farmId) {
        return RxResult.completable(api.deleteFarm(farmId));
    }

    @NonNull
    public Completable quitFarm(long farmId) {
        return RxResult.completable(api.quitFarm(farmId));
    }

    // ===== API-F09–F14 =====

    @NonNull
    public Single<FarmDetailVO> getFarmDetail(long farmId) {
        return api.getFarmDetail(farmId).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<FarmBasicVO> updateBasic(long farmId, @NonNull UpdateFarmBasicRequest req) {
        return api.updateBasic(farmId, req).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<FarmDetailVO> disableFarm(long farmId, @NonNull FarmVersionRequest req) {
        return api.disableFarm(farmId, req).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<FarmDetailVO> enableFarm(long farmId, @NonNull FarmVersionRequest req) {
        return api.enableFarm(farmId, req).compose(RxResult.unwrap());
    }

    /** API-F13：data 为空时直接完成 */
    @NonNull
    public Maybe<FarmChangeVO> getLatestChange(long farmId) {
        return api.getLatestChange(farmId)
                .flatMapMaybe(resp -> {
                    if (!resp.isSuccess()) {
                        return Maybe.<FarmChangeVO>error(RxResult.fromResponse(resp));
                    }
                    FarmChangeVO data = resp.getData();
                    return data == null ? Maybe.<FarmChangeVO>empty() : Maybe.just(data);
                })
                .onErrorResumeNext(t -> Maybe.error(RxResult.toApiException(t)));
    }

    @NonNull
    public Single<FarmChangeVO> submitChange(long farmId, @NonNull SubmitFarmChangeRequest req, @NonNull String idemKey) {
        return api.submitChange(farmId, req, idemKey).compose(RxResult.unwrap());
    }

    // ===== API-F28–F32 =====

    @NonNull
    public Single<TransferPageVO> getTransferPage(long farmId) {
        return api.getTransferPage(farmId).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<TransferVO> createTransfer(long farmId, @NonNull CreateTransferRequest req, @NonNull String idemKey) {
        return api.createTransfer(farmId, req, idemKey).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<TransferVO> cancelTransfer(long transferId) {
        return api.cancelTransfer(transferId).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<TransferAcceptVO> acceptTransfer(long transferId) {
        return api.acceptTransfer(transferId).compose(RxResult.unwrap());
    }

    @NonNull
    public Completable rejectTransfer(long transferId) {
        return RxResult.completable(api.rejectTransfer(transferId));
    }

    /** F01 tab_counts 的键 */
    public static int tabCount(@NonNull Map<String, Integer> counts, @NonNull String key) {
        Integer v = counts.get(key);
        return v == null ? 0 : v;
    }
}
