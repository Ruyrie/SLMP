package com.zhimu.slmp.health.ui.immuneplan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingData;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.data.HealthSharedRepository;
import com.zhimu.slmp.health.data.ImmuneRepository;
import com.zhimu.slmp.health.data.MedicationRepository;
import com.zhimu.slmp.health.model.request.ImmunePlanPostponeRequest;
import com.zhimu.slmp.health.model.request.VersionRequest;
import com.zhimu.slmp.health.model.vo.ImmunePlanOverviewVO;
import com.zhimu.slmp.health.model.vo.ImmunePlanVO;
import com.zhimu.slmp.health.model.vo.MedicationTargetVO;
import com.zhimu.slmp.health.model.vo.MemberItemVO;
import com.zhimu.slmp.health.ui.common.RecordLogHost;
import com.zhimu.slmp.health.util.HealthFormRules;
import com.zhimu.slmp.health.util.HealthFormat;
import com.zhimu.slmp.health.util.ImmuneRules;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * D01 免疫计划：批次概况、状态筛选（ALL / PENDING / MISSED / POSTPONED / DONE / CANCELLED）、入栏前项目（员工不返回）、
 * 计划列表（API-D02 分页）；所有者、场长可确认入栏前项目、加入计划、调整日期、补录漏免、修改与删除项目、添加项目。
 * 进入时没有 batch_id（生产页入口）时先选批次（API-D19 BATCH）。
 */
