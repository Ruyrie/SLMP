package com.zhimu.slmp.feed.ui.exec;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.feed.data.FeedRepository;
import com.zhimu.slmp.feed.model.request.FeedExecQuery;
import com.zhimu.slmp.feed.model.vo.FeedAttentionItemVO;
import com.zhimu.slmp.feed.model.vo.FeedExecDayVO;
import com.zhimu.slmp.feed.model.vo.FeedExecSummaryVO;
import com.zhimu.slmp.feed.model.vo.FeedPlanBatchOptionVO;
import com.zhimu.slmp.feed.ui.common.FeedNav;
import com.zhimu.slmp.feed.util.FeedFormat;
import com.zhimu.slmp.feed.widget.BarChartModel;
import com.zhimu.slmp.feed.widget.FeedStatCard;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * G04 饲喂执行情况（所有者、场长；员工 40303 无权查看）：批次 Chip（末尾“全部批次”）、时间范围（近 7 天 / 近 30 天 / 本批次 /
 * 自定义），四张指标卡、每日投喂量柱状图（computation 线程组装 BarChartModel）、“需要关注”分页列表；点柱显示当天各餐明细。
 * 统计截至昨天（服务端计算）。
 */
public class FeedExecViewModel extends BaseListViewModel<FeedExecQuery, FeedAttentionItemVO> {

    static final int MAX_SPAN_DAYS = 366;

    private final FeedRepository repository;

    private final MutableLiveData<List<FeedPlanBatchOptionVO>> batchChips = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> selectedBatchId = new MutableLiveData<>();
    private final MutableLiveData<String> range = new MutableLiveData<>(FeedExecQuery.RANGE_LAST_7);
    private final MutableLiveData<String> customStart = new MutableLiveData<>();
    private final MutableLiveData<String> customEnd = new MutableLiveData<>();
    private final MutableLiveData<FeedExecSummaryVO> summary = new MutableLiveData<>();
    private final MutableLiveData<String> rangeText = new MutableLiveData<>("");
    private final MutableLiveData<List<FeedStatCard>> statCards = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<BarChartModel> chartModel = new MutableLiveData<>();
    private final MutableLiveData<FeedExecDayVO> dayDetail = new MutableLiveData<>();
    private final MutableLiveData<Integer> selectedBar = new MutableLiveData<>(-1);
    private final MutableLiveData<String> emptyKind = new MutableLiveData<>();
    private final MutableLiveData<Boolean> summaryLoading = new MutableLiveData<>(false);

    private final SingleLiveEvent<String> showBarTip = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showRangePicker = new SingleLiveEvent<>();
    private final SingleLiveEvent<Bundle> openRegister = new SingleLiveEvent<>();

    private boolean started;

    @Inject
    public FeedExecViewModel(@NonNull FeedRepository repository) {
        super(null);
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        load();
    }

    @NonNull
    @Override
    protected PagingSource<Integer, FeedAttentionItemVO> createPagingSource(@Nullable FeedExecQuery filter) {
        FeedExecQuery q = filter == null ? new FeedExecQuery(null, FeedExecQuery.RANGE_LAST_7, null, null) : filter;
        return repository.attentionSource(q);
    }

    // ===================== 对外 =====================

    public LiveData<List<FeedPlanBatchOptionVO>> getBatchChips() {
        return batchChips;
    }

    public LiveData<Long> getSelectedBatchId() {
        return selectedBatchId;
    }

    public LiveData<String> getRange() {
        return range;
    }

    public LiveData<String> getCustomStart() {
        return customStart;
    }

    public LiveData<String> getCustomEnd() {
        return customEnd;
    }

    public LiveData<FeedExecSummaryVO> getSummary() {
        return summary;
    }

    public LiveData<String> getRangeText() {
        return rangeText;
    }

    public LiveData<List<FeedStatCard>> getStatCards() {
        return statCards;
    }

    public LiveData<BarChartModel> getChartModel() {
        return chartModel;
    }

    public LiveData<FeedExecDayVO> getDayDetail() {
        return dayDetail;
    }

    public LiveData<Integer> getSelectedBar() {
        return selectedBar;
    }

    /** NO_ACTIVE_BATCH 整页空；NO_TASK 图表区“这段时间没有饲喂任务” */
    public LiveData<String> getEmptyKind() {
        return emptyKind;
    }

    public LiveData<Boolean> getSummaryLoading() {
        return summaryLoading;
    }

    public SingleLiveEvent<String> getShowBarTip() {
        return showBarTip;
    }

    public SingleLiveEvent<Void> getShowRangePicker() {
        return showRangePicker;
    }

    public SingleLiveEvent<Bundle> getOpenRegister() {
        return openRegister;
    }

    /** 是否为全部批次视图（列表主文字前加批次号） */
    public boolean isAllBatches() {
        return selectedBatchId.getValue() == null;
    }

    // ===================== 加载 =====================

