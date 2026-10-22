package com.zhimu.slmp.feed.ui.today;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.paging.PagingData;
import androidx.paging.PagingDataTransforms;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.feed.FeedExtras;
import com.zhimu.slmp.feed.data.FeedRepository;
import com.zhimu.slmp.feed.model.request.FeedTaskQuery;
import com.zhimu.slmp.feed.model.vo.FeedPlanBatchOptionVO;
import com.zhimu.slmp.feed.model.vo.FeedTaskItemVO;
import com.zhimu.slmp.feed.model.vo.FeedTodaySummaryVO;
import com.zhimu.slmp.feed.model.vo.PlanShortageVO;
import com.zhimu.slmp.feed.ui.common.FeedNav;
import com.zhimu.slmp.feed.util.FeedFormat;
import com.zhimu.slmp.feed.widget.FeedStatCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * G01 今日饲喂任务：指标卡、库存不足横幅（最多 2 条，其余合并）、状态与栏舍筛选、按餐次分组的任务列表（Paging 3，
 * insertSeparators 插入“早餐 · 07:00”）。员工只看负责栏舍，没有方案、统计入口与“去入库”（can_manage、can_stock_in 为 false）。
 * 页面参数 EXTRA_PEN_ID（可选）预选栏舍筛选。
 */
public class FeedTodayViewModel extends BaseListViewModel<FeedTaskQuery, FeedTaskItemVO> {

    /** 空状态种类 */
    public enum FeedEmptyKind {
        /** 员工没有负责栏舍 */
        NO_PEN,
        /** 在养批次都没有饲喂方案（养殖户引导设置方案） */
        NO_PLAN,
        /** 今天没有任务 */
        NO_TASK
    }

    /** 最多显示 2 条库存横幅 */
    static final int MAX_BANNERS = 2;

    private final FeedRepository repository;
    private final Executor computation = command -> Schedulers.computation().scheduleDirect(command);

    private final MutableLiveData<FeedTodaySummaryVO> summary = new MutableLiveData<>();
    private final LiveData<FeedStatCard> planCard;
    private final LiveData<FeedStatCard> doneCard;
    private final LiveData<FeedStatCard> overdueCard;
    private final LiveData<List<PlanShortageVO>> shortageBanners;
    private final LiveData<String> shortageMoreText;
    private final LiveData<Boolean> showManageEntry;
    private final LiveData<Boolean> showStockIn;
    private final MutableLiveData<String> statusFilter = new MutableLiveData<>();
    private final MutableLiveData<FeedTodaySummaryVO.FeedPenOptionVO> penFilter = new MutableLiveData<>();
    private final LiveData<PagingData<FeedTaskRow>> feedTasks;
    private final MutableLiveData<FeedEmptyKind> emptyKind = new MutableLiveData<>();
    private final MutableLiveData<List<FeedPlanBatchOptionVO>> planBatches =
            new MutableLiveData<>(Collections.emptyList());

    private final SingleLiveEvent<Bundle> openRegister = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showBatchSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openPlan = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openExec = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showPenSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openStockIn = new SingleLiveEvent<>();

    @Nullable
    private Long presetPenId;

    @Inject
    public FeedTodayViewModel(@NonNull FeedRepository repository) {
        super(new FeedTaskQuery(null, null));
        this.repository = repository;
        planCard = Transformations.map(summary, FeedTodayViewModel::planCardOf);
        doneCard = Transformations.map(summary, FeedTodayViewModel::doneCardOf);
        overdueCard = Transformations.map(summary, FeedTodayViewModel::overdueCardOf);
        shortageBanners = Transformations.map(summary, s -> {
            List<PlanShortageVO> list = s == null ? Collections.emptyList() : s.getPlanShortage();
            return list.size() > MAX_BANNERS ? new ArrayList<>(list.subList(0, MAX_BANNERS)) : list;
        });
        shortageMoreText = Transformations.map(summary, FeedTodayViewModel::moreTextOf);
        showManageEntry = Transformations.map(summary, s -> s != null && s.isCanManage());
        showStockIn = Transformations.map(summary, s -> s != null && s.isCanStockIn());
        feedTasks = Transformations.map(getPagingData(), this::decorate);
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        presetPenId = FeedExtras.readId(handle.get(RouterPath.EXTRA_PEN_ID));
        if (presetPenId != null) {
            setFilter(new FeedTaskQuery(null, presetPenId));
        }
        load();
    }

    @NonNull
    @Override
    protected PagingSource<Integer, FeedTaskItemVO> createPagingSource(@Nullable FeedTaskQuery filter) {
        return repository.todayTaskSource(filter);
    }

