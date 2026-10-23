package com.zhimu.slmp.health.ui.disinfectregister;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.data.DisinfectRepository;
import com.zhimu.slmp.health.data.HealthSharedRepository;
import com.zhimu.slmp.health.data.ImmuneRepository;
import com.zhimu.slmp.health.model.request.DisinfectRecordCreateRequest;
import com.zhimu.slmp.health.model.request.DisinfectRecordUpdateRequest;
import com.zhimu.slmp.health.model.vo.DisinfectRecordVO;
import com.zhimu.slmp.health.model.vo.DisinfectTaskOptionVO;
import com.zhimu.slmp.health.model.vo.DisinfectantOptionVO;
import com.zhimu.slmp.health.model.vo.DrugLotOptionVO;
import com.zhimu.slmp.health.model.vo.DrugLotOptionsVO;
import com.zhimu.slmp.health.model.vo.MemberItemVO;
import com.zhimu.slmp.health.model.vo.PenOptionVO;
import com.zhimu.slmp.health.ui.common.DraftStore;
import com.zhimu.slmp.health.ui.common.RecordLogHost;
import com.zhimu.slmp.health.util.ConcentrationUtil;
import com.zhimu.slmp.health.util.HealthFormRules;
import com.zhimu.slmp.health.util.HealthFormat;
import com.zhimu.slmp.health.util.LotPicker;
import com.zhimu.slmp.health.util.SubmitSupport;
import com.zhimu.slmp.media.data.MediaScene;
import com.zhimu.slmp.media.upload.UploadManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * D08 登记消毒（REGISTER 计划任务 / BACKFILL 逾期补录 / TEMP 空栏或紧急消毒 / PICK 未带任务 / VIEW 查看 / EDIT 修改）：
 * 登记类型、消毒任务（API-D37）或栏舍（临时消毒，本场启用中栏舍 API-L07）、实际时间（日期 + 24 小时制，不晚于现在）、
 * 实际执行人（员工不显示）、消毒剂批号（API-M27）、用量（不超过结存）、浓度（与计划不一致时必须确认）、消毒方式、
 * 现场照片（lib_media，场景 DISINFECT，最多 6 张）。提交 API-D38（Idempotency-Key，超时 API-D41），修改 API-D40。
 */
public class DisinfectRegisterViewModel extends BaseViewModel implements RecordLogHost {

    public static final String PAGE_CODE = "D08";
    public static final int MAX_PHOTOS = 6;
    public static final String MODE_REGISTER = "REGISTER";
    public static final String MODE_BACKFILL = "BACKFILL";
    public static final String MODE_TEMP = "TEMP";
    public static final String MODE_PICK = "PICK";
    public static final String MODE_VIEW = "VIEW";
    public static final String MODE_EDIT = "EDIT";
    public static final String MSG_PHOTO_UPLOADING = "照片还在上传，请稍候";
    public static final String DEFAULT_UNIT = "升";

    public static final String F_TASK = "task_id";
    public static final String F_PEN = "pen_id";
    public static final String F_DRUG = "drug_id";
    public static final String F_LOT = "drug_lot_id";
    public static final String F_TIME = "actual_time";
    public static final String F_USAGE = "usage_qty";
    public static final String F_CONCENTRATION = "concentration";
    public static final String F_CONFIRMED = "concentration_confirmed";
    public static final String F_METHOD = "method_code";

    private static final DateTimeFormatter REQUEST_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 草稿内容 */
    static final class Draft {
        String recordType;
        Long taskId;
        Long penId;
        Long drugId;
        Long drugLotId;
        String actualTime;
        String usageQty;
        String concentration;
        Boolean concentrationConfirmed;
        String methodCode;
        Long executorId;
    }

    private final DisinfectRepository repository;
    private final ImmuneRepository immuneRepository;
    private final HealthSharedRepository shared;
    private final FarmContext farmContext;
    private final UploadManager.Factory uploads;
    private final DraftStore<Draft> drafts;

    private final MutableLiveData<String> mode = new MutableLiveData<>(MODE_REGISTER);
    private final MutableLiveData<String> recordType = new MutableLiveData<>(HealthExtras.RECORD_PLAN_TASK);
    private final MutableLiveData<List<DisinfectTaskOptionVO>> taskOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<DisinfectTaskOptionVO> selectedTask = new MutableLiveData<>();
    private final MutableLiveData<List<PenOptionVO>> pens = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<DisinfectantOptionVO>> disinfectants = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<DictItemVO>> methods = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<DrugLotOptionsVO> lotOptions = new MutableLiveData<>();
    private final MutableLiveData<List<MemberItemVO>> executors = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> taskId = new MutableLiveData<>();
    private final MutableLiveData<Long> penId = new MutableLiveData<>();
    private final MutableLiveData<Long> executorId = new MutableLiveData<>();
    private final MutableLiveData<Long> drugId = new MutableLiveData<>();
    private final MutableLiveData<Long> drugLotId = new MutableLiveData<>();
    private final MutableLiveData<String> actualTime = new MutableLiveData<>(nowText());
    private final MutableLiveData<String> usageQty = new MutableLiveData<>("");
    private final MutableLiveData<String> concentration = new MutableLiveData<>("");
    private final MutableLiveData<String> methodCode = new MutableLiveData<>();
    private final MutableLiveData<Boolean> concentrationConfirmed = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> concentrationMismatch = new MediatorLiveData<>();
    private final MutableLiveData<DisinfectRecordVO> record = new MutableLiveData<>();
    private final MutableLiveData<List<RecordLogVO>> logs = new MutableLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    private final SingleLiveEvent<String> finishWithSuccess = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> backToTasks = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> scrollToNotice = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> scrollToField = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showLotSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> needChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> restoreDraftEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> discardEvent = new SingleLiveEvent<>();