    /** API-G09 → API-G14、G15：默认第一个在养批次和“近 7 天” */
    public void load() {
        Single<Object[]> source = repository.getPlanBatchOptions().flatMap(list -> {
            Long first = list.isEmpty() ? null : list.get(0).getBatchId();
            Long batch = started ? selectedBatchId.getValue() : first;
            FeedExecQuery q = new FeedExecQuery(batch, rangeOrDefault(), customStart.getValue(), customEnd.getValue());
            return repository.getExecSummary(q).observeOn(Schedulers.computation())
                    .map(s -> new Object[]{list, batch, q, s, chartOf(s)});
        });
        load(source, r -> {
            @SuppressWarnings("unchecked")
            List<FeedPlanBatchOptionVO> list = (List<FeedPlanBatchOptionVO>) r[0];
            batchChips.setValue(list);
            selectedBatchId.setValue((Long) r[1]);
            started = true;
            setFilter((FeedExecQuery) r[2]);
            applySummary((FeedExecSummaryVO) r[3], (BarChartModel) r[4]);
        });
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        if (data instanceof Object[]) {
            Object s = ((Object[]) data)[3];
            return s instanceof FeedExecSummaryVO
                    && FeedExecSummaryVO.EMPTY_NO_ACTIVE_BATCH.equals(((FeedExecSummaryVO) s).getEmptyReason());
        }
        return super.isEmptyData(data);
    }

    @Override
    public void retry() {
        load();
    }

    @Override
    public void refresh() {
        loadSummary();
        super.refresh();
    }

    @NonNull
    private String rangeOrDefault() {
        String r = range.getValue();
        return r == null ? FeedExecQuery.RANGE_LAST_7 : r;
    }

    @NonNull
    FeedExecQuery currentQuery() {
        return new FeedExecQuery(selectedBatchId.getValue(), rangeOrDefault(), customStart.getValue(),
                customEnd.getValue());
    }

    /** 条件变化：重新取汇总与图表（API-G14），“需要关注”重建分页（API-G15） */
    private void loadSummary() {
        FeedExecQuery q = currentQuery();
        setFilter(q);
        clearBar();
        summaryLoading.setValue(true);
        launch(repository.getExecSummary(q).observeOn(Schedulers.computation())
                .map(s -> new Object[]{s, chartOf(s)}), r -> {
            summaryLoading.setValue(false);
            applySummary((FeedExecSummaryVO) r[0], (BarChartModel) r[1]);
        }, e -> {
            summaryLoading.setValue(false);
            handleError(e);
        });
    }

    /** 图表模型（在 computation 线程组装）；没有每日数据时为 null */
    @Nullable
    static BarChartModel chartOf(@NonNull FeedExecSummaryVO s) {
        return s.getDaily().isEmpty() ? null : BarChartModel.from(s);
    }

    private void applySummary(@NonNull FeedExecSummaryVO s, @Nullable BarChartModel chart) {
        summary.setValue(s);
        emptyKind.setValue(s.isHasData() ? null : s.getEmptyReason() == null ? FeedExecSummaryVO.EMPTY_NO_TASK
                : s.getEmptyReason());
        rangeText.setValue(rangeTextOf(s, isAllBatches()));
        statCards.setValue(statCardsOf(s));
        chartModel.setValue(chart);
    }

    // ===================== 条件 =====================

    /** 批次 Chip：null 为全部批次 */
    public void selectBatch(@Nullable Long batchId) {
        if (java.util.Objects.equals(batchId, selectedBatchId.getValue())) {
            return;
        }
        selectedBatchId.setValue(batchId);
        loadSummary();
    }

    /** 时间范围按钮组 */
    public void selectRange(@NonNull String r) {
        if (r.equals(range.getValue())) {
            return;
        }
        range.setValue(r);
        customStart.setValue(null);
        customEnd.setValue(null);
        loadSummary();
    }

    public void onRangeMenuClick() {
        showRangePicker.call();
    }

    /** 自定义日期范围：结束不晚于今天、跨度不超过 366 天（早于入栏由服务端 40001 返回） */
    public void onRangePicked(@NonNull String start, @NonNull String end) {
        LocalDate s = DateFormatter.parseDate(start);
        LocalDate e = DateFormatter.parseDate(end);
        if (s == null || e == null) {
            return;
        }
        if (e.isAfter(DateFormatter.today())) {
            showMessage("结束日期不能晚于今天");
            return;
        }
        if (ChronoUnit.DAYS.between(s, e) + 1 > MAX_SPAN_DAYS) {
            showMessage("日期范围不能超过 366 天");
            return;
        }
        range.setValue(FeedExecQuery.RANGE_CUSTOM);
        customStart.setValue(start);
        customEnd.setValue(end);
        loadSummary();
    }

    // ===================== 点柱与列表 =====================

    /** 点柱：提示气泡，当天明细显示在“需要关注”位置（API-G16）；再点同一根柱或空白处取消 */
    public void onBarClick(int index) {
        BarChartModel chart = chartModel.getValue();
        if (chart == null || index < 0 || index >= chart.bars.size()) {
            return;
        }
        Integer cur = selectedBar.getValue();
        if (cur != null && cur == index) {
            clearBar();
            return;
        }
        selectedBar.setValue(index);
        BarChartModel.Bar bar = chart.bars.get(index);
        showBarTip.setValue(bar.tip);
        dayDetail.setValue(null);
        launch(repository.getExecDay(currentQuery(), bar.date), d -> {
            Integer now = selectedBar.getValue();
            if (now != null && now == index) {
                dayDetail.setValue(d);
            }
        });
    }

