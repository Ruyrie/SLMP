package com.zhimu.slmp.inspect.ui.today;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.inspect.InspectExtras;
import com.zhimu.slmp.inspect.data.InspectionRepository;
import com.zhimu.slmp.inspect.model.request.InspectionSettingRequest;
import com.zhimu.slmp.inspect.model.vo.InspectionListItemVO;
import com.zhimu.slmp.inspect.model.vo.InspectionSettingVO;
import com.zhimu.slmp.inspect.model.vo.InspectionSummaryVO;
import com.zhimu.slmp.inspect.model.vo.RecentIssueVO;
import com.zhimu.slmp.inspect.ui.common.Args;
import com.zhimu.slmp.inspect.ui.common.InspectFormat;
import com.zhimu.slmp.inspect.ui.common.NavTarget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

/**
 * I01 今日巡检（模块07 文档 2.4.1）：统计卡 API-I01、今日清单分页 API-I02（tab_counts 为筛选 Chip 数量）、
 * 养殖户版“前 3 天 · 超时与补录”API-I03、员工版“昨天”分组 API-I02（昨天）；截止时间面板 API-I04 / I05。
 * 员工（isManagerView 为 false）不渲染截止时间“修改”、筛选 Chip、前 3 天分组与“补录”。
 */
public class InspectTodayViewModel extends BaseListViewModel<TodayQuery, InspectionListItemVO> {

    public static final String MSG_DEADLINE_FORBIDDEN = "只有所有者和场长可以设置巡检截止时间";

    /** 空状态：1 没有在栏批次 / 2 员工没有负责栏舍 / 3 筛选无结果 */
    public static final int EMPTY_NONE = 0;
    public static final int EMPTY_NO_BATCH = 1;
    public static final int EMPTY_NO_PENS = 2;
    public static final int EMPTY_FILTER = 3;

    private final InspectionRepository repository;
    private final FarmContext farmContext;

    private final MutableLiveData<InspectionSummaryVO> summary = new MutableLiveData<>();
    private final MutableLiveData<String> progressText = new MutableLiveData<>("完成 0.0%");
    private final MutableLiveData<Integer> progress = new MutableLiveData<>(0);
    private final MutableLiveData<String> deadlineTime = new MutableLiveData<>("");
    private final MutableLiveData<String> deadlineText = new MutableLiveData<>("");
    private final MutableLiveData<String> scopeText = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isManagerView = new MutableLiveData<>(false);
    private final MutableLiveData<Map<String, Integer>> tabCounts = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<List<InspectionListItemVO>> yesterdayItems =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<RecentIssueVO>> recentIssues = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> recentVisible = new MutableLiveData<>(false);
    private final MutableLiveData<InspectionSettingVO> setting = new MutableLiveData<>();
    private final MutableLiveData<String> draftDeadline = new MutableLiveData<>("");
    private final MutableLiveData<String> draftRemind = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> deadlineSaving = new MutableLiveData<>(false);
    private final MutableLiveData<String> deadlineError = new MutableLiveData<>();

    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showDeadlinePanel = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> dismissDeadlinePanel = new SingleLiveEvent<>();

    @Nullable
    private Long todoId;

    @Inject
    public InspectTodayViewModel(@NonNull InspectionRepository repository, @NonNull FarmContext farmContext) {
        super(new TodayQuery(DateFormatter.todayString(), TodayQuery.ALL));
        this.repository = repository;
        this.farmContext = farmContext;
        isManagerView.setValue(farmContext.isOwnerOrManager());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        todoId = Args.getLong(handle, RouterPath.EXTRA_TODO_ID);
        String date = Args.getString(handle, RouterPath.EXTRA_DATE);
        if (date != null && DateFormatter.parseDate(date) != null) {
            setFilter(new TodayQuery(date, TodayQuery.ALL));
        }
        loadAll();
    }

    @NonNull
    @Override
    protected PagingSource<Integer, InspectionListItemVO> createPagingSource(@Nullable TodayQuery filter) {
        TodayQuery q = filter == null ? new TodayQuery(DateFormatter.todayString(), TodayQuery.ALL) : filter;
        return repository.todaySource(q.date, q.status, counts -> tabCounts.postValue(
                counts == null ? Collections.emptyMap() : counts));
    }

