package com.zhimu.slmp.health.ui.medicationarchive;

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
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.ui.change.model.request.ChangeRequestSubmitRequest;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.data.HealthSharedRepository;
import com.zhimu.slmp.health.data.MedicationRepository;
import com.zhimu.slmp.health.model.request.NotifyManagerRequest;
import com.zhimu.slmp.health.model.vo.DeleteImpactVO;
import com.zhimu.slmp.health.model.vo.MedicationRecordVO;
import com.zhimu.slmp.health.model.vo.MedicationTargetVO;
import com.zhimu.slmp.health.model.vo.WithdrawalVO;
import com.zhimu.slmp.health.ui.common.RecordLogHost;
import com.zhimu.slmp.health.util.HealthFormRules;
import com.zhimu.slmp.health.util.HealthFormat;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.network.paging.PagedSource;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * D05 用药档案：休药状态卡（API-D17）、按药品计算、用药记录（API-D18 分页）。删除先判定影响（API-D25）再分派：
 * 养殖户 NO_CHANGE_TODAY 直接删除（API-D26）；ADVANCE / NO_CHANGE_EARLIER / LOCKED 填写申请原因（200 字）提交修改申请（API-U01 → U02）；
 * 员工只能“通知场长”（API-D27）。
 */
public class MedicationArchiveViewModel extends BaseListViewModel<Long, MedicationRecordVO> implements RecordLogHost {

    public static final String IMPACT_ADVANCE = "ADVANCE";
    public static final String IMPACT_NO_CHANGE_TODAY = "NO_CHANGE_TODAY";
    public static final String IMPACT_NO_CHANGE_EARLIER = "NO_CHANGE_EARLIER";
    public static final String IMPACT_LOCKED = "LOCKED";
    public static final String CHANGE_DELETE = "DELETE";

    private final MedicationRepository repository;
    private final HealthSharedRepository shared;
    private final FarmContext farmContext;

    private final MutableLiveData<WithdrawalVO> withdrawal = new MutableLiveData<>();
    private final MutableLiveData<DeleteImpactVO> deleteImpact = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isManager = new MutableLiveData<>(false);
    private final MutableLiveData<String> deleteReason = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<List<RecordLogVO>> logs = new MutableLiveData<>();
    private final MutableLiveData<Integer> recordTotal = new MutableLiveData<>();

    private final SingleLiveEvent<DeleteImpactVO> showDeleteConfirm = new SingleLiveEvent<>();
    private final SingleLiveEvent<DeleteImpactVO> showDeleteRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<DeleteImpactVO> showStaffRestricted = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> closeDeleteRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openEdit = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openRegister = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showLogs = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showTip = new SingleLiveEvent<>();
    private final SingleLiveEvent<List<MedicationTargetVO>> pickBatch = new SingleLiveEvent<>();

    @Nullable
    private Long batchId;
    /** 正在删除的记录（对话框结果回来后使用） */
    @Nullable
    private MedicationRecordVO pendingRecord;
    @Nullable
    private String requestKey;
    @Nullable
    private List<MedicationTargetVO> batchOptions;

    @Inject
    public MedicationArchiveViewModel(@NonNull MedicationRepository repository, @NonNull HealthSharedRepository shared,
                                      @NonNull FarmContext farmContext) {
        super(null);
        this.repository = repository;
        this.shared = shared;
        this.farmContext = farmContext;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        isManager.setValue(farmContext.isOwnerOrManager());
        Object b = handle.get(RouterPath.EXTRA_BATCH_ID);
        Long id = b instanceof Number ? Long.valueOf(((Number) b).longValue()) : null;
        if (id != null) {
            start(id);
        } else {
            requestBatchPick();
        }
    }

    // ===== 对外 =====

    public LiveData<WithdrawalVO> getWithdrawal() { return withdrawal; }

    public LiveData<PagingData<MedicationRecordVO>> getRecords() { return getPagingData(); }

