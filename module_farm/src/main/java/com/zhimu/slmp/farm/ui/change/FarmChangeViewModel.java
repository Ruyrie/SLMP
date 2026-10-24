package com.zhimu.slmp.farm.ui.change;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.Gson;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.form.FormValidator;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.IdemKey;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.farm.FarmExtras;
import com.zhimu.slmp.farm.model.request.SubmitFarmChangeRequest;
import com.zhimu.slmp.farm.model.vo.ChangeSnapshotVO;
import com.zhimu.slmp.farm.model.vo.FarmChangeVO;
import com.zhimu.slmp.farm.model.vo.QualFileVO;
import com.zhimu.slmp.farm.model.vo.RegistrationVO;
import com.zhimu.slmp.farm.repository.FarmRepository;
import com.zhimu.slmp.farm.ui.FarmUi;
import com.zhimu.slmp.farm.util.FarmRules;
import com.zhimu.slmp.map.model.vo.LocationResult;
import com.zhimu.slmp.map.model.vo.PickLocationInput;
import com.zhimu.slmp.media.data.MediaScene;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.media.upload.UploadManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

/**
 * F06 资料变更（模块02 技术文档 2.4.6、2.6.3）：名称、地址、资质图逐项与当前生效信息比较（名称去首尾空格；地址文字或经纬度
 * 任一不同；资质图按 file_id 集合，只调顺序不算），三项都未修改不发请求；API-F13 有待审核变更时只读；提交 API-F14。
 */
public class FarmChangeViewModel extends BaseViewModel {

    public static final String F_NAME = "name";
    public static final String F_ADDRESS = "address";
    public static final String F_QUAL = "qualification_file_ids";
    public static final String F_REMARK = "remark";

    static final String KEY_NAME = "change_name";
    static final String KEY_REMARK = "change_remark";
    static final String KEY_LOCATION = "change_location";
    static final String KEY_PREFILLED = "change_prefilled";
    static final String KEY_QUAL = "change_qual_files";
    static final String TAG_NEW = "新增";

    private final FarmRepository repository;
    private final FarmContext farmContext;
    private final Gson gson;
    private final UploadManager.Factory uploads;

    private SavedStateHandle handle;
    private long farmId;
    @Nullable
    private String mode;
    @Nullable
    private String scrollTarget;
    @Nullable
    private RegistrationVO origin;
    @Nullable
    private LocationResult originLocation;
    private final Set<Long> originIds = new HashSet<>();
    private UploadManager qualFiles;

    private MutableLiveData<String> name;
    private MutableLiveData<String> remark;
    private MutableLiveData<LocationResult> location;
    private final MutableLiveData<RegistrationVO> originLive = new MutableLiveData<>();
    private final MutableLiveData<FarmChangeVO> latest = new MutableLiveData<>();
    private final MutableLiveData<Boolean> readOnly = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> nameModified = new MediatorLiveData<>(false);
    private final MutableLiveData<Boolean> addressModified = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> qualModified = new MutableLiveData<>(false);
    private final MutableLiveData<String> qualSummary = new MutableLiveData<>("");
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canSubmit = new MutableLiveData<>(false);

    private final SingleLiveEvent<String> scrollToField = new SingleLiveEvent<>();
    private final SingleLiveEvent<PickLocationInput> openMapPick = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> submitted = new SingleLiveEvent<>();

    @Nullable
    private String idemKey;