    // ===== 加载 =====

    /** 统计、前 3 天（养殖户）或昨天（员工） */
    private void loadAll() {
        TodayQuery q = query();
        isManagerView.setValue(farmContext.isOwnerOrManager());
        load(repository.getSummary(q.date), this::applySummary);
        if (Boolean.TRUE.equals(isManagerView.getValue())) {
            launch(repository.getRecentIssues(), list -> {
                recentIssues.setValue(list == null ? Collections.emptyList() : list);
                updateRecentVisible();
            }, e -> recentIssues.setValue(Collections.emptyList()));
        } else {
            String yesterday = DateFormatter.toDateString(DateFormatter.today().minusDays(1));
            launch(repository.getDayList(yesterday), page -> yesterdayItems.setValue(page.getList()),
                    e -> yesterdayItems.setValue(Collections.emptyList()));
        }
    }

    private void applySummary(@NonNull InspectionSummaryVO s) {
        summary.setValue(s);
        BigDecimal rate = s.getCompletionRate();
        progressText.setValue(InspectFormat.completion(rate));
        progress.setValue(rate == null ? 0 : rate.multiply(BigDecimal.valueOf(1000)).intValue());
        deadlineTime.setValue("截止 " + s.getDeadlineTime());
        deadlineText.setValue(" · " + s.getRemindTime() + (s.isMyPens() ? " 会提醒你" : " 提醒负责员工"));
        String date = DateFormatter.formatDate(s.getDate(), true);
        String scope = s.isMyPens()
                ? "你负责 " + (s.getResponsiblePenCount() == null ? 0 : s.getResponsiblePenCount()) + " 个栏舍"
                : "有在栏批次的栏舍";
        scopeText.setValue(s.getFarmName() + InspectFormat.DOT + date + InspectFormat.DOT + scope);
        updateRecentVisible();
    }

    private void updateRecentVisible() {
        TodayQuery q = query();
        recentVisible.setValue(Boolean.TRUE.equals(isManagerView.getValue()) && TodayQuery.ALL.equals(q.status)
                && q.isToday() && recentIssues.getValue() != null && !recentIssues.getValue().isEmpty());
    }

    @Override
    public void retry() {
        loadAll();
        refresh();
    }

    /** 下拉刷新；子页 setResult 返回后也调用 */
    public void onRefresh() {
        loadAll();
        refresh();
    }

    @NonNull
    private TodayQuery query() {
        TodayQuery q = currentFilter();
        return q == null ? new TodayQuery(DateFormatter.todayString(), TodayQuery.ALL) : q;
    }

    // ===== 对外 =====

    public LiveData<InspectionSummaryVO> getSummary() { return summary; }

    /** “完成 50.0%” */
    public LiveData<String> getProgressText() { return progressText; }

    /** 进度 0–1000 */
    public LiveData<Integer> getProgress() { return progress; }

    /** “截止 18:00”（加粗部分） */
    public LiveData<String> getDeadlineTime() { return deadlineTime; }

    /** “ · 17:00 提醒负责员工” */
    public LiveData<String> getDeadlineText() { return deadlineText; }

    /** “东山猪场 · 10月19日 周一 · 有在栏批次的栏舍” */
    public LiveData<String> getScopeText() { return scopeText; }

    public LiveData<Boolean> getIsManagerView() { return isManagerView; }

    public LiveData<Map<String, Integer>> getTabCounts() { return tabCounts; }

    public LiveData<List<InspectionListItemVO>> getYesterdayItems() { return yesterdayItems; }

    public LiveData<List<RecentIssueVO>> getRecentIssues() { return recentIssues; }

    public LiveData<Boolean> getRecentVisible() { return recentVisible; }

    public LiveData<InspectionSettingVO> getSetting() { return setting; }

    public MutableLiveData<String> getDraftDeadline() { return draftDeadline; }

    /** 截止前 1 小时 */
    public LiveData<String> getDraftRemind() { return draftRemind; }

    public LiveData<Boolean> getDeadlineSaving() { return deadlineSaving; }

    public LiveData<String> getDeadlineError() { return deadlineError; }

