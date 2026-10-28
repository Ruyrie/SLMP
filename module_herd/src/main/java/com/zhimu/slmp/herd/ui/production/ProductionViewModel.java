package com.zhimu.slmp.herd.ui.production;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.data.StockRepository;
import com.zhimu.slmp.herd.model.vo.ProductionBadges;
import com.zhimu.slmp.herd.model.vo.ProductionOverviewVO;
import com.zhimu.slmp.herd.model.vo.ProductionReminderItemVO;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.herd.ui.common.NavTarget;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

/**
 * L01 生产页：今日生产概况（存栏 / 在养批次 / 生产提醒三张卡）、生产提醒列表、功能入口。
 * 今日待办与预警只在首页（T01）展示，生产页不再出现。员工视图同样三张卡，数字由服务端按可见栏舍给出。
 */
public class ProductionViewModel extends BaseViewModel {

    public static final String METRIC_STOCK = "STOCK";
    /** 在养批次卡：与功能入口“批次档案”同一目标 */
    public static final String METRIC_BATCH = "BATCH";
    /** 生产提醒卡：滚动到下方生产提醒列表 */
    public static final String METRIC_REMINDER = "REMINDER";
    /** 字段缺失（老服务端）时的占位 */
    public static final String PLACEHOLDER = "—";
    /** 功能入口“批次档案”：打开 L03 的批次视图 */
    public static final String ENTRY_BATCH_ARCHIVE = "L03_BATCH";

    private final StockRepository repository;

    private final MutableLiveData<ProductionOverviewVO> overview = new MutableLiveData<>();
    private final LiveData<String> dateText;
    private final LiveData<String> stockText;
    private final LiveData<String> batchValueText;
    private final LiveData<String> penOccupancyText;
    private final LiveData<Integer> reminderCount;
    private final LiveData<List<ProductionReminderItemVO>> reminders;
    private final LiveData<Boolean> showNoPenNotice;
    private final LiveData<Boolean> showAuditNotice;
    private final LiveData<ProductionBadges> badges;
    private final LiveData<Long> currentFarmId;
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    /** 生产提醒卡点击：滚动到提醒列表 */
    private final SingleLiveEvent<Boolean> scrollToReminders = new SingleLiveEvent<>();

    @Nullable
    private Long loadedFarmId;
    private boolean loadedOnce;

    @Inject
    public ProductionViewModel(@NonNull StockRepository repository, @NonNull FarmContext farmContext) {
        this.repository = repository;
        dateText = Transformations.map(overview, o -> o == null ? "" : DateFormatter.formatDate(o.getToday()));
        stockText = Transformations.map(overview, o -> o == null ? "" : HerdFormat.qty(o.getStockTotal(),
                o.getUnitText()));
        batchValueText = Transformations.map(overview, ProductionViewModel::batchValueOf);
        penOccupancyText = Transformations.map(overview, ProductionViewModel::penOccupancyOf);
        reminderCount = Transformations.map(overview, o -> o == null ? 0 : o.getReminderTotal());
        reminders = Transformations.map(overview, o -> o == null || isAuditPending(o)
                ? Collections.emptyList() : o.getReminders());
        showNoPenNotice = Transformations.map(overview, o -> o != null && !isAuditPending(o) && !o.isHasPen());
        showAuditNotice = Transformations.map(overview, o -> o != null && isAuditPending(o));
        badges = Transformations.map(overview, ProductionOverviewVO::getBadges);
        currentFarmId = Transformations.distinctUntilChanged(Transformations.map(farmContext.getContext(),
                c -> c == null ? null : c.getFarmId()));
    }

    static boolean isAuditPending(@NonNull ProductionOverviewVO o) {
        return FarmContextVO.STATUS_PENDING.equals(o.getFarmStatus())
                || FarmContextVO.STATUS_REJECTED.equals(o.getFarmStatus());
    }

    /** 在养批次卡数字；服务端没有 active_batch_count 时为“—” */
    @NonNull
    static String batchValueOf(@Nullable ProductionOverviewVO o) {
        Integer n = o == null ? null : o.getActiveBatchCount();
        return n == null ? PLACEHOLDER : HerdFormat.number(n);
    }

    /** 栏舍占用“a/b”（有在养批次的栏舍 / 启用栏舍）；任一字段缺失时为 null（不显示副标题） */
    @Nullable
    static String penOccupancyOf(@Nullable ProductionOverviewVO o) {
        if (o == null || o.getOccupiedPenCount() == null || o.getPenCount() == null) {
            return null;
        }
        return HerdFormat.number(o.getOccupiedPenCount()) + "/" + HerdFormat.number(o.getPenCount());
    }

    // ===== 对外 LiveData =====

    public LiveData<ProductionOverviewVO> getOverview() {
        return overview;
    }

    public LiveData<String> getDateText() {
        return dateText;
    }

    public LiveData<String> getStockText() {
        return stockText;
    }