    private final SubmitSupport.IdemKeeper idem = new SubmitSupport.IdemKeeper();
    @Nullable
    private UploadManager photos;
    @Nullable
    private Long recordId;
    @Nullable
    private Long todoId;
    @Nullable
    private Long presetTaskId;
    @Nullable
    private String draftKey;
    @Nullable
    private Draft pendingDraft;
    @Nullable
    private String initialSnapshot;
    private boolean finished;
    private boolean backfill;
    @Nullable
    private String pendingLotNo;

    @Inject
    public DisinfectRegisterViewModel(@NonNull DisinfectRepository repository, @NonNull ImmuneRepository immuneRepository,
                                      @NonNull HealthSharedRepository shared, @NonNull FarmContext farmContext,
                                      @NonNull AppDatabase database, @NonNull UploadManager.Factory uploads) {
        this.repository = repository;
        this.immuneRepository = immuneRepository;
        this.shared = shared;
        this.farmContext = farmContext;
        this.uploads = uploads;
        this.drafts = new DraftStore<>(database.formDraftDao(), PAGE_CODE, Draft.class, disposables);
        concentrationMismatch.addSource(concentration, v -> concentrationMismatch.setValue(computeMismatch()));
        concentrationMismatch.addSource(selectedTask, v -> concentrationMismatch.setValue(computeMismatch()));
        concentrationMismatch.addSource(recordType, v -> concentrationMismatch.setValue(computeMismatch()));
        canSubmit.addSource(submitting, v -> canSubmit.setValue(!Boolean.TRUE.equals(v)));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        idem.attach(handle);
        photos = uploads.create(handle, "photos", MediaScene.DISINFECT, MAX_PHOTOS);
        todoId = asLong(handle.get(RouterPath.EXTRA_TODO_ID));
        Object b = handle.get(RouterPath.EXTRA_BACKFILL);
        init(asLong(handle.get(RouterPath.EXTRA_TASK_ID)), handle.get(RouterPath.EXTRA_RECORD_TYPE),
                Boolean.TRUE.equals(b) || "true".equals(b), asLong(handle.get(RouterPath.EXTRA_RECORD_ID)),
                asLong(handle.get(RouterPath.EXTRA_PEN_ID)));
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

    @NonNull
    static String nowText() {
        return LocalDateTime.now(DateFormatter.ZONE).withSecond(0).withNano(0).format(REQUEST_TIME);
    }

    // ===== 对外 =====

    public LiveData<String> getMode() { return mode; }

    public MutableLiveData<String> getRecordType() { return recordType; }

    public LiveData<List<DisinfectTaskOptionVO>> getTaskOptions() { return taskOptions; }

    public LiveData<DisinfectTaskOptionVO> getSelectedTask() { return selectedTask; }

    public LiveData<List<PenOptionVO>> getPens() { return pens; }

    public LiveData<List<DisinfectantOptionVO>> getDisinfectants() { return disinfectants; }

    public LiveData<List<DictItemVO>> getMethods() { return methods; }

    public LiveData<DrugLotOptionsVO> getLotOptions() { return lotOptions; }

    public LiveData<List<MemberItemVO>> getExecutors() { return executors; }

    public MutableLiveData<Long> getTaskId() { return taskId; }

    public MutableLiveData<Long> getPenId() { return penId; }

    public MutableLiveData<Long> getExecutorId() { return executorId; }

    public MutableLiveData<Long> getDrugId() { return drugId; }

    public MutableLiveData<Long> getDrugLotId() { return drugLotId; }

    public MutableLiveData<String> getActualTime() { return actualTime; }

    public MutableLiveData<String> getUsageQty() { return usageQty; }

    public MutableLiveData<String> getConcentration() { return concentration; }

    public MutableLiveData<String> getMethodCode() { return methodCode; }

    public MutableLiveData<Boolean> getConcentrationConfirmed() { return concentrationConfirmed; }

    public LiveData<Boolean> getConcentrationMismatch() { return concentrationMismatch; }

    public LiveData<DisinfectRecordVO> getRecord() { return record; }

    @Override
    public LiveData<List<RecordLogVO>> getLogs() { return logs; }

    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }

    public LiveData<Boolean> getSubmitting() { return submitting; }

    public LiveData<Boolean> getCanSubmit() { return canSubmit; }

    @NonNull
    public UploadManager getPhotos() { return Objects.requireNonNull(photos); }

    public SingleLiveEvent<String> getFinishWithSuccess() { return finishWithSuccess; }

    public SingleLiveEvent<String> getBackToTasks() { return backToTasks; }

    public SingleLiveEvent<Void> getScrollToNotice() { return scrollToNotice; }

    public SingleLiveEvent<String> getScrollToField() { return scrollToField; }

