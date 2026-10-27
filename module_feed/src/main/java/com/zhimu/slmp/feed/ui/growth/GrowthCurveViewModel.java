package com.zhimu.slmp.feed.ui.growth;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.feed.FeedExtras;
import com.zhimu.slmp.feed.data.WeighRepository;
import com.zhimu.slmp.feed.model.request.GrowthCurveQuery;
import com.zhimu.slmp.feed.model.vo.GrowthCompareOptionVO;
import com.zhimu.slmp.feed.model.vo.GrowthCurveVO;
import com.zhimu.slmp.feed.model.vo.WeighBatchOptionVO;
import com.zhimu.slmp.feed.model.vo.WeighFormBatchVO;
import com.zhimu.slmp.feed.model.vo.WeighRecordItemVO;
import com.zhimu.slmp.feed.ui.common.FeedNav;
import com.zhimu.slmp.feed.util.FeedFormat;
import com.zhimu.slmp.feed.util.GrowthCalc;
import com.zhimu.slmp.feed.widget.FeedStatCard;
import com.zhimu.slmp.feed.widget.GrowthChartModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * G06 生长曲线：批次标题与生长标记（已结束批次为“已结束”）、三张指标卡、实际 / 标准体重曲线与偏差带（GrowthCurveView，
 * 模型在 computation 线程组装）、同品种对比（最多 3 个，添加时带 compare_batch_ids 重取 API-G24，移除时本地去掉系列）、
 * 称重记录分页（API-G25）。入参 EXTRA_BATCH_ID，没有时先取 API-G18（include_ended 为 true）选批次。
 */
public class GrowthCurveViewModel extends BaseListViewModel<Long, WeighRecordItemVO> {

    private final WeighRepository repository;

    private final MutableLiveData<Long> batchId = new MutableLiveData<>();
    private final MutableLiveData<GrowthCurveVO> curve = new MutableLiveData<>();
    private final MutableLiveData<String> headerText = new MutableLiveData<>("");
    private final MutableLiveData<String> headerSummary = new MutableLiveData<>("");
    private final MutableLiveData<String> statusTag = new MutableLiveData<>();
    private final MutableLiveData<List<FeedStatCard>> metricCards = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<GrowthChartModel> chartModel = new MutableLiveData<>();
    private final MutableLiveData<List<GrowthLegendItem>> legendItems = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> stdNotice = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canRegister = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> showEmpty = new MutableLiveData<>(false);
    private final MutableLiveData<List<Long>> compareIds = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<GrowthCompareOptionVO>> compareOptions = new MutableLiveData<>();
    private final MutableLiveData<List<WeighBatchOptionVO>> batchOptions = new MutableLiveData<>(Collections.emptyList());

    private final SingleLiveEvent<Void> showCompareSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> showPointTip = new SingleLiveEvent<>();
    private final SingleLiveEvent<WeighRecordItemVO> showRecordMenu = new SingleLiveEvent<>();
    private final SingleLiveEvent<Bundle> openWeigh = new SingleLiveEvent<>();
    private final SingleLiveEvent<Bundle> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showBatchSheet = new SingleLiveEvent<>();

    @Inject
    public GrowthCurveViewModel(@NonNull WeighRepository repository) {
        super(null);
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        init(FeedExtras.readId(handle.get(RouterPath.EXTRA_BATCH_ID)));
    }

    @NonNull
    @Override
    protected PagingSource<Integer, WeighRecordItemVO> createPagingSource(@Nullable Long filter) {
        return repository.weighRecordSource(filter == null ? 0L : filter);
    }

    // ===================== 对外 =====================

    public LiveData<Long> getBatchId() {
        return batchId;
    }

    public LiveData<GrowthCurveVO> getCurve() {
        return curve;
    }

    public LiveData<String> getHeaderText() {
        return headerText;
    }

    public LiveData<String> getHeaderSummary() {
        return headerSummary;
    }

