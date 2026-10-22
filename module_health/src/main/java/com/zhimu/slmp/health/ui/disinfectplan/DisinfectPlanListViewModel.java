package com.zhimu.slmp.health.ui.disinfectplan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingData;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.health.data.DisinfectRepository;
import com.zhimu.slmp.health.model.request.DisinfectPlanPauseRequest;
import com.zhimu.slmp.health.model.request.VersionRequest;
import com.zhimu.slmp.health.model.vo.DisinfectPlanVO;
import com.zhimu.slmp.health.util.HealthFormRules;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

/**
 * D06 消毒计划（所有者、场长）：状态筛选（ALL / ACTIVE / PAUSED，数量取 tab_counts）、计划卡片（API-D28 分页）、
 * 暂停（底部面板填写原因，API-D33）、启用（不需要确认，API-D34）、修改（D07）。员工打开时整页“只有所有者和场长可以管理消毒计划”。
 */
public class DisinfectPlanListViewModel extends BaseListViewModel<String, DisinfectPlanVO> {

    public static final String ALL = "ALL";
    public static final String ACTIVE = "ACTIVE";
    public static final String PAUSED = "PAUSED";
    public static final String[] STATUSES = {ALL, ACTIVE, PAUSED};
    public static final String MSG_STAFF = "只有所有者和场长可以管理消毒计划";

    private final DisinfectRepository repository;
    private final FarmContext farmContext;

    private final MutableLiveData<String> statusFilter = new MutableLiveData<>(ALL);
    private final MutableLiveData<Map<String, Integer>> tabCounts = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<String> pauseReason = new MutableLiveData<>("");
    private final MutableLiveData<Long> busyPlanId = new MutableLiveData<>();
    private final MutableLiveData<Boolean> pausing = new MutableLiveData<>(false);

    private final SingleLiveEvent<DisinfectPlanVO> showPauseSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> closePauseSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openEdit = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openTasks = new SingleLiveEvent<>();

    @Nullable
    private DisinfectPlanVO pausePlan;

    @Inject
    public DisinfectPlanListViewModel(@NonNull DisinfectRepository repository, @NonNull FarmContext farmContext) {
        super(ALL);
        this.repository = repository;
        this.farmContext = farmContext;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        if (farmContext.isStaff()) {
            setUiState(UiState.noPermission(MSG_STAFF, ErrorCodes.ROLE_FORBIDDEN));
        }
    }

    // ===== 对外 =====

    public LiveData<String> getStatusFilter() { return statusFilter; }

    public LiveData<Map<String, Integer>> getTabCounts() { return tabCounts; }

    public LiveData<PagingData<DisinfectPlanVO>> getPlans() { return getPagingData(); }

    public MutableLiveData<String> getPauseReason() { return pauseReason; }

    public LiveData<Long> getBusyPlanId() { return busyPlanId; }

    public LiveData<Boolean> getPausing() { return pausing; }

    public SingleLiveEvent<DisinfectPlanVO> getShowPauseSheet() { return showPauseSheet; }

    public SingleLiveEvent<Void> getClosePauseSheet() { return closePauseSheet; }

    public SingleLiveEvent<Long> getOpenEdit() { return openEdit; }

    public SingleLiveEvent<Void> getOpenTasks() { return openTasks; }

    @Nullable
    public DisinfectPlanVO getPausePlan() { return pausePlan; }

    public boolean isStaff() { return farmContext.isStaff(); }

    // ===== 列表 =====

    @NonNull
    @Override
    protected PagingSource<Integer, DisinfectPlanVO> createPagingSource(@Nullable String filter) {
        return repository.planPager(filter == null ? ALL : filter, page -> tabCounts.postValue(page.getTabCounts()));
    }

    public void selectStatus(@NonNull String status) {
        statusFilter.setValue(status);
        setFilter(status);
    }

    /** “全部 3”“启用中 2”“已暂停 1”（tab_counts 键：all、active、paused） */
    public int countOf(@NonNull String status) {
        Map<String, Integer> m = tabCounts.getValue();
        Integer v = m == null ? null : m.get(status.toLowerCase(java.util.Locale.ROOT));
        return v == null ? 0 : v;
    }

    @NonNull
    public static String statusLabel(@NonNull String status) {
        return ACTIVE.equals(status) ? "启用中" : PAUSED.equals(status) ? "已暂停" : "全部";
    }

    /** 列表加载失败（含员工 40303）切换整页状态 */
    public void onListError(@NonNull Throwable t) {
        ApiException e = ApiException.from(t);
        if (ErrorCodes.isNoPermission(e.getCode())) {
            setUiState(UiState.noPermission(e.getMessage(), e.getCode()));
        }
    }

    // ===== 操作 =====

    public void onFabClick() {
        openEdit.setValue(null);
    }

    public void onEdit(@NonNull DisinfectPlanVO p) {
        openEdit.setValue(p.getId());
    }

    public void onTasksClick() {
        openTasks.call();
    }

    public void onPauseClick(@NonNull DisinfectPlanVO p) {
        pausePlan = p;
        pauseReason.setValue("");
        showPauseSheet.setValue(p);
    }

    /** API-D33 暂停：原因选填，不超过 30 字 */
    public void pause() {
        DisinfectPlanVO p = pausePlan;
        if (p == null || Boolean.TRUE.equals(pausing.getValue())) {
            return;
        }
        String reason = HealthFormRules.trimToNull(pauseReason.getValue());
        if (HealthFormRules.length(reason) > HealthFormRules.PAUSE_REASON_MAX) {
            showMessage("暂停原因最多 30 字");
            return;
        }
        DisinfectPlanPauseRequest req = new DisinfectPlanPauseRequest();
        req.reason = reason;
        req.version = p.getVersion();
        pausing.setValue(true);
        busyPlanId.setValue(p.getId());
        launch(repository.pausePlan(p.getId(), req), vo -> {
            pausing.setValue(false);
            busyPlanId.setValue(null);
            pausePlan = null;
            closePauseSheet.call();
            showMessage(vo.getToast() != null ? vo.getToast() : "已暂停计划，暂停期间不生成任务");
            refresh();
        }, e -> {
            pausing.setValue(false);
            busyPlanId.setValue(null);
            closePauseSheet.call();
            onActionError(e);
        });
    }

    /** API-D34 启用（不需要确认），提示“已启用计划，下次任务 10月20日” */
    public void resume(@NonNull DisinfectPlanVO p) {
        if (busyPlanId.getValue() != null) {
            return;
        }
        busyPlanId.setValue(p.getId());
        launch(repository.resumePlan(p.getId(), new VersionRequest(p.getVersion())), vo -> {
            busyPlanId.setValue(null);
            showMessage(vo.getToast() != null ? vo.getToast() : "已启用计划");
            refresh();
        }, e -> {
            busyPlanId.setValue(null);
            onActionError(e);
        });
    }

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
}
