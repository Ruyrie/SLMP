package com.zhimu.slmp.farm.ui.create;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.form.FormValidator;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.session.AppSettings;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.model.request.CreateFarmRequest;
import com.zhimu.slmp.farm.model.request.UpdateRegistrationRequest;
import com.zhimu.slmp.farm.model.vo.FarmAuditVO;
import com.zhimu.slmp.farm.model.vo.QualFileVO;
import com.zhimu.slmp.farm.model.vo.RegistrationVO;
import com.zhimu.slmp.farm.repository.FarmRepository;
import com.zhimu.slmp.farm.util.CountUnitText;
import com.zhimu.slmp.farm.util.FarmRules;
import com.zhimu.slmp.farm.util.PhoneTextWatcher;
import com.zhimu.slmp.map.model.vo.LocationResult;
import com.zhimu.slmp.map.model.vo.PickLocationInput;
import com.zhimu.slmp.media.data.MediaScene;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.media.upload.UploadManager;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * F02 新建养殖场 / 修改注册信息（模块02 技术文档 2.4.2、2.6.2）。表单关键字段保存在 SavedStateHandle（草稿），
 * 资质图由 lib_media 的 UploadManager（scene = FARM_QUAL，最多 9 张）压缩上传。
 * 提交：FormValidator 全量校验 → 检查上传 → 生成 idemKey（重试沿用，改字段或成功后置 null）→ API-F05 或 API-F07。
 */
public class FarmCreateViewModel extends BaseViewModel {

    public static final String MODE_CREATE = FarmExtras.MODE_CREATE;
    public static final String MODE_EDIT = FarmExtras.MODE_EDIT;

    /** 字段键为请求 JSON 字段名（与 40001 field_errors 一致） */
    public static final String F_NAME = "name";
    public static final String F_ADDRESS = "address";
    public static final String F_SCALE = "scale";
    public static final String F_CATEGORY = "main_category_id";
    public static final String F_CONTACT_NAME = "contact_name";
    public static final String F_CONTACT_PHONE = "contact_phone";
    public static final String F_QUAL = "qualification_file_ids";

    static final String KEY_NAME = "draft_name";
    static final String KEY_SCALE = "draft_scale";
    static final String KEY_CONTACT_NAME = "draft_contact_name";
    static final String KEY_CONTACT_PHONE = "draft_contact_phone";
    static final String KEY_CATEGORY = "draft_category";
    static final String KEY_LOCATION = "draft_location";
    static final String KEY_AUDIT_VERSION = "draft_audit_version";
    static final String KEY_PREFILLED = "draft_prefilled";
    static final String KEY_QUAL = "qual_files";

    /** 40901 等待后重发、超时重发各一次 */
    static final long DUPLICATE_RETRY_DELAY_MS = 2000;

    private final FarmRepository repository;
    private final BaseDataRepository baseData;
    private final SessionManager sessionManager;
    private final AppSettings appSettings;
    private final UploadManager.Factory uploads;

    private SavedStateHandle handle;
    private UploadManager qualFiles;

    private final MutableLiveData<String> mode = new MutableLiveData<>(MODE_CREATE);
    private long farmId;

    private MutableLiveData<String> name;
    private MutableLiveData<String> scale;
    private MutableLiveData<String> contactName;
    private MutableLiveData<String> contactPhone;
    private MutableLiveData<Long> mainCategoryId;
    private MutableLiveData<LocationResult> location;

    private final MutableLiveData<List<CategoryVO>> categories = new MutableLiveData<>(Collections.emptyList());
    private final MediatorLiveData<String> scaleUnit = new MediatorLiveData<>(CountUnitText.TEXT_HEAD);
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>(false);
    private int auditVersion;

    private final SingleLiveEvent<String> scrollToField = new SingleLiveEvent<>();
    private final SingleLiveEvent<PickLocationInput> openMapPick = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showDiscard = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> submitted = new SingleLiveEvent<>();

    @Nullable
    private String idemKey;
    private int resendCount;