    @Inject
    public FarmChangeViewModel(@NonNull FarmRepository repository, @NonNull FarmContext farmContext,
                               @NonNull Gson gson, @NonNull UploadManager.Factory uploads) {
        this.repository = repository;
        this.farmContext = farmContext;
        this.gson = gson;
        this.uploads = uploads;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle h) {
        handle = h;
        Long id = h.get(RouterPath.EXTRA_FARM_ID);
        farmId = id != null && id > 0 ? id : farmContext.farmIdOrZero();
        mode = h.get(RouterPath.EXTRA_MODE);
        scrollTarget = h.get(RouterPath.EXTRA_SCROLL_TARGET);
        name = h.getLiveData(KEY_NAME, "");
        remark = h.getLiveData(KEY_REMARK, "");
        location = h.getLiveData(KEY_LOCATION);
        qualFiles = uploads.create(h, KEY_QUAL, MediaScene.FARM_QUAL, FarmRules.QUAL_MAX);
        qualFiles.setNewItemTag(TAG_NEW, StatusTagView.PRIMARY);

        nameModified.addSource(name, v -> onChanged(F_NAME));
        nameModified.addSource(location, v -> onChanged(F_ADDRESS));
        nameModified.addSource(qualFiles.getItems(), v -> onChanged(F_QUAL));
        nameModified.addSource(remark, v -> onChanged(F_REMARK));
        nameModified.addSource(submitting, v -> updateCanSubmit());
        nameModified.addSource(readOnly, v -> updateCanSubmit());

        if (FarmUi.deniedByContext(farmContext, farmId, farmContext.permissions().isCanSubmitChange())) {
            setUiState(FarmUi.noPermissionState());
            return;
        }
        String json = h.get(FarmExtras.EXTRA_REGISTRATION_JSON);
        RegistrationVO o = json == null ? null : gson.fromJson(json, RegistrationVO.class);
        if (o != null) {
            init(o);
        } else {
            // 没有带比较基准（推送、调试入口）时先取 API-F09 的注册信息
            load(repository.getFarmDetail(farmId), d -> {
                if (d.getRegistration() != null) {
                    init(d.getRegistration());
                }
            });
        }
    }

    /** 以当前生效的注册信息为比较基准初始化表单，并取最近一次资料变更（API-F13） */
    void init(@NonNull RegistrationVO o) {
        origin = o;
        originLive.setValue(o);
        originLocation = new LocationResult(o.getLongitude(), o.getLatitude(), o.getProvince(), o.getCity(),
                o.getDistrict(), o.getTownship(), o.getAdcode(), o.getAddress(), null, o.getLocAccuracy());
        originIds.clear();
        for (QualFileVO f : o.getQualificationFiles()) {
            originIds.add(f.getFileId());
        }
        if (!Boolean.TRUE.equals(handle.get(KEY_PREFILLED))) {
            handle.set(KEY_PREFILLED, true);
            name.setValue(o.getName() == null ? "" : o.getName());
            location.setValue(originLocation);
        }
        qualFiles.setInitialFiles(o.getQualificationFiles());
        recompute();
        launch(repository.getLatestChange(farmId).map(Optional::of).defaultIfEmpty(Optional.empty()),
                opt -> applyLatest(opt.orElse(null)), e -> {
                    // 取不到最近一次变更时按可提交处理，服务端 40906 兜底
                });
        if (scrollTarget != null) {
            scrollToField.setValue(targetField(scrollTarget));
        }
    }

    private void applyLatest(@Nullable FarmChangeVO change) {
        latest.setValue(change);
        readOnly.setValue(change != null && change.isPending());
        if (change != null && FarmChangeVO.STATUS_REJECTED.equals(change.getStatus())
                && FarmExtras.MODE_REEDIT.equals(mode) && !Boolean.TRUE.equals(handle.get("change_reedited"))) {
            handle.set("change_reedited", true);
            ChangeSnapshotVO after = change.getAfter();
            if (after != null) {
                if (after.getName() != null) {
                    name.setValue(after.getName());
                }
                if (after.getAddress() != null) {
                    location.setValue(new LocationResult(after.getLongitude(), after.getLatitude(),
                            after.getProvince(), after.getCity(), after.getDistrict(), after.getTownship(),
                            after.getAdcode(), after.getAddress(), null, after.getLocAccuracy()));
                }
                if (change.getRemark() != null) {
                    remark.setValue(change.getRemark());
                }
            }
        }
    }

    @NonNull
    private static String targetField(@NonNull String target) {
        switch (target) {
            case FarmExtras.TARGET_ADDRESS:
                return F_ADDRESS;
            case FarmExtras.TARGET_QUALIFICATION:
                return F_QUAL;
            default:
                return F_NAME;
        }
    }

    // ===== 比较 =====

    private void onChanged(@NonNull String field) {
        idemKey = null;
        Map<String, String> errors = fieldErrors.getValue();
        if (errors != null && errors.containsKey(field)) {
            Map<String, String> copy = new LinkedHashMap<>(errors);
            copy.remove(field);
            fieldErrors.setValue(copy);
        }
        recompute();
    }

