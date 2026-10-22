package com.zhimu.slmp.farm.ui.info;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.Gson;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmPermissionVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.model.request.FarmVersionRequest;
import com.zhimu.slmp.farm.model.request.UpdateFarmBasicRequest;
import com.zhimu.slmp.farm.model.vo.FarmBasicVO;
import com.zhimu.slmp.farm.model.vo.FarmDetailVO;
import com.zhimu.slmp.farm.repository.FarmRepository;
import com.zhimu.slmp.farm.service.FarmContextServiceImpl;
import com.zhimu.slmp.farm.ui.FarmUi;
import com.zhimu.slmp.farm.util.FarmRules;
import com.zhimu.slmp.farm.util.PhoneTextWatcher;

import java.util.Map;

import javax.inject.Inject;

/**
 * F05 养殖场信息（模块02 技术文档 2.4.5、2.6.3）：API-F09；基本信息单项保存 API-F10；停用 / 启用 API-F11、F12；退出 API-F27。
 * 注册信息行（所有者）进入 F06；成员管理进入 F07；转让所有权进入 F10。员工经旧链接进入时显示 U03 无权查看。
 */
public class FarmInfoViewModel extends BaseViewModel {

    public static final String FIELD_CONTACT_NAME = "CONTACT_NAME";
    public static final String FIELD_CONTACT_PHONE = "CONTACT_PHONE";
    public static final String FIELD_SCALE = "SCALE";
    public static final String FIELD_INTRO = "INTRO";

    public static final String CONFIRM_DISABLE = "DISABLE";
    public static final String CONFIRM_ENABLE = "ENABLE";
    public static final String CONFIRM_QUIT = "QUIT";

    /** 刷新按钮的操作编号（40902 Snackbar“刷新”） */
    public static final String ACTION_REFRESH = "refresh";

    private final FarmRepository repository;
    private final FarmContextServiceImpl contextService;
    private final FarmContext farmContext;
    private final Gson gson;

    private long farmId;