    /** 生长偏慢 / 生长偏快 / 已结束；正常时为 null */
    public LiveData<String> getStatusTag() {
        return statusTag;
    }

    public LiveData<List<FeedStatCard>> getMetricCards() {
        return metricCards;
    }

    public LiveData<GrowthChartModel> getChartModel() {
        return chartModel;
    }

    public LiveData<List<GrowthLegendItem>> getLegendItems() {
        return legendItems;
    }

    public LiveData<Boolean> getStdNotice() {
        return stdNotice;
    }

    /** 在养批次可登记称重（顶部栏 scale、空状态按钮） */
    public LiveData<Boolean> getCanRegister() {
        return canRegister;
    }

    public LiveData<Boolean> getShowEmpty() {
        return showEmpty;
    }

    public LiveData<List<Long>> getCompareIds() {
        return compareIds;
    }

    public LiveData<List<GrowthCompareOptionVO>> getCompareOptions() {
        return compareOptions;
    }

    public LiveData<List<WeighBatchOptionVO>> getBatchOptions() {
        return batchOptions;
    }

    public SingleLiveEvent<Void> getShowCompareSheet() {
        return showCompareSheet;
    }

    public SingleLiveEvent<String> getShowPointTip() {
        return showPointTip;
    }

    public SingleLiveEvent<WeighRecordItemVO> getShowRecordMenu() {
        return showRecordMenu;
    }

    public SingleLiveEvent<Bundle> getOpenWeigh() {
        return openWeigh;
    }

    public SingleLiveEvent<Bundle> getOpenChangeRequest() {
        return openChangeRequest;
    }

    public SingleLiveEvent<Void> getShowBatchSheet() {
        return showBatchSheet;
    }

    /** 选择对比批次面板说明中的品种名 */
    @NonNull
    public String breedName() {
        GrowthCurveVO c = curve.getValue();
        return c == null || c.getBatch() == null ? "" : FeedFormat.orEmpty(c.getBatch().getBreedName());
    }

    // ===================== 加载 =====================

    /** API-G24；记录列表按批次重建分页（API-G25） */
    public void init(@Nullable Long id) {
        if (id == null) {
            load(repository.getBatchOptions(true), list -> {
                batchOptions.setValue(list);
                if (list.size() == 1) {
                    init(list.get(0).getBatchId());
                } else if (!list.isEmpty()) {
                    showBatchSheet.call();
                }
            });
            return;
        }
        batchId.setValue(id);
        setFilter(id);
        load(curveSource(id, compareIds.getValue()), r -> apply((GrowthCurveVO) r[0], (GrowthChartModel) r[1]));
    }

    @NonNull
    private io.reactivex.rxjava3.core.Single<Object[]> curveSource(long id, @Nullable List<Long> compare) {
        return repository.getGrowthCurve(id, compare).observeOn(Schedulers.computation())
                .map(c -> new Object[]{c, GrowthChartModel.from(c)});
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        if (data instanceof Object[]) {
            Object c = ((Object[]) data)[0];
            return c instanceof GrowthCurveVO && ((GrowthCurveVO) c).getMetrics() == null;
        }
        return super.isEmptyData(data);
    }

    @Override
    public void retry() {
        init(batchId.getValue());
    }

    @Override
    public void refresh() {
        Long id = batchId.getValue();
        if (id != null) {
            load(curveSource(id, compareIds.getValue()), r -> apply((GrowthCurveVO) r[0], (GrowthChartModel) r[1]));
        }
        super.refresh();
    }

    /** G05 返回后重新加载 */
    public void onChildResult(boolean changed) {
        if (changed) {
            refresh();
        }
    }

