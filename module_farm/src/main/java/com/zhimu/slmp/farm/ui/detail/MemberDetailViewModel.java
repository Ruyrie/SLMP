package com.zhimu.slmp.farm.ui.detail;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.model.vo.PenBriefVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.model.request.UpdateMemberPensRequest;
import com.zhimu.slmp.farm.model.request.UpdateMemberRoleRequest;
import com.zhimu.slmp.farm.model.vo.MemberChangeResultVO;
import com.zhimu.slmp.farm.model.vo.MemberDetailVO;
import com.zhimu.slmp.farm.model.vo.MemberRemoveResultVO;
import com.zhimu.slmp.farm.model.vo.PenOptionVO;
import com.zhimu.slmp.farm.model.vo.RemovalImpactVO;
import com.zhimu.slmp.farm.repository.FarmRepository;
import com.zhimu.slmp.farm.repository.MemberRepository;
import com.zhimu.slmp.farm.service.FarmContextServiceImpl;
import com.zhimu.slmp.farm.ui.FarmUi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * F09 成员详情（模块02 技术文档 2.4.9、2.6.4）：API-F17；修改角色 F23、调整栏舍 F18 + F24、移出 F25 + F26、退出 F27。
 * 按钮显隐只取 MemberDetailVO.actions。
 */
public class MemberDetailViewModel extends BaseViewModel {

    /** Snackbar“分配栏舍”的操作编号 */
    public static final String ACTION_ASSIGN_PENS = "assign_pens";

    private final MemberRepository repository;
    private final FarmRepository farmRepository;
    private final FarmContextServiceImpl contextService;
    private final FarmContext farmContext;

    private long farmId;
    private long memberId;