    private void recompute() {
        if (origin == null) {
            updateCanSubmit();
            return;
        }
        boolean nm = !FarmRules.trim(name.getValue()).equals(FarmRules.trim(origin.getName()));
        if (!Boolean.valueOf(nm).equals(nameModified.getValue())) {
            nameModified.setValue(nm);
        }
        LocationResult loc = location.getValue();
        boolean am = loc != null && !loc.sameAddressAs(originLocation);
        setIfChanged(addressModified, am);

        List<UploadItem> items = qualFiles.current();
        Set<Long> currentIds = new HashSet<>();
        int added = 0;
        for (UploadItem item : items) {
            if (item.getFileId() > 0 && originIds.contains(item.getFileId())) {
                currentIds.add(item.getFileId());
            } else {
                added++;
            }
        }
        int removed = originIds.size() - currentIds.size();
        setIfChanged(qualModified, added > 0 || removed > 0);
        qualSummary.setValue(summaryOf(added, removed, items.size()));
        updateCanSubmit();
    }

    /** “新增 1 张，共 4 张”“删除 1 张，共 2 张”；没有增删时为空 */
    @NonNull
    static String summaryOf(int added, int removed, int total) {
        List<String> parts = new ArrayList<>();
        if (added > 0) {
            parts.add("新增 " + added + " 张");
        }
        if (removed > 0) {
            parts.add("删除 " + removed + " 张");
        }
        if (parts.isEmpty()) {
            return "";
        }
        return TextUtils.join("、", parts) + "，共 " + total + " 张";
    }

    private void updateCanSubmit() {
        boolean ok = origin != null && !Boolean.TRUE.equals(submitting.getValue())
                && !Boolean.TRUE.equals(readOnly.getValue())
                && !FarmRules.trim(name.getValue()).isEmpty() && location.getValue() != null
                && !qualFiles.current().isEmpty();
        setIfChanged(canSubmit, ok);
    }

    private static void setIfChanged(@NonNull MutableLiveData<Boolean> live, boolean v) {
        if (!Boolean.valueOf(v).equals(live.getValue())) {
            live.setValue(v);
        }
    }

    public boolean isAnyModified() {
        return Boolean.TRUE.equals(nameModified.getValue()) || Boolean.TRUE.equals(addressModified.getValue())
                || Boolean.TRUE.equals(qualModified.getValue());
    }

    // ===== 输入 =====

    public void onNameFocusLost() {
        String n = FarmRules.trim(name.getValue());
        if (!Boolean.TRUE.equals(nameModified.getValue()) || n.isEmpty() || FarmRules.length(n) > FarmRules.NAME_MAX) {
            return;
        }
        launch(repository.checkName(n, farmId), r -> {
            if (!r.isAvailable() && n.equals(FarmRules.trim(name.getValue()))) {
                putError(F_NAME, r.getMessage() != null ? r.getMessage() : String.format(FarmRules.MSG_NAME_DUP, n));
            }
        }, e -> {
        });
    }

    public void onAddressClick() {
        if (Boolean.TRUE.equals(readOnly.getValue())) {
            return;
        }
        openMapPick.setValue(new PickLocationInput(PickLocationInput.MODE_CHANGE,
                origin == null ? null : origin.getName(), location.getValue()));
    }

    public void onLocationPicked(@Nullable LocationResult result) {
        if (result != null) {
            location.setValue(result);
        }
    }