    private void apply(@NonNull GrowthCurveVO c, @Nullable GrowthChartModel chart) {
        curve.setValue(c);
        WeighFormBatchVO b = c.getBatch();
        boolean active = b == null || b.isActive();
        headerText.setValue(b == null ? "" : FeedFormat.orEmpty(b.getBatchNo()));
        headerSummary.setValue(b == null ? "" : FeedFormat.join(b.getPenNames(), b.getBreedName(),
                b.getDayAge() == null ? null : b.getDayAge() + " 日龄",
                b.getCurrentCount() == null ? null : "存栏 " + FeedFormat.head(b.getCurrentCount())));
        statusTag.setValue(!active ? "已结束" : GrowthCalc.statusTag(c.getGrowthStatus()));
        canRegister.setValue(active);
        stdNotice.setValue(c.isStdUpdatedNotice());
        showEmpty.setValue(c.getMetrics() == null);
        metricCards.setValue(metricCardsOf(c));
        List<Long> ids = new ArrayList<>();
        for (GrowthCurveVO.GrowthCompareSeriesVO s : c.getCompareSeries()) {
            ids.add(s.getBatchId());
        }
        compareIds.setValue(ids);
        chartModel.setValue(chart);
        legendItems.setValue(legendOf(c, chart));
    }

    /** 三张指标卡：平均体重、偏离标准、日增重；只有一次称重时日增重辅助文字为“首次称重” */
    @NonNull
    static List<FeedStatCard> metricCardsOf(@NonNull GrowthCurveVO c) {
        List<FeedStatCard> cards = new ArrayList<>();
        GrowthCurveVO.GrowthMetricsVO m = c.getMetrics();
        if (m == null) {
            return cards;
        }
        cards.add(FeedStatCard.of("平均体重", FeedFormat.weightNumber(m.getAvgWeight()), FeedFormat.KG,
                m.getDayAge() == null ? null : m.getDayAge() + " 日龄"));
        if (m.getStdWeight() == null) {
            cards.add(FeedStatCard.of("偏离标准", FeedFormat.NO_DATA, null, null));
        } else {
            boolean out = GrowthCalc.outOfRange(m.getDeviationRatio(), m.getAllowedDeviation());
            cards.add(new FeedStatCard("偏离标准", m.getDeviationRatio() == null ? FeedFormat.DASH
                    : FeedFormat.percentNumber(m.getDeviationRatio(), true), m.getDeviationRatio() == null ? null : "%",
                    "标准 " + FeedFormat.weightKg(m.getStdWeight())
                            + (m.isCurrentStandardComparison() ? FeedFormat.DOT + "按当前标准对比" : ""), 0, false, out));
        }
        String helper;
        if (m.getPrevDailyGain() != null) {
            helper = "上次 " + FeedFormat.kg2(m.getPrevDailyGain());
        } else {
            int weighs = 0;
            for (GrowthCurveVO.GrowthActualPointVO p : c.getActualPoints()) {
                if (!p.isEntry()) {
                    weighs++;
                }
            }
            helper = weighs <= 1 ? "首次称重" : null;
        }
        cards.add(FeedStatCard.of("日增重", m.getDailyGain() == null ? FeedFormat.DASH : FeedFormat.num2(m.getDailyGain()),
                m.getDailyGain() == null ? null : FeedFormat.KG, helper));
        return cards;
    }

    /** 图例：实际、标准、对比批次号、允许偏差 ±10%；无标准时去掉“标准”“允许偏差” */
    @NonNull
    static List<GrowthLegendItem> legendOf(@NonNull GrowthCurveVO c, @Nullable GrowthChartModel chart) {
        List<GrowthLegendItem> items = new ArrayList<>();
        items.add(new GrowthLegendItem(GrowthLegendItem.ACTUAL, "实际"));
        boolean std = chart != null ? chart.stdAvailable : c.isStdAvailable();
        if (std) {
            items.add(new GrowthLegendItem(GrowthLegendItem.STD, "标准"));
        }
        if (chart != null) {
            for (GrowthChartModel.Series s : chart.compare) {
                if (!s.points.isEmpty()) {
                    items.add(new GrowthLegendItem(GrowthLegendItem.COMPARE, s.batchNo));
                }
            }
        }
        if (std) {
            GrowthCurveVO.GrowthMetricsVO m = c.getMetrics();
            String allowed = m == null || m.getAllowedDeviation() == null ? ""
                    : " ±" + FeedFormat.thresholdPercent(m.getAllowedDeviation()) + "%";
            items.add(new GrowthLegendItem(GrowthLegendItem.BAND, "允许偏差" + allowed));
        }
        return items;
    }

