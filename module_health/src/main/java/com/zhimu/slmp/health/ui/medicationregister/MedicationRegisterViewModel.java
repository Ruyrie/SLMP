package com.zhimu.slmp.health.ui.medicationregister;

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
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.data.HealthSharedRepository;
import com.zhimu.slmp.health.data.ImmuneRepository;
import com.zhimu.slmp.health.data.MedicationRepository;
import com.zhimu.slmp.health.model.request.MedicationCreateRequest;
import com.zhimu.slmp.health.model.request.MedicationPreviewRequest;
import com.zhimu.slmp.health.model.request.MedicationUpdateRequest;
import com.zhimu.slmp.health.model.request.NotifyManagerRequest;
import com.zhimu.slmp.health.model.vo.DrugLotOptionVO;
import com.zhimu.slmp.health.model.vo.DrugLotOptionsVO;
import com.zhimu.slmp.health.model.vo.LinkableReportVO;
import com.zhimu.slmp.health.model.vo.MedicationCreateResultVO;
import com.zhimu.slmp.health.model.vo.MedicationDrugOptionVO;
import com.zhimu.slmp.health.model.vo.MedicationRecordVO;
import com.zhimu.slmp.health.model.vo.MedicationTargetVO;
import com.zhimu.slmp.health.model.vo.MemberItemVO;
import com.zhimu.slmp.health.model.vo.ReleaseDatePreviewVO;
import com.zhimu.slmp.health.ui.common.DraftStore;
import com.zhimu.slmp.health.util.HealthFormRules;
import com.zhimu.slmp.health.util.HealthFormat;
import com.zhimu.slmp.health.util.LotPicker;
import com.zhimu.slmp.health.util.SubmitSupport;
import com.zhimu.slmp.health.util.WithdrawalCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * D04 登记 / 修改用药：用药对象（批次 / 栏舍，API-D19）、药品（API-D20，搜索命中禁用药时提示 banned_hint）、库存批号（API-M27）、
 * 剂量（超过推荐值须写超量说明）、疗程（“末次 10月23日”）、实际用量、首次用药日期、实际执行人（员工不显示）、用药原因、关联诊断（API-I24）；
 * 底部“提交后可出栏日期”（API-D21 预估，失败时按本药品末次 + 休药天数预估）。提交 API-D22（Idempotency-Key），修改 API-D24。
 * 新建状态离开时保存草稿（form_draft，page_code D04）。
 */
public class MedicationRegisterViewModel extends BaseViewModel {

    public static final String PAGE_CODE = "D04";
    public static final String F_BATCH = "batch_id";
    public static final String F_PEN = "pen_id";
    public static final String F_DRUG = "drug_id";
    public static final String F_LOT = "drug_lot_id";
    public static final String F_DOSE = "dose";
    public static final String F_COURSE = "course_days";
    public static final String F_FIRST_DATE = "first_date";
    public static final String F_USED = "used_qty";
    public static final String F_OVER_NOTE = "over_dose_note";
    public static final String F_USAGE_NOTE = "usage_note";
    public static final String F_REASON = "reason";

    /** 草稿内容 */
    static final class Draft {
        String targetType;
        Long batchId;
        Long penId;
        Long drugId;
        Long drugLotId;
        String dose;
        String courseDays;
        String firstDate;
        String usedQty;
        String overDoseNote;
        String usageNote;
        String reason;
        Long executorId;
    }

    private final MedicationRepository repository;
    private final ImmuneRepository immuneRepository;
    private final HealthSharedRepository shared;
    private final FarmContext farmContext;
    private final DraftStore<Draft> drafts;

    private final MutableLiveData<String> targetType = new MutableLiveData<>(MedicationRepository.TARGET_BATCH);
    private final MutableLiveData<List<MedicationTargetVO>> targets = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<MedicationDrugOptionVO>> drugs = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> bannedHint = new MutableLiveData<>();
    private final MutableLiveData<MedicationDrugOptionVO> selectedDrug = new MutableLiveData<>();
    private final MutableLiveData<DrugLotOptionsVO> lotOptions = new MutableLiveData<>();
    private final MutableLiveData<Long> batchId = new MutableLiveData<>();
    private final MutableLiveData<Long> penId = new MutableLiveData<>();
    private final MutableLiveData<Long> drugId = new MutableLiveData<>();
    private final MutableLiveData<Long> drugLotId = new MutableLiveData<>();
    private final MutableLiveData<Long> executorId = new MutableLiveData<>();
    private final MutableLiveData<String> dose = new MutableLiveData<>("");
    private final MutableLiveData<String> courseDays = new MutableLiveData<>("");
    private final MutableLiveData<String> firstDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<String> usedQty = new MutableLiveData<>("");
    private final MutableLiveData<String> overDoseNote = new MutableLiveData<>("");
    private final MutableLiveData<String> usageNote = new MutableLiveData<>("");
    private final MutableLiveData<String> reason = new MutableLiveData<>("");
    private final MediatorLiveData<Boolean> overDose = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> categoryMismatch = new MediatorLiveData<>();
    private final MediatorLiveData<String> lastDateText = new MediatorLiveData<>();
    private final MutableLiveData<ReleaseDatePreviewVO> preview = new MutableLiveData<>();
    private final MediatorLiveData<String> releaseText = new MediatorLiveData<>();
    private final MutableLiveData<List<MemberItemVO>> executors = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<LinkableReportVO>> reports = new MutableLiveData<>();
    /** 初值为 null：未关联时页面也要渲染“关联异常上报或诊断” Chip */
    private final MutableLiveData<LinkableReportVO> linkedReport = new MutableLiveData<>(null);
    private final MutableLiveData<MedicationRecordVO> record = new MutableLiveData<>();
    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    private final SingleLiveEvent<MedicationCreateResultVO> finishWithSuccess = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> finishWithMessage = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> needChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> staffRestricted = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showReportSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> restoreDraftEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> discardEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> scrollToField = new SingleLiveEvent<>();

