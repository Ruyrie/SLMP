package com.zhimu.slmp.farm.ui.invite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.Gson;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.model.vo.PenBriefVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.session.AppSettings;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.model.request.InviteMemberRequest;
import com.zhimu.slmp.farm.model.vo.InviteItemVO;
import com.zhimu.slmp.farm.model.vo.PenOptionVO;
import com.zhimu.slmp.farm.repository.MemberRepository;
import com.zhimu.slmp.farm.ui.FarmUi;
import com.zhimu.slmp.farm.util.FarmRules;
import com.zhimu.slmp.farm.util.PhoneTextWatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

/**
 * F08 邀请成员（模块02 技术文档 2.4.8）：打开即调 API-F18（不带 member_id）取可分配栏舍；选“场长”不提交 pen_ids；
 * 前端只校验手机号格式与本人，已是成员、已有邀请由 40001 返回；提交 API-F20（重新邀请带 resend_of_id）。
 */
public class InviteMemberViewModel extends BaseViewModel {

    public static final String ROLE_MANAGER = FarmContextVO.ROLE_MANAGER;
    public static final String ROLE_STAFF = FarmContextVO.ROLE_STAFF;
    public static final String F_PHONE = "invitee_phone";

    static final String MSG_PHONE_EMPTY = "请输入对方的手机号";
    static final String MSG_SELF = "不能邀请你自己";
    static final String MSG_DONE = "已发送邀请，对方同意后加入";
    static final String MSG_STALE = "这条邀请已处理，列表已刷新";
    static final String HINT_SELECTED = "可多选，已选 %d 个；一个栏舍可以有多名负责员工";
    static final String HINT_NONE = "还没有选择负责栏舍。对方加入后看不到任何栏舍，可以之后在成员详情中分配。";

    private final MemberRepository repository;
    private final FarmContext farmContext;
    private final SessionManager sessionManager;
    private final AppSettings appSettings;
    private final Gson gson;

    private long farmId;
    @Nullable
    private Long resendOfId;
    private boolean reinvite;

    private final MutableLiveData<String> phone = new MutableLiveData<>("");
    private final MutableLiveData<String> role = new MutableLiveData<>(ROLE_STAFF);
    private final MutableLiveData<List<PenOptionVO>> penOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Set<Long>> selectedPenIds = new MutableLiveData<>(Collections.emptySet());
    private final MutableLiveData<String> penHint = new MutableLiveData<>(HINT_NONE);
    private final MutableLiveData<Boolean> noPens = new MutableLiveData<>(false);
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>(false);

    private final SingleLiveEvent<String> done = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> stale = new SingleLiveEvent<>();

    @Nullable
    private String idemKey;

    @Inject
    public InviteMemberViewModel(@NonNull MemberRepository repository, @NonNull FarmContext farmContext,
                                 @NonNull SessionManager sessionManager, @NonNull AppSettings appSettings,
                                 @NonNull Gson gson) {
        this.repository = repository;
        this.farmContext = farmContext;
        this.sessionManager = sessionManager;
        this.appSettings = appSettings;
        this.gson = gson;
        canSubmit.addSource(phone, v -> onChanged(true));
        canSubmit.addSource(role, v -> onChanged(false));
        canSubmit.addSource(selectedPenIds, v -> onChanged(false));
        canSubmit.addSource(submitting, v -> updateCanSubmit());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = handle.get(RouterPath.EXTRA_FARM_ID);
        farmId = id != null && id > 0 ? id : farmContext.farmIdOrZero();
        String json = handle.get(FarmExtras.EXTRA_REINVITE_JSON);
        InviteItemVO origin = json == null ? null : gson.fromJson(json, InviteItemVO.class);
        init(origin);
        if (FarmUi.deniedByContext(farmContext, farmId, farmContext.permissions().isCanInvite())) {
            setUiState(FarmUi.noPermissionState());
            return;
        }
        load(repository.getPenOptions(farmId, null), list -> {
            penOptions.setValue(list);
            noPens.setValue(list.isEmpty());
        });
    }

    /** 重新邀请时按原邀请预填手机号、角色和负责栏舍 */
    void init(@Nullable InviteItemVO origin) {
        if (origin == null) {
            return;
        }
        reinvite = true;
        resendOfId = origin.getInviteId();
        phone.setValue(PhoneTextWatcher.format(origin.getInviteePhone()));
        role.setValue(ROLE_MANAGER.equals(origin.getRole()) ? ROLE_MANAGER : ROLE_STAFF);
        Set<Long> ids = new LinkedHashSet<>();
        for (PenBriefVO p : origin.getPens()) {
            ids.add(p.getPenId());
        }
        selectedPenIds.setValue(ids);
        updateHint();
    }