    @Inject
    public FarmCreateViewModel(@NonNull FarmRepository repository, @NonNull BaseDataRepository baseData,
                               @NonNull SessionManager sessionManager, @NonNull AppSettings appSettings,
                               @NonNull UploadManager.Factory uploads) {
        this.repository = repository;
        this.baseData = baseData;
        this.sessionManager = sessionManager;
        this.appSettings = appSettings;
        this.uploads = uploads;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle h) {
        handle = h;
        String m = h.get(RouterPath.EXTRA_MODE);
        Long id = h.get(RouterPath.EXTRA_FARM_ID);
        init(MODE_EDIT.equals(m) ? MODE_EDIT : MODE_CREATE, id == null ? 0 : id);
    }

    /** 读取启动参数并加载品类、EDIT 时的注册信息 */
    void init(@NonNull String m, long id) {
        mode.setValue(m);
        farmId = id;
        boolean edit = isEdit();
        AccountUserVO user = sessionManager.currentUser();
        String defaultName = edit || user == null || user.getName() == null ? "" : user.getName();
        String lastPhone = appSettings.getLastPhone();
        String defaultPhone = !edit && FarmRules.isPhone(lastPhone) ? PhoneTextWatcher.format(lastPhone) : "";

        name = handle.getLiveData(KEY_NAME, "");
        scale = handle.getLiveData(KEY_SCALE, "");
        contactName = handle.getLiveData(KEY_CONTACT_NAME, defaultName);
        contactPhone = handle.getLiveData(KEY_CONTACT_PHONE, defaultPhone);
        mainCategoryId = handle.getLiveData(KEY_CATEGORY);
        location = handle.getLiveData(KEY_LOCATION);
        Integer v = handle.get(KEY_AUDIT_VERSION);
        auditVersion = v == null ? 0 : v;
        qualFiles = uploads.create(handle, KEY_QUAL, MediaScene.FARM_QUAL, FarmRules.QUAL_MAX);

        scaleUnit.addSource(mainCategoryId, c -> updateScaleUnit());
        scaleUnit.addSource(categories, c -> updateScaleUnit());

        canSubmit.addSource(name, s -> onFieldChanged(F_NAME));
        canSubmit.addSource(scale, s -> onFieldChanged(F_SCALE));
        canSubmit.addSource(contactName, s -> onFieldChanged(F_CONTACT_NAME));
        canSubmit.addSource(contactPhone, s -> onFieldChanged(F_CONTACT_PHONE));
        canSubmit.addSource(mainCategoryId, s -> onFieldChanged(F_CATEGORY));
        canSubmit.addSource(location, s -> onFieldChanged(F_ADDRESS));
        canSubmit.addSource(qualFiles.getItems(), s -> onFieldChanged(F_QUAL));
        canSubmit.addSource(submitting, s -> updateCanSubmit());

        launch(baseData.getCategories(), categories::setValue, e -> {
            // 品类加载失败时下拉为空，提交时提示“请选择主要品类”
        });
        if (edit) {
            loadAudit();
        }
    }

    private void loadAudit() {
        load(repository.getAudit(farmId), this::applyAudit);
    }

    @Override
    public void retry() {
        if (isEdit()) {
            loadAudit();
        }
    }

    private void applyAudit(@NonNull FarmAuditVO audit) {
        auditVersion = audit.getAuditVersion() == null ? 0 : audit.getAuditVersion();
        handle.set(KEY_AUDIT_VERSION, auditVersion);
        RegistrationVO r = audit.getRegistration();
        if (r == null || Boolean.TRUE.equals(handle.get(KEY_PREFILLED))) {
            return;
        }
        handle.set(KEY_PREFILLED, true);
        name.setValue(r.getName() == null ? "" : r.getName());
        scale.setValue(r.getScale() > 0 ? String.valueOf(r.getScale()) : "");
        contactName.setValue(r.getContactName() == null ? "" : r.getContactName());
        contactPhone.setValue(PhoneTextWatcher.format(r.getContactPhone()));
        if (r.getMainCategoryId() > 0) {
            mainCategoryId.setValue(r.getMainCategoryId());
        }
        if (r.getAddress() != null) {
            location.setValue(new LocationResult(r.getLongitude(), r.getLatitude(), r.getProvince(), r.getCity(),
                    r.getDistrict(), r.getTownship(), r.getAdcode(), r.getAddress(), null, r.getLocAccuracy()));
        }
        List<UploadItem> items = new ArrayList<>();
        for (QualFileVO f : r.getQualificationFiles()) {
            UploadItem item = UploadItem.remote(f);
            items.add(f.isReshoot() ? item.withTag(MSG_RESHOOT, StatusTagView.ERROR) : item);
        }
        qualFiles.setInitialItems(items);
    }

