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
 * L01 生产页（所有者、场长）：今日生产概况、生产提醒、功能入口。
 */
public class ProductionViewModel extends BaseViewModel {

    public static final String METRIC_STOCK = "STOCK";
    public static final String METRIC_TODO = "TODO";
    public static final String METRIC_WARNING = "WARNING";
    /** T03 只显示预警类待办（参数 mode，待模块03 确认） */
    public static final String T03_MODE_WARNING = "WARNING";
    /** 功能入口“批次档案”：打开 L03 的批次视图 */
    public static final String ENTRY_BATCH_ARCHIVE = "L03_BATCH";

    private final StockRepository repository;

    private final MutableLiveData<ProductionOverviewVO> overview = new MutableLiveData<>();
    private final LiveData<String> dateText;
    private final LiveData<String> stockText;
    private final LiveData<Integer> todoCount;
    private final LiveData<Integer> reminderCount;
    private final LiveData<List<ProductionReminderItemVO>> reminders;
    private final LiveData<Boolean> showNoPenNotice;
    private final LiveData<Boolean> showAuditNotice;
    private final LiveData<ProductionBadges> badges;
    private final LiveData<Long> currentFarmId;
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();

    @Nullable
    private Long loadedFarmId;
    private boolean loadedOnce;

    @Inject
    public ProductionViewModel(@NonNull StockRepository repository, @NonNull FarmContext farmContext) {
        this.repository = repository;
        dateText = Transformations.map(overview, o -> o == null ? "" : DateFormatter.formatDate(o.getToday()));
        stockText = Transformations.map(overview, o -> o == null ? "" : HerdFormat.qty(o.getStockTotal(),
                o.getUnitText()));
        todoCount = Transformations.map(overview, o -> o == null ? 0 : o.getTodayTodoCount());
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

    public LiveData<Integer> getTodoCount() {
        return todoCount;
    }

    public LiveData<Integer> getReminderCount() {
        return reminderCount;
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

    /** 提醒项：按 route 打开 D05（带 batch_id）或 M01 */
    public void onReminderClick(@NonNull ProductionReminderItemVO item) {
        NavTarget t = NavTarget.ofPage(item.getRoute());
        if (t == null) {
            return;
        }
        navEvent.setValue(t.put(RouterPath.EXTRA_BATCH_ID, item.getBatchId())
                .put(RouterPath.EXTRA_TODO_ID, item.getTodoId()));
    }

    /** “查看全部 N 条预警” */
    public void onViewAllReminders() {
        onMetricClick(METRIC_WARNING);
    }

    /** 指标卡：存栏 → L03；今日待办 → T03（今天）；预警 → T03 预警筛选 */
    public void onMetricClick(@NonNull String metric) {
        ProductionOverviewVO o = overview.getValue();
        String today = o != null ? o.getToday() : DateFormatter.todayString();
        switch (metric) {
            case METRIC_STOCK:
                navEvent.setValue(NavTarget.of(RouterPath.L03).put(HerdExtras.EXTRA_VIEW_TYPE, HerdExtras.VIEW_PEN));
                break;
            case METRIC_TODO:
                navEvent.setValue(NavTarget.of(RouterPath.T03).put(RouterPath.EXTRA_DATE, today));
                break;
            case METRIC_WARNING:
                navEvent.setValue(NavTarget.of(RouterPath.T03).put(RouterPath.EXTRA_DATE, today)
                        .put(RouterPath.EXTRA_MODE, T03_MODE_WARNING));
                break;
            default:
                break;
        }
    }

    /** 功能入口：页面编号；“批次档案”为 L03 批次视图 */
    public void onEntryClick(@NonNull String code) {
        if (ENTRY_BATCH_ARCHIVE.equals(code)) {
            navEvent.setValue(NavTarget.of(RouterPath.L03).put(HerdExtras.EXTRA_VIEW_TYPE, HerdExtras.VIEW_BATCH));
            return;
        }
        if ("L03".equals(code)) {
            navEvent.setValue(NavTarget.of(RouterPath.L03).put(HerdExtras.EXTRA_VIEW_TYPE, HerdExtras.VIEW_PEN));
            return;
        }
        NavTarget t = NavTarget.ofPage(code);
        if (t != null) {
            navEvent.setValue(t);
        }
    }

    /** FAB“登记” → T02 快捷登记面板 */
    public void onFabClick() {
        navEvent.setValue(NavTarget.of(RouterPath.T02));
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