    public SingleLiveEvent<NavTarget> getNavEvent() { return navEvent; }

    public SingleLiveEvent<Void> getShowDeadlinePanel() { return showDeadlinePanel; }

    public SingleLiveEvent<Void> getDismissDeadlinePanel() { return dismissDeadlinePanel; }

    @NonNull
    public String currentStatus() { return query().status; }

    @NonNull
    public String currentDate() { return query().date; }

    // ===== 筛选与日期 =====

    public void setStatus(@NonNull String status) {
        TodayQuery q = query();
        if (!status.equals(q.status)) {
            setFilter(new TodayQuery(q.date, status));
            updateRecentVisible();
        }
    }

    /** 养殖户版 calendar_month：查看今天及以前某天的清单 */
    public void setDate(@NonNull String date) {
        LocalDate d = DateFormatter.parseDate(date);
        if (d == null || d.isAfter(DateFormatter.today())) {
            return;
        }
        TodayQuery q = query();
        if (!date.equals(q.date)) {
            setFilter(new TodayQuery(date, q.status));
            resetContentLoaded();
            loadAll();
        }
    }

    /** 空状态类型（清单为空时） */
    public int emptyKind() {
        TodayQuery q = query();
        if (!TodayQuery.ALL.equals(q.status)) {
            return EMPTY_FILTER;
        }
        InspectionSummaryVO s = summary.getValue();
        if (s != null && s.isMyPens() && (s.getResponsiblePenCount() == null || s.getResponsiblePenCount() == 0)) {
            return EMPTY_NO_PENS;
        }
        return EMPTY_NO_BATCH;
    }

    // ===== 截止时间面板 =====

    public void openDeadlinePanel() {
        if (!Boolean.TRUE.equals(isManagerView.getValue())) {
            return;
        }
        launch(repository.getSetting(), s -> {
            setting.setValue(s);
            deadlineError.setValue(null);
            setDraftDeadline(s.getDeadlineTime());
            showDeadlinePanel.call();
        });
    }

    /** 快捷 Chip 或滚轮选择 */
    public void setDraftDeadline(@Nullable String hhmm) {
        if (hhmm == null || InspectFormat.parseTime(hhmm) == null) {
            return;
        }
        if (!hhmm.equals(draftDeadline.getValue())) {
            draftDeadline.setValue(hhmm);
        }
        draftRemind.setValue(InspectFormat.minusHour(hhmm));
        deadlineError.setValue(null);
    }

    /** API-I05：与原值相同直接关闭；不带幂等键 */
    public void saveDeadline() {
        InspectionSettingVO s = setting.getValue();
        String value = draftDeadline.getValue();
        if (s == null || value == null || Boolean.TRUE.equals(deadlineSaving.getValue())) {
            return;
        }
        if (value.equals(s.getDeadlineTime())) {
            dismissDeadlinePanel.call();
            return;
        }
        if (!inRange(value, s.getMinTime(), s.getMaxTime())) {
            deadlineError.setValue("截止时间需在 " + s.getMinTime() + "–" + s.getMaxTime() + " 之间");
            return;
        }
        deadlineSaving.setValue(true);
        launch(repository.updateSetting(new InspectionSettingRequest(value)), vo -> {
            deadlineSaving.setValue(false);
            setting.setValue(vo);
            dismissDeadlinePanel.call();
            showMessage("巡检截止时间已改为 " + vo.getDeadlineTime());
            launch(repository.getSummary(query().date), this::applySummary, e -> {
            });
        }, e -> {
            deadlineSaving.setValue(false);
            if (e.getCode() == ErrorCodes.ROLE_FORBIDDEN) {
                dismissDeadlinePanel.call();
                showMessage(e.getMessage() != null ? e.getMessage() : MSG_DEADLINE_FORBIDDEN);
            } else if (e.isNetwork()) {
                deadlineError.setValue("保存失败，请检查网络后重试");
            } else {
                deadlineError.setValue(e.getMessage());
            }
        });
    }