    public SingleLiveEvent<Void> getShowLotSheet() { return showLotSheet; }

    public SingleLiveEvent<String> getNeedChangeRequest() { return needChangeRequest; }

    public SingleLiveEvent<Long> getOpenChangeRequest() { return openChangeRequest; }

    public SingleLiveEvent<Long> getOpenRequest() { return openRequest; }

    public SingleLiveEvent<Void> getRestoreDraftEvent() { return restoreDraftEvent; }

    public SingleLiveEvent<Void> getDiscardEvent() { return discardEvent; }

    public boolean isStaff() { return farmContext.isStaff(); }

    public boolean isPlanTask() {
        return HealthExtras.RECORD_PLAN_TASK.equals(recordType.getValue());
    }

    public boolean isEditable() {
        return !MODE_VIEW.equals(mode.getValue());
    }

    public boolean isEditMode() {
        return MODE_EDIT.equals(mode.getValue());
    }

    @Nullable
    public Long getRecordId() { return recordId; }

    // ===== 初始化 =====

    public void init(@Nullable Long task, @Nullable String type, boolean backfill, @Nullable Long recordId,
                     @Nullable Long presetPen) {
        this.recordId = recordId;
        this.backfill = backfill;
        this.presetTaskId = task;
        Single<List<DictItemVO>> methodList = shared.getDisinfectMethods().onErrorReturnItem(Collections.emptyList());
        if (recordId != null) {
            mode.setValue(MODE_VIEW);
            load(Single.zip(repository.getRecord(recordId), methodList, (r, m) -> new Object[]{r, m}), arr -> {
                @SuppressWarnings("unchecked")
                List<DictItemVO> m = (List<DictItemVO>) arr[1];
                methods.setValue(m);
                showRecord((DisinfectRecordVO) arr[0]);
            });
            return;
        }
        boolean temp = HealthExtras.RECORD_EMPTY_PEN.equals(type) || HealthExtras.RECORD_EMERGENCY.equals(type);
        recordType.setValue(temp ? type : HealthExtras.RECORD_PLAN_TASK);
        mode.setValue(temp ? MODE_TEMP : task == null ? MODE_PICK : backfill ? MODE_BACKFILL : MODE_REGISTER);
        draftKey = task != null ? "T" + task : temp ? "P" + (presetPen == null ? "" : presetPen) : "";
        Single<List<DisinfectTaskOptionVO>> tasks = repository.getTaskOptions().onErrorReturnItem(Collections.emptyList());
        Single<List<PenOptionVO>> penList = shared.getActivePens().onErrorReturnItem(Collections.emptyList());
        Single<List<DisinfectantOptionVO>> options = repository.getDisinfectants(null)
                .onErrorReturnItem(Collections.emptyList());
        Single<List<MemberItemVO>> members = isStaff() ? Single.just(Collections.emptyList())
                : shared.getMembers().onErrorReturnItem(Collections.emptyList());
        load(Single.zip(tasks, penList, options, methodList, members, (a, b, c, d, e) -> new Object[]{a, b, c, d, e}),
                arr -> {
                    @SuppressWarnings("unchecked")
                    List<DisinfectTaskOptionVO> t = (List<DisinfectTaskOptionVO>) arr[0];
                    @SuppressWarnings("unchecked")
                    List<PenOptionVO> p = (List<PenOptionVO>) arr[1];
                    @SuppressWarnings("unchecked")
                    List<DisinfectantOptionVO> o = (List<DisinfectantOptionVO>) arr[2];
                    @SuppressWarnings("unchecked")
                    List<DictItemVO> m = (List<DictItemVO>) arr[3];
                    @SuppressWarnings("unchecked")
                    List<MemberItemVO> mem = (List<MemberItemVO>) arr[4];
                    taskOptions.setValue(t);
                    pens.setValue(p);
                    disinfectants.setValue(o);
                    methods.setValue(m);
                    executors.setValue(mem);
                    if (task != null) {
                        DisinfectTaskOptionVO option = taskOption(task);
                        if (option != null) {
                            selectTask(option);
                        } else {
                            taskId.setValue(null);
                            showMessage("这条消毒任务不在可登记的列表中，请重新选择");
                        }
                    } else if (temp && presetPen != null) {
                        selectPen(presetPen);
                    }
                    initialSnapshot = snapshot();
                    checkDraft();
                });
    }

    @Override
    public void retry() {
        init(presetTaskId, recordType.getValue(), backfill, recordId, penId.getValue());
    }

    // ===== 类型、任务与栏舍 =====

    /** 切换“计划任务 / 空栏消毒 / 紧急消毒”：任务与栏舍字段互换；实际时间、执行人、用量、照片保留 */
    public void onRecordTypeChanged(@NonNull String type) {
        if (type.equals(recordType.getValue()) || !isEditable() || isEditMode()) {
            return;
        }
        boolean wasPlan = isPlanTask();
        recordType.setValue(type);
        clearError(F_TASK);
        clearError(F_PEN);
        clearError(F_CONFIRMED);
        if (wasPlan && !isPlanTask()) {
            // 临时消毒：批号不预填，方式默认不选，不与计划浓度比较
            selectedTask.setValue(null);
            taskId.setValue(null);
            penId.setValue(null);
            drugId.setValue(null);
            drugLotId.setValue(null);
            lotOptions.setValue(null);
            methodCode.setValue(null);
            concentrationConfirmed.setValue(false);
        } else if (!wasPlan && isPlanTask()) {
            penId.setValue(null);
            drugId.setValue(null);
            drugLotId.setValue(null);
            lotOptions.setValue(null);
        }
    }

