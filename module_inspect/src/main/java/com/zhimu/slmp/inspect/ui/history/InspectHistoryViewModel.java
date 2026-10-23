package com.zhimu.slmp.inspect.ui.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.inspect.InspectExtras;
import com.zhimu.slmp.inspect.data.HistoryQuery;
import com.zhimu.slmp.inspect.data.InspectionRepository;
import com.zhimu.slmp.inspect.model.vo.PenInspectionOverviewVO;
import com.zhimu.slmp.inspect.model.vo.PenInspectionRowVO;
import com.zhimu.slmp.inspect.ui.common.Args;
import com.zhimu.slmp.inspect.ui.common.InspectFormat;
import com.zhimu.slmp.inspect.ui.common.NavTarget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * I04 巡检历史（模块07 文档 2.4.5）：栏舍概况 API-I12、历史分页 API-I11（筛选：全部 / 异常 / 超时 / 补录 / 非现场）、
 * 每行“更多”菜单（查看详情、修改巡检或提交修改申请、查看修改记录 API-U14）。员工不显示规则横幅与“提交修改申请”。
 * 参数：pen_id、locate_date（定位并闪一次该行）。
 */
public class InspectHistoryViewModel extends BaseListViewModel<HistoryQuery, PenInspectionRowVO> {

    public static final int MENU_DETAIL = 1;
    public static final int MENU_MODIFY = 2;
    public static final int MENU_CHANGE = 3;
    public static final int MENU_VIEW_CHANGE = 4;
    public static final int MENU_LOGS = 5;

    private final InspectionRepository repository;
    private final FarmContext farmContext;

    private final MutableLiveData<PenInspectionOverviewVO> overview = new MutableLiveData<>();
    private final MutableLiveData<String> overviewText = new MutableLiveData<>("");
    private final MutableLiveData<String> ruleText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isManager = new MutableLiveData<>(false);
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    /** 打开修改记录面板（U14），值为记录 ID */
    private final SingleLiveEvent<Long> showRecordLogs = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> highlightDate = new SingleLiveEvent<>();

    private long penId;

    @Inject
    public InspectHistoryViewModel(@NonNull InspectionRepository repository, @NonNull FarmContext farmContext) {
        super(new HistoryQuery(HistoryQuery.ALL, null, null, null));
        this.repository = repository;
        this.farmContext = farmContext;
        isManager.setValue(farmContext.isOwnerOrManager());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long pen = Args.getLong(handle, RouterPath.EXTRA_PEN_ID);
        penId = pen == null ? 0 : pen;
        String locate = Args.getString(handle, RouterPath.EXTRA_LOCATE_DATE);
        if (locate != null) {
            setFilter(new HistoryQuery(HistoryQuery.ALL, null, null, locate));
            highlightDate.setValue(locate);
        }
        loadOverview();
    }

    @NonNull
    @Override
    protected PagingSource<Integer, PenInspectionRowVO> createPagingSource(@Nullable HistoryQuery filter) {
        return repository.historySource(penId, filter == null ? new HistoryQuery(HistoryQuery.ALL, null, null, null)
                : filter);
    }

    private void loadOverview() {
        load(repository.getPenOverview(penId, DateFormatter.todayString()), this::applyOverview);
    }

    private void applyOverview(@NonNull PenInspectionOverviewVO o) {
        overview.setValue(o);
        StringBuilder sb = new StringBuilder(InspectFormat.join(o.getBatchNos(), ""));
        if (!o.getResponsibleNames().isEmpty()) {
            sb.append(InspectFormat.DOT).append("负责人 ").append(InspectFormat.join(o.getResponsibleNames(), ""));
        }
        sb.append(InspectFormat.DOT).append("近 7 天已巡 ").append(o.getRecent7DoneDays()).append(" / ")
                .append(o.getRecent7ShouldDays()).append(" 天");
        overviewText.setValue(sb.toString());
        ruleText.setValue(farmContext.isOwnerOrManager()
                ? "漏巡只能补录前 3 天内的记录。今天可补录 " + InspectFormat.monthDay(o.getBackfillFrom()) + "–"
                + InspectFormat.monthDay(o.getBackfillTo())
                + "。超过 3 天的记录需要提交修改申请，管理员审核通过后才会修改。" : null);
    }

    @Override
    public void retry() {
        loadOverview();
        refresh();
    }

    /** 子页保存后：刷新概况与列表 */
    public void onChildDone() {
        retry();
    }

    // ===== 对外 =====

    public LiveData<PenInspectionOverviewVO> getOverview() { return overview; }

    /** “ZM2610-031 · 负责人 李明 · 近 7 天已巡 7 / 7 天” */
    public LiveData<String> getOverviewText() { return overviewText; }

    /** 规则横幅（员工为 null，不显示） */
    public LiveData<String> getRuleText() { return ruleText; }