    public LiveData<DeleteImpactVO> getDeleteImpact() { return deleteImpact; }

    /** 用药记录总数（“用药记录 · 2 条”） */
    public LiveData<Integer> getRecordTotal() { return recordTotal; }

    public LiveData<Boolean> getIsManager() { return isManager; }

    public MutableLiveData<String> getDeleteReason() { return deleteReason; }

    public LiveData<Boolean> getSubmitting() { return submitting; }

    @Override
    public LiveData<List<RecordLogVO>> getLogs() { return logs; }

    public SingleLiveEvent<DeleteImpactVO> getShowDeleteConfirm() { return showDeleteConfirm; }

    public SingleLiveEvent<DeleteImpactVO> getShowDeleteRequest() { return showDeleteRequest; }

    public SingleLiveEvent<DeleteImpactVO> getShowStaffRestricted() { return showStaffRestricted; }

    public SingleLiveEvent<Void> getCloseDeleteRequest() { return closeDeleteRequest; }

    public SingleLiveEvent<Long> getOpenEdit() { return openEdit; }

    public SingleLiveEvent<Long> getOpenRegister() { return openRegister; }

    public SingleLiveEvent<Long> getOpenChangeRequest() { return openChangeRequest; }

    public SingleLiveEvent<Long> getOpenRequest() { return openRequest; }

    public SingleLiveEvent<Void> getShowLogs() { return showLogs; }

    public SingleLiveEvent<Void> getShowTip() { return showTip; }

    public SingleLiveEvent<List<MedicationTargetVO>> getPickBatch() { return pickBatch; }

    public boolean isStaff() { return farmContext.isStaff(); }

    @Nullable
    public MedicationRecordVO getPendingRecord() { return pendingRecord; }

    // ===== 加载 =====

    public void start(long id) {
        batchId = id;
        setFilter(id);
        loadWithdrawal();
    }

    private void loadWithdrawal() {
        if (batchId != null) {
            load(repository.getWithdrawal(batchId), withdrawal::setValue);
        }
    }

    @Override
    public void refresh() {
        loadWithdrawal();
        super.refresh();
    }

    @Override
    public void retry() {
        if (batchId == null) {
            requestBatchPick();
            return;
        }
        resetContentLoaded();
        loadWithdrawal();
        super.refresh();
    }

    @NonNull
    @Override
    protected PagingSource<Integer, MedicationRecordVO> createPagingSource(@Nullable Long filter) {
        if (filter == null) {
            return new PagedSource<>((page, size) -> Single.just(new PageVO<MedicationRecordVO>()));
        }
        return repository.recordPager(filter, page -> recordTotal.postValue(page.getTotal()));
    }

    /** 没有 batch_id 时先选批次（生产页“用药”入口） */
    public void requestBatchPick() {
        if (batchOptions != null) {
            showBatchPick(batchOptions);
            return;
        }
        setUiState(UiState.loading());
        launch(repository.getTargets(MedicationRepository.TARGET_BATCH, null), list -> {
            batchOptions = list;
            showBatchPick(list);
        }, this::applyErrorState);
    }

    private void showBatchPick(@NonNull List<MedicationTargetVO> list) {
        if (list.size() == 1 && list.get(0).getBatchId() != null) {
            start(list.get(0).getBatchId());
            return;
        }
        setUiState(UiState.empty("请选择批次", list.isEmpty() ? "当前养殖场没有在养批次。" : "选择一个在养批次查看用药档案。"));
        if (!list.isEmpty()) {
            pickBatch.setValue(list);
        }
    }

    // ===== 列表操作 =====

    public void onFabClick() {
        openRegister.setValue(batchId);
    }

    public void onInfoClick() {
        showTip.call();
    }