    static boolean inRange(@NonNull String value, @Nullable String min, @Nullable String max) {
        int[] v = InspectFormat.parseTime(value);
        int[] lo = InspectFormat.parseTime(min);
        int[] hi = InspectFormat.parseTime(max);
        if (v == null) {
            return false;
        }
        int m = v[0] * 60 + v[1];
        return (lo == null || m >= lo[0] * 60 + lo[1]) && (hi == null || m <= hi[0] * 60 + hi[1]);
    }

    // ===== 跳转 =====

    /** 栏舍行：已上报且可看诊断 → I06；可修改 → I02 修改；可登记 → I02；其他已巡 → I02 只读 */
    public void onItemClick(@NonNull InspectionListItemVO item) {
        navEvent.setValue(itemTarget(item));
    }

    @Nullable
    NavTarget itemTarget(@NonNull InspectionListItemVO item) {
        if (item.getReportId() != null && item.isCanViewDiagnosis()) {
            return NavTarget.of(RouterPath.I06).put(RouterPath.EXTRA_REPORT_ID, item.getReportId());
        }
        NavTarget t = NavTarget.of(RouterPath.I02).put(RouterPath.EXTRA_PEN_ID, item.getPenId()).forResult();
        if (item.isCanModify()) {
            return t.put(RouterPath.EXTRA_MODE, InspectExtras.MODE_MODIFY)
                    .put(RouterPath.EXTRA_INSPECTION_ID, item.getInspectionId());
        }
        if (item.isCanRegister()) {
            return t.put(RouterPath.EXTRA_MODE, InspectExtras.MODE_REGISTER).put(RouterPath.EXTRA_TODO_ID, todoId);
        }
        if (item.isDone() || item.isAbnormal()) {
            return t.put(RouterPath.EXTRA_MODE, InspectExtras.MODE_READONLY)
                    .put(RouterPath.EXTRA_INSPECTION_ID, item.getInspectionId());
        }
        return null;
    }

    /** “转上报”：巡检已保存，直接进入 I05 */
    public void onReportClick(@NonNull InspectionListItemVO item) {
        if (item.getReportId() != null) {
            navEvent.setValue(NavTarget.of(RouterPath.I06).put(RouterPath.EXTRA_REPORT_ID, item.getReportId()));
            return;
        }
        navEvent.setValue(NavTarget.of(RouterPath.I05)
                .put(RouterPath.EXTRA_INSPECTION_ID, item.getInspectionId())
                .put(RouterPath.EXTRA_PEN_ID, item.getPenId())
                .forResult());
    }

    /** 前 3 天的“超时”“补录”行 → I04 定位到该日 */
    public void onIssueClick(@NonNull RecentIssueVO issue) {
        if (RecentIssueVO.TYPE_MISSED.equals(issue.getIssueType()) && issue.isCanBackfill()) {
            onBackfillClick(issue);
            return;
        }
        navEvent.setValue(NavTarget.of(RouterPath.I04).put(RouterPath.EXTRA_PEN_ID, issue.getPenId())
                .put(RouterPath.EXTRA_LOCATE_DATE, issue.getInspectDate()).forResult());
    }

    /** “补录” → I03，栏舍和日期已带入 */
    public void onBackfillClick(@NonNull RecentIssueVO issue) {
        navEvent.setValue(NavTarget.of(RouterPath.I03).put(RouterPath.EXTRA_PEN_ID, issue.getPenId())
                .put(RouterPath.EXTRA_INSPECT_DATE, issue.getInspectDate()).forResult());
    }

    /** 员工版“昨天”分组 → I04 */
    public void onYesterdayClick(@NonNull InspectionListItemVO item) {
        navEvent.setValue(NavTarget.of(RouterPath.I04).put(RouterPath.EXTRA_PEN_ID, item.getPenId())
                .put(RouterPath.EXTRA_LOCATE_DATE,
                        DateFormatter.toDateString(DateFormatter.today().minusDays(1))).forResult());
    }

    /** 空状态“批次入栏” → L08 */
    public void onEmptyAction() {
        navEvent.setValue(NavTarget.of(RouterPath.L08));
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        if (Objects.equals(e.getCode(), ErrorCodes.ROLE_FORBIDDEN) && summary.getValue() != null) {
            showMessage(e.getMessage());
        } else {
            super.onNoPermission(e);
        }
    }
}