    public LiveData<Boolean> getIsManager() { return isManager; }

    public SingleLiveEvent<NavTarget> getNavEvent() { return navEvent; }

    public SingleLiveEvent<Long> getShowRecordLogs() { return showRecordLogs; }

    public SingleLiveEvent<String> getHighlightDate() { return highlightDate; }

    public long getPenId() { return penId; }

    @NonNull
    public String currentFilterType() {
        HistoryQuery q = currentFilter();
        return q == null ? HistoryQuery.ALL : q.filter;
    }

    // ===== 操作 =====

    public void setFilterType(@NonNull String filter) {
        HistoryQuery q = currentFilter();
        HistoryQuery base = q == null ? new HistoryQuery(HistoryQuery.ALL, null, null, null) : q;
        if (!filter.equals(base.filter)) {
            setFilter(base.withFilter(filter).withLocateDate(null));
        }
    }

    /** 日历选择日期：列表定位到该日 */
    public void locate(@NonNull String date) {
        HistoryQuery q = currentFilter();
        HistoryQuery base = q == null ? new HistoryQuery(HistoryQuery.ALL, null, null, null) : q;
        setFilter(base.withLocateDate(date));
        highlightDate.setValue(date);
    }

    /** “更多”菜单项（按记录日期与角色，UI 文档 I04 表格）；员工只剩“查看详情”时返回空列表（点整行即查看详情） */
    @NonNull
    public List<Integer> menuFor(@NonNull PenInspectionRowVO row) {
        List<Integer> items = new ArrayList<>();
        if (row.isMissed()) {
            return items;
        }
        items.add(MENU_DETAIL);
        if (row.isCanModify()) {
            items.add(MENU_MODIFY);
        } else if (row.getPendingRequestId() != null && farmContext.isOwnerOrManager()) {
            items.add(MENU_VIEW_CHANGE);
        } else if (row.isCanChangeRequest()) {
            items.add(MENU_CHANGE);
        }
        if (row.isHasRevisions()) {
            items.add(MENU_LOGS);
        }
        return items.size() == 1 ? Collections.emptyList() : items;
    }

    /** 点记录行：漏巡可补录 → I03；其余查看详情 */
    public void onRowClick(@NonNull PenInspectionRowVO row) {
        if (row.isMissed()) {
            if (row.isCanBackfill()) {
                onBackfill(row);
            }
            return;
        }
        onMenu(row, MENU_DETAIL);
    }

    public void onBackfill(@NonNull PenInspectionRowVO row) {
        navEvent.setValue(NavTarget.of(RouterPath.I03).put(RouterPath.EXTRA_PEN_ID, penId)
                .put(RouterPath.EXTRA_INSPECT_DATE, row.getInspectDate()).forResult());
    }

    public void onMenu(@NonNull PenInspectionRowVO row, int action) {
        switch (action) {
            case MENU_DETAIL:
                navEvent.setValue(NavTarget.of(RouterPath.I02).put(RouterPath.EXTRA_PEN_ID, penId)
                        .put(RouterPath.EXTRA_MODE, InspectExtras.MODE_READONLY)
                        .put(RouterPath.EXTRA_INSPECTION_ID, row.getInspectionId()));
                break;
            case MENU_MODIFY:
                if (PenInspectionRowVO.TARGET_BACKFILL.equals(row.getModifyTarget())) {
                    navEvent.setValue(NavTarget.of(RouterPath.I03).put(RouterPath.EXTRA_PEN_ID, penId)
                            .put(RouterPath.EXTRA_INSPECTION_ID, row.getInspectionId())
                            .put(RouterPath.EXTRA_INSPECT_DATE, row.getInspectDate())
                            .put(RouterPath.EXTRA_DATE_LOCKED, true).forResult());
                } else {
                    navEvent.setValue(NavTarget.of(RouterPath.I02).put(RouterPath.EXTRA_PEN_ID, penId)
                            .put(RouterPath.EXTRA_MODE, InspectExtras.MODE_MODIFY)
                            .put(RouterPath.EXTRA_INSPECTION_ID, row.getInspectionId()).forResult());
                }
                break;
            case MENU_CHANGE:
                navEvent.setValue(NavTarget.of(RouterPath.U01)
                        .put(RouterPath.EXTRA_BIZ_TYPE, InspectExtras.BIZ_INSPECTION)
                        .put(RouterPath.EXTRA_BIZ_ID, row.getInspectionId()).forResult());
                break;
            case MENU_VIEW_CHANGE:
                navEvent.setValue(NavTarget.of(RouterPath.U02)
                        .put(RouterPath.EXTRA_REQUEST_ID, row.getPendingRequestId()));
                break;
            case MENU_LOGS:
                showRecordLogs.setValue(row.getInspectionId());
                break;
            default:
                break;
        }
    }
}