    private final MutableLiveData<FarmDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<FarmBasicVO> basic = new MutableLiveData<>();
    private final MutableLiveData<String> changeBanner = new MutableLiveData<>();
    private final MutableLiveData<String> transferText = new MutableLiveData<>();
    private final MutableLiveData<FarmPermissionVO> permissions = new MutableLiveData<>();
    private final MutableLiveData<String> editField = new MutableLiveData<>();
    private final MutableLiveData<String> editValue = new MutableLiveData<>("");
    private final MutableLiveData<String> editError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);

    private final SingleLiveEvent<String> showEditSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> saved = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> showConfirm = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> openChange = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openMembers = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openTransfer = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> quitDone = new SingleLiveEvent<>();

    @Inject
    public FarmInfoViewModel(@NonNull FarmRepository repository, @NonNull FarmContextServiceImpl contextService,
                             @NonNull FarmContext farmContext, @NonNull Gson gson) {
        this.repository = repository;
        this.contextService = contextService;
        this.farmContext = farmContext;
        this.gson = gson;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = handle.get(RouterPath.EXTRA_FARM_ID);
        farmId = id != null && id > 0 ? id : farmContext.farmIdOrZero();
        if (FarmUi.deniedByContext(farmContext, farmId, farmContext.permissions().isCanViewFarmInfo())) {
            setUiState(FarmUi.noPermissionState());
            return;
        }
        load();
    }

    public void load() {
        load(repository.getFarmDetail(farmId), this::applyDetail);
    }

    /** 下拉刷新与子页（F06、F07、F10）返回后 */
    public void refresh() {
        load();
    }

    @Override
    public void retry() {
        load();
    }

    private void applyDetail(@NonNull FarmDetailVO d) {
        detail.setValue(d);
        basic.setValue(d.getBasic());
        permissions.setValue(d.getPermissions() == null ? new FarmPermissionVO() : d.getPermissions());
        if (d.getPendingChange() != null) {
            changeBanner.setValue(FarmUi.monthDay(d.getPendingChange().getSubmittedAt()));
        } else if (d.getRejectedChange() != null) {
            changeBanner.setValue(d.getRejectedChange().getRejectReason());
        } else {
            changeBanner.setValue(null);
        }
        transferText.setValue(d.getPendingTransfer() != null && d.getPendingTransfer().isPending()
                ? d.getPendingTransfer().getToName() : null);
    }

    // ===== 基本信息单项修改 =====

    public void openEdit(@NonNull String field) {
        FarmBasicVO b = basic.getValue();
        FarmPermissionVO p = permissions.getValue();
        if (b == null || p == null || !p.isCanEditBasic()) {
            return;
        }
        String value;
        switch (field) {
            case FIELD_CONTACT_NAME:
                value = b.getContactName();
                break;
            case FIELD_CONTACT_PHONE:
                value = PhoneTextWatcher.format(b.getContactPhone());
                break;
            case FIELD_SCALE:
                value = b.getScale() > 0 ? String.valueOf(b.getScale()) : "";
                break;
            default:
                value = b.getIntro();
                break;
        }
        editField.setValue(field);
        editValue.setValue(value == null ? "" : value);
        editError.setValue(null);
        showEditSheet.setValue(field);
    }

    public void saveField() {
        String field = editField.getValue();
        FarmBasicVO b = basic.getValue();
        if (field == null || b == null || Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        String raw = editValue.getValue();
        UpdateFarmBasicRequest req = new UpdateFarmBasicRequest(b.getVersion());
        String error = null;
        switch (field) {
            case FIELD_CONTACT_NAME: {
                String v = FarmRules.trim(raw);
                if (v.isEmpty()) {
                    error = FarmRules.MSG_CONTACT_EMPTY;
                } else if (FarmRules.length(v) > FarmRules.CONTACT_MAX) {
                    error = FarmRules.MSG_CONTACT_TOO_LONG;
                }
                req.setContactName(v);
                break;
            }
            case FIELD_CONTACT_PHONE:
                if (!FarmRules.isPhone(raw)) {
                    error = FarmRules.MSG_PHONE_INVALID;
                }
                req.setContactPhone(PhoneTextWatcher.digits(raw));
                break;
            case FIELD_SCALE: {
                Integer s = FarmRules.parseScale(raw);
                if (s == null) {
                    error = FarmRules.MSG_SCALE_INVALID;
                }
                req.setScale(s);
                break;
            }
            default: {
                String v = FarmRules.trim(raw);
                if (FarmRules.length(v) > FarmRules.INTRO_MAX) {
                    error = FarmRules.MSG_INTRO_TOO_LONG;
                }
                req.setIntro(v);
                break;
            }
        }
        if (error != null) {
            editError.setValue(error);
            return;
        }
        editError.setValue(null);
        saving.setValue(true);
        launch(repository.updateBasic(farmId, req), nb -> {
            saving.setValue(false);
            basic.setValue(nb);
            saved.setValue(field);
        }, e -> {
            saving.setValue(false);
            onActionError(e);
        });
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> fieldErrors) {
        if (editField.getValue() == null || fieldErrors.isEmpty()) {
            return false;
        }
        editError.setValue(fieldErrors.values().iterator().next());
        return true;
    }

    /** 40902：“内容已更新，请刷新后再修改”+“刷新”；其余交给 handleError（40903 提示后刷新） */
    private void onActionError(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.VERSION_CONFLICT) {
            showMessage(UiMessage.withAction(e.getMessage() != null ? e.getMessage() : MSG_CONFLICT,
                    "刷新", ACTION_REFRESH));
            return;
        }
        handleError(e);
    }

    static final String MSG_CONFLICT = "内容已更新，请刷新后再修改";

    /** 打开页面后权限被收回：提示后重新取上下文并刷新 */
    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        if (detail.getValue() == null) {
            super.onNoPermission(e);
            return;
        }
        showMessage("你没有这个操作的权限");
        launch(contextService.refreshContext(), this::load, err -> load());
    }

    // ===== 停用、启用、退出 =====

    public void onDisableClick() {
        showConfirm.setValue(CONFIRM_DISABLE);
    }

    public void onEnableClick() {
        showConfirm.setValue(CONFIRM_ENABLE);
    }

    public void onQuitClick() {
        showConfirm.setValue(CONFIRM_QUIT);
    }

    public void disable() {
        FarmBasicVO b = basic.getValue();
        if (b == null || Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        saving.setValue(true);
        launch(repository.disableFarm(farmId, new FarmVersionRequest(b.getVersion())), d -> {
            saving.setValue(false);
            applyDetail(d);
            showMessage("已停用" + nameOf(d));
            refreshContextQuietly();
        }, e -> {
            saving.setValue(false);
            onActionError(e);
        });
    }

    public void enable() {
        FarmBasicVO b = basic.getValue();
        if (b == null || Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        saving.setValue(true);
        launch(repository.enableFarm(farmId, new FarmVersionRequest(b.getVersion())), d -> {
            saving.setValue(false);
            applyDetail(d);
            showMessage("已重新启用" + nameOf(d));
            refreshContextQuietly();
        }, e -> {
            saving.setValue(false);
            onActionError(e);
        });
    }

    /** 退出（场长）：API-F27 后按 2.6.1 选下一个养殖场，回 T01 */
    public void quit() {
        if (Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        saving.setValue(true);
        String name = nameOf(detail.getValue());
        launch(repository.quitFarm(farmId).andThen(contextService.selectNext(farmId)), opt -> {
            saving.setValue(false);
            quitDone.setValue("你已退出" + name);
        }, e -> {
            saving.setValue(false);
            if (e.getCode() == ErrorCodes.ROLE_FORBIDDEN) {
                showMessage(e.getMessage());
            } else {
                handleError(e);
            }
        });
    }

    /** 停用、启用后刷新 FarmContext，其他模块登记入口随之禁用或恢复 */
    private void refreshContextQuietly() {
        Long current = farmContext.currentFarmId();
        if (current != null && current == farmId) {
            launch(contextService.refreshContext(), () -> {
            }, e -> {
            });
        }
    }

    @NonNull
    private static String nameOf(@Nullable FarmDetailVO d) {
        return d == null || d.getName() == null ? "养殖场" : d.getName();
    }

    // ===== 跳转 =====

    public void onRegistrationRowClick(@NonNull String target) {
        FarmPermissionVO p = permissions.getValue();
        if (p != null && p.isCanSubmitChange()) {
            openChange.setValue(target);
        }
    }

    public void onMembersClick() {
        openMembers.call();
    }

    public void onTransferClick() {
        openTransfer.call();
    }

    /** F06 的比较基准（Gson 字符串） */
    @Nullable
    public String registrationJson() {
        FarmDetailVO d = detail.getValue();
        return d == null || d.getRegistration() == null ? null : gson.toJson(d.getRegistration());
    }

    public long getFarmId() {
        return farmId;
    }

    // ===== 对外 =====

    public LiveData<FarmDetailVO> getDetail() {
        return detail;
    }

    public LiveData<FarmBasicVO> getBasic() {
        return basic;
    }

    public LiveData<String> getChangeBanner() {
        return changeBanner;
    }

    public LiveData<String> getTransferText() {
        return transferText;
    }

    public LiveData<FarmPermissionVO> getPermissions() {
        return permissions;
    }

    public LiveData<String> getEditField() {
        return editField;
    }

    public MutableLiveData<String> getEditValue() {
        return editValue;
    }

    public LiveData<String> getEditError() {
        return editError;
    }

    public LiveData<Boolean> getSaving() {
        return saving;
    }

    public SingleLiveEvent<String> getShowEditSheet() {
        return showEditSheet;
    }

    public SingleLiveEvent<String> getSaved() {
        return saved;
    }

    public SingleLiveEvent<String> getShowConfirm() {
        return showConfirm;
    }

    public SingleLiveEvent<String> getOpenChange() {
        return openChange;
    }

    public SingleLiveEvent<Void> getOpenMembers() {
        return openMembers;
    }

    public SingleLiveEvent<Void> getOpenTransfer() {
        return openTransfer;
    }

    public SingleLiveEvent<String> getQuitDone() {
        return quitDone;
    }
}