    static final String MSG_RESHOOT = "需重拍";

    // ===== 输入 =====

    private void onFieldChanged(@NonNull String field) {
        idemKey = null;
        Map<String, String> errors = fieldErrors.getValue();
        if (errors != null && errors.containsKey(field)) {
            Map<String, String> copy = new LinkedHashMap<>(errors);
            copy.remove(field);
            fieldErrors.setValue(copy);
        }
        updateCanSubmit();
    }

    private void updateCanSubmit() {
        boolean ok = !Boolean.TRUE.equals(submitting.getValue())
                && !FarmRules.trim(name.getValue()).isEmpty()
                && !FarmRules.trim(scale.getValue()).isEmpty()
                && !FarmRules.trim(contactName.getValue()).isEmpty()
                && !FarmRules.trim(contactPhone.getValue()).isEmpty()
                && mainCategoryId.getValue() != null
                && location.getValue() != null
                && !qualFiles.current().isEmpty();
        if (!Boolean.valueOf(ok).equals(canSubmit.getValue())) {
            canSubmit.setValue(ok);
        }
    }

    private void updateScaleUnit() {
        Long id = mainCategoryId.getValue();
        String unit = CountUnitText.TEXT_HEAD;
        List<CategoryVO> list = categories.getValue();
        if (id != null && list != null) {
            for (CategoryVO c : list) {
                if (c.getId() == id) {
                    unit = CountUnitText.of(c.getCountUnit(), c.getCountUnitText());
                    break;
                }
            }
        }
        if (!unit.equals(scaleUnit.getValue())) {
            scaleUnit.setValue(unit);
        }
    }

    /** 名称框失焦：API-F04 查重（EDIT 带 exclude_farm_id）；42901 等错误不阻塞 */
    public void onNameFocusLost() {
        String n = FarmRules.trim(name.getValue());
        if (n.isEmpty() || FarmRules.length(n) > FarmRules.NAME_MAX) {
            return;
        }
        launch(repository.checkName(n, isEdit() ? farmId : null), r -> {
            if (!r.isAvailable() && n.equals(FarmRules.trim(name.getValue()))) {
                putError(F_NAME, r.getMessage() != null ? r.getMessage() : String.format(FarmRules.MSG_NAME_DUP, n));
            }
        }, e -> {
            // 查重失败不阻塞填写，提交时由服务端 40905 兜底
        });
    }

    public void onCategorySelected(long categoryId) {
        mainCategoryId.setValue(categoryId);
    }

    public void onAddressClick() {
        String n = FarmRules.trim(name.getValue());
        openMapPick.setValue(new PickLocationInput(PickLocationInput.MODE_NEW, n.isEmpty() ? null : n,
                location.getValue()));
    }

    public void onLocationPicked(@Nullable LocationResult result) {
        if (result != null) {
            location.setValue(result);
        }
    }

    /** 按返回：有内容时确认放弃；返回 true 表示已拦截 */
    public boolean onBackPressed() {
        if (hasContent()) {
            showDiscard.call();
            return true;
        }
        return false;
    }

    private boolean hasContent() {
        return !FarmRules.trim(name.getValue()).isEmpty() || !FarmRules.trim(scale.getValue()).isEmpty()
                || mainCategoryId.getValue() != null || location.getValue() != null || !qualFiles.current().isEmpty();
    }