    /** 可分配栏舍为空时不算空页面（显示“还没有栏舍”说明） */
    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return false;
    }

    private void onChanged(boolean phoneChanged) {
        idemKey = null;
        if (phoneChanged) {
            Map<String, String> errors = fieldErrors.getValue();
            if (errors != null && errors.containsKey(F_PHONE)) {
                fieldErrors.setValue(Collections.emptyMap());
            }
        }
        updateCanSubmit();
    }

    private void updateCanSubmit() {
        boolean ok = !Boolean.TRUE.equals(submitting.getValue()) && !FarmRules.trim(phone.getValue()).isEmpty();
        if (!Boolean.valueOf(ok).equals(canSubmit.getValue())) {
            canSubmit.setValue(ok);
        }
    }

    public void onRoleSelected(@NonNull String r) {
        role.setValue(ROLE_MANAGER.equals(r) ? ROLE_MANAGER : ROLE_STAFF);
    }

    public void onPenToggled(long penId) {
        Set<Long> ids = new LinkedHashSet<>(currentPens());
        if (!ids.remove(penId)) {
            ids.add(penId);
        }
        selectedPenIds.setValue(ids);
        updateHint();
    }

    private void updateHint() {
        int n = currentPens().size();
        penHint.setValue(n > 0 ? String.format(HINT_SELECTED, n) : HINT_NONE);
    }

    @NonNull
    private Set<Long> currentPens() {
        Set<Long> s = selectedPenIds.getValue();
        return s == null ? Collections.emptySet() : s;
    }

    /** 失焦时校验手机号格式 */
    public void onPhoneFocusLost() {
        String err = phoneError();
        if (err != null && !FarmRules.trim(phone.getValue()).isEmpty()) {
            fieldErrors.setValue(Collections.singletonMap(F_PHONE, err));
        }
    }

    @Nullable
    private String phoneError() {
        String p = phone.getValue();
        if (FarmRules.trim(p).isEmpty()) {
            return MSG_PHONE_EMPTY;
        }
        if (!FarmRules.isPhone(p)) {
            return FarmRules.MSG_PHONE_INVALID;
        }
        if (isSelf(PhoneTextWatcher.digits(p))) {
            return MSG_SELF;
        }
        return null;
    }

    private boolean isSelf(@NonNull String digits) {
        String last = appSettings.getLastPhone();
        if (last != null && PhoneTextWatcher.digits(last).equals(digits)) {
            return true;
        }
        AccountUserVO user = sessionManager.currentUser();
        String masked = user == null ? null : user.getPhoneMasked();
        return last == null && masked != null && masked.equals(NumberFormatter.maskPhone(digits));
    }

    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        String err = phoneError();
        if (err != null) {
            fieldErrors.setValue(Collections.singletonMap(F_PHONE, err));
            return;
        }
        fieldErrors.setValue(Collections.emptyMap());
        if (idemKey == null) {
            idemKey = IdemKey.newKey();
        }
        boolean staff = ROLE_STAFF.equals(role.getValue());
        List<Long> penIds = null;
        if (staff) {
            penIds = new ArrayList<>();
            List<PenOptionVO> options = penOptions.getValue();
            Set<Long> selected = currentPens();
            if (options != null) {
                for (PenOptionVO o : options) {
                    if (selected.contains(o.getPenId())) {
                        penIds.add(o.getPenId());
                    }
                }
            }
        }
        InviteMemberRequest req = new InviteMemberRequest(PhoneTextWatcher.digits(phone.getValue()),
                staff ? ROLE_STAFF : ROLE_MANAGER, penIds, resendOfId);
        submitting.setValue(true);
        launch(repository.sendInvite(farmId, req, idemKey), item -> {
            submitting.setValue(false);
            idemKey = null;
            done.setValue(MSG_DONE);
        }, e -> {
            submitting.setValue(false);
            if (e.getCode() == ErrorCodes.STATE_CHANGED) {
                stale.setValue(e.getMessage() != null ? e.getMessage() : MSG_STALE);
            } else if (e.isNetwork()) {
                showMessage(e.keptMessage(FarmRules.MSG_NETWORK_KEPT));
            } else {
                handleError(e);
            }
        });
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        String msg = errors.containsKey(F_PHONE) ? errors.get(F_PHONE) : errors.values().iterator().next();
        Map<String, String> map = new LinkedHashMap<>();
        map.put(F_PHONE, msg);
        fieldErrors.setValue(map);
        return true;
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        if (penOptions.getValue() == null || Boolean.TRUE.equals(submitting.getValue())) {
            super.onNoPermission(e);
        } else {
            showMessage(e.getMessage());
        }
    }

    @Override
    public void retry() {
        load(repository.getPenOptions(farmId, null), list -> {
            penOptions.setValue(list);
            noPens.setValue(list.isEmpty());
        });
    }

    public boolean isReinvite() {
        return reinvite;
    }

    // ===== 对外 =====

    public MutableLiveData<String> getPhone() {
        return phone;
    }

    public MutableLiveData<String> getRole() {
        return role;
    }

    public LiveData<List<PenOptionVO>> getPenOptions() {
        return penOptions;
    }

    public LiveData<Set<Long>> getSelectedPenIds() {
        return selectedPenIds;
    }

    public LiveData<String> getPenHint() {
        return penHint;
    }

    public LiveData<Boolean> getNoPens() {
        return noPens;
    }

    public LiveData<Map<String, String>> getFieldErrors() {
        return fieldErrors;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public SingleLiveEvent<String> getDone() {
        return done;
    }

    public SingleLiveEvent<String> getStale() {
        return stale;
    }
}