    /** 记录菜单：EDIT、DELETE、CHANGE_REQUEST、VIEW_REQUEST、VIEW_LOG */
    public void onRecordAction(@NonNull MedicationRecordVO r, @NonNull String action) {
        switch (action) {
            case HealthExtras.ACTION_EDIT:
                openEdit.setValue(r.getId());
                break;
            case HealthExtras.ACTION_DELETE:
                requestDelete(r);
                break;
            case HealthExtras.ACTION_CHANGE_REQUEST:
                openChangeRequest.setValue(r.getId());
                break;
            case HealthExtras.ACTION_VIEW_REQUEST:
                openRequest.setValue(r.getChangeState() == null ? null : r.getChangeState().getPendingRequestId());
                break;
            case HealthExtras.ACTION_VIEW_LOG:
                loadLogs(r);
                showLogs.call();
                break;
            default:
                break;
        }
    }

    /** 菜单“删除”：先判定影响（API-D25），再按角色与影响弹出对应对话框 */
    public void requestDelete(@NonNull MedicationRecordVO r) {
        pendingRecord = r;
        launch(repository.getDeleteImpact(r.getId()), impact -> {
            deleteImpact.setValue(impact);
            dispatchDelete(impact);
        }, this::onActionError);
    }

    /** 养殖户 NO_CHANGE_TODAY 普通确认；其他影响填写申请原因；员工“不能直接删除” */
    void dispatchDelete(@NonNull DeleteImpactVO impact) {
        if (isStaff()) {
            showStaffRestricted.setValue(impact);
        } else if (IMPACT_NO_CHANGE_TODAY.equals(impact.getImpact())) {
            showDeleteConfirm.setValue(impact);
        } else {
            deleteReason.setValue("");
            requestKey = null;
            showDeleteRequest.setValue(impact);
        }
    }

    /** “删除后可出栏日期不变，仍为 10月29日 周四。” */
    @NonNull
    public String deleteConfirmMessage(@NonNull DeleteImpactVO impact) {
        WithdrawalVO w = withdrawal.getValue();
        String date = impact.getReleaseDateBefore() != null ? impact.getReleaseDateBefore()
                : w == null ? null : w.getReleaseDate();
        return date == null ? "删除后可出栏日期不变。" : "删除后可出栏日期不变，仍为 " + HealthFormat.dateWeek(date) + "。";
    }

    /** 提交修改申请对话框的后果说明（图 8-8） */
    @NonNull
    public static String deleteRequestMessage(@NonNull DeleteImpactVO impact) {
        if (IMPACT_ADVANCE.equals(impact.getImpact())) {
            return "这项修改会使可出栏日期提前（" + HealthFormat.date(impact.getReleaseDateBefore()) + HealthFormat.ARROW
                    + HealthFormat.date(impact.getReleaseDateAfter()) + "），需要提交修改申请，由管理员审核。审核通过前，记录和可出栏日期保持不变。";
        }
        if (IMPACT_LOCKED.equals(impact.getImpact())) {
            return "记录已锁定，需要提交修改申请，由管理员审核。审核通过前，记录保持不变。";
        }
        return "删除会影响库存和批次成本，需要提交修改申请，由管理员审核。审核通过前，记录保持不变。";
    }

    /** 员工删除受限对话框（图 8-9）的说明 */
    @NonNull
    public static String staffRestrictedMessage(@NonNull DeleteImpactVO impact) {
        String who = notifyWho(impact);
        String name = HealthFormat.nonNull(impact.getNotifyName());
        if (IMPACT_ADVANCE.equals(impact.getImpact())) {
            return "删除会使可出栏日期提前（" + HealthFormat.date(impact.getReleaseDateBefore()) + HealthFormat.ARROW
                    + HealthFormat.date(impact.getReleaseDateAfter()) + "），需要由所有者或场长提交修改申请。你可以通知" + who
                    + name + "处理。";
        }
        return "删除用药记录需要由所有者或场长提交修改申请。你可以通知" + who + name + "处理。";
    }