public class ImmunePlanViewModel extends BaseListViewModel<ImmunePlanViewModel.PlanFilter, ImmunePlanVO>
        implements RecordLogHost {

    public static final String ALL = "ALL";
    /** Chip 顺序与文字 */
    public static final String[] STATUSES = {ALL, ImmuneRules.STATUS_PENDING, ImmuneRules.STATUS_MISSED,
            ImmuneRules.STATUS_POSTPONED, ImmuneRules.STATUS_DONE, ImmuneRules.STATUS_CANCELLED};
    public static final String F_NEW_DATE = "new_date";
    public static final String F_REASON = "reason";
    static final String UNDO_PREFIX = "health_undo_pre_entry:";

    /** 列表筛选：批次 + 状态 */
    public static final class PlanFilter {
        final long batchId;
        @NonNull
        final String status;

        PlanFilter(long batchId, @NonNull String status) {
            this.batchId = batchId;
            this.status = status;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PlanFilter)) {
                return false;
            }
            PlanFilter f = (PlanFilter) o;
            return f.batchId == batchId && f.status.equals(status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(batchId, status);
        }
    }

    private final ImmuneRepository repository;
    private final MedicationRepository medicationRepository;
    private final HealthSharedRepository shared;
    private final FarmContext farmContext;

    private final MutableLiveData<ImmunePlanOverviewVO> overview = new MutableLiveData<>();
    private final MutableLiveData<List<ImmunePlanOverviewVO.PreEntryItemVO>> preEntryItems =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> statusFilter = new MutableLiveData<>(ALL);
    private final MutableLiveData<Boolean> canManage = new MutableLiveData<>(false);
    private final MediatorLiveData<String> emptyText = new MediatorLiveData<>();
    private final MutableLiveData<String> postponeDate = new MutableLiveData<>("");
    private final MutableLiveData<String> postponeReason = new MutableLiveData<>("");
    private final MediatorLiveData<String> postponeAgeText = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> postponing = new MutableLiveData<>(false);
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Long> highlightPlanId = new MutableLiveData<>();
    private final MutableLiveData<List<RecordLogVO>> logs = new MutableLiveData<>();
    private final MutableLiveData<String> reminderNames = new MutableLiveData<>("");

    private final SingleLiveEvent<ImmunePlanVO> openRegister = new SingleLiveEvent<>();
    private final SingleLiveEvent<ImmunePlanVO> openEditItem = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openRecord = new SingleLiveEvent<>();
    private final SingleLiveEvent<ImmunePlanVO> showPostpone = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> closePostpone = new SingleLiveEvent<>();
    private final SingleLiveEvent<ImmunePlanVO> showDeleteConfirm = new SingleLiveEvent<>();
    private final SingleLiveEvent<List<MedicationTargetVO>> pickBatch = new SingleLiveEvent<>();

    @Nullable
    private Long batchId;
    @Nullable
    private ImmunePlanVO postponePlan;
    @Nullable
    private List<MedicationTargetVO> batchOptions;
    /** 已确认的入栏前项目：plan_id → 确认后的版本（撤销用；服务端每次修改版本加 1） */
    private final Map<Long, Integer> confirmedVersions = new LinkedHashMap<>();

    @Inject
    public ImmunePlanViewModel(@NonNull ImmuneRepository repository, @NonNull MedicationRepository medicationRepository,
                               @NonNull HealthSharedRepository shared, @NonNull FarmContext farmContext) {
        super(null);
        this.repository = repository;
        this.medicationRepository = medicationRepository;
        this.shared = shared;
        this.farmContext = farmContext;
        emptyText.addSource(overview, v -> emptyText.setValue(computeEmptyText()));
        emptyText.addSource(statusFilter, v -> emptyText.setValue(computeEmptyText()));
        postponeAgeText.addSource(postponeDate, v -> postponeAgeText.setValue(computeAgeText()));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = asLong(handle.get(RouterPath.EXTRA_BATCH_ID));
        if (id != null) {
            start(id);
        } else {
            requestBatchPick();
        }
    }

    @Nullable
    static Long asLong(@Nullable Object o) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o instanceof String) {
            try {
                return Long.parseLong((String) o);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // ===== 对外 =====

    public LiveData<ImmunePlanOverviewVO> getOverview() { return overview; }

    public LiveData<List<ImmunePlanOverviewVO.PreEntryItemVO>> getPreEntryItems() { return preEntryItems; }

    public LiveData<String> getStatusFilter() { return statusFilter; }

    public LiveData<PagingData<ImmunePlanVO>> getPlans() { return getPagingData(); }

    public LiveData<Boolean> getCanManage() { return canManage; }

    public LiveData<String> getEmptyText() { return emptyText; }

    public MutableLiveData<String> getPostponeDate() { return postponeDate; }

    public MutableLiveData<String> getPostponeReason() { return postponeReason; }

    public LiveData<String> getPostponeAgeText() { return postponeAgeText; }

    public LiveData<Boolean> getPostponing() { return postponing; }

    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }

    public LiveData<Long> getHighlightPlanId() { return highlightPlanId; }

    @Override
    public LiveData<List<RecordLogVO>> getLogs() { return logs; }

    public SingleLiveEvent<ImmunePlanVO> getOpenRegister() { return openRegister; }

    public SingleLiveEvent<ImmunePlanVO> getOpenEditItem() { return openEditItem; }

    public SingleLiveEvent<Long> getOpenRecord() { return openRecord; }

    public SingleLiveEvent<ImmunePlanVO> getShowPostpone() { return showPostpone; }

    public SingleLiveEvent<Void> getClosePostpone() { return closePostpone; }

    public SingleLiveEvent<ImmunePlanVO> getShowDeleteConfirm() { return showDeleteConfirm; }

    public SingleLiveEvent<List<MedicationTargetVO>> getPickBatch() { return pickBatch; }

    @Nullable
    public Long getBatchId() { return batchId; }

    @Nullable
    public ImmunePlanVO getPostponePlan() { return postponePlan; }

    public boolean isStaff() { return farmContext.isStaff(); }

    // ===== 加载 =====

    /** 选定批次后加载概况与列表 */
    public void start(long id) {
        batchId = id;
        String status = statusFilter.getValue() == null ? ALL : statusFilter.getValue();
        setFilter(new PlanFilter(id, status));
        loadOverview();
    }

    private void loadOverview() {
        if (batchId == null) {
            return;
        }
        load(repository.getOverview(batchId), this::applyOverview);
    }

    private void applyOverview(@NonNull ImmunePlanOverviewVO vo) {
        overview.setValue(vo);
        preEntryItems.setValue(isStaff() ? Collections.emptyList() : vo.getPreEntryItems());
        canManage.setValue(!isStaff() && vo.isCanManage() && !vo.isBatchEnded());
    }

    /** 下拉刷新：概况与列表一起重新加载，筛选保留 */
    @Override
    public void refresh() {
        loadOverview();
        super.refresh();
    }

    @Override
    public void retry() {
        if (batchId == null) {
            requestBatchPick();
            return;
        }
        resetContentLoaded();
        loadOverview();
        super.refresh();
    }

    @NonNull
    @Override
    protected PagingSource<Integer, ImmunePlanVO> createPagingSource(@Nullable PlanFilter filter) {
        if (filter == null) {
            return new PagedSource<>((page, size) -> Single.just(new PageVO<ImmunePlanVO>()));
        }
        return repository.planPager(filter.batchId, filter.status, null);
    }

    /** 筛选 Chip */
    public void selectStatus(@NonNull String status) {
        if (status.equals(statusFilter.getValue())) {
            return;
        }
        statusFilter.setValue(status);
        if (batchId != null) {
            setFilter(new PlanFilter(batchId, status));
        }
    }

    /** 各 Chip 的数量（数量为 0 的 Chip 仍显示） */
    public int countOf(@NonNull String status) {
        ImmunePlanOverviewVO vo = overview.getValue();
        ImmunePlanOverviewVO.Counts c = vo == null ? null : vo.getCounts();
        if (c == null) {
            return 0;
        }
        switch (status) {
            case ImmuneRules.STATUS_PENDING:
                return c.getPending();
            case ImmuneRules.STATUS_MISSED:
                return c.getMissed();
            case ImmuneRules.STATUS_POSTPONED:
                return c.getPostponed();
            case ImmuneRules.STATUS_DONE:
                return c.getDone();
            case ImmuneRules.STATUS_CANCELLED:
                return c.getCancelled();
            default:
                return c.getAll();
        }
    }

    @NonNull
    public static String statusLabel(@NonNull String status) {
        switch (status) {
            case ImmuneRules.STATUS_PENDING:
                return "待接种";
            case ImmuneRules.STATUS_MISSED:
                return "漏免";
            case ImmuneRules.STATUS_POSTPONED:
                return "已延期";
            case ImmuneRules.STATUS_DONE:
                return "已接种";
            case ImmuneRules.STATUS_CANCELLED:
                return "已取消";
            default:
                return "全部";
        }
    }

    /** 列表为空时的文案：无模板（按角色）、筛选后为空（“没有漏免的项目。”） */
    @Nullable
    String computeEmptyText() {
        ImmunePlanOverviewVO vo = overview.getValue();
        String status = statusFilter.getValue() == null ? ALL : statusFilter.getValue();
        if (vo == null) {
            return null;
        }
        if (ALL.equals(status) && !vo.isHasTemplate()) {
            return isStaff() ? "该品种没有免疫模板，请联系所有者或场长添加免疫项目。" : "该品种没有免疫模板，请手工添加。";
        }
        if (!ALL.equals(status)) {
            return "没有" + statusLabel(status) + "的项目。";
        }
        return "还没有免疫项目。";
    }

    /** 空状态是否显示“添加免疫项目”按钮（养殖户、无模板） */
    public boolean showAddOnEmpty() {
        ImmunePlanOverviewVO vo = overview.getValue();
        return vo != null && !vo.isHasTemplate() && Boolean.TRUE.equals(canManage.getValue())
                && ALL.equals(statusFilter.getValue());
    }

    // ===== 批次选择（没有 batch_id 时） =====

    public void requestBatchPick() {
        if (batchOptions != null) {
            showBatchPick(batchOptions);
            return;
        }
        setUiState(UiState.loading());
        launch(medicationRepository.getTargets(MedicationRepository.TARGET_BATCH, null), list -> {
            batchOptions = list;
            showBatchPick(list);
        }, this::applyErrorState);
    }

    private void showBatchPick(@NonNull List<MedicationTargetVO> list) {
        if (list.size() == 1 && list.get(0).getBatchId() != null) {
            start(list.get(0).getBatchId());
            return;
        }
        setUiState(UiState.empty("请选择批次", list.isEmpty() ? "当前养殖场没有在养批次。" : "选择一个在养批次查看免疫计划。"));
        if (!list.isEmpty()) {
            pickBatch.setValue(list);
        }
    }

    @Nullable
    public List<MedicationTargetVO> getBatchOptions() {
        return batchOptions;
    }

    // ===== 入栏前项目 =====

    /** API-D03 确认已接种（不扣库存），Snackbar 带“撤销” */
    public void confirmPreEntry(@NonNull ImmunePlanOverviewVO.PreEntryItemVO item) {
        launch(repository.confirmPreEntry(item.getPlanId(), new VersionRequest(item.getVersion())), vo -> {
            confirmedVersions.put(item.getPlanId(), item.getVersion() + 1);
            applyOverview(vo);
            showMessage(UiMessage.withAction("已确认" + HealthFormat.nonNull(item.getVaccineName()) + "入栏前已接种",
                    "撤销", UNDO_PREFIX + item.getPlanId()));
        }, this::onActionError);
    }

    /** API-D05 撤销确认 */
    public void undoPreEntry(long planId) {
        Integer version = confirmedVersions.get(planId);
        if (version == null) {
            return;
        }
        launch(repository.undoPreEntry(planId, new VersionRequest(version)), vo -> {
            confirmedVersions.remove(planId);
            applyOverview(vo);
        }, this::onActionError);
    }

    /** Snackbar 操作“撤销” */
    public void onMessageAction(@NonNull String actionId) {
        if (actionId.startsWith(UNDO_PREFIX)) {
            Long id = asLong(actionId.substring(UNDO_PREFIX.length()));
            if (id != null) {
                undoPreEntry(id);
            }
        }
    }

    /** API-D04 加入计划（计划日期为今天） */
    public void joinPreEntry(@NonNull ImmunePlanOverviewVO.PreEntryItemVO item) {
        launch(repository.joinPreEntry(item.getPlanId(), new VersionRequest(item.getVersion())), plan -> {
            highlightPlanId.setValue(plan.getId());
            showMessage("已加入免疫计划，计划日期为今天");
            refresh();
        }, this::onActionError);
    }

    // ===== 列表项操作 =====

    /** 卡片上的按钮与菜单：REGISTER、BACKFILL、POSTPONE、EDIT、DELETE */
    public void onPlanAction(@NonNull ImmunePlanVO plan, @NonNull String action) {
        switch (action) {
            case HealthExtras.ACTION_REGISTER:
            case HealthExtras.ACTION_BACKFILL:
                openRegister.setValue(plan);
                break;
            case HealthExtras.ACTION_POSTPONE:
                beginPostpone(plan);
                break;
            case HealthExtras.ACTION_EDIT:
                openEditItem.setValue(plan);
                break;
            case HealthExtras.ACTION_DELETE:
                showDeleteConfirm.setValue(plan);
                break;
            default:
                break;
        }
    }

    /** 点已接种卡片：进入接种记录查看 */
    public void onPlanClick(@NonNull ImmunePlanVO plan) {
        if (ImmuneRules.STATUS_DONE.equals(plan.getStatus()) && plan.getRecordId() != null) {
            openRecord.setValue(plan.getRecordId());
        }
    }

    /** FAB“添加项目”与无模板空状态按钮 */
    public void onAddItem() {
        openEditItem.setValue(null);
    }

    /** D02 / D03 返回成功后刷新（D02 带回新项目 ID 时高亮） */
    public void onChildResult(@Nullable Long planId) {
        if (planId != null) {
            highlightPlanId.setValue(planId);
        }
        refresh();
    }

    public void consumeHighlight() {
        highlightPlanId.setValue(null);
    }

    // ===== 调整日期 =====

    private void beginPostpone(@NonNull ImmunePlanVO plan) {
        postponePlan = plan;
        postponeDate.setValue(plan.getPlanDate());
        postponeReason.setValue("");
        fieldErrors.setValue(Collections.emptyMap());
        loadReminderNames();
        showPostpone.setValue(plan);
    }

    @Nullable
    private String computeAgeText() {
        ImmunePlanOverviewVO vo = overview.getValue();
        String date = postponeDate.getValue();
        if (vo == null || date == null || date.isEmpty()) {
            return "";
        }
        return ImmuneRules.ageText(vo.getEntryAgeDays(), vo.getEntryDate(), date);
    }

    public void onPostponeDateSelected(@Nullable String date) {
        if (date != null) {
            postponeDate.setValue(date);
            clearError(F_NEW_DATE);
        }
    }

    public void clearError(@NonNull String field) {
        Map<String, String> m = fieldErrors.getValue();
        if (m != null && m.containsKey(field)) {
            Map<String, String> copy = new LinkedHashMap<>(m);
            copy.remove(field);
            fieldErrors.setValue(copy);
        }
    }

    /** 对话框说明：“猪瘟活疫苗 · 加强免疫，原计划 10月19日 周一。改期后，系统在新日期前 1 天和当天提醒李明、刘芳和你。” */
    @NonNull
    public String postponeMessage() {
        ImmunePlanVO p = postponePlan;
        if (p == null) {
            return "";
        }
        String title = HealthFormat.nonNull(p.getVaccineName())
                + (p.getImmuneRound() == null ? "" : HealthFormat.DOT + p.getImmuneRound());
        String names = reminderNames.getValue();
        return title + "，原计划 " + HealthFormat.dateWeek(p.getPlanDate()) + "。改期后，系统在新日期前 1 天和当天提醒"
                + (names == null || names.isEmpty() ? "负责员工、所有者和场长" : names) + "。";
    }

    public LiveData<String> getReminderNames() {
        return reminderNames;
    }

    /** 提醒对象：该栏舍负责员工 + 场长（不含本人）+ “你” */
    private void loadReminderNames() {
        ImmunePlanVO p = postponePlan;
        String staff = null;
        if (p != null && p.getResponsibleText() != null && !p.getResponsibleText().startsWith("所有者")) {
            staff = p.getResponsibleText().replace("负责", "").trim();
        }
        final String staffName = staff;
        reminderNames.setValue(joinNames(staffName, Collections.emptyList()));
        launch(shared.getMembers(), members -> {
            List<String> managers = new ArrayList<>();
            for (MemberItemVO m : members) {
                if ("MANAGER".equals(m.getRole()) && !m.isMe()) {
                    managers.add(m.getName());
                }
            }
            reminderNames.setValue(joinNames(staffName, managers));
        }, e -> {
        });
    }

    @NonNull
    static String joinNames(@Nullable String staff, @NonNull List<String> managers) {
        List<String> names = new ArrayList<>();
        if (staff != null && !staff.isEmpty()) {
            names.add(staff);
        }
        names.addAll(managers);
        if (names.isEmpty()) {
            return "你";
        }
        return String.join("、", names) + "和你";
    }

    /** API-D06 确认延期 */
    public void submitPostpone() {
        ImmunePlanVO plan = postponePlan;
        if (plan == null || Boolean.TRUE.equals(postponing.getValue())) {
            return;
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String dateError = HealthFormRules.postponeDate(postponeDate.getValue(), plan.getPlanDate());
        if (dateError != null) {
            errors.put(F_NEW_DATE, dateError);
        }
        String reasonError = HealthFormRules.postponeReason(postponeReason.getValue());
        if (reasonError != null) {
            errors.put(F_REASON, reasonError);
        }
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            return;
        }
        ImmunePlanPostponeRequest req = new ImmunePlanPostponeRequest();
        req.newDate = postponeDate.getValue();
        req.reason = Objects.requireNonNull(postponeReason.getValue()).trim();
        req.version = plan.getVersion();
        postponing.setValue(true);
        launch(repository.postpone(plan.getId(), req), vo -> {
            postponing.setValue(false);
            postponePlan = null;
            closePostpone.call();
            showMessage("已延期到 " + HealthFormat.dateWeek(vo.getPlanDate() != null ? vo.getPlanDate() : req.newDate));
            highlightPlanId.setValue(vo.getId());
            refresh();
        }, e -> {
            postponing.setValue(false);
            if (e.getCode() == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()) {
                fieldErrors.setValue(new LinkedHashMap<>(e.getFieldErrors()));
                return;
            }
            if (ErrorCodes.isDataChanged(e.getCode()) || ErrorCodes.isNoPermission(e.getCode())) {
                closePostpone.call();
                showMessage(e.getMessage());
                refresh();
                return;
            }
            showMessage(e.isNetwork() ? ApiException.MSG_NETWORK : e.getMessage());
        });
    }

    public void cancelPostpone() {
        postponePlan = null;
    }

    // ===== 删除项目 =====

    /** API-D09 删除项目（置为已取消，相关待办同时取消） */
    public void deletePlan(@NonNull ImmunePlanVO plan) {
        launch(repository.deletePlan(plan.getId(), plan.getVersion()), () -> {
            showMessage("已删除项目");
            refresh();
        }, this::onActionError);
    }

    /** 弹窗外的操作失败：无权限只提示（不切换整页），数据已变化提示后刷新 */
    private void onActionError(@NonNull ApiException e) {
        if (ErrorCodes.isNoPermission(e.getCode())) {
            showMessage(e.getMessage());
            return;
        }
        handleError(e);
    }

    @Override
    protected void onDataChanged(@NonNull ApiException e) {
        refresh();
    }

    // ===== 修改记录 =====

    /** API-U14（IMMUNE_PLAN、batch_id） */
    public void loadLogs() {
        if (batchId == null) {
            return;
        }
        logs.setValue(null);
        launch(shared.getRecordLogs(HealthExtras.BIZ_IMMUNE_PLAN, batchId), logs::setValue,
                e -> logs.setValue(Collections.emptyList()));
    }
}