    @Nullable
    DisinfectTaskOptionVO taskOption(@Nullable Long id) {
        List<DisinfectTaskOptionVO> list = taskOptions.getValue();
        if (id == null || list == null) {
            return null;
        }
        for (DisinfectTaskOptionVO t : list) {
            if (t.getTaskId() == id) {
                return t;
            }
        }
        return null;
    }

    public void onTaskSelected(long id) {
        DisinfectTaskOptionVO t = taskOption(id);
        if (t != null) {
            selectTask(t);
        }
    }

    /** 选择任务：预填栏舍、消毒剂与默认批号、计划浓度、方式、执行人；已逾期的任务为补录 */
    void selectTask(@NonNull DisinfectTaskOptionVO t) {
        selectedTask.setValue(t);
        taskId.setValue(t.getTaskId());
        penId.setValue(t.getPenId());
        clearError(F_TASK);
        if (t.isOverdue()) {
            backfill = true;
            if (MODE_REGISTER.equals(mode.getValue()) || MODE_PICK.equals(mode.getValue())) {
                mode.setValue(MODE_BACKFILL);
            }
        }
        methodCode.setValue(t.getMethodCode());
        if (t.getPlanConcentration() != null) {
            concentration.setValue(t.getPlanConcentration());
        }
        concentrationConfirmed.setValue(false);
        if (!isStaff() && t.getDefaultExecutorId() != null) {
            executorId.setValue(t.getDefaultExecutorId());
        }
        drugId.setValue(t.getDrugId());
        drugLotId.setValue(null);
        lotOptions.setValue(null);
        if (t.getDrugId() != null) {
            queryLots(t.getDrugId(), t.getDefaultLotId());
        } else {
            putError(F_LOT, LotPicker.noLotText(disinfectantName(), isStaff()));
        }
    }

    public void onPenSelected(long id) {
        selectPen(id);
    }

    private void selectPen(long id) {
        penId.setValue(id);
        clearError(F_PEN);
    }

    /** 临时消毒选择消毒剂：没有本场档案（drug_id 为空）时提示先入库 */
    public void onDisinfectantSelected(long catalogId) {
        DisinfectantOptionVO o = disinfectantByCatalog(catalogId);
        if (o == null) {
            return;
        }
        clearError(F_DRUG);
        drugLotId.setValue(null);
        lotOptions.setValue(null);
        drugId.setValue(o.getDrugId());
        if (HealthFormat.nonNull(concentration.getValue()).trim().isEmpty() && o.getRecommendedConcentration() != null) {
            concentration.setValue(o.getRecommendedConcentration());
        }
        if (o.getDrugId() == null) {
            putError(F_LOT, LotPicker.noLotText(o.getName(), isStaff()));
            return;
        }
        queryLots(o.getDrugId(), null);
    }

    @Nullable
    DisinfectantOptionVO disinfectantByCatalog(long catalogId) {
        List<DisinfectantOptionVO> list = disinfectants.getValue();
        if (list == null) {
            return null;
        }
        for (DisinfectantOptionVO o : list) {
            if (o.getCatalogId() == catalogId) {
                return o;
            }
        }
        return null;
    }

    @Nullable
    DisinfectantOptionVO disinfectantByDrug(@Nullable Long drug) {
        List<DisinfectantOptionVO> list = disinfectants.getValue();
        if (drug == null || list == null) {
            return null;
        }
        for (DisinfectantOptionVO o : list) {
            if (Objects.equals(o.getDrugId(), drug)) {
                return o;
            }
        }
        return null;
    }

    /** 当前消毒剂的目录 ID（临时消毒下拉回显） */
    @Nullable
    public Long currentCatalogId() {
        DisinfectantOptionVO o = disinfectantByDrug(drugId.getValue());
        return o == null ? null : o.getCatalogId();
    }

    @Nullable
    public String disinfectantName() {
        DisinfectantOptionVO o = disinfectantByDrug(drugId.getValue());
        if (o != null) {
            return o.getName();
        }
        DisinfectRecordVO r = record.getValue();
        return r == null ? null : r.getDisinfectantName();
    }

    @NonNull
    public String usageUnit() {
        DisinfectantOptionVO o = disinfectantByDrug(drugId.getValue());
        if (o != null && o.getStockUnit() != null) {
            return o.getStockUnit();
        }
        DisinfectRecordVO r = record.getValue();
        return r != null && r.getUsageUnit() != null ? r.getUsageUnit() : DEFAULT_UNIT;
    }

    // ===== 批号 =====