    /** 在养批次卡数字（字段缺失时“—”） */
    public LiveData<String> getBatchValueText() {
        return batchValueText;
    }

    /** 在养批次卡副标题中的“a/b”，null 时不显示副标题 */
    public LiveData<String> getPenOccupancyText() {
        return penOccupancyText;
    }

    /** 生产提醒卡数字 = reminder_total（= 提醒列表条数） */
    public LiveData<Integer> getReminderCount() {
        return reminderCount;
    }

    /** 生产提醒卡点击后滚动到提醒列表（一次性事件） */
    public SingleLiveEvent<Boolean> getScrollToReminders() {
        return scrollToReminders;
    }

    public LiveData<List<ProductionReminderItemVO>> getReminders() {
        return reminders;
    }

    public LiveData<Boolean> getShowNoPenNotice() {
        return showNoPenNotice;
    }

    public LiveData<Boolean> getShowAuditNotice() {
        return showAuditNotice;
    }

    public LiveData<ProductionBadges> getBadges() {
        return badges;
    }

    public LiveData<Long> getCurrentFarmId() {
        return currentFarmId;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    // ===== 加载 =====

    /** 当前养殖场变化（含首次）：整页重新加载，显示骨架屏 */
    public void onFarmChanged(@Nullable Long farmId) {
        if (loadedOnce && Objects.equals(farmId, loadedFarmId)) {
            return;
        }
        loadedOnce = true;
        loadedFarmId = farmId;
        resetContentLoaded();
        load();
    }

    /** API-L01；已有内容时为刷新，失败保留旧数据 */
    public void load() {
        load(repository.getOverview(), overview::setValue);
    }

    public void refresh() {
        load();
    }

    @Override
    public void retry() {
        load();
    }

    // ===== 点击 =====

    /**
     * 提醒项：未处理异常上报 → I06 诊断详情（带 report_id；缺 report_id 时退回 T04 待办详情）；
     * 其余按 route 打开 D05（带 batch_id）、M01 等。
     */
    public void onReminderClick(@NonNull ProductionReminderItemVO item) {
        if (ProductionReminderItemVO.TYPE_ABNORMAL_REPORT.equals(item.getReminderType())) {
            if (item.getReportId() != null) {
                navEvent.setValue(NavTarget.of(RouterPath.I06).put(RouterPath.EXTRA_REPORT_ID, item.getReportId()));
            } else if (item.getTodoId() != null) {
                navEvent.setValue(NavTarget.of(RouterPath.T04).put(RouterPath.EXTRA_TODO_ID, item.getTodoId()));
            }
            return;
        }
        NavTarget t = NavTarget.ofPage(item.getRoute());
        if (t == null) {
            return;
        }
        navEvent.setValue(t.put(RouterPath.EXTRA_BATCH_ID, item.getBatchId())
                .put(RouterPath.EXTRA_TODO_ID, item.getTodoId()));
    }

    /**
     * 指标卡：存栏 → L03 栏舍视图；在养批次 → L03 批次视图（与功能入口“批次档案”相同）；
     * 生产提醒 → 滚动到提醒列表（0 项时不响应）。
     */
    public void onMetricClick(@NonNull String metric) {
        switch (metric) {
            case METRIC_STOCK:
                navEvent.setValue(NavTarget.of(RouterPath.L03).put(RouterPath.EXTRA_VIEW_TYPE, HerdExtras.VIEW_PEN));
                break;
            case METRIC_BATCH:
                onEntryClick(ENTRY_BATCH_ARCHIVE);
                break;
            case METRIC_REMINDER:
                ProductionOverviewVO o = overview.getValue();
                if (o != null && o.getReminderTotal() > 0) {
                    scrollToReminders.setValue(Boolean.TRUE);
                }
                break;
            default:
                break;
        }
    }

    /** 功能入口：页面编号；“批次档案”为 L03 批次视图 */
    public void onEntryClick(@NonNull String code) {
        if (ENTRY_BATCH_ARCHIVE.equals(code)) {
            navEvent.setValue(NavTarget.of(RouterPath.L03).put(RouterPath.EXTRA_VIEW_TYPE, HerdExtras.VIEW_BATCH));
            return;
        }
        if ("L03".equals(code)) {
            navEvent.setValue(NavTarget.of(RouterPath.L03).put(RouterPath.EXTRA_VIEW_TYPE, HerdExtras.VIEW_PEN));
            return;
        }
        NavTarget t = NavTarget.ofPage(code);
        if (t != null) {
            navEvent.setValue(t);
        }
    }

    /** 未审核 Notice“查看审核结果” → F03 */
    public void onAuditResultClick() {
        ProductionOverviewVO o = overview.getValue();
        navEvent.setValue(NavTarget.of(RouterPath.F03).put(RouterPath.EXTRA_FARM_ID, o == null ? null : o.getFarmId()));
    }

    /** 无栏舍 Notice“新增栏舍” → L05 */
    public void onAddPenClick() {
        navEvent.setValue(NavTarget.of(RouterPath.L05));
    }
}