    public void clearBar() {
        selectedBar.setValue(-1);
        dayDetail.setValue(null);
    }

    /** 需要关注：有记录带 record_id；没有记录且可补登带 task_id；否则不跳转 */
    public void onAttentionClick(@NonNull FeedAttentionItemVO item) {
        Long record = item.getRecordId();
        if (record != null) {
            openRegister.setValue(FeedNav.recordArgs(record));
        } else if (item.isCanBackfill() && item.getTaskId() > 0) {
            openRegister.setValue(FeedNav.taskArgs(item.getTaskId()));
        }
    }

    /** G02 返回后刷新 */
    public void onChildResult(boolean changed) {
        if (changed) {
            refresh();
        }
    }

    // ===================== 文案 =====================

    /** “10月12日 – 10月18日 · 计划 21 餐”；全部批次“起止日期 · 批次数 · 计划餐数” */
    @NonNull
    static String rangeTextOf(@NonNull FeedExecSummaryVO s, boolean allBatches) {
        String dates = FeedFormat.date(s.getStartDate()) + " – " + FeedFormat.date(s.getEndDate());
        String meals = "计划 " + s.getPlannedMeals() + " 餐";
        if (allBatches) {
            return FeedFormat.join(dates, s.getBatchCount() + " 个批次", meals);
        }
        return FeedFormat.join(dates, meals);
    }

    /** 四张指标卡：执行率、投喂偏差、累计用量、投喂异常；没有计划餐次时数字为“无数据” */
    @NonNull
    static List<FeedStatCard> statCardsOf(@NonNull FeedExecSummaryVO s) {
        List<FeedStatCard> cards = new ArrayList<>();
        boolean noData = !s.isHasData() || s.getPlannedMeals() == 0;
        int base = com.zhimu.slmp.base.R.drawable.ic_task_alt;
        if (noData) {
            cards.add(new FeedStatCard("执行率", FeedFormat.NO_DATA, null, null, base, false, false));
            cards.add(new FeedStatCard("投喂偏差", FeedFormat.NO_DATA, null, null,
                    com.zhimu.slmp.feed.R.drawable.feed_ic_swap_vert, false, false));
            cards.add(new FeedStatCard("累计用量", FeedFormat.NO_DATA, null, null,
                    com.zhimu.slmp.base.R.drawable.ic_rice_bowl, false, false));
            cards.add(new FeedStatCard("投喂异常", FeedFormat.NO_DATA, null, null,
                    com.zhimu.slmp.base.R.drawable.ic_warning, false, false));
            return cards;
        }
        cards.add(new FeedStatCard("执行率", s.getExecRate() == null ? FeedFormat.NO_DATA
                : FeedFormat.percentNumber(s.getExecRate(), false), s.getExecRate() == null ? null : "%",
                "已完成 " + s.getDoneMeals() + " / " + s.getPlannedMeals() + " 餐", base, false, false));
        String dirText = FeedExecSummaryVO.DIR_MORE.equals(s.getDeviationDirection()) ? "实际比计划多"
                : FeedExecSummaryVO.DIR_EQUAL.equals(s.getDeviationDirection()) ? "与计划一致" : "实际比计划少";
        cards.add(new FeedStatCard("投喂偏差", s.getDeviationRatio() == null ? FeedFormat.NO_DATA
                : FeedFormat.percentNumber(s.getDeviationRatio(), true), s.getDeviationRatio() == null ? null : "%",
                s.getDeviationRatio() == null ? null : dirText, com.zhimu.slmp.feed.R.drawable.feed_ic_swap_vert,
                false, false));
        cards.add(new FeedStatCard("累计用量", FeedFormat.num(s.getActualQty()), FeedFormat.KG,
                "计划 " + FeedFormat.kg(s.getPlanQty()), com.zhimu.slmp.base.R.drawable.ic_rice_bowl, false, false));
        cards.add(new FeedStatCard("投喂异常", String.valueOf(s.getAbnormalCount()), "次", abnormalHelper(s),
                com.zhimu.slmp.base.R.drawable.ic_warning, false, s.getAbnormalCount() > 0));
        return cards;
    }

    /** “均为少投 12.5%”；多投、少投都有时“少投 2 次 · 多投 1 次” */
    @Nullable
    static String abnormalHelper(@NonNull FeedExecSummaryVO s) {
        if (s.getAbnormalCount() == 0) {
            return null;
        }
        if (s.getUnderCount() > 0 && s.getOverCount() > 0) {
            return "少投 " + s.getUnderCount() + " 次" + FeedFormat.DOT + "多投 " + s.getOverCount() + " 次";
        }
        String dir = s.getOverCount() > 0 ? "多投" : "少投";
        if (s.getAbnormalSameRatio() != null) {
            return "均为" + dir + " " + FeedFormat.absPercent(s.getAbnormalSameRatio());
        }
        return dir + " " + s.getAbnormalCount() + " 次";
    }
}
