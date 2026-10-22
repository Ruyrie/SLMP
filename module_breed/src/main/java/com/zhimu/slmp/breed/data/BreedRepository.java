package com.zhimu.slmp.breed.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.breed.data.paging.AnimalPagingSource;
import com.zhimu.slmp.breed.data.paging.FarrowDuePagingSource;
import com.zhimu.slmp.breed.data.paging.HatchDuePagingSource;
import com.zhimu.slmp.breed.data.paging.RecordPagingSource;
import com.zhimu.slmp.breed.model.request.AnimalExitRequest;
import com.zhimu.slmp.breed.model.request.BreedingAnimalQuery;
import com.zhimu.slmp.breed.model.request.BreedingAnimalRequest;
import com.zhimu.slmp.breed.model.request.BreedingDueQuery;
import com.zhimu.slmp.breed.model.request.BreedingOptionQuery;
import com.zhimu.slmp.breed.model.request.CandlingRequest;
import com.zhimu.slmp.breed.model.request.FarrowingRequest;
import com.zhimu.slmp.breed.model.request.FarrowingUpdateRequest;
import com.zhimu.slmp.breed.model.request.HatchRequest;
import com.zhimu.slmp.breed.model.request.IncubationRequest;
import com.zhimu.slmp.breed.model.request.IncubationUpdateRequest;
import com.zhimu.slmp.breed.model.request.MatingRequest;
import com.zhimu.slmp.breed.model.request.MatingUpdateRequest;
import com.zhimu.slmp.breed.model.request.PregnancyCheckRequest;
import com.zhimu.slmp.breed.model.request.PregnancyCheckUpdateRequest;
import com.zhimu.slmp.breed.model.request.WeaningRequest;
import com.zhimu.slmp.breed.model.request.WeaningUpdateRequest;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalDetailVO;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalItemVO;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalOptionVO;
import com.zhimu.slmp.breed.model.vo.BreedingOverviewVO;
import com.zhimu.slmp.breed.model.vo.BreedingRecordVO;
import com.zhimu.slmp.breed.model.vo.FarrowDueItemVO;
import com.zhimu.slmp.breed.model.vo.FarrowingDetailVO;
import com.zhimu.slmp.breed.model.vo.FlockOptionVO;
import com.zhimu.slmp.breed.model.vo.HatchDueItemVO;
import com.zhimu.slmp.breed.model.vo.IncubationDetailVO;
import com.zhimu.slmp.breed.model.vo.PenOptionVO;
import com.zhimu.slmp.breed.model.vo.PendingCheckVO;
import com.zhimu.slmp.breed.model.vo.RegisterContextVO;
import com.zhimu.slmp.breed.model.vo.RegisterResultVO;
import com.zhimu.slmp.breed.model.vo.SubmissionVO;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 繁育管理仓库（技术文档 2.5.1 节）：查询与提交转为 BreedApi 调用并解出 data；分页方法返回 Paging 数据源，
 * 由 ViewModel 的 Pager 使用（每页 20 条）。
 */
@FeatureScope
public class BreedRepository {

    /** 待妊检不分页，一次取 50 条 */
    public static final int CHECK_PAGE_SIZE = 50;
    private static final String PEN_USAGE_OPTIONS = "OPTIONS";

    private final BreedApi api;

    @Inject
    public BreedRepository(@NonNull BreedApi api) {
        this.api = api;
    }

    // ===== 查询 =====

    /** API-B01 */
    @NonNull
    public Single<BreedingOverviewVO> getOverview() {
        return api.getOverview().compose(RxResult.unwrap());
    }

    /** API-B02 分页源 */
    @NonNull
    public AnimalPagingSource animalSource(@NonNull BreedingAnimalQuery query,
                                           @Nullable Consumer<PageVO<BreedingAnimalItemVO>> onFirstPage) {
        return new AnimalPagingSource(api, query, onFirstPage);
    }

    /** API-B04 */
    @NonNull
    public Single<PageVO<BreedingAnimalOptionVO>> getOptions(@NonNull BreedingOptionQuery query) {
        return api.getOptions(query.getPurpose(), query.getKeyword(), query.getPage(), query.getSize())
                .compose(RxResult.unwrap());
    }

    /** API-B05 */
    @NonNull
    public Single<BreedingAnimalDetailVO> getAnimal(long animalId) {
        return api.getAnimal(animalId).compose(RxResult.unwrap());
    }

    /** API-B06 分页源 */
    @NonNull
    public RecordPagingSource recordSource(long animalId) {
        return new RecordPagingSource(api, animalId);
    }

    /** API-B11 */
    @NonNull
    public Single<RegisterContextVO> getRegisterContext(long animalId, @NonNull String type) {
        return api.getRegisterContext(animalId, type).compose(RxResult.unwrap());
    }

    /** API-B14 */
    @NonNull
    public Single<PageVO<PendingCheckVO>> getPendingChecks() {
        return api.getPendingChecks(1, CHECK_PAGE_SIZE).compose(RxResult.unwrap());
    }

    /** API-B19 */
    @NonNull
    public Single<FarrowingDetailVO> getFarrowing(long farrowingId) {
        return api.getFarrowing(farrowingId).compose(RxResult.unwrap());
    }

    /** API-B22 分页源（第 1 页回调 tab_counts） */
    @NonNull
    public FarrowDuePagingSource farrowDueSource(@NonNull BreedingDueQuery query,
                                                 @Nullable Consumer<PageVO<FarrowDueItemVO>> onFirstPage) {
        return new FarrowDuePagingSource(api, query, onFirstPage);
    }