    private final MutableLiveData<MemberDetailVO> member = new MutableLiveData<>();
    private final MutableLiveData<String> roleDraft = new MutableLiveData<>();
    private final MutableLiveData<List<PenOptionVO>> penOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Set<Long>> penDraft = new MutableLiveData<>(Collections.emptySet());
    private final MutableLiveData<String> penWarning = new MutableLiveData<>();
    private final MutableLiveData<RemovalImpactVO> impact = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);

    private final SingleLiveEvent<Void> showRoleSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showPenSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> sheetDone = new SingleLiveEvent<>();
    private final SingleLiveEvent<UiMessage> roleChanged = new SingleLiveEvent<>();
    private final SingleLiveEvent<RemovalImpactVO> showRemoveDialog = new SingleLiveEvent<>();
    private final SingleLiveEvent<MemberRemoveResultVO> removed = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> dial = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> quitDone = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> gone = new SingleLiveEvent<>();

    /** 本页改过成员信息（返回 F07 时刷新） */
    private boolean changed;
    /** 改动后没有负责员工的栏舍（返回 F07 显示横幅） */
    private final List<String> unassigned = new ArrayList<>();

    @Inject
    public MemberDetailViewModel(@NonNull MemberRepository repository, @NonNull FarmRepository farmRepository,
                                 @NonNull FarmContextServiceImpl contextService, @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.farmRepository = farmRepository;
        this.contextService = contextService;
        this.farmContext = farmContext;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long fid = handle.get(RouterPath.EXTRA_FARM_ID);
        farmId = fid != null && fid > 0 ? fid : farmContext.farmIdOrZero();
        Long mid = handle.get(RouterPath.EXTRA_MEMBER_ID);
        memberId = mid == null ? 0 : mid;
        if (FarmUi.deniedByContext(farmContext, farmId, farmContext.permissions().isCanViewMembers())) {
            setUiState(FarmUi.noPermissionState());
            return;
        }
        load();
    }

    public void load() {
        load(repository.getMemberDetail(farmId, memberId), member::setValue);
    }

    @Override
    public void retry() {
        load();
    }

    /** 40401“该成员已不在东山猪场”：返回 F07 刷新 */
    @Override
    protected void applyErrorState(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.NOT_FOUND) {
            gone.setValue(e.getMessage());
            return;
        }
        super.applyErrorState(e);
    }

    private void onActionError(@NonNull ApiException e) {
        saving.setValue(false);
        int code = e.getCode();
        if (code == ErrorCodes.NOT_FOUND) {
            gone.setValue(e.getMessage());
        } else if (ErrorCodes.isNoPermission(code)) {
            sheetDone.call();
            showMessage("你没有这个操作的权限");
            launch(contextService.refreshContext(), this::load, err -> load());
        } else {
            if (ErrorCodes.isDataChanged(code)) {
                sheetDone.call();
            }
            handleError(e);
        }
    }

    // ===== 修改角色 =====

    public void openRoleSheet() {
        MemberDetailVO m = member.getValue();
        if (m == null || !m.getActions().isCanChangeRole()) {
            return;
        }
        roleDraft.setValue(m.getRole());
        showRoleSheet.call();
    }

    public void onRoleSelected(@NonNull String role) {
        roleDraft.setValue(role);
    }

    public void saveRole() {
        MemberDetailVO m = member.getValue();
        String role = roleDraft.getValue();
        if (m == null || role == null || Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        if (role.equals(m.getRole())) {
            sheetDone.call();
            return;
        }
        saving.setValue(true);
        launch(repository.updateRole(farmId, memberId, new UpdateMemberRoleRequest(role, m.getVersion())), r -> {
            saving.setValue(false);
            applyChange(r);
            sheetDone.call();
            String name = m.getName() == null ? "" : m.getName();
            if (FarmContextVO.ROLE_STAFF.equals(role)) {
                roleChanged.setValue(UiMessage.withAction("已把" + name + "改为员工", "分配栏舍", ACTION_ASSIGN_PENS));
            } else {
                roleChanged.setValue(UiMessage.of("已把" + name + "改为场长"));
            }
        }, this::onActionError);
    }

    // ===== 调整栏舍 =====

    public void openPenSheet() {
        MemberDetailVO m = member.getValue();
        if (m == null || Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        saving.setValue(true);
        launch(repository.getPenOptions(farmId, memberId), list -> {
            saving.setValue(false);
            penOptions.setValue(list);
            Set<Long> ids = new LinkedHashSet<>();
            for (PenOptionVO o : list) {
                if (o.isSelected()) {
                    ids.add(o.getPenId());
                }
            }
            penDraft.setValue(ids);
            penWarning.setValue(null);
            showPenSheet.call();
        }, this::onActionError);
    }

    public void togglePen(long penId) {
        Set<Long> ids = new LinkedHashSet<>(currentDraft());
        if (!ids.remove(penId)) {
            ids.add(penId);
        }
        penDraft.setValue(ids);
        penWarning.setValue(warningOf(penOptions.getValue(), ids));
    }

    /** 原本负责、被取消、且没有其他负责员工的栏舍：“取消后 3 号育肥舍将没有负责员工……” */
    @Nullable
    static String warningOf(@Nullable List<PenOptionVO> options, @NonNull Set<Long> draft) {
        if (options == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (PenOptionVO o : options) {
            if (o.isSelected() && !draft.contains(o.getPenId()) && o.getOtherStaffCount() == 0 && o.getName() != null) {
                names.add(o.getName());
            }
        }
        return names.isEmpty() ? null
                : "取消后 " + FarmUi.joinNames(names) + "将没有负责员工，提醒和待办改发给所有者和场长";
    }

    public void savePens() {
        MemberDetailVO m = member.getValue();
        if (m == null || Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        List<PenOptionVO> options = penOptions.getValue();
        Set<Long> draft = currentDraft();
        if (options != null) {
            for (PenOptionVO o : options) {
                if (draft.contains(o.getPenId())) {
                    ids.add(o.getPenId());
                }
            }
        }
        saving.setValue(true);
        launch(repository.updatePens(farmId, memberId, new UpdateMemberPensRequest(ids, m.getVersion())), r -> {
            saving.setValue(false);
            applyChange(r);
            sheetDone.call();
            showMessage("已更新" + (m.getName() == null ? "" : m.getName()) + "的负责栏舍");
        }, this::onActionError);
    }

    @NonNull
    private Set<Long> currentDraft() {
        Set<Long> s = penDraft.getValue();
        return s == null ? Collections.emptySet() : s;
    }

    private void applyChange(@NonNull MemberChangeResultVO r) {
        changed = true;
        if (r.getMember() != null) {
            member.setValue(r.getMember());
        }
        for (PenBriefVO p : r.getNewlyUnassignedPens()) {
            if (p.getName() != null && !unassigned.contains(p.getName())) {
                unassigned.add(p.getName());
            }
        }
    }

    // ===== 移出与退出 =====

    public void prepareRemove() {
        MemberDetailVO m = member.getValue();
        if (m == null || !m.getActions().isCanRemove() || Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        saving.setValue(true);
        launch(repository.getRemovalImpact(farmId, memberId), i -> {
            saving.setValue(false);
            impact.setValue(i);
            showRemoveDialog.setValue(i);
        }, this::onActionError);
    }

    public void remove() {
        MemberDetailVO m = member.getValue();
        if (m == null || Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        saving.setValue(true);
        launch(repository.removeMember(farmId, memberId, m.getVersion()), r -> {
            saving.setValue(false);
            changed = true;
            for (PenBriefVO p : r.getUnassignedPens()) {
                if (p.getName() != null && !unassigned.contains(p.getName())) {
                    unassigned.add(p.getName());
                }
            }
            removed.setValue(r);
        }, this::onActionError);
    }

    public void quit() {
        if (Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        MemberDetailVO m = member.getValue();
        String farmName = m == null || m.getFarmName() == null ? "养殖场" : m.getFarmName();
        saving.setValue(true);
        launch(farmRepository.quitFarm(farmId).andThen(contextService.selectNext(farmId)), opt -> {
            saving.setValue(false);
            quitDone.setValue("你已退出" + farmName);
        }, e -> {
            saving.setValue(false);
            if (e.getCode() == ErrorCodes.ROLE_FORBIDDEN) {
                showMessage(e.getMessage());
            } else {
                handleError(e);
            }
        });
    }

    public void onCallClick() {
        MemberDetailVO m = member.getValue();
        if (m != null && m.getPhone() != null) {
            dial.setValue(m.getPhone());
        }
    }

    public boolean isChanged() {
        return changed;
    }

    @NonNull
    public List<String> getUnassigned() {
        return unassigned;
    }

    // ===== 对外 =====

    public LiveData<MemberDetailVO> getMember() {
        return member;
    }

    public LiveData<String> getRoleDraft() {
        return roleDraft;
    }

    public LiveData<List<PenOptionVO>> getPenOptions() {
        return penOptions;
    }

    public LiveData<Set<Long>> getPenDraft() {
        return penDraft;
    }

    public LiveData<String> getPenWarning() {
        return penWarning;
    }

    public LiveData<RemovalImpactVO> getImpact() {
        return impact;
    }

    public LiveData<Boolean> getSaving() {
        return saving;
    }

    public SingleLiveEvent<Void> getShowRoleSheet() {
        return showRoleSheet;
    }

    public SingleLiveEvent<Void> getShowPenSheet() {
        return showPenSheet;
    }

    public SingleLiveEvent<Void> getSheetDone() {
        return sheetDone;
    }

    public SingleLiveEvent<UiMessage> getRoleChanged() {
        return roleChanged;
    }

    public SingleLiveEvent<RemovalImpactVO> getShowRemoveDialog() {
        return showRemoveDialog;
    }

    public SingleLiveEvent<MemberRemoveResultVO> getRemoved() {
        return removed;
    }

    public SingleLiveEvent<String> getDial() {
        return dial;
    }

    public SingleLiveEvent<String> getQuitDone() {
        return quitDone;
    }

    public SingleLiveEvent<String> getGone() {
        return gone;
    }
}
