package com.zhimu.slmp.herd.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.herd.model.request.PenCreateRequest;
import com.zhimu.slmp.herd.model.request.PenStatusRequest;
import com.zhimu.slmp.herd.model.request.PenUpdateRequest;
import com.zhimu.slmp.herd.model.vo.ExecutorOptionVO;
import com.zhimu.slmp.herd.model.vo.PenDetailVO;
import com.zhimu.slmp.herd.model.vo.PenFormOptionsVO;
import com.zhimu.slmp.herd.model.vo.PenOptionVO;
import com.zhimu.slmp.network.RxResult;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * 栏舍接口（API-L07、L08、L10–L13、L25、L27）。
 */
@FeatureScope
public class PenRepository {

    /** API-L07 不分页下拉 */
    public static final String USAGE_OPTIONS = "OPTIONS";

    private final HerdApi api;

    @Inject
    public PenRepository(@NonNull HerdApi api) {
        this.api = api;
    }

    /** API-L07（usage = OPTIONS）：categoryId 标出品类冲突，excludePenId 排除转出栏舍 */
    @NonNull
    public Single<List<PenOptionVO>> getPenOptions(@Nullable Long categoryId, @Nullable Long excludePenId) {
        return api.getPenOptions(USAGE_OPTIONS, categoryId, excludePenId).compose(RxResult.unwrap());
    }

    /** API-L08 */
    @NonNull
    public Single<PenDetailVO> getPenDetail(long penId) {
        return api.getPenDetail(penId).compose(RxResult.unwrap());
    }

    /** API-L10 */
    @NonNull
    public Single<PenFormOptionsVO> getFormOptions() {
        return api.getFormOptions().compose(RxResult.unwrap());
    }

    /** API-L11 */
    @NonNull
    public Single<PenDetailVO> createPen(@NonNull PenCreateRequest req, @NonNull String idemKey) {
        return api.createPen(req, idemKey).compose(RxResult.unwrap());
    }

    /** API-L12 */
    @NonNull
    public Single<PenDetailVO> updatePen(long penId, @NonNull PenUpdateRequest req) {
        return api.updatePen(penId, req).compose(RxResult.unwrap());
    }

    /** API-L13 */
    @NonNull
    public Single<PenDetailVO> disablePen(long penId, @NonNull PenStatusRequest req) {
        return api.disablePen(penId, req).compose(RxResult.unwrap());
    }

    /** API-L27 */
    @NonNull
    public Single<PenDetailVO> enablePen(long penId, @NonNull PenStatusRequest req) {
        return api.enablePen(penId, req).compose(RxResult.unwrap());
    }

    /** API-L25 */
    @NonNull
    public Single<List<ExecutorOptionVO>> getExecutors(long penId) {
        return api.getExecutors(penId).compose(RxResult.unwrap());
    }
}