    /** API-B23 分页源 */
    @NonNull
    public HatchDuePagingSource hatchDueSource(@NonNull BreedingDueQuery query,
                                               @Nullable Consumer<PageVO<HatchDueItemVO>> onFirstPage) {
        return new HatchDuePagingSource(api, query, onFirstPage);
    }

    /** API-B28 */
    @NonNull
    public Single<IncubationDetailVO> getIncubation(long incubationId) {
        return api.getIncubation(incubationId).compose(RxResult.unwrap());
    }

    /** API-B32 */
    @NonNull
    public Single<SubmissionVO> getSubmission(@NonNull String clientRequestId, @NonNull String type) {
        return api.getSubmission(clientRequestId, type).compose(RxResult.unwrap());
    }

    /** API-L06：本场用途为种用的在栏禽类批次 */
    @NonNull
    public Single<List<FlockOptionVO>> getFlocks() {
        return api.getFlocks("BREEDING", "POULTRY", "ACTIVE", 1, CHECK_PAGE_SIZE)
                .compose(RxResult.unwrap())
                .map(PageVO::getList);
    }

    /** API-L07 栏舍下拉 */
    @NonNull
    public Single<List<PenOptionVO>> getPenOptions(@Nullable Long categoryId) {
        return api.getPenOptions(PEN_USAGE_OPTIONS, categoryId).compose(RxResult.unwrap());
    }

    // ===== 档案 =====

    /** API-B07 */
    @NonNull
    public Single<BreedingAnimalDetailVO> createAnimal(@NonNull BreedingAnimalRequest req, @NonNull String idemKey) {
        return api.createAnimal(req, idemKey).compose(RxResult.unwrap());
    }

    /** API-B08 */
    @NonNull
    public Single<BreedingAnimalDetailVO> updateAnimal(long animalId, @NonNull BreedingAnimalRequest req) {
        return api.updateAnimal(animalId, req).compose(RxResult.unwrap());
    }

    /** API-B09 */
    @NonNull
    public Single<BreedingAnimalDetailVO> exitAnimal(long animalId, @NonNull AnimalExitRequest req,
                                                     @NonNull String idemKey) {
        return api.exitAnimal(animalId, req, idemKey).compose(RxResult.unwrap());
    }

    // ===== 登记与修改 =====

    /** API-B12 */
    @NonNull
    public Single<RegisterResultVO> createMating(@NonNull MatingRequest req, @NonNull String idemKey) {
        return api.createMating(req, idemKey).compose(RxResult.unwrap());
    }

    /** API-B13 */
    @NonNull
    public Single<BreedingRecordVO> updateMating(long recordId, @NonNull MatingUpdateRequest req) {
        return api.updateMating(recordId, req).compose(RxResult.unwrap());
    }

    /** API-B15 */
    @NonNull
    public Single<RegisterResultVO> createPregCheck(@NonNull PregnancyCheckRequest req, @NonNull String idemKey) {
        return api.createPregCheck(req, idemKey).compose(RxResult.unwrap());
    }

    /** API-B16 */
    @NonNull
    public Single<BreedingRecordVO> updatePregCheck(long recordId, @NonNull PregnancyCheckUpdateRequest req) {
        return api.updatePregCheck(recordId, req).compose(RxResult.unwrap());
    }

    /** API-B17 */
    @NonNull
    public Single<RegisterResultVO> createFarrowing(@NonNull FarrowingRequest req, @NonNull String idemKey) {
        return api.createFarrowing(req, idemKey).compose(RxResult.unwrap());
    }

    /** API-B18 */
    @NonNull
    public Single<BreedingRecordVO> updateFarrowing(long recordId, @NonNull FarrowingUpdateRequest req) {
        return api.updateFarrowing(recordId, req).compose(RxResult.unwrap());
    }

    /** API-B20 */
    @NonNull
    public Single<RegisterResultVO> createWeaning(@NonNull WeaningRequest req, @NonNull String idemKey) {
        return api.createWeaning(req, idemKey).compose(RxResult.unwrap());
    }

    /** API-B21 */
    @NonNull
    public Single<BreedingRecordVO> updateWeaning(long recordId, @NonNull WeaningUpdateRequest req) {
        return api.updateWeaning(recordId, req).compose(RxResult.unwrap());
    }

    // ===== 孵化 =====

    /** API-B26 */
    @NonNull
    public Single<RegisterResultVO> createIncubation(@NonNull IncubationRequest req, @NonNull String idemKey) {
        return api.createIncubation(req, idemKey).compose(RxResult.unwrap());
    }

    /** API-B29 */
    @NonNull
    public Single<IncubationDetailVO> updateIncubation(long incubationId, @NonNull IncubationUpdateRequest req) {
        return api.updateIncubation(incubationId, req).compose(RxResult.unwrap());
    }

    /** API-B30 */
    @NonNull
    public Single<IncubationDetailVO> candle(long incubationId, @NonNull CandlingRequest req, @NonNull String idemKey) {
        return api.candle(incubationId, req, idemKey).compose(RxResult.unwrap());
    }

    /** API-B31 */
    @NonNull
    public Single<RegisterResultVO> hatch(long incubationId, @NonNull HatchRequest req, @NonNull String idemKey) {
        return api.hatch(incubationId, req, idemKey).compose(RxResult.unwrap());
    }
}