    private final SubmitSupport.IdemKeeper idem = new SubmitSupport.IdemKeeper();
    @Nullable
    private Long recordId;
    @Nullable
    private Long todoId;
    @Nullable
    private Long presetReportId;
    @Nullable
    private Long presetDiagnosisId;
    @Nullable
    private String draftKey;
    @Nullable
    private Draft pendingDraft;
    @Nullable
    private String initialSnapshot;
    private boolean finished;
    @Nullable
    private Disposable previewRequest;
    @Nullable
    private Disposable drugRequest;
    @Nullable
    private Long pendingDrugId;
    @Nullable
    private Long pendingLotId;
    private final Map<String, List<MedicationTargetVO>> targetCache = new LinkedHashMap<>();

    @Inject
    public MedicationRegisterViewModel(@NonNull MedicationRepository repository,
                                       @NonNull ImmuneRepository immuneRepository,
                                       @NonNull HealthSharedRepository shared, @NonNull FarmContext farmContext,
                                       @NonNull AppDatabase database) {
        this.repository = repository;
        this.immuneRepository = immuneRepository;
        this.shared = shared;
        this.farmContext = farmContext;
        this.drafts = new DraftStore<>(database.formDraftDao(), PAGE_CODE, Draft.class, disposables);
        overDose.addSource(dose, v -> overDose.setValue(computeOverDose()));
        overDose.addSource(selectedDrug, v -> overDose.setValue(computeOverDose()));
        overDose.addSource(preview, v -> overDose.setValue(computeOverDose()));
        categoryMismatch.addSource(selectedDrug, v -> categoryMismatch.setValue(computeMismatch()));
        categoryMismatch.addSource(preview, v -> categoryMismatch.setValue(computeMismatch()));
        lastDateText.addSource(firstDate, v -> lastDateText.setValue(computeLastDateText()));
        lastDateText.addSource(courseDays, v -> lastDateText.setValue(computeLastDateText()));
        releaseText.addSource(preview, v -> releaseText.setValue(computeReleaseText()));
        releaseText.addSource(selectedDrug, v -> releaseText.setValue(computeReleaseText()));
        releaseText.addSource(lastDateText, v -> releaseText.setValue(computeReleaseText()));
        canSubmit.addSource(submitting, v -> canSubmit.setValue(!Boolean.TRUE.equals(v)));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        idem.attach(handle);
        todoId = asLong(handle.get(RouterPath.EXTRA_TODO_ID));
        presetReportId = asLong(handle.get(HealthExtras.EXTRA_REPORT_ID));
        presetDiagnosisId = asLong(handle.get(HealthExtras.EXTRA_DIAGNOSIS_ID));
        init(asLong(handle.get(RouterPath.EXTRA_BATCH_ID)), asLong(handle.get(RouterPath.EXTRA_PEN_ID)),
                asLong(handle.get(HealthExtras.EXTRA_RECORD_ID)));
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

    // ===== 对外 =====

    public MutableLiveData<String> getTargetType() { return targetType; }

    public LiveData<List<MedicationTargetVO>> getTargets() { return targets; }

    public LiveData<List<MedicationDrugOptionVO>> getDrugs() { return drugs; }

    public LiveData<String> getBannedHint() { return bannedHint; }

    public LiveData<MedicationDrugOptionVO> getSelectedDrug() { return selectedDrug; }

    public LiveData<DrugLotOptionsVO> getLotOptions() { return lotOptions; }

    public MutableLiveData<Long> getBatchId() { return batchId; }

    public MutableLiveData<Long> getPenId() { return penId; }

    public MutableLiveData<Long> getDrugId() { return drugId; }

    public MutableLiveData<Long> getDrugLotId() { return drugLotId; }

    public MutableLiveData<Long> getExecutorId() { return executorId; }

    public MutableLiveData<String> getDose() { return dose; }

    public MutableLiveData<String> getCourseDays() { return courseDays; }

    public MutableLiveData<String> getFirstDate() { return firstDate; }

    public MutableLiveData<String> getUsedQty() { return usedQty; }

    public MutableLiveData<String> getOverDoseNote() { return overDoseNote; }

    public MutableLiveData<String> getUsageNote() { return usageNote; }

    public MutableLiveData<String> getReason() { return reason; }

    public LiveData<Boolean> getOverDose() { return overDose; }

    public LiveData<Boolean> getCategoryMismatch() { return categoryMismatch; }

    public LiveData<String> getLastDateText() { return lastDateText; }

    public LiveData<ReleaseDatePreviewVO> getPreview() { return preview; }

    public LiveData<String> getReleaseText() { return releaseText; }

    public LiveData<List<MemberItemVO>> getExecutors() { return executors; }

    public LiveData<List<LinkableReportVO>> getReports() { return reports; }

    public LiveData<LinkableReportVO> getLinkedReport() { return linkedReport; }

    public LiveData<MedicationRecordVO> getRecord() { return record; }

    public LiveData<Boolean> getEditMode() { return editMode; }

    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }

    public LiveData<Boolean> getSubmitting() { return submitting; }

    public LiveData<Boolean> getCanSubmit() { return canSubmit; }

    public SingleLiveEvent<MedicationCreateResultVO> getFinishWithSuccess() { return finishWithSuccess; }

    public SingleLiveEvent<String> getFinishWithMessage() { return finishWithMessage; }