    void queryLots(long drug, @Nullable Long keepLotId) {
        launch(shared.getLotOptions(drug, HealthExtras.PURPOSE_DISINFECT, null), options -> {
            lotOptions.setValue(options);
            DrugLotOptionVO keep = LotPicker.find(options, keepLotId);
            if (keep == null && pendingLotNo != null) {
                keep = LotPicker.findByNo(options, pendingLotNo);
            }
            pendingLotNo = null;
            DrugLotOptionVO lot = keep != null ? keep : LotPicker.defaultLot(options);
            // 默认批号在快照之后异步填入：表单尚未被用户改动时同步更新快照，避免没填写就按返回也提示“放弃这次登记？”
            boolean untouched = initialSnapshot != null && initialSnapshot.equals(snapshot());
            drugLotId.setValue(lot == null ? null : lot.getLotId());
            if (untouched) {
                initialSnapshot = snapshot();
            }
            if (lot == null) {
                putError(F_LOT, LotPicker.noLotText(disinfectantName(), isStaff()));
            } else {
                clearError(F_LOT);
            }
        }, e -> showMessage(e.isNetwork() ? ApiException.MSG_NETWORK : e.getMessage()));
    }

    public void onLotFieldClick() {
        if (isEditable() && lotOptions.getValue() != null) {
            showLotSheet.call();
        }
    }

    public void selectLot(long lotId) {
        if (LotPicker.find(lotOptions.getValue(), lotId) != null) {
            drugLotId.setValue(lotId);
            clearError(F_LOT);
            clearError(F_USAGE);
        }
    }

    @Nullable
    public DrugLotOptionVO selectedLot() {
        return LotPicker.find(lotOptions.getValue(), drugLotId.getValue());
    }

    /** 批号字段：“聚维酮碘溶液 · 2025033” */
    @NonNull
    public String lotText() {
        DrugLotOptionVO lot = selectedLot();
        String name = disinfectantName();
        if (lot == null) {
            DisinfectRecordVO r = record.getValue();
            if (r != null && lotOptions.getValue() == null) {
                return HealthFormat.nonNull(r.getDisinfectantName()) + HealthFormat.DOT + HealthFormat.nonNull(r.getLotNo());
            }
            return "";
        }
        return (name == null ? "" : name + HealthFormat.DOT) + HealthFormat.nonNull(lot.getLotNo());
    }

    @Nullable
    public String lotHelper() {
        DrugLotOptionVO lot = selectedLot();
        if (lot == null) {
            return null;
        }
        return LotPicker.helperText(lot, false) + HealthFormat.DOT + "结存 " + HealthFormat.qty(lot.getStock(), usageUnit());
    }

    // ===== 浓度 =====

    boolean computeMismatch() {
        DisinfectTaskOptionVO t = selectedTask.getValue();
        String planned = t != null ? t.getPlanConcentration() : null;
        if (planned == null) {
            DisinfectRecordVO r = record.getValue();
            planned = r == null ? null : r.getPlanConcentration();
        }
        return isPlanTask() && planned != null && ConcentrationUtil.differs(concentration.getValue(), planned);
    }

    /** 浓度变化：需要重新确认 */
    public void onConcentrationChanged() {
        if (Boolean.TRUE.equals(concentrationConfirmed.getValue())) {
            concentrationConfirmed.setValue(false);
        }
        clearError(F_CONCENTRATION);
    }

    /** 横幅“确认” */
    public void confirmConcentration() {
        concentrationConfirmed.setValue(true);
        clearError(F_CONFIRMED);
    }

    @Nullable
    public String plannedConcentration() {
        DisinfectTaskOptionVO t = selectedTask.getValue();
        if (t != null) {
            return t.getPlanConcentration();
        }
        DisinfectRecordVO r = record.getValue();
        return r == null ? null : r.getPlanConcentration();
    }

    // ===== 时间、执行人、方式 =====

    public void onActualDateTimeSelected(@Nullable String date, @Nullable String time) {
        if (date == null || time == null) {
            return;
        }
        actualTime.setValue(date + " " + time + ":00");
        clearError(F_TIME);
    }

    public void onExecutorSelected(long userId) {
        executorId.setValue(userId);
    }

    public void onMethodSelected(@Nullable String code) {
        methodCode.setValue(code);
        clearError(F_METHOD);
    }

    /** 补录时实际时间不早于任务日期 00:00 */
    @Nullable
    public String minDate() {
        DisinfectTaskOptionVO t = selectedTask.getValue();
        if (t != null) {
            return t.getTaskDate();
        }
        DisinfectRecordVO r = record.getValue();
        return r == null ? null : r.getTaskDate();
    }

    // ===== 校验与提交 =====

