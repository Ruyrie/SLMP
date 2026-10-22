package com.zhimu.slmp.farm.ui.audit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.model.vo.FarmAuditVO;
import com.zhimu.slmp.farm.repository.FarmRepository;
import com.zhimu.slmp.farm.service.FarmContextServiceImpl;
import com.zhimu.slmp.farm.ui.FarmUi;

import javax.inject.Inject;

/**
 * F03 审核结果（模块02 技术文档 2.4.3）：API-F06；所有者可修改注册信息（F02 EDIT）或删除未通过审核的养殖场（API-F08）。
 * 已通过（F06 返回 NORMAL）时切换到该养殖场并打开 F05。
 */
public class FarmAuditViewModel extends BaseViewModel {

    static final String TEXT_EDIT_RESUBMIT = "修改并重新提交";
    static final String TEXT_EDIT = "修改注册信息";

    private final FarmRepository repository;
    private final FarmContextServiceImpl contextService;
    private final FarmContext farmContext;

    private long farmId;

    private final MutableLiveData<FarmAuditVO> audit = new MutableLiveData<>();
    private final MutableLiveData<String> timeLine = new MutableLiveData<>("");
    private final MutableLiveData<String> primaryText = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> deleting = new MutableLiveData<>(false);

    private final SingleLiveEvent<Long> openEdit = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> showDeleteDialog = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> deleted = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> approved = new SingleLiveEvent<>();
    private final SingleLiveEvent<Integer> openViewer = new SingleLiveEvent<>();

    @Inject
    public FarmAuditViewModel(@NonNull FarmRepository repository, @NonNull FarmContextServiceImpl contextService,
                              @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.contextService = contextService;
        this.farmContext = farmContext;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = handle.get(RouterPath.EXTRA_FARM_ID);
        farmId = id != null ? id : farmContext.farmIdOrZero();
        load();
    }

    public void load() {
        load(repository.getAudit(farmId), this::apply);
    }

    @Override
    public void retry() {
        load();
    }

    private void apply(@NonNull FarmAuditVO vo) {
        audit.setValue(vo);
        String status = vo.getStatus();
        if (FarmContextVO.STATUS_REJECTED.equals(status)) {
            timeLine.setValue(FarmUi.monthDay(submittedOf(vo)) + " 提交 · " + FarmUi.monthDayTime(vo.getAuditedAt())
                    + " 驳回");
            primaryText.setValue(TEXT_EDIT_RESUBMIT);
        } else if (FarmContextVO.STATUS_PENDING.equals(status)) {
            timeLine.setValue(FarmUi.monthDay(submittedOf(vo)) + " 提交");
            primaryText.setValue(TEXT_EDIT);
        } else {
            timeLine.setValue("");
            primaryText.setValue("");
            if (FarmContextVO.STATUS_NORMAL.equals(status) || FarmContextVO.STATUS_DISABLED.equals(status)) {
                // 已通过：切换到该养殖场后打开 F05
                launch(contextService.switchTo(farmId), ctx -> approved.setValue(farmId),
                        e -> approved.setValue(farmId));
            }
        }
    }

    @Nullable
    private static String submittedOf(@NonNull FarmAuditVO vo) {
        return vo.getSubmittedAt() != null ? vo.getSubmittedAt() : vo.getFirstSubmittedAt();
    }

    public void onEditClick() {
        openEdit.setValue(farmId);
    }

    public void onDeleteMenuClick() {
        FarmAuditVO vo = audit.getValue();
        showDeleteDialog.setValue(vo == null ? null : vo.getName());
    }

    public void onQualClick(int index) {
        openViewer.setValue(index);
    }

    /** 删除确认后调用 API-F08；41001 视为已删除；是当前养殖场时按 2.6.1 重定 */
    public void delete() {
        if (Boolean.TRUE.equals(deleting.getValue())) {
            return;
        }
        deleting.setValue(true);
        launch(repository.deleteFarm(farmId), this::afterDeleted, e -> {
            if (e.getCode() == ErrorCodes.RECORD_DELETED) {
                afterDeleted();
                return;
            }
            deleting.setValue(false);
            if (e.getCode() == ErrorCodes.FARM_STATUS_FORBIDDEN) {
                showMessage(e.getMessage());
                load();
            } else {
                handleError(e);
            }
        });
    }

    private void afterDeleted() {
        FarmAuditVO vo = audit.getValue();
        String name = vo == null ? null : vo.getName();
        Long current = farmContext.currentFarmId();
        if (current != null && current == farmId) {
            launch(contextService.selectNext(farmId), opt -> finishDeleted(name), e -> finishDeleted(name));
        } else {
            finishDeleted(name);
        }
    }

    private void finishDeleted(@Nullable String name) {
        deleting.setValue(false);
        deleted.setValue(name == null ? "" : name);
    }

    /** 删除时被收回权限：只提示 */
    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        if (audit.getValue() == null) {
            super.onNoPermission(e);
        } else {
            showMessage(e.getMessage());
        }
    }

    public long getFarmId() {
        return farmId;
    }

    public LiveData<FarmAuditVO> getAudit() {
        return audit;
    }

    public LiveData<String> getTimeLine() {
        return timeLine;
    }

    public LiveData<String> getPrimaryText() {
        return primaryText;
    }

    public LiveData<Boolean> getDeleting() {
        return deleting;
    }

    public SingleLiveEvent<Long> getOpenEdit() {
        return openEdit;
    }

    public SingleLiveEvent<String> getShowDeleteDialog() {
        return showDeleteDialog;
    }

    public SingleLiveEvent<String> getDeleted() {
        return deleted;
    }

    public SingleLiveEvent<Long> getApproved() {
        return approved;
    }

    public SingleLiveEvent<Integer> getOpenViewer() {
        return openViewer;
    }
}