    // ===== 提交 =====

    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue()) || origin == null) {
            return;
        }
        if (Boolean.TRUE.equals(readOnly.getValue())) {
            showMessage(MSG_PENDING);
            return;
        }
        if (!isAnyModified()) {
            showMessage(FarmRules.MSG_NO_CHANGE);
            return;
        }
        String n = FarmRules.trim(name.getValue());
        FormValidator v = new FormValidator()
                .required(F_NAME, n, FarmRules.MSG_NAME_EMPTY)
                .check(F_NAME, FarmRules.length(n) <= FarmRules.NAME_MAX, FarmRules.MSG_NAME_TOO_LONG)
                .check(F_ADDRESS, FarmRules.isLocationValid(location.getValue()), FarmRules.MSG_ADDRESS)
                .maxLength(F_REMARK, FarmRules.trim(remark.getValue()), FarmRules.REMARK_MAX,
                        FarmRules.MSG_REMARK_TOO_LONG);
        Map<String, String> old = fieldErrors.getValue();
        if (old != null && old.containsKey(F_NAME)) {
            v.check(F_NAME, false, old.get(F_NAME));
        }
        if (!qualFiles.hasUploading() && !qualFiles.hasFailed()) {
            v.check(F_QUAL, !qualFiles.getFileIds().isEmpty(), FarmRules.MSG_QUAL_KEEP_ONE);
        }
        Map<String, String> errors = v.validate();
        if (!errors.isEmpty()) {
            fieldErrors.setValue(errors);
            scrollToField.setValue(errors.keySet().iterator().next());
            return;
        }
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
        LocationResult loc = location.getValue();
        String r = FarmRules.trim(remark.getValue());
        SubmitFarmChangeRequest req = new SubmitFarmChangeRequest()
                .setName(n)
                .setQualificationFileIds(qualFiles.getFileIds())
                .setRemark(r.isEmpty() ? null : r);
        if (loc != null) {
            Integer accuracy = LocationResult.SOURCE_LOCATION.equals(loc.getSource()) ? loc.getLocAccuracy() : null;
            req.setLocation(FarmRules.trim(loc.getAddress()), loc.getProvince(), loc.getCity(), loc.getDistrict(),
                    loc.getTownship(), loc.getAdcode(), loc.getLongitude(), loc.getLatitude(), accuracy);
        }
        submitting.setValue(true);
        launch(repository.submitChange(farmId, req, idemKey), change -> {
            submitting.setValue(false);
            idemKey = null;
            submitted.call();
        }, this::onSubmitError);
    }

    static final String MSG_PENDING = "资料变更审核中，审核期间不能再次提交。";

    private void onSubmitError(@NonNull ApiException e) {
        submitting.setValue(false);
        int code = e.getCode();
        if (e.isNetwork()) {
            showMessage(e.keptMessage(FarmRules.MSG_NETWORK_KEPT));
        } else if (code == ErrorCodes.PENDING_EXISTS) {
            showMessage(e.getMessage() != null ? e.getMessage() : MSG_PENDING);
            readOnly.setValue(true);
        } else if (code == ErrorCodes.NAME_DUPLICATE) {
            putError(F_NAME, e.getMessage());
            scrollToField.setValue(F_NAME);
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

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        if (origin == null) {
            super.onNoPermission(e);
        } else {
            showMessage(e.getMessage());
        }
    }

    @Override
    public void retry() {
        if (origin == null) {
            load(repository.getFarmDetail(farmId), d -> {
                if (d.getRegistration() != null) {
                    init(d.getRegistration());
                }
            });
        }
    }

    @Override
    protected void onDataChanged(@NonNull ApiException e) {
        // 40903：状态已变化，重新取最近一次变更
        launch(repository.getLatestChange(farmId).map(Optional::of).defaultIfEmpty(Optional.empty()),
                opt -> applyLatest(opt.orElse(null)), err -> {
                });
    }

    private void putError(@NonNull String field, @Nullable String message) {
        Map<String, String> errors = fieldErrors.getValue();
        Map<String, String> copy = errors == null ? new LinkedHashMap<>() : new LinkedHashMap<>(errors);
        copy.put(field, message == null ? "" : message);
        fieldErrors.setValue(copy);
    }

    @Override
    protected void onCleared() {
        if (qualFiles != null) {
            qualFiles.release();
        }
        super.onCleared();
    }

    // ===== 对外 =====

    public long getFarmId() {
        return farmId;
    }

    public LiveData<RegistrationVO> getOrigin() {
        return originLive;
    }

    public MutableLiveData<String> getName() {
        return name;
    }

    public MutableLiveData<String> getRemark() {
        return remark;
    }

    public LiveData<LocationResult> getLocation() {
        return location;
    }

    public UploadManager getQualFiles() {
        return qualFiles;
    }

    public LiveData<FarmChangeVO> getLatest() {
        return latest;
    }

    public LiveData<Boolean> getReadOnly() {
        return readOnly;
    }

    public LiveData<Boolean> getNameModified() {
        return nameModified;
    }

    public LiveData<Boolean> getAddressModified() {
        return addressModified;
    }

    public LiveData<Boolean> getQualModified() {
        return qualModified;
    }

    public LiveData<String> getQualSummary() {
        return qualSummary;
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

    public SingleLiveEvent<String> getScrollToField() {
        return scrollToField;
    }

    public SingleLiveEvent<PickLocationInput> getOpenMapPick() {
        return openMapPick;
    }

    public SingleLiveEvent<Void> getSubmitted() {
        return submitted;
    }
}