    // ===================== 对比 =====================

    /** “添加批次”：API-G26 */
    public void openCompareSheet() {
        Long id = batchId.getValue();
        if (id == null) {
            return;
        }
        compareOptions.setValue(null);
        showCompareSheet.call();
        launch(repository.getCompareOptions(id), compareOptions::setValue, e -> {
            compareOptions.setValue(Collections.emptyList());
            handleError(e);
        });
    }

    /** 面板“确定”：带 compare_batch_ids 重取 API-G24，新增的对比线从左到右描出 */
    public void confirmCompare(@NonNull List<Long> ids) {
        Long id = batchId.getValue();
        if (id == null) {
            return;
        }
        List<Long> limited = new GrowthCurveQuery(ids).compareBatchIds;
        launch(curveSource(id, limited), r -> apply((GrowthCurveVO) r[0], (GrowthChartModel) r[1]));
    }

    /** Chip 的 close：本地移除系列，曲线淡出 */
    public void removeCompare(long compareId) {
        List<Long> ids = new ArrayList<>(compareIds.getValue() == null ? Collections.emptyList()
                : compareIds.getValue());
        ids.remove(compareId);
        compareIds.setValue(ids);
        GrowthCurveVO c = curve.getValue();
        if (c != null) {
            List<GrowthCurveVO.GrowthCompareSeriesVO> left = new ArrayList<>();
            for (GrowthCurveVO.GrowthCompareSeriesVO s : c.getCompareSeries()) {
                if (s.getBatchId() != compareId) {
                    left.add(s);
                }
            }
            c.setCompareSeries(left);
        }
        GrowthChartModel chart = chartModel.getValue();
        GrowthChartModel next = c != null ? GrowthChartModel.from(c)
                : chart == null ? null : chart.withoutSeries(compareId);
        chartModel.setValue(next);
        if (c != null) {
            legendItems.setValue(legendOf(c, next));
        }
    }

    // ===================== 点按 =====================

    public void onPointClick(@Nullable String tip) {
        if (tip != null) {
            showPointTip.setValue(tip);
        }
    }

    /** 称重记录：EDIT 进入 G05 修改模式；REQUEST_CHANGE 弹出“提交修改申请”菜单；NONE 无响应 */
    public void onRecordClick(@NonNull WeighRecordItemVO item) {
        if (WeighRecordItemVO.ACTION_EDIT.equals(item.getAction())) {
            Bundle b = new Bundle();
            b.putLong(RouterPath.EXTRA_WEIGH_RECORD_ID, item.getId());
            Long id = batchId.getValue();
            if (id != null) {
                b.putLong(RouterPath.EXTRA_BATCH_ID, id);
            }
            b.putBoolean(RouterPath.EXTRA_FROM_GROWTH, true);
            openWeigh.setValue(b);
        } else if (WeighRecordItemVO.ACTION_REQUEST_CHANGE.equals(item.getAction())) {
            showRecordMenu.setValue(item);
        }
    }

    public void requestChange(@NonNull WeighRecordItemVO item) {
        openChangeRequest.setValue(FeedNav.changeArgs(FeedExtras.BIZ_WEIGH_RECORD, item.getId()));
    }

    /** 顶部栏 scale、空状态“登记称重”：进入本批次 G05 */
    public void onWeighClick() {
        Long id = batchId.getValue();
        if (id == null || !Boolean.TRUE.equals(canRegister.getValue())) {
            return;
        }
        Bundle b = FeedNav.batchArgs(id);
        b.putBoolean(RouterPath.EXTRA_FROM_GROWTH, true);
        openWeigh.setValue(b);
    }
}