    @NonNull
    Map<String, String> validate(@NonNull LocalDateTime now) {
        Map<String, String> e = new LinkedHashMap<>();
        boolean edit = isEditMode();
        if (!edit) {
            if (isPlanTask() && taskId.getValue() == null) {
                List<DisinfectTaskOptionVO> list = taskOptions.getValue();
                e.put(F_TASK, list == null || list.isEmpty()
                        ? "今天没有待执行的消毒任务。可以切换到空栏消毒或紧急消毒临时登记。" : "请选择消毒任务");
            }
            if (!isPlanTask() && penId.getValue() == null) {
                e.put(F_PEN, "请选择栏舍");
            }
            if (!isPlanTask() && drugId.getValue() == null && !hasError(F_LOT)) {
                e.put(F_DRUG, "请选择消毒剂");
            }
        }
        DrugLotOptionVO lot = selectedLot();
        if (lot == null && (drugId.getValue() != null || isPlanTask())) {
            Map<String, String> current = fieldErrors.getValue();
            String lotErr = current == null ? null : current.get(F_LOT);
            e.put(F_LOT, lotErr != null ? lotErr : HealthFormRules.MSG_LOT);
        }
        String usage = HealthFormRules.usageQty(usageQty.getValue(), lot == null ? null : lot.getStock(),
                lot == null ? null : lot.getLotNo(), usageUnit());
        if (usage != null) {
            e.put(F_USAGE, usage);
        }
        String c = HealthFormRules.concentration(concentration.getValue());
        if (c != null) {
            e.put(F_CONCENTRATION, c);
        }
        if (methodCode.getValue() == null) {
            e.put(F_METHOD, HealthFormRules.MSG_DISINFECT_METHOD);
        }
        String t = HealthFormRules.actualTime(actualTime.getValue(), backfill || edit ? minDate() : null, now);
        if (t != null) {
            e.put(F_TIME, t);
        }
        if (Boolean.TRUE.equals(concentrationMismatch.getValue()) && !Boolean.TRUE.equals(concentrationConfirmed.getValue())) {
            e.put(F_CONFIRMED, HealthFormRules.MSG_CONFIRM_CONCENTRATION);
        }
        return e;
    }