    public SingleLiveEvent<String> getNeedChangeRequest() { return needChangeRequest; }

    public SingleLiveEvent<String> getStaffRestricted() { return staffRestricted; }

    public SingleLiveEvent<Long> getOpenChangeRequest() { return openChangeRequest; }

    public SingleLiveEvent<Void> getShowReportSheet() { return showReportSheet; }

    public SingleLiveEvent<Void> getRestoreDraftEvent() { return restoreDraftEvent; }

    public SingleLiveEvent<Void> getDiscardEvent() { return discardEvent; }

    public SingleLiveEvent<String> getScrollToField() { return scrollToField; }

    public boolean isStaff() { return farmContext.isStaff(); }

    public boolean isBatchTarget() {
        return MedicationRepository.TARGET_BATCH.equals(targetType.getValue());
    }

    // ===== 初始化 =====

    public void init(@Nullable Long presetBatch, @Nullable Long presetPen, @Nullable Long recordId) {
        this.recordId = recordId;
        boolean edit = recordId != null;
        editMode.setValue(edit);
        Single<List<MemberItemVO>> members = isStaff() ? Single.just(Collections.emptyList())
                : shared.getMembers().onErrorReturnItem(Collections.emptyList());
        if (edit) {
            load(Single.zip(repository.getDetail(recordId), members, (r, m) -> new Object[]{r, m}), arr -> {
                @SuppressWarnings("unchecked")
                List<MemberItemVO> m = (List<MemberItemVO>) arr[1];
                executors.setValue(m);
                applyRecord((MedicationRecordVO) arr[0]);
            });
            return;
        }
        String type = presetBatch == null && presetPen != null ? MedicationRepository.TARGET_PEN
                : MedicationRepository.TARGET_BATCH;
        targetType.setValue(type);
        draftKey = presetBatch != null ? "B" + presetBatch : presetPen != null ? "P" + presetPen : "";
        load(Single.zip(repository.getTargets(type, null), members, (t, m) -> new Object[]{t, m}), arr -> {
            @SuppressWarnings("unchecked")
            List<MedicationTargetVO> t = (List<MedicationTargetVO>) arr[0];
            @SuppressWarnings("unchecked")
            List<MemberItemVO> m = (List<MemberItemVO>) arr[1];
            targetCache.put(type, t);
            targets.setValue(t);
            executors.setValue(m);
            if (presetBatch != null) {
                selectBatch(presetBatch);
            } else if (presetPen != null) {
                selectPen(presetPen);
            } else if (t.size() == 1) {
                selectTarget(t.get(0));
            }
            initialSnapshot = snapshot();
            checkDraft();
        });
    }

    @Override
    public void retry() {
        init(batchId.getValue(), penId.getValue(), recordId);
    }

    /** 修改状态回填（用药对象和批次只读） */
    private void applyRecord(@NonNull MedicationRecordVO r) {
        record.setValue(r);
        targetType.setValue(MedicationRepository.TARGET_BATCH);
        batchId.setValue(r.getBatchId());
        penId.setValue(r.getPenId());
        dose.setValue(HealthFormat.plain(r.getDose()));
        courseDays.setValue(String.valueOf(r.getCourseDays()));
        firstDate.setValue(r.getFirstDate());
        usedQty.setValue(HealthFormat.plain(r.getUsedQty()));
        overDoseNote.setValue(HealthFormat.nonNull(r.getOverDoseNote()));
        usageNote.setValue(HealthFormat.nonNull(r.getUsageNote()));
        reason.setValue(HealthFormat.nonNull(r.getReason()));
        Long executor = r.getExecutorId();
        if (executor == null && executors.getValue() != null) {
            for (MemberItemVO m : executors.getValue()) {
                if (Objects.equals(m.getName(), r.getExecutorName())) {
                    executor = m.getUserId();
                    break;
                }
            }
        }
        executorId.setValue(executor);
        if (r.getReportId() != null) {
            linkedReport.setValue(reportOf(r.getReportId(), null, r.getReportSummary()));
        }
        pendingDrugId = r.getDrugId();
        pendingLotId = r.getDrugLotId();
        pendingLotNo = r.getLotNo();
        loadDrugs(null);
        initialSnapshot = snapshot();
    }

    @Nullable
    private String pendingLotNo;

    // ===== 用药对象 =====

    /** 切换“批次 / 栏舍”：已填的药品、剂量等保留 */
    public void onTargetTypeChanged(@NonNull String type) {
        if (type.equals(targetType.getValue()) || Boolean.TRUE.equals(editMode.getValue())) {
            return;
        }
        targetType.setValue(type);
        batchId.setValue(null);
        penId.setValue(null);
        clearError(F_BATCH);
        clearError(F_PEN);
        preview.setValue(null);
        List<MedicationTargetVO> cached = targetCache.get(type);
        if (cached != null) {
            targets.setValue(cached);
            return;
        }
        targets.setValue(Collections.emptyList());
        launch(repository.getTargets(type, null), list -> {
            targetCache.put(type, list);
            if (type.equals(targetType.getValue())) {
                targets.setValue(list);
            }
        });
    }

    public void onTargetSelected(long id) {
        MedicationTargetVO t = targetOf(id);
        if (t != null) {
            selectTarget(t);
        }
    }

    private void selectBatch(long id) {
        MedicationTargetVO t = targetOf(id);
        batchId.setValue(id);
        if (t != null) {
            selectTarget(t);
        } else {
            loadDrugs(null);
        }
    }

    private void selectPen(long id) {
        MedicationTargetVO t = targetOf(id);
        penId.setValue(id);
        if (t != null) {
            selectTarget(t);
        } else {
            loadDrugs(null);
        }
    }