    /** 本场没有场长时通知所有者 */
    @NonNull
    public static String notifyWho(@NonNull DeleteImpactVO impact) {
        return "OWNER".equals(impact.getNotifyRole()) ? "所有者" : "场长";
    }

    /** 普通确认“删除”：API-D26 */
    public void deleteDirectly() {
        MedicationRecordVO r = pendingRecord;
        if (r == null) {
            return;
        }
        launch(repository.delete(r.getId(), r.getVersion()), toast -> {
            pendingRecord = null;
            showMessage(toast.isEmpty() ? "已删除用药记录" : toast);
            refresh();
        }, this::onActionError);
    }

    /**
     * 删除申请对话框“提交修改申请”：原因 1–200 字；API-U01 取 record_version，再 API-U02（Idempotency-Key）。
     *
     * @return 原因未通过校验时返回错误文案（不发请求）
     */
    @Nullable
    public String submitDeleteRequest(@Nullable String reason) {
        MedicationRecordVO r = pendingRecord;
        String error = HealthFormRules.changeReason(reason);
        if (error != null) {
            showMessage(error);
            return error;
        }
        if (r == null || Boolean.TRUE.equals(submitting.getValue())) {
            return null;
        }
        String text = reason == null ? "" : reason.trim();
        deleteReason.setValue(text);
        if (requestKey == null) {
            requestKey = IdemKey.newKey();
        }
        final String key = requestKey;
        submitting.setValue(true);
        launch(shared.getChangeRequestForm(HealthExtras.BIZ_MEDICATION_RECORD, r.getId())
                .flatMap(form -> shared.submitChangeRequest(new ChangeRequestSubmitRequest(
                        HealthExtras.BIZ_MEDICATION_RECORD, r.getId(), CHANGE_DELETE, null, null,
                        form.getRecordVersion() != null ? form.getRecordVersion() : String.valueOf(r.getVersion()),
                        text, null), key)), vo -> {
            submitting.setValue(false);
            requestKey = null;
            pendingRecord = null;
            closeDeleteRequest.call();
            showMessage(UiMessage.withPage("已提交修改申请，审核结果会通过消息通知你", "查看", RouterPath.U02));
            refresh();
        }, e -> {
            submitting.setValue(false);
            if (e.getCode() == ErrorCodes.PENDING_EXISTS || ErrorCodes.isDataChanged(e.getCode())) {
                closeDeleteRequest.call();
                showMessage(e.getMessage());
                refresh();
                return;
            }
            showMessage(e.isNetwork() ? ApiException.MSG_NETWORK : e.getMessage());
        });
        return null;
    }

    public void cancelDelete() {
        pendingRecord = null;
        requestKey = null;
    }

    /** 员工“通知场长”（API-D27，action = DELETE）：“已通知刘芳”；10 分钟内重复返回 42901 */
    public void notifyManager() {
        MedicationRecordVO r = pendingRecord;
        if (r == null) {
            return;
        }
        launch(repository.notifyManager(r.getId(), new NotifyManagerRequest(CHANGE_DELETE)), names -> {
            pendingRecord = null;
            showMessage("已通知" + (names == null || names.isEmpty() ? "场长" : String.join("、", names)));
        }, e -> showMessage(e.getMessage()));
    }

    /** 弹窗内的操作失败：无权限只提示，数据已变化提示后刷新 */
    private void onActionError(@NonNull ApiException e) {
        if (ErrorCodes.isNoPermission(e.getCode()) || e.getCode() == ErrorCodes.CHANGE_REQUEST_REQUIRED
                || e.getCode() == ErrorCodes.PENDING_EXISTS) {
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

    public void loadLogs(@NonNull MedicationRecordVO r) {
        logs.setValue(null);
        launch(shared.getRecordLogs(HealthExtras.BIZ_MEDICATION_RECORD, r.getId()), logs::setValue,
                e -> logs.setValue(Collections.emptyList()));
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }
}