    /** PagingData.map 生成 FeedTaskRow，insertSeparators 插入餐次标题 */
    @NonNull
    private PagingData<FeedTaskRow> decorate(@NonNull PagingData<FeedTaskItemVO> data) {
        PagingData<FeedTaskRow> rows = PagingDataTransforms.map(data, computation, FeedTaskRow::of);
        return PagingDataTransforms.insertSeparators(rows, computation, (before, after) -> {
            if (after == null || after.item == null) {
                return null;
            }
            FeedTaskItemVO prev = before == null ? null : before.item;
            return FeedTaskRow.newMeal(prev, after.item) ? FeedTaskRow.header(after.item) : null;
        });
    }

    // ===== 对外 =====

    public LiveData<FeedTodaySummaryVO> getSummary() {
        return summary;
    }

    public LiveData<FeedStatCard> getPlanCard() {
        return planCard;
    }

    public LiveData<FeedStatCard> getDoneCard() {
        return doneCard;
    }

    public LiveData<FeedStatCard> getOverdueCard() {
        return overdueCard;
    }

    public LiveData<List<PlanShortageVO>> getShortageBanners() {
        return shortageBanners;
    }

    public LiveData<String> getShortageMoreText() {
        return shortageMoreText;
    }

    public LiveData<Boolean> getShowManageEntry() {
        return showManageEntry;
    }

    public LiveData<Boolean> getShowStockIn() {
        return showStockIn;
    }

    public LiveData<String> getStatusFilter() {
        return statusFilter;
    }

    public LiveData<FeedTodaySummaryVO.FeedPenOptionVO> getPenFilter() {
        return penFilter;
    }

    public LiveData<PagingData<FeedTaskRow>> getFeedTasks() {
        return feedTasks;
    }

    public LiveData<FeedEmptyKind> getEmptyKind() {
        return emptyKind;
    }

    public LiveData<List<FeedPlanBatchOptionVO>> getPlanBatches() {
        return planBatches;
    }

    public SingleLiveEvent<Bundle> getOpenRegister() {
        return openRegister;
    }

    public SingleLiveEvent<Void> getShowBatchSheet() {
        return showBatchSheet;
    }

    public SingleLiveEvent<Long> getOpenPlan() {
        return openPlan;
    }

    public SingleLiveEvent<Void> getOpenExec() {
        return openExec;
    }

    public SingleLiveEvent<Void> getShowPenSheet() {
        return showPenSheet;
    }

    public SingleLiveEvent<Long> getOpenStockIn() {
        return openStockIn;
    }

    /** 员工视图（不显示方案、统计入口与“去入库”） */
    public boolean isManager() {
        FeedTodaySummaryVO s = summary.getValue();
        return s != null && s.isCanManage();
    }

    // ===== 加载 =====

    /** API-G01；列表由 Pager 按筛选条件加载（API-G02） */
    public void load() {
        load(repository.getTodaySummary(), s -> {
            summary.setValue(s);
            emptyKind.setValue(emptyKindOf(s.getEmptyReason()));
            if (presetPenId != null) {
                for (FeedTodaySummaryVO.FeedPenOptionVO p : s.getPenOptions()) {
                    if (p.getPenId() == presetPenId) {
                        penFilter.setValue(p);
                    }
                }
                presetPenId = null;
            }
        });
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return data instanceof FeedTodaySummaryVO ? ((FeedTodaySummaryVO) data).getEmptyReason() != null
                : super.isEmptyData(data);
    }

    @Nullable
    static FeedEmptyKind emptyKindOf(@Nullable String reason) {
        if (FeedTodaySummaryVO.EMPTY_NO_PEN.equals(reason)) {
            return FeedEmptyKind.NO_PEN;
        }
        if (FeedTodaySummaryVO.EMPTY_NO_PLAN.equals(reason)) {
            return FeedEmptyKind.NO_PLAN;
        }
        if (FeedTodaySummaryVO.EMPTY_NO_TASK.equals(reason)) {
            return FeedEmptyKind.NO_TASK;
        }
        return null;
    }

    /** 下拉刷新：指标、横幅与列表 */
    @Override
    public void refresh() {
        load();
        super.refresh();
    }

    @Override
    public void retry() {
        load();
        super.refresh();
    }

    // ===== 筛选 =====

    /** 状态 Chip：null 为全部；指标卡不变 */
    public void selectStatus(@Nullable String status) {
        statusFilter.setValue(status);
        applyFilter();
    }

    /** 栏舍面板：null 为全部栏舍 */
    public void selectPen(@Nullable FeedTodaySummaryVO.FeedPenOptionVO pen) {
        penFilter.setValue(pen);
        applyFilter();
    }

    public void selectPenById(long penId) {
        FeedTodaySummaryVO s = summary.getValue();
        if (penId <= 0 || s == null) {
            selectPen(null);
            return;
        }
        for (FeedTodaySummaryVO.FeedPenOptionVO p : s.getPenOptions()) {
            if (p.getPenId() == penId) {
                selectPen(p);
                return;
            }
        }
        selectPen(null);
    }

