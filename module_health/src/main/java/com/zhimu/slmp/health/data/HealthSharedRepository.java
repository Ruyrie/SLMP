package com.zhimu.slmp.health.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.data.DictTypes;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.ChangeRequestFormVO;
import com.zhimu.slmp.common.model.vo.ChangeRequestVO;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.ui.change.model.request.ChangeRequestSubmitRequest;
import com.zhimu.slmp.health.model.vo.DrugLotOptionsVO;
import com.zhimu.slmp.health.model.vo.LinkableReportVO;
import com.zhimu.slmp.health.model.vo.MemberItemVO;
import com.zhimu.slmp.health.model.vo.PenOptionVO;
import com.zhimu.slmp.network.RxResult;

import java.math.BigDecimal;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * 复用接口：批号（M27）、可关联上报（I24）、成员（F16）、修改申请（U01、U02）、栏舍下拉（L07）、
 * 字典（U10：VACCINATE_METHOD、DISINFECT_METHOD，dict_cache 1 小时）与修改记录（U14）。
 */
@FeatureScope
public class HealthSharedRepository {

    /** API-L07 栏舍下拉用法 */
    public static final String PEN_USAGE_OPTIONS = "OPTIONS";
    /** 执行人下拉一次取 50 人（养殖场成员不会超过） */
    public static final int MEMBER_PAGE_SIZE = 50;

    private final HealthSharedApi api;
    private final BaseDataRepository baseData;
    private final FarmContext farmContext;

    @Inject
    public HealthSharedRepository(@NonNull HealthSharedApi api, @NonNull BaseDataRepository baseData,
                                  @NonNull FarmContext farmContext) {
        this.api = api;
        this.baseData = baseData;
        this.farmContext = farmContext;
    }

    @NonNull
    public Single<DrugLotOptionsVO> getLotOptions(long drugId, @NonNull String purpose, @Nullable BigDecimal needQty) {
        return api.getLotOptions(drugId, purpose, needQty).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<List<LinkableReportVO>> getLinkableReports(@Nullable Long batchId, @Nullable Long penId) {
        return api.getLinkableReports(batchId, penId).compose(RxResult.unwrap());
    }

    /** 当前养殖场在场成员（实际执行人下拉） */
    @NonNull
    public Single<List<MemberItemVO>> getMembers() {
        return api.getMembers(farmContext.farmIdOrZero(), "ALL", 1, MEMBER_PAGE_SIZE)
                .compose(RxResult.unwrap()).map(p -> p.getList());
    }

    @NonNull
    public Single<ChangeRequestFormVO> getChangeRequestForm(@NonNull String bizType, long bizId) {
        return api.getChangeRequestForm(bizType, bizId).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<ChangeRequestVO> submitChangeRequest(@NonNull ChangeRequestSubmitRequest body,
                                                       @NonNull String idemKey) {
        return api.submitChangeRequest(body, idemKey).compose(RxResult.unwrap());
    }

    /** 本场启用中栏舍（D07 多选、D08 临时消毒；员工由服务端裁剪为负责栏舍） */
    @NonNull
    public Single<List<PenOptionVO>> getActivePens() {
        return api.getPenOptions(PEN_USAGE_OPTIONS).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<List<DictItemVO>> getDicts(@NonNull String dictType, @Nullable Long categoryId) {
        return baseData.getDicts(dictType, categoryId);
    }

    @NonNull
    public Single<List<DictItemVO>> getVaccinateMethods(@Nullable Long categoryId) {
        return baseData.getDicts(DictTypes.VACCINATE_METHOD, categoryId);
    }

    @NonNull
    public Single<List<DictItemVO>> getDisinfectMethods() {
        return baseData.getDicts(DictTypes.DISINFECT_METHOD, null);
    }

    @NonNull
    public Single<List<RecordLogVO>> getRecordLogs(@NonNull String bizType, long bizId) {
        return baseData.getRecordLogs(bizType, bizId);
    }
}