    private boolean hasError(@NonNull String field) {
        Map<String, String> m = fieldErrors.getValue();
        return m != null && m.containsKey(field);
    }

    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> errors = validate(LocalDateTime.now(DateFormatter.ZONE));
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            if (errors.size() == 1 && errors.containsKey(F_CONFIRMED)) {
                scrollToNotice.call();
            } else {
                scrollToField.setValue(errors.keySet().iterator().next());
            }
            return;
        }
        UploadManager grid = photos;
        if (grid != null && grid.hasUploading()) {
            showMessage(MSG_PHOTO_UPLOADING);
            return;
        }
        String block = grid == null ? null : grid.getSubmitBlockMessage();
        if (block != null) {
            showMessage(block);
            return;
        }
        List<Long> fileIds = grid == null ? Collections.emptyList() : grid.getFileIds();
        Long executor = isStaff() ? null : executorId.getValue();
        DrugLotOptionVO lot = Objects.requireNonNull(selectedLot());
        boolean mismatch = Boolean.TRUE.equals(concentrationMismatch.getValue());
        submitting.setValue(true);
        if (isEditMode() && recordId != null) {
            DisinfectRecordVO r = record.getValue();
            DisinfectRecordUpdateRequest req = new DisinfectRecordUpdateRequest();
            req.actualTime = actualTime.getValue();
            req.executorId = executor;
            req.drugLotId = lot.getLotId();
            req.usageQty = HealthFormRules.parseDecimal(usageQty.getValue());
            req.concentration = ConcentrationUtil.normalize(concentration.getValue());
            req.concentrationConfirmed = mismatch ? Boolean.TRUE : null;
            req.methodCode = methodCode.getValue();
            req.fileIds = fileIds;
            req.version = r == null ? 0 : r.getVersion();
            launch(repository.updateRecord(recordId, req), vo -> {
                submitting.setValue(false);
                finished = true;
                finishWithSuccess.setValue("已保存修改");
            }, this::onSubmitError);
            return;
        }
        DisinfectRecordCreateRequest req = new DisinfectRecordCreateRequest();
        req.recordType = recordType.getValue();
        req.taskId = isPlanTask() ? taskId.getValue() : null;
        req.penId = isPlanTask() ? null : penId.getValue();
        req.actualTime = actualTime.getValue();
        req.executorId = executor;
        req.drugId = Objects.requireNonNull(drugId.getValue());
        req.drugLotId = lot.getLotId();
        req.usageQty = HealthFormRules.parseDecimal(usageQty.getValue());
        req.concentration = ConcentrationUtil.normalize(concentration.getValue());
        req.methodCode = methodCode.getValue();
        req.concentrationConfirmed = mismatch ? Boolean.TRUE : null;
        req.fileIds = fileIds.isEmpty() ? null : fileIds;
        launch(repository.createRecord(req, idem.keyFor(req)), this::onCreated, this::onSubmitError);
    }

    private void onCreated(@NonNull DisinfectRecordVO vo) {
        submitting.setValue(false);
        idem.reset();
        finished = true;
        discardDraft();
        notifyTodo(vo.getId());
        finishWithSuccess.setValue(vo.getToast() != null ? vo.getToast() : successText());
    }

    @NonNull
    private String successText() {
        String type = recordType.getValue();
        if (HealthExtras.RECORD_EMPTY_PEN.equals(type)) {
            return "已登记空栏消毒";
        }
        if (HealthExtras.RECORD_EMERGENCY.equals(type)) {
            return "已登记紧急消毒";
        }
        return "已登记消毒，待办已自动完成";
    }

    private void notifyTodo(long id) {
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.notifyTodoChanged(new TodoChange(todoId, HealthExtras.BIZ_DISINFECT_RECORD, id,
                    farmContext.farmIdOrZero()));
        }
    }

    private void onSubmitError(@NonNull ApiException e) {
        if (!isEditMode() && SubmitSupport.isTimeout(e)) {
            confirmByRequestId();
            return;
        }
        submitting.setValue(false);
        switch (e.getCode()) {
            case ErrorCodes.STOCK_NOT_ENOUGH:
            case ErrorCodes.LOT_UNAVAILABLE:
                String msg = e.getMessage();
                putError(msg.contains("结存") && !msg.contains("没有可用") ? F_USAGE : F_LOT, msg);
                scrollToField.setValue(F_LOT);
                Long drug = drugId.getValue();
                if (drug != null) {
                    queryLots(drug, drugLotId.getValue());
                }
                return;
            case ErrorCodes.STATE_CHANGED:
                backToTasks.setValue(e.getMessage());
                return;
            case ErrorCodes.FILE_INVALID:
                showMessage(e.getMessage());
                return;
            case ErrorCodes.CHANGE_REQUEST_REQUIRED:
                if (isStaff()) {
                    showMessage(e.getMessage());
                } else {
                    needChangeRequest.setValue(e.getMessage());
                }
                return;
            default:
                break;
        }
        if (ErrorCodes.isNoPermission(e.getCode())) {
            showMessage(e.getMessage());
            return;
        }
        if (e.isNetwork()) {
            showMessage(SubmitSupport.MSG_NETWORK_KEEP);
            return;
        }
        handleError(e);
    }

    /** API-D41（DISINFECT_RECORD） */
    public void confirmByRequestId() {
        String key = idem.current();
        if (key == null) {
            submitting.setValue(false);
            return;
        }
        launch(immuneRepository.getRequestStatus(key, HealthExtras.BIZ_DISINFECT_RECORD), status -> {
            submitting.setValue(false);
            if ("DONE".equals(status.getStatus())) {
                idem.reset();
                finished = true;
                discardDraft();
                Long id = status.getBizIds().isEmpty() ? null : status.getBizIds().get(0);
                notifyTodo(id == null ? 0 : id);
                finishWithSuccess.setValue(successText());
            } else {
                showMessage(UiMessage.withAction(SubmitSupport.MSG_SUBMIT_FAILED, "重试", SubmitSupport.ACTION_RETRY));
            }
        }, err -> {
            submitting.setValue(false);
            showMessage(UiMessage.withAction(SubmitSupport.MSG_SUBMIT_FAILED, "重试", SubmitSupport.ACTION_RETRY));
        });
    }

    public void onMessageAction(@NonNull String actionId) {
        if (SubmitSupport.ACTION_RETRY.equals(actionId)) {
            submit();
        }
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        if (errors.containsKey(F_CONFIRMED)) {
            scrollToNotice.call();
        } else if (!errors.isEmpty()) {
            scrollToField.setValue(errors.keySet().iterator().next());
        }
        return true;
    }

    public void clearError(@NonNull String field) {
        Map<String, String> m = fieldErrors.getValue();
        if (m != null && m.containsKey(field)) {
            Map<String, String> copy = new LinkedHashMap<>(m);
            copy.remove(field);
            fieldErrors.setValue(copy);
        }
    }

    void putError(@NonNull String field, @NonNull String message) {
        Map<String, String> copy = new LinkedHashMap<>(fieldErrors.getValue() == null ? Collections.emptyMap()
                : fieldErrors.getValue());
        copy.put(field, message);
        fieldErrors.setValue(copy);
    }

    // ===== 查看与修改 =====

    private void showRecord(@NonNull DisinfectRecordVO r) {
        record.setValue(r);
        recordType.setValue(r.getRecordType() == null ? HealthExtras.RECORD_PLAN_TASK : r.getRecordType());
        taskId.setValue(r.getTaskId());
        penId.setValue(r.getPenId());
        actualTime.setValue(r.getActualTime());
        usageQty.setValue(HealthFormat.plain(r.getUsageQty()));
        concentration.setValue(HealthFormat.nonNull(r.getConcentration()));
        concentrationConfirmed.setValue(r.isConcentrationConfirmed());
        methodCode.setValue(r.getMethodCode());
        backfill = r.isBackfill();
    }

    /** 查看状态底部：EDIT“修改记录”、CHANGE_REQUEST“提交修改申请”、VIEW_REQUEST */
    public void onViewAction(@NonNull String action) {
        DisinfectRecordVO r = record.getValue();
        if (r == null) {
            return;
        }
        if (HealthExtras.ACTION_EDIT.equals(action)) {
            enterEdit();
        } else if (HealthExtras.ACTION_CHANGE_REQUEST.equals(action)) {
            openChangeRequest.setValue(r.getId());
        } else if (HealthExtras.ACTION_VIEW_REQUEST.equals(action)) {
            openRequest.setValue(r.getChangeState() == null ? null : r.getChangeState().getPendingRequestId());
        }
    }

    /** 进入修改：照片整体替换（已有照片作为初始项），取消毒剂批号与执行人 */
    public void enterEdit() {
        DisinfectRecordVO r = record.getValue();
        if (r == null) {
            return;
        }
        mode.setValue(MODE_EDIT);
        if (photos != null) {
            photos.setInitialFiles(r.getPhotos());
        }
        Single<List<DisinfectantOptionVO>> options = repository.getDisinfectants(null)
                .onErrorReturnItem(Collections.emptyList());
        Single<List<MemberItemVO>> members = isStaff() ? Single.just(Collections.emptyList())
                : shared.getMembers().onErrorReturnItem(Collections.emptyList());
        launch(Single.zip(options, members, (o, m) -> new Object[]{o, m}), arr -> {
            @SuppressWarnings("unchecked")
            List<DisinfectantOptionVO> o = (List<DisinfectantOptionVO>) arr[0];
            @SuppressWarnings("unchecked")
            List<MemberItemVO> mem = (List<MemberItemVO>) arr[1];
            disinfectants.setValue(o);
            executors.setValue(mem);
            Long executor = r.getExecutorId();
            if (executor == null) {
                for (MemberItemVO m : mem) {
                    if (Objects.equals(m.getName(), r.getExecutorName())) {
                        executor = m.getUserId();
                        break;
                    }
                }
            }
            executorId.setValue(executor);
            Long drug = r.getDrugId();
            if (drug == null) {
                for (DisinfectantOptionVO d : o) {
                    if (Objects.equals(d.getName(), r.getDisinfectantName())) {
                        drug = d.getDrugId();
                        break;
                    }
                }
            }
            drugId.setValue(drug);
            pendingLotNo = r.getLotNo();
            if (drug != null) {
                queryLots(drug, r.getDrugLotId());
            }
        });
    }

    public void onChangeRequestConfirmed() {
        DisinfectRecordVO r = record.getValue();
        if (r != null) {
            openChangeRequest.setValue(r.getId());
        }
    }

    public void loadLogs() {
        if (recordId == null) {
            return;
        }
        logs.setValue(null);
        launch(shared.getRecordLogs(HealthExtras.BIZ_DISINFECT_RECORD, recordId), logs::setValue,
                e -> logs.setValue(Collections.emptyList()));
    }

    // ===== 返回与草稿 =====

    @NonNull
    private String snapshot() {
        return recordType.getValue() + "|" + taskId.getValue() + "|" + penId.getValue() + "|" + drugId.getValue() + "|"
                + drugLotId.getValue() + "|" + usageQty.getValue() + "|" + concentration.getValue() + "|"
                + methodCode.getValue() + "|" + executorId.getValue();
    }

    public boolean isDirty() {
        if (MODE_VIEW.equals(mode.getValue())) {
            return false;
        }
        if (isEditMode()) {
            return true;
        }
        boolean photosAdded = photos != null && !photos.current().isEmpty();
        return photosAdded || (initialSnapshot != null && !initialSnapshot.equals(snapshot()));
    }

    public boolean onBackPressed() {
        if (isDirty()) {
            discardEvent.call();
            return true;
        }
        return false;
    }

    public void onDiscardConfirmed() {
        finished = true;
        discardDraft();
    }

    /** onStop 且新建状态有改动时保存草稿（照片不进草稿） */
    public void saveDraft() {
        if (finished || !isEditable() || isEditMode() || draftKey == null || initialSnapshot == null
                || initialSnapshot.equals(snapshot())) {
            return;
        }
        Draft d = new Draft();
        d.recordType = recordType.getValue();
        d.taskId = taskId.getValue();
        d.penId = penId.getValue();
        d.drugId = drugId.getValue();
        d.drugLotId = drugLotId.getValue();
        d.actualTime = actualTime.getValue();
        d.usageQty = usageQty.getValue();
        d.concentration = concentration.getValue();
        d.concentrationConfirmed = concentrationConfirmed.getValue();
        d.methodCode = methodCode.getValue();
        d.executorId = executorId.getValue();
        drafts.save(farmContext.farmIdOrZero(), draftKey, d);
    }

    private void checkDraft() {
        if (draftKey == null) {
            return;
        }
        drafts.load(farmContext.farmIdOrZero(), draftKey, d -> {
            pendingDraft = d;
            restoreDraftEvent.call();
        });
    }

    public void restoreDraft() {
        Draft d = pendingDraft;
        pendingDraft = null;
        if (d == null) {
            return;
        }
        if (d.recordType != null) {
            onRecordTypeChanged(d.recordType);
        }
        if (d.taskId != null) {
            DisinfectTaskOptionVO t = taskOption(d.taskId);
            if (t != null) {
                selectTask(t);
            }
        }
        if (d.penId != null && !isPlanTask()) {
            selectPen(d.penId);
        }
        if (d.drugId != null && !isPlanTask()) {
            drugId.setValue(d.drugId);
            queryLots(d.drugId, d.drugLotId);
        } else if (d.drugId != null && d.drugLotId != null) {
            queryLots(d.drugId, d.drugLotId);
        }
        if (d.actualTime != null) {
            actualTime.setValue(d.actualTime);
        }
        usageQty.setValue(HealthFormat.nonNull(d.usageQty));
        concentration.setValue(HealthFormat.nonNull(d.concentration));
        concentrationConfirmed.setValue(Boolean.TRUE.equals(d.concentrationConfirmed));
        methodCode.setValue(d.methodCode);
        executorId.setValue(d.executorId);
    }

    public void discardDraft() {
        pendingDraft = null;
        if (draftKey != null) {
            drafts.delete(farmContext.farmIdOrZero(), draftKey);
        }
    }

    @Override
    protected void onCleared() {
        if (photos != null) {
            photos.release();
        }
        super.onCleared();
    }
}