    // ===== 提交 =====

    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> errors = validate();
        if (!errors.isEmpty()) {
            fieldErrors.setValue(errors);
            scrollToField.setValue(errors.keySet().iterator().next());
            return;
        }
        fieldErrors.setValue(Collections.emptyMap());
        if (qualFiles.hasUploading()) {
            showMessage(FarmRules.MSG_QUAL_UPLOADING);
            return;
        }
        String block = qualFiles.getSubmitBlockMessage();
        if (block != null) {
            showMessage(block);
            return;
        }
        if (idemKey == null) {
            idemKey = IdemKey.newKey();
        }
        resendCount = 0;
        send(buildRequest(), idemKey);
    }

    /** 前端逐项校验（顺序与页面字段一致） */
    @NonNull
    @VisibleForTesting
    Map<String, String> validate() {
        String n = FarmRules.trim(name.getValue());
        String cn = FarmRules.trim(contactName.getValue());
        FormValidator v = new FormValidator()
                .required(F_NAME, n, FarmRules.MSG_NAME_EMPTY)
                .check(F_NAME, FarmRules.length(n) <= FarmRules.NAME_MAX, FarmRules.MSG_NAME_TOO_LONG)
                .check(F_ADDRESS, FarmRules.isLocationValid(location.getValue()), FarmRules.MSG_ADDRESS)
                .required(F_SCALE, scale.getValue(), FarmRules.MSG_SCALE_EMPTY)
                .check(F_SCALE, FarmRules.parseScale(scale.getValue()) != null, FarmRules.MSG_SCALE_INVALID)
                .requiredValue(F_CATEGORY, mainCategoryId.getValue(), FarmRules.MSG_CATEGORY_EMPTY)
                .required(F_CONTACT_NAME, cn, FarmRules.MSG_CONTACT_EMPTY)
                .check(F_CONTACT_NAME, FarmRules.length(cn) <= FarmRules.CONTACT_MAX, FarmRules.MSG_CONTACT_TOO_LONG)
                .check(F_CONTACT_PHONE, FarmRules.isPhone(contactPhone.getValue()), FarmRules.MSG_PHONE_INVALID);
        Map<String, String> nameError = fieldErrors.getValue();
        if (nameError != null && nameError.containsKey(F_NAME)) {
            v.check(F_NAME, false, nameError.get(F_NAME));
        }
        if (!qualFiles.hasUploading() && !qualFiles.hasFailed()) {
            v.check(F_QUAL, !qualFiles.getFileIds().isEmpty(), FarmRules.MSG_QUAL_EMPTY);
        }
        return v.validate();
    }

    @NonNull
    private CreateFarmRequest buildRequest() {
        LocationResult loc = location.getValue();
        CreateFarmRequest req = isEdit() ? new UpdateRegistrationRequest().setAuditVersion(auditVersion)
                : new CreateFarmRequest();
        Integer s = FarmRules.parseScale(scale.getValue());
        Long cat = mainCategoryId.getValue();
        req.setName(FarmRules.trim(name.getValue()))
                .setMainCategoryId(cat == null ? 0 : cat)
                .setScale(s == null ? 0 : s)
                .setContactName(FarmRules.trim(contactName.getValue()))
                .setContactPhone(PhoneTextWatcher.digits(contactPhone.getValue()))
                .setQualificationFileIds(qualFiles.getFileIds());
        if (loc != null) {
            Integer accuracy = LocationResult.SOURCE_LOCATION.equals(loc.getSource()) ? loc.getLocAccuracy() : null;
            req.setLocation(FarmRules.trim(loc.getAddress()), loc.getProvince(), loc.getCity(), loc.getDistrict(),
                    loc.getTownship(), loc.getAdcode(), loc.getLongitude(), loc.getLatitude(), accuracy);
        }
        return req;
    }

    private void send(@NonNull CreateFarmRequest req, @NonNull String key) {
        submitting.setValue(true);
        Single<Long> call = isEdit()
                ? repository.updateRegistration(farmId, (UpdateRegistrationRequest) req, key).toSingleDefault(farmId)
                : repository.createFarm(req, key).map(FarmAuditVO::getFarmId);
        launch(call, id -> {
            submitting.setValue(false);
            idemKey = null;
            submitted.setValue(id);
        }, e -> onSubmitError(req, key, e));
    }

    private void onSubmitError(@NonNull CreateFarmRequest req, @NonNull String key, @NonNull ApiException e) {
        int code = e.getCode();
        if (code == ErrorCodes.DUPLICATE_SUBMIT && resendCount < 1) {
            // 40901 静默等待 2 秒后用同一幂等键再发
            resendCount++;
            disposables.add(Completable.timer(DUPLICATE_RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> send(req, key)));
            return;
        }
        if (e.isNetwork() && e.getCause() instanceof SocketTimeoutException) {
            if (resendCount < 1) {
                // 超时：没有按请求编号查询的接口，按钮保持加载并用同一幂等键重发一次
                resendCount++;
                send(req, key);
                return;
            }
            submitting.setValue(false);
            showMessage(FarmRules.MSG_SUBMIT_UNFINISHED);
            return;
        }
        submitting.setValue(false);
        if (e.isNetwork()) {
            showMessage(FarmRules.MSG_NETWORK_KEPT);
        } else if (code == ErrorCodes.NAME_DUPLICATE) {
            putError(F_NAME, e.getMessage());
            scrollToField.setValue(F_NAME);
        } else if (code == ErrorCodes.DUPLICATE_SUBMIT) {
            showMessage(FarmRules.MSG_SUBMIT_UNFINISHED);
        } else {
            handleError(e);
        }
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        scrollToField.setValue(errors.keySet().iterator().next());
        return true;
    }

    /** 40902 / 40903：EDIT 时重新取 audit_version（不覆盖已填写的内容） */
    @Override
    protected void onDataChanged(@NonNull ApiException e) {
        if (isEdit()) {
            launch(repository.getAudit(farmId), a -> {
                auditVersion = a.getAuditVersion() == null ? auditVersion : a.getAuditVersion();
                handle.set(KEY_AUDIT_VERSION, auditVersion);
            }, err -> {
            });
        }
    }

    /** 提交中被收回权限：只提示，不切换整页 */
    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        showMessage(e.getMessage());
    }

    private void putError(@NonNull String field, @Nullable String message) {
        Map<String, String> errors = fieldErrors.getValue();
        Map<String, String> copy = errors == null ? new LinkedHashMap<>() : new LinkedHashMap<>(errors);
        copy.put(field, message == null ? "" : message);
        fieldErrors.setValue(copy);
    }

    public boolean isEdit() {
        return MODE_EDIT.equals(mode.getValue());
    }

    @Override
    protected void onCleared() {
        if (qualFiles != null) {
            qualFiles.release();
        }
        super.onCleared();
    }

    // ===== 对外 =====

    public LiveData<String> getMode() {
        return mode;
    }

    public long getFarmId() {
        return farmId;
    }

    public MutableLiveData<String> getName() {
        return name;
    }

    public MutableLiveData<String> getScale() {
        return scale;
    }

    public MutableLiveData<String> getContactName() {
        return contactName;
    }

    public MutableLiveData<String> getContactPhone() {
        return contactPhone;
    }

    public MutableLiveData<Long> getMainCategoryId() {
        return mainCategoryId;
    }

    public LiveData<List<CategoryVO>> getCategories() {
        return categories;
    }

    public LiveData<String> getScaleUnit() {
        return scaleUnit;
    }

    public LiveData<LocationResult> getLocation() {
        return location;
    }

    /** 资质图网格（MediaPickerView.bind） */
    public UploadManager getQualFiles() {
        return qualFiles;
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

    public int getAuditVersion() {
        return auditVersion;
    }

    public SingleLiveEvent<String> getScrollToField() {
        return scrollToField;
    }

    public SingleLiveEvent<PickLocationInput> getOpenMapPick() {
        return openMapPick;
    }

    public SingleLiveEvent<Void> getShowDiscard() {
        return showDiscard;
    }

    public SingleLiveEvent<Long> getSubmitted() {
        return submitted;
    }

    @VisibleForTesting
    @Nullable
    String getIdemKey() {
        return idemKey;
    }
}