    /** 选中批次或栏舍：重取可选兽药（API-D20） */
    void selectTarget(@NonNull MedicationTargetVO t) {
        if (isBatchTarget()) {
            batchId.setValue(t.getBatchId());
            penId.setValue(null);
            clearError(F_BATCH);
        } else {
            penId.setValue(t.getPenId());
            batchId.setValue(null);
            clearError(F_PEN);
            if (Boolean.FALSE.equals(t.getHasBatch())) {
                putError(F_PEN, HealthFormat.nonNull(t.getPenName()) + "没有在栏批次，不能登记用药");
            }
        }
        loadDrugs(null);
        requestPreview();
    }

    /** 当前对象（批次时按 batch_id，栏舍时按 pen_id） */
    @Nullable
    MedicationTargetVO targetOf(@Nullable Long id) {
        List<MedicationTargetVO> list = targets.getValue();
        if (id == null || list == null) {
            return null;
        }
        boolean batch = isBatchTarget();
        for (MedicationTargetVO t : list) {
            if (Objects.equals(batch ? t.getBatchId() : t.getPenId(), id)) {
                return t;
            }
        }
        return null;
    }

    @Nullable
    MedicationTargetVO currentTarget() {
        return targetOf(isBatchTarget() ? batchId.getValue() : penId.getValue());
    }

