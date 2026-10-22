package com.zhimu.slmp.farm.ui.transfer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.model.request.CreateTransferRequest;
import com.zhimu.slmp.farm.model.vo.TransferCandidateVO;
import com.zhimu.slmp.farm.model.vo.TransferPageVO;
import com.zhimu.slmp.farm.model.vo.TransferVO;
import com.zhimu.slmp.farm.repository.FarmRepository;
import com.zhimu.slmp.farm.ui.FarmUi;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * F10 转让所有权（模块02 技术文档 2.4.10）：API-F28；只能从本场场长中单选；发起 API-F29（幂等键）；
 * 待确认时显示横幅与“撤回转让”（API-F32）。
 */
public class TransferViewModel extends BaseViewModel {

    static final String MSG_TRANSFERRED = "已向%s发送转让请求，对方确认后生效";
    static final String MSG_CANCELED = "已撤回转让请求";

    private final FarmRepository repository;
    private final FarmContext farmContext;

    private long farmId;
    private final MutableLiveData<TransferPageVO> page = new MutableLiveData<>();
    private final MutableLiveData<List<TransferCandidateVO>> candidates = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> selectedMemberId = new MutableLiveData<>();
    private final MutableLiveData<String> selectedName = new MutableLiveData<>("");
    private final MutableLiveData<TransferVO> pending = new MutableLiveData<>();
    private final MutableLiveData<Boolean> emptyState = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);

    private final SingleLiveEvent<String> transferred = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> canceled = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openMembers = new SingleLiveEvent<>();

    @Nullable
    private String idemKey;

    @Inject
    public TransferViewModel(@NonNull FarmRepository repository, @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.farmContext = farmContext;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = handle.get(RouterPath.EXTRA_FARM_ID);
        farmId = id != null && id > 0 ? id : farmContext.farmIdOrZero();
        if (FarmUi.deniedByContext(farmContext, farmId, farmContext.permissions().isCanTransfer())) {
            setUiState(FarmUi.noPermissionState());
            return;
        }
        load();
    }

    public void load() {
        load(repository.getTransferPage(farmId), this::apply);
    }

    @Override
    public void retry() {
        load();
    }

    private void apply(@NonNull TransferPageVO p) {
        page.setValue(p);
        List<TransferCandidateVO> list = p.getCandidates();
        candidates.setValue(list);
        TransferVO pend = p.getPending() != null && p.getPending().isPending() ? p.getPending() : null;
        pending.setValue(pend);
        emptyState.setValue(list.isEmpty() && pend == null);
        Long current = selectedMemberId.getValue();
        if (pend != null) {
            select(pend.getToMemberId());
        } else if (list.size() == 1) {
            select(list.get(0).getMemberId());
        } else if (current != null) {
            select(current);
        } else {
            selectedName.setValue("");
        }
    }

    public void select(long memberId) {
        if (pending.getValue() != null && pending.getValue().getToMemberId() != memberId) {
            return;
        }
        selectedMemberId.setValue(memberId);
        idemKey = null;
        String name = "";
        List<TransferCandidateVO> list = candidates.getValue();
        if (list != null) {
            for (TransferCandidateVO c : list) {
                if (c.getMemberId() == memberId && c.getName() != null) {
                    name = c.getName();
                }
            }
        }
        if (name.isEmpty() && pending.getValue() != null && pending.getValue().getToName() != null) {
            name = pending.getValue().getToName();
        }
        selectedName.setValue(name);
    }

    public void confirm() {
        Long id = selectedMemberId.getValue();
        if (id == null || pending.getValue() != null || Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        if (idemKey == null) {
            idemKey = IdemKey.newKey();
        }
        String name = selectedName.getValue();
        submitting.setValue(true);
        launch(repository.createTransfer(farmId, new CreateTransferRequest(id), idemKey), vo -> {
            submitting.setValue(false);
            idemKey = null;
            pending.setValue(vo);
            transferred.setValue(String.format(MSG_TRANSFERRED, vo.getToName() != null ? vo.getToName() : name));
        }, this::onActionError);
    }

    public void cancelTransfer() {
        TransferVO p = pending.getValue();
        if (p == null || Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        submitting.setValue(true);
        launch(repository.cancelTransfer(p.getTransferId()), vo -> {
            submitting.setValue(false);
            pending.setValue(null);
            showMessage(MSG_CANCELED);
            canceled.call();
            load();
        }, this::onActionError);
    }

    private void onActionError(@NonNull ApiException e) {
        submitting.setValue(false);
        int code = e.getCode();
        if (code == ErrorCodes.STATE_CHANGED || code == ErrorCodes.NAME_DUPLICATE) {
            showMessage(e.getMessage());
            if (code == ErrorCodes.STATE_CHANGED) {
                load();
            }
        } else {
            handleError(e);
        }
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        if (page.getValue() == null) {
            super.onNoPermission(e);
        } else {
            showMessage(e.getMessage());
        }
    }

    public void onGoMembersClick() {
        openMembers.call();
    }

    public long getFarmId() {
        return farmId;
    }

    // ===== 对外 =====

    public LiveData<TransferPageVO> getPage() {
        return page;
    }

    public LiveData<List<TransferCandidateVO>> getCandidates() {
        return candidates;
    }

    public LiveData<Long> getSelectedMemberId() {
        return selectedMemberId;
    }

    public LiveData<String> getSelectedName() {
        return selectedName;
    }

    public LiveData<TransferVO> getPending() {
        return pending;
    }

    public LiveData<Boolean> getEmptyState() {
        return emptyState;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public SingleLiveEvent<String> getTransferred() {
        return transferred;
    }

    public SingleLiveEvent<Void> getCanceled() {
        return canceled;
    }

    public SingleLiveEvent<Void> getOpenMembers() {
        return openMembers;
    }
}
