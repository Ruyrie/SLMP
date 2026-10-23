package com.zhimu.slmp.inspect.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.inspect.model.request.InspectionBackfillRequest;
import com.zhimu.slmp.inspect.model.request.InspectionSettingRequest;
import com.zhimu.slmp.inspect.model.request.InspectionSubmitRequest;
import com.zhimu.slmp.inspect.model.vo.InspectionDetailVO;
import com.zhimu.slmp.inspect.model.vo.InspectionFormVO;
import com.zhimu.slmp.inspect.model.vo.InspectionListItemVO;
import com.zhimu.slmp.inspect.model.vo.InspectionSettingVO;
import com.zhimu.slmp.inspect.model.vo.InspectionSubmitResultVO;
import com.zhimu.slmp.inspect.model.vo.InspectionSummaryVO;
import com.zhimu.slmp.inspect.model.vo.PenInspectionOverviewVO;
import com.zhimu.slmp.inspect.model.vo.PenInspectionRowVO;
import com.zhimu.slmp.inspect.model.vo.RecentIssueVO;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * 巡检数据（模块07 文档 2.3.2）：今日清单与统计、截止时间、表单上下文、登记 / 修改 / 补录、历史与修改记录。
 */
@FeatureScope
public class InspectionRepository {

    public static final String BIZ_INSPECTION = "INSPECTION";

    private final InspectApi api;

    @Inject
    public InspectionRepository(@NonNull InspectApi api) {
        this.api = api;
    }

    /** API-I01 */
    @NonNull
    public Single<InspectionSummaryVO> getSummary(@NonNull String date) {
        return api.getSummary(date).compose(RxResult.unwrap());
    }

    /** API-I02 分页（第 1 页回调 tab_counts，io 线程） */
    @NonNull
    public PagedSource<InspectionListItemVO> todaySource(@NonNull String date, @NonNull String status,
                                                         @NonNull Consumer<Map<String, Integer>> onTabCounts) {
        return new PagedSource<>((page, size) -> api.getDayList(date, status, page, size).compose(RxResult.unwrap()),
                vo -> onTabCounts.accept(vo.getTabCounts()));
    }

    /** API-I02 某日全部（员工版“昨天”分组，一页 50 条足够） */
    @NonNull
    public Single<PageVO<InspectionListItemVO>> getDayList(@NonNull String date) {
        return api.getDayList(date, "ALL", 1, 50).compose(RxResult.unwrap());
    }

    /** API-I03 */
    @NonNull
    public Single<List<RecentIssueVO>> getRecentIssues() {
        return api.getRecentIssues().compose(RxResult.unwrap());
    }

    /** API-I04 */
    @NonNull
    public Single<InspectionSettingVO> getSetting() {
        return api.getSetting().compose(RxResult.unwrap());
    }

    /** API-I05 */
    @NonNull
    public Single<InspectionSettingVO> updateSetting(@NonNull InspectionSettingRequest request) {
        return api.updateSetting(request).compose(RxResult.unwrap());
    }

    /** API-I06 */
    @NonNull
    public Single<InspectionFormVO> getFormContext(long penId, @NonNull String mode, @Nullable String inspectDate,
                                                   @Nullable Long inspectionId) {
        return api.getFormContext(penId, mode, inspectDate, inspectionId).compose(RxResult.unwrap());
    }

    /** API-I07（幂等键为 client_request_id） */
    @NonNull
    public Single<InspectionSubmitResultVO> register(@NonNull InspectionSubmitRequest request) {
        return api.register(request, request.clientRequestId).compose(RxResult.unwrap());
    }

    /** API-I08 */
    @NonNull
    public Single<InspectionSubmitResultVO> modify(long inspectionId, @NonNull InspectionSubmitRequest request) {
        return api.modify(inspectionId, request).compose(RxResult.unwrap());
    }

    /** API-I09 */
    @NonNull
    public Single<InspectionSubmitResultVO> backfill(@NonNull InspectionBackfillRequest request) {
        return api.backfill(request, request.clientRequestId).compose(RxResult.unwrap());
    }

    /** API-I10 */
    @NonNull
    public Single<InspectionDetailVO> getDetail(long inspectionId) {
        return api.getDetail(inspectionId).compose(RxResult.unwrap());
    }

    /** API-I11 分页 */
    @NonNull
    public PagedSource<PenInspectionRowVO> historySource(long penId, @NonNull HistoryQuery q) {
        return new PagedSource<>((page, size) -> api.getPenHistory(penId, q.filter, q.startDate, q.endDate,
                q.locateDate, page, size).compose(RxResult.unwrap()));
    }

    /** API-I12 */
    @NonNull
    public Single<PenInspectionOverviewVO> getPenOverview(long penId, @Nullable String to) {
        return api.getPenOverview(penId, to).compose(RxResult.unwrap());
    }

    /** API-I25 */
    @NonNull
    public Single<InspectionSubmitResultVO> findByRequest(@NonNull String clientRequestId) {
        return api.findByRequest(clientRequestId).compose(RxResult.unwrap());
    }
}