    /** 批次：“存栏 320 头 · 选栏舍时对舍内所有在栏批次生效”；栏舍：“在栏批次 ZM2610-045（320 头）” */
    @Nullable
    public String targetHelper() {
        MedicationTargetVO t = currentTarget();
        String unit = HealthFormat.countUnit(farmContext);
        if (t == null) {
            MedicationRecordVO r = record.getValue();
            return r == null ? null : HealthFormat.nonNull(r.getPenName());
        }
        if (isBatchTarget()) {
            return "存栏 " + HealthFormat.qty(t.getCurrentCount(), unit) + HealthFormat.DOT + "选栏舍时对舍内所有在栏批次生效";
        }
        if (t.getBatches().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("在栏批次 ");
        for (int i = 0; i < t.getBatches().size(); i++) {
            MedicationTargetVO.BatchItem b = t.getBatches().get(i);
            if (i > 0) {
                sb.append("、");
            }
            sb.append(b.getBatchNo()).append("（").append(HealthFormat.qty(b.getStockCount(), unit)).append("）");
        }
        return sb.toString();
    }

    // ===== 药品与批号 =====

    /** 药品搜索框停止输入 300 毫秒后（关键词命中禁用药时返回 banned_hint） */
    public void searchDrug(@Nullable String keyword) {
        String k = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        MedicationDrugOptionVO current = selectedDrug.getValue();
        if (current != null && Objects.equals(current.getDrugName(), k)) {
            return;
        }
        loadDrugs(k);
    }

    private void loadDrugs(@Nullable String keyword) {
        Long b = isBatchTarget() ? batchId.getValue() : null;
        Long p = isBatchTarget() ? null : penId.getValue();
        if (b == null && p == null) {
            drugs.setValue(Collections.emptyList());
            return;
        }
        if (drugRequest != null) {
            drugRequest.dispose();
        }
        drugRequest = launch(repository.getDrugOptions(b, p, keyword), result -> {
            drugs.setValue(result.getList());
            bannedHint.setValue(result.getBannedHint());
            Long want = pendingDrugId != null ? pendingDrugId : drugId.getValue();
            if (want != null) {
                for (MedicationDrugOptionVO d : result.getList()) {
                    if (d.getDrugId() == want) {
                        pendingDrugId = null;
                        applyDrug(d, pendingLotId);
                        break;
                    }
                }
            }
        }, e -> {
            if (ErrorCodes.isNoPermission(e.getCode())) {
                showMessage(e.getMessage());
            }
        });
    }

    public void onDrugSelected(long id) {
        List<MedicationDrugOptionVO> list = drugs.getValue();
        if (list == null) {
            return;
        }
        for (MedicationDrugOptionVO d : list) {
            if (d.getDrugId() == id) {
                applyDrug(d, null);
                return;
            }
        }
    }

    /** 选择药品：辅助文字更新休药天数，批号自动选有效期最早的一个，剂量单位和推荐值随药品更新 */
    void applyDrug(@NonNull MedicationDrugOptionVO d, @Nullable Long keepLotId) {
        boolean changed = !Objects.equals(drugId.getValue(), d.getDrugId());
        selectedDrug.setValue(d);
        drugId.setValue(d.getDrugId());
        bannedHint.setValue(null);
        clearError(F_DRUG);
        if (HealthFormat.nonNull(courseDays.getValue()).isEmpty() && d.getRecommendedCourseDays() != null) {
            courseDays.setValue(String.valueOf(d.getRecommendedCourseDays()));
        }
        if (changed || lotOptions.getValue() == null) {
            drugLotId.setValue(null);
            queryLots(d.getDrugId(), keepLotId);
        }
        requestPreview();
    }

    void queryLots(long drug, @Nullable Long keepLotId) {
        launch(shared.getLotOptions(drug, HealthExtras.PURPOSE_MEDICATION, null), options -> {
            lotOptions.setValue(options);
            DrugLotOptionVO keep = LotPicker.find(options, keepLotId);
            if (keep == null && pendingLotNo != null) {
                keep = LotPicker.findByNo(options, pendingLotNo);
            }
            pendingLotId = null;
            pendingLotNo = null;
            DrugLotOptionVO lot = keep != null ? keep : LotPicker.defaultLot(options);
            drugLotId.setValue(lot == null ? null : lot.getLotId());
            if (lot == null) {
                putError(F_LOT, LotPicker.noLotText(drugName(), isStaff()));
            } else {
                clearError(F_LOT);
            }
        }, e -> showMessage(e.isNetwork() ? ApiException.MSG_NETWORK : e.getMessage()));
    }

    public void selectLot(long lotId) {
        if (LotPicker.find(lotOptions.getValue(), lotId) != null) {
            drugLotId.setValue(lotId);
            clearError(F_LOT);
        }
    }

    @Nullable
    public DrugLotOptionVO selectedLot() {
        return LotPicker.find(lotOptions.getValue(), drugLotId.getValue());
    }

    @Nullable
    String drugName() {
        MedicationDrugOptionVO d = selectedDrug.getValue();
        if (d != null) {
            return d.getDrugName();
        }
        MedicationRecordVO r = record.getValue();
        return r == null ? null : r.getDrugName();
    }

    /** 记账单位（克） */
    @NonNull
    public String stockUnit() {
        MedicationDrugOptionVO d = selectedDrug.getValue();
        if (d != null && d.getStockUnit() != null) {
            return d.getStockUnit();
        }
        MedicationRecordVO r = record.getValue();
        return r == null ? "" : HealthFormat.nonNull(r.getUsedUnit());
    }

    /** 剂量单位（克 / 升水） */
    @NonNull
    public String doseUnit() {
        MedicationDrugOptionVO d = selectedDrug.getValue();
        if (d != null && d.getDoseUnit() != null) {
            return d.getDoseUnit();
        }
        MedicationRecordVO r = record.getValue();
        return r == null ? "" : HealthFormat.nonNull(r.getDoseUnit());
    }

    /** 药品辅助文字“休药 14 天 · 禁用药不可选” */
    @Nullable
    public String drugHelper() {
        MedicationDrugOptionVO d = selectedDrug.getValue();
        if (d == null) {
            return "禁用药不可选";
        }
        return d.getHintText() != null ? d.getHintText() : "休药 " + d.getWithdrawalDays() + " 天" + HealthFormat.DOT
                + "禁用药不可选";
    }

    /** 剂量辅助文字“推荐 0.3 克 / 升水” */
    @Nullable
    public String doseHelper() {
        MedicationDrugOptionVO d = selectedDrug.getValue();
        if (d == null || d.getRecommendedDose() == null) {
            return null;
        }
        return "推荐 " + HealthFormat.plain(d.getRecommendedDose()) + " " + HealthFormat.nonNull(d.getDoseUnit());
    }

    /** 实际用量辅助文字：有预计用量时“预计用量 760 克”，否则按记账单位填写 */
    @NonNull
    public String usedHelper() {
        ReleaseDatePreviewVO p = preview.getValue();
        if (p != null && p.isReady() && p.getEstimatedQty() != null) {
            return "预计用量 " + HealthFormat.qty(p.getEstimatedQty(), p.getUsedUnit() != null ? p.getUsedUnit()
                    : stockUnit());
        }
        return "按" + (stockUnit().isEmpty() ? "记账单位" : stockUnit()) + "填写，登记后扣减库存";
    }

    /** 批号辅助文字“2027-08-31 到期” */
    @Nullable
    public String lotHelper() {
        DrugLotOptionVO lot = selectedLot();
        if (lot == null) {
            MedicationRecordVO r = record.getValue();
            return r == null || r.getLotExpireDate() == null ? null : r.getLotExpireDate() + " 到期";
        }
        String s = HealthFormat.nonNull(lot.getExpireDate()) + " 到期";
        if (LotPicker.STATUS_NEAR_EXPIRY.equals(lot.getLotStatus()) && lot.getDaysToExpire() != null) {
            s += HealthFormat.DOT + "剩 " + lot.getDaysToExpire() + " 天";
        }
        return s;
    }

    // ===== 派生 =====

    boolean computeOverDose() {
        ReleaseDatePreviewVO p = preview.getValue();
        if (p != null && p.isReady() && p.getOverDose() != null) {
            return p.getOverDose();
        }
        MedicationDrugOptionVO d = selectedDrug.getValue();
        return d != null && HealthFormRules.isOverDose(dose.getValue(), d.getRecommendedDose());
    }

    boolean computeMismatch() {
        ReleaseDatePreviewVO p = preview.getValue();
        if (p != null && p.isReady() && p.getCategoryMismatch() != null) {
            return p.getCategoryMismatch();
        }
        MedicationDrugOptionVO d = selectedDrug.getValue();
        if (d != null) {
            return !d.isApplicable();
        }
        MedicationRecordVO r = record.getValue();
        return r != null && Boolean.TRUE.equals(r.getCategoryMismatch());
    }

    /** “末次 10月23日”（首次用药日期 + 疗程 − 1） */
    @Nullable
    String computeLastDateText() {
        LocalDate last = lastDate();
        return last == null ? null : "末次 " + HealthFormat.date(DateFormatter.toDateString(last));
    }

    @Nullable
    LocalDate lastDate() {
        LocalDate first = DateFormatter.parseDate(firstDate.getValue());
        Integer days = HealthFormRules.parseInt(courseDays.getValue());
        if (first == null || days == null || days < 1) {
            return null;
        }
        return WithdrawalCalculator.lastDate(first, days);
    }

    /**
     * 底部“提交后可出栏日期”：API-D21 的 release_text（按栏舍时“11月6日 周五 等 2 个批次”）；
     * 预估未返回时按本药品“末次 + 休药天数”界面预估（以提交结果为准）。
     */
    @Nullable
    String computeReleaseText() {
        ReleaseDatePreviewVO p = preview.getValue();
        if (p != null && p.isReady()) {
            if (p.getReleaseText() != null) {
                return p.getReleaseText();
            }
            if (p.getReleaseDate() != null) {
                return HealthFormat.dateWeek(p.getReleaseDate());
            }
        }
        MedicationDrugOptionVO d = selectedDrug.getValue();
        LocalDate last = lastDate();
        if (d == null || last == null) {
            return null;
        }
        return HealthFormat.dateWeek(DateFormatter.toDateString(
                WithdrawalCalculator.releaseDate(last, d.getWithdrawalDays())));
    }

    /** 五个输入（对象、药品、首次日期、疗程、剂量）停止变化 300 毫秒后调 API-D21；新请求取消旧请求，失败静默 */
    public void requestPreview() {
        if (previewRequest != null) {
            previewRequest.dispose();
        }
        MedicationPreviewRequest req = new MedicationPreviewRequest();
        // 修改时记录按批次一行，按批次预估（exclude_record_id 排除原记录）
        req.targetType = targetType.getValue();
        req.batchId = isBatchTarget() ? batchId.getValue() : null;
        req.penId = isBatchTarget() ? null : penId.getValue();
        req.drugId = drugId.getValue();
        req.firstDate = firstDate.getValue();
        req.courseDays = HealthFormRules.parseInt(courseDays.getValue());
        req.dose = HealthFormRules.parseDecimal(dose.getValue());
        req.excludeRecordId = recordId;
        boolean complete = (req.batchId != null || req.penId != null) && req.drugId != null && req.firstDate != null
                && req.courseDays != null && req.courseDays > 0 && req.dose != null && req.dose.signum() > 0;
        if (!complete) {
            preview.setValue(null);
            return;
        }
        previewRequest = launch(repository.preview(req), preview::setValue, e -> preview.setValue(null));
    }

    // ===== 执行人与关联诊断 =====

    public void onExecutorSelected(long userId) {
        executorId.setValue(userId);
    }

    public void onFirstDateSelected(@Nullable String date) {
        if (date != null) {
            firstDate.setValue(date);
            clearError(F_FIRST_DATE);
            requestPreview();
        }
    }

    /** 点“关联异常上报或诊断”：取该批次或栏舍近 30 天的上报与诊断（API-I24） */
    public void loadReports() {
        Long b = isBatchTarget() ? batchId.getValue() : null;
        Long p = isBatchTarget() ? null : penId.getValue();
        if (b == null && p == null) {
            putError(isBatchTarget() ? F_BATCH : F_PEN, isBatchTarget() ? "请选择批次" : "请选择栏舍");
            return;
        }
        launch(shared.getLinkableReports(b, p), list -> {
            reports.setValue(list);
            if (list.isEmpty()) {
                showMessage("近 30 天没有可关联的异常上报或诊断");
            } else {
                showReportSheet.call();
            }
        }, e -> showMessage(e.getMessage()));
    }

    public void onReportSelected(long reportId) {
        List<LinkableReportVO> list = reports.getValue();
        if (list == null) {
            return;
        }
        for (LinkableReportVO r : list) {
            if (r.getReportId() == reportId) {
                linkedReport.setValue(r);
                return;
            }
        }
    }

    public void unlinkReport() {
        linkedReport.setValue(null);
    }

    /** I07 进入时带 report_id、diagnosis_id：按 API-I24 取显示文字 */
    void resolvePresetReport() {
        if (presetReportId == null || linkedReport.getValue() != null) {
            return;
        }
        linkedReport.setValue(reportOf(presetReportId, presetDiagnosisId, "已关联异常上报"));
        Long b = isBatchTarget() ? batchId.getValue() : null;
        Long p = isBatchTarget() ? null : penId.getValue();
        if (b == null && p == null) {
            return;
        }
        launch(shared.getLinkableReports(b, p), list -> {
            for (LinkableReportVO r : list) {
                if (r.getReportId() == presetReportId) {
                    linkedReport.setValue(r);
                    return;
                }
            }
        }, e -> {
        });
    }

    @NonNull
    static LinkableReportVO reportOf(long reportId, @Nullable Long diagnosisId, @Nullable String label) {
        String json = "{\"report_id\":" + reportId + (diagnosisId == null ? "" : ",\"diagnosis_id\":" + diagnosisId)
                + ",\"label\":" + com.zhimu.slmp.network.GsonFactory.get().toJson(label == null ? "" : label) + "}";
        return com.zhimu.slmp.network.GsonFactory.get().fromJson(json, LinkableReportVO.class);
    }

    // ===== 校验与提交 =====

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        boolean edit = Boolean.TRUE.equals(editMode.getValue());
        if (!edit) {
            if (isBatchTarget() && batchId.getValue() == null) {
                e.put(F_BATCH, "请选择批次");
            }
            if (!isBatchTarget()) {
                MedicationTargetVO t = currentTarget();
                if (penId.getValue() == null) {
                    e.put(F_PEN, "请选择栏舍");
                } else if (t != null && Boolean.FALSE.equals(t.getHasBatch())) {
                    e.put(F_PEN, HealthFormat.nonNull(t.getPenName()) + "没有在栏批次，不能登记用药");
                }
            }
        }
        if (drugId.getValue() == null) {
            e.put(F_DRUG, "请选择药品");
        }
        DrugLotOptionVO lot = selectedLot();
        if (drugId.getValue() != null && lot == null) {
            Map<String, String> current = fieldErrors.getValue();
            String lotErr = current == null ? null : current.get(F_LOT);
            e.put(F_LOT, lotErr != null ? lotErr : HealthFormRules.MSG_LOT);
        }
        String doseErr = HealthFormRules.dose(dose.getValue());
        if (doseErr != null) {
            e.put(F_DOSE, doseErr);
        }
        String courseErr = HealthFormRules.courseDays(courseDays.getValue());
        if (courseErr != null) {
            e.put(F_COURSE, courseErr);
        }
        String dateErr = HealthFormRules.firstDate(firstDate.getValue());
        if (dateErr != null) {
            e.put(F_FIRST_DATE, dateErr);
        }
        String usedErr = HealthFormRules.positiveDecimal(usedQty.getValue(), "请填写实际用量");
        if (usedErr != null) {
            e.put(F_USED, usedErr);
        } else if (lot != null && lot.getStock() != null) {
            BigDecimal used = HealthFormRules.parseDecimal(usedQty.getValue());
            if (used != null && used.compareTo(lot.getStock()) > 0) {
                e.put(F_USED, "不能超过批号 " + lot.getLotNo() + " 结存 " + HealthFormat.qty(lot.getStock(), stockUnit()));
            }
        }
        String overErr = HealthFormRules.note(overDoseNote.getValue(), Boolean.TRUE.equals(overDose.getValue()),
                "请填写超量说明");
        if (overErr != null) {
            e.put(F_OVER_NOTE, overErr);
        }
        String usageErr = HealthFormRules.note(usageNote.getValue(), Boolean.TRUE.equals(categoryMismatch.getValue()),
                "请填写用药说明");
        if (usageErr != null) {
            e.put(F_USAGE_NOTE, usageErr);
        }
        String reasonErr = HealthFormRules.medicationReason(reason.getValue());
        if (reasonErr != null) {
            e.put(F_REASON, reasonErr);
        }
        return e;
    }

    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            scrollToField.setValue(errors.keySet().iterator().next());
            return;
        }
        Long executor = isStaff() ? null : executorId.getValue();
        LinkableReportVO report = linkedReport.getValue();
        submitting.setValue(true);
        if (Boolean.TRUE.equals(editMode.getValue()) && recordId != null) {
            MedicationRecordVO r = record.getValue();
            MedicationUpdateRequest req = new MedicationUpdateRequest();
            req.drugId = Objects.requireNonNull(drugId.getValue());
            req.drugLotId = Objects.requireNonNull(drugLotId.getValue());
            req.dose = HealthFormRules.parseDecimal(dose.getValue());
            req.usedQty = HealthFormRules.parseDecimal(usedQty.getValue());
            req.courseDays = Objects.requireNonNull(HealthFormRules.parseInt(courseDays.getValue()));
            req.firstDate = firstDate.getValue();
            req.overDoseNote = Boolean.TRUE.equals(overDose.getValue()) ? HealthFormRules.trimToNull(overDoseNote.getValue()) : null;
            req.usageNote = Boolean.TRUE.equals(categoryMismatch.getValue()) ? HealthFormRules.trimToNull(usageNote.getValue()) : null;
            req.reason = HealthFormRules.trimToNull(reason.getValue());
            req.executorId = executor;
            req.reportId = report == null ? null : report.getReportId();
            req.version = r == null ? 0 : r.getVersion();
            launch(repository.update(recordId, req), vo -> {
                submitting.setValue(false);
                finished = true;
                finishWithMessage.setValue("已保存修改");
            }, this::onSubmitError);
            return;
        }
        MedicationCreateRequest req = buildCreateRequest(executor, report);
        launch(repository.create(req, idem.keyFor(req)), this::onCreated, this::onSubmitError);
    }

    @NonNull
    MedicationCreateRequest buildCreateRequest(@Nullable Long executor, @Nullable LinkableReportVO report) {
        MedicationCreateRequest req = new MedicationCreateRequest();
        req.targetType = targetType.getValue();
        req.batchId = isBatchTarget() ? batchId.getValue() : null;
        req.penId = isBatchTarget() ? null : penId.getValue();
        req.drugId = Objects.requireNonNull(drugId.getValue());
        req.drugLotId = Objects.requireNonNull(drugLotId.getValue());
        req.dose = HealthFormRules.parseDecimal(dose.getValue());
        req.usedQty = HealthFormRules.parseDecimal(usedQty.getValue());
        req.courseDays = Objects.requireNonNull(HealthFormRules.parseInt(courseDays.getValue()));
        req.firstDate = firstDate.getValue();
        req.overDoseNote = Boolean.TRUE.equals(overDose.getValue()) ? HealthFormRules.trimToNull(overDoseNote.getValue()) : null;
        req.usageNote = Boolean.TRUE.equals(categoryMismatch.getValue()) ? HealthFormRules.trimToNull(usageNote.getValue()) : null;
        req.reason = HealthFormRules.trimToNull(reason.getValue());
        req.executorId = executor;
        req.reportId = report == null ? null : report.getReportId();
        req.diagnosisId = report == null ? null : report.getDiagnosisId();
        return req;
    }

    private void onCreated(@NonNull MedicationCreateResultVO result) {
        submitting.setValue(false);
        idem.reset();
        finished = true;
        discardDraft();
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            Long id = result.getRecordIds().isEmpty() ? null : result.getRecordIds().get(0);
            todo.notifyTodoChanged(new TodoChange(todoId, HealthExtras.BIZ_MEDICATION_RECORD, id,
                    farmContext.farmIdOrZero()));
        }
        finishWithSuccess.setValue(result);
    }

    /** 成功后进入 D05 的批次（栏舍登记时取舍内第一个在栏批次） */
    @Nullable
    public Long archiveBatchId() {
        if (isBatchTarget()) {
            return batchId.getValue();
        }
        MedicationTargetVO t = currentTarget();
        return t == null || t.getBatches().isEmpty() ? null : t.getBatches().get(0).getBatchId();
    }

    private void onSubmitError(@NonNull ApiException e) {
        boolean edit = Boolean.TRUE.equals(editMode.getValue());
        if (!edit && SubmitSupport.isTimeout(e)) {
            confirmByRequestId();
            return;
        }
        submitting.setValue(false);
        switch (e.getCode()) {
            case ErrorCodes.BANNED_DRUG:
                putError(F_DRUG, e.getMessage());
                scrollToField.setValue(F_DRUG);
                return;
            case ErrorCodes.LOT_UNAVAILABLE:
            case ErrorCodes.STOCK_NOT_ENOUGH:
                putError(F_LOT, e.getMessage());
                scrollToField.setValue(F_LOT);
                Long drug = drugId.getValue();
                if (drug != null) {
                    queryLots(drug, drugLotId.getValue());
                }
                if (e.getMessage().contains("库存已变化")) {
                    showMessage(UiMessage.withAction(e.getMessage(), "刷新", SubmitSupport.ACTION_REFRESH_LOTS));
                }
                return;
            case ErrorCodes.CHANGE_REQUEST_REQUIRED:
                onChangeRequired(e);
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

    /** API-D24 返回 40307：ADVANCE 养殖户确认后提交修改申请（U01），员工“不能直接修改”（知道了 / 通知场长）；其他原因提示 */
    private void onChangeRequired(@NonNull ApiException e) {
        String reasonCode = e.dataString("reason");
        if (isStaff()) {
            if ("ADVANCE".equals(reasonCode)) {
                staffRestricted.setValue(e.getMessage());
            } else {
                showMessage(e.getMessage());
            }
            return;
        }
        needChangeRequest.setValue(e.getMessage());
    }

    public void onChangeRequestConfirmed() {
        if (recordId != null) {
            openChangeRequest.setValue(recordId);
        }
    }

    /** 员工“通知场长”（API-D27，action = UPDATE） */
    public void notifyManager() {
        if (recordId == null) {
            return;
        }
        launch(repository.notifyManager(recordId, new NotifyManagerRequest("UPDATE")), names -> showMessage(
                "已通知" + (names == null || names.isEmpty() ? "场长" : String.join("、", names))), e -> showMessage(
                e.getMessage()));
    }

    /** API-D41（MEDICATION_RECORD） */
    public void confirmByRequestId() {
        String key = idem.current();
        if (key == null) {
            submitting.setValue(false);
            return;
        }
        launch(immuneRepository.getRequestStatus(key, HealthExtras.BIZ_MEDICATION_RECORD), status -> {
            submitting.setValue(false);
            if ("DONE".equals(status.getStatus())) {
                idem.reset();
                finished = true;
                discardDraft();
                finishWithMessage.setValue("已登记用药");
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
        } else if (SubmitSupport.ACTION_REFRESH_LOTS.equals(actionId)) {
            Long drug = drugId.getValue();
            if (drug != null) {
                queryLots(drug, drugLotId.getValue());
            }
        }
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        if (!errors.isEmpty()) {
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

    // ===== 返回与草稿 =====

    @NonNull
    private String snapshot() {
        return targetType.getValue() + "|" + batchId.getValue() + "|" + penId.getValue() + "|" + drugId.getValue()
                + "|" + drugLotId.getValue() + "|" + dose.getValue() + "|" + courseDays.getValue() + "|"
                + firstDate.getValue() + "|" + usedQty.getValue() + "|" + overDoseNote.getValue() + "|"
                + usageNote.getValue() + "|" + reason.getValue() + "|" + executorId.getValue();
    }

    public boolean isDirty() {
        return initialSnapshot != null && !initialSnapshot.equals(snapshot());
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

    /** onStop 且新建状态有改动时保存草稿 */
    public void saveDraft() {
        if (finished || Boolean.TRUE.equals(editMode.getValue()) || !isDirty() || draftKey == null) {
            return;
        }
        Draft d = new Draft();
        d.targetType = targetType.getValue();
        d.batchId = batchId.getValue();
        d.penId = penId.getValue();
        d.drugId = drugId.getValue();
        d.drugLotId = drugLotId.getValue();
        d.dose = dose.getValue();
        d.courseDays = courseDays.getValue();
        d.firstDate = firstDate.getValue();
        d.usedQty = usedQty.getValue();
        d.overDoseNote = overDoseNote.getValue();
        d.usageNote = usageNote.getValue();
        d.reason = reason.getValue();
        d.executorId = executorId.getValue();
        drafts.save(farmContext.farmIdOrZero(), draftKey, d);
    }

    private void checkDraft() {
        resolvePresetReport();
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
        dose.setValue(HealthFormat.nonNull(d.dose));
        courseDays.setValue(HealthFormat.nonNull(d.courseDays));
        if (d.firstDate != null) {
            firstDate.setValue(d.firstDate);
        }
        usedQty.setValue(HealthFormat.nonNull(d.usedQty));
        overDoseNote.setValue(HealthFormat.nonNull(d.overDoseNote));
        usageNote.setValue(HealthFormat.nonNull(d.usageNote));
        reason.setValue(HealthFormat.nonNull(d.reason));
        executorId.setValue(d.executorId);
        pendingDrugId = d.drugId;
        pendingLotId = d.drugLotId;
        if (d.targetType != null && !d.targetType.equals(targetType.getValue())) {
            onTargetTypeChanged(d.targetType);
        }
        if (d.batchId != null) {
            batchId.setValue(d.batchId);
        }
        if (d.penId != null) {
            penId.setValue(d.penId);
        }
        loadDrugs(null);
    }

    public void discardDraft() {
        pendingDraft = null;
        if (draftKey != null) {
            drafts.delete(farmContext.farmIdOrZero(), draftKey);
        }
    }
}