    private void applyFilter() {
        FeedTodaySummaryVO.FeedPenOptionVO pen = penFilter.getValue();
        setFilter(new FeedTaskQuery(statusFilter.getValue(), pen == null ? null : pen.getPenId()));
    }

    /** 筛选后没有任务时的列表区文字：“没有已逾期的饲喂任务” */
    @NonNull
    public String filterEmptyText() {
        String status = statusFilter.getValue();
        String label = FeedTaskItemVO.STATUS_PENDING.equals(status) ? "待投喂"
                : FeedTaskItemVO.STATUS_DONE.equals(status) ? "已投喂"
                : FeedTaskItemVO.STATUS_OVERDUE.equals(status) ? "已逾期" : null;
        FeedTodaySummaryVO.FeedPenOptionVO pen = penFilter.getValue();
        if (label != null) {
            return (pen == null ? "" : pen.getPenName()) + "没有" + label + "的饲喂任务";
        }
        return (pen == null ? "" : pen.getPenName()) + "今天没有饲喂任务";
    }

    // ===== 点击 =====

    /** 待投喂 / 已逾期带 task_id；已投喂（EDIT、VIEW）带 record_id */
    public void onTaskClick(@NonNull FeedTaskItemVO item) {
        String action = item.getAction();
        Long record = item.getRecordId();
        if ((FeedTaskItemVO.ACTION_EDIT.equals(action) || FeedTaskItemVO.ACTION_VIEW.equals(action)) && record != null) {
            openRegister.setValue(FeedNav.recordArgs(record));
        } else if (item.isDone() && record != null) {
            openRegister.setValue(FeedNav.recordArgs(record));
        } else {
            openRegister.setValue(FeedNav.taskArgs(item.getTaskId()));
        }
    }

    /** tune：只有一个在养批次时直接进入 G03，多个时先选批次（API-G09） */
    public void onPlanEntryClick() {
        launch(repository.getPlanBatchOptions(), list -> {
            planBatches.setValue(list);
            if (list.isEmpty()) {
                showMessage("还没有在养批次，批次入栏后再设置饲喂方案");
            } else if (list.size() == 1) {
                openPlan.setValue(list.get(0).getBatchId());
            } else {
                showBatchSheet.call();
            }
        });
    }

    public void onPlanBatchPicked(long batchId) {
        openPlan.setValue(batchId);
    }

    public void onExecEntryClick() {
        openExec.call();
    }

    public void onPenChipClick() {
        showPenSheet.call();
    }

    public void onStockInClick(long feedId) {
        openStockIn.setValue(feedId);
    }

    /** 子页面（G02、G03、M03）返回：有改动时刷新 */
    public void onChildResult(boolean changed) {
        if (changed) {
            refresh();
        }
    }

    // ===== 指标卡文字 =====

    @Nullable
    static FeedStatCard planCardOf(@Nullable FeedTodaySummaryVO s) {
        if (s == null) {
            return null;
        }
        return FeedStatCard.of("今日计划", FeedFormat.num(s.getPlanTotalQty()), FeedFormat.KG, null);
    }

    @Nullable
    static FeedStatCard doneCardOf(@Nullable FeedTodaySummaryVO s) {
        if (s == null) {
            return null;
        }
        return FeedStatCard.of("已投喂", String.valueOf(s.getDoneCount()), "/ " + s.getTaskTotal() + " 餐", null);
    }

    @Nullable
    static FeedStatCard overdueCardOf(@Nullable FeedTodaySummaryVO s) {
        if (s == null) {
            return null;
        }
        return new FeedStatCard("已逾期", String.valueOf(s.getOverdueCount()), "餐", null, 0,
                s.getOverdueCount() > 0, false);
    }

    @Nullable
    static String moreTextOf(@Nullable FeedTodaySummaryVO s) {
        if (s == null) {
            return null;
        }
        int more = s.getPlanShortageMoreCount() + Math.max(0, s.getPlanShortage().size() - MAX_BANNERS);
        return more > 0 ? "另有 " + more + " 种饲料库存不足" : null;
    }

    /** 库存横幅正文：“结存 500 千克，今天还需 540 千克，晚餐前需要补货。” */
    @NonNull
    public static String shortageText(@NonNull PlanShortageVO s) {
        StringBuilder sb = new StringBuilder();
        sb.append("结存 ").append(FeedFormat.kg(s.getStockQty())).append("，今天还需 ")
                .append(FeedFormat.kg(s.getTodayNeedQty())).append("，");
        if (s.getRestockBeforeMealName() != null) {
            sb.append(s.getRestockBeforeMealName()).append("前需要补货。");
        } else {
            sb.append("需要补货。");
        }
        return sb.toString();
    }
}
