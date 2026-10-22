package com.zhimu.slmp.health.ui.immuneitem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.data.HealthSharedRepository;
import com.zhimu.slmp.health.data.ImmuneRepository;
import com.zhimu.slmp.health.model.request.ImmunePlanCreateRequest;
import com.zhimu.slmp.health.model.request.ImmunePlanUpdateRequest;
import com.zhimu.slmp.health.model.vo.ImmunePlanOverviewVO;
import com.zhimu.slmp.health.model.vo.ImmunePlanVO;
import com.zhimu.slmp.health.model.vo.VaccineOptionVO;
import com.zhimu.slmp.health.util.HealthFormRules;
import com.zhimu.slmp.health.util.HealthFormat;
import com.zhimu.slmp.health.util.ImmuneRules;
import com.zhimu.slmp.health.util.SubmitSupport;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * D02 添加 / 修改免疫项目：疫苗（本场疫苗档案 API-D13）、计划时间（按日龄 / 按日期，两字段互换并换算）、剂量、接种方式（字典 VACCINATE_METHOD）。
 * 新增 API-D07（Idempotency-Key，“保存并继续添加”留在本页并清空疫苗、日龄、剂量、方式）；修改 API-D08（只改剂量与方式）。
 */
public class ImmuneItemEditViewModel extends BaseViewModel {

    public static final String F_DRUG = "drug_id";
    public static final String F_AGE = "plan_age_day";
    public static final String F_DATE = "plan_date";
    public static final String F_DOSE = "dose";
    public static final String F_METHOD = "method_code";

    /** D01 传入的显示用参数（本组件内部键） */
    public static final String EXTRA_VACCINE_NAME = "vaccine_name";
    public static final String EXTRA_PLAN_MODE = "plan_mode";
    public static final String EXTRA_PLAN_DATE = "plan_date";
    public static final String EXTRA_AGE_AT_PLAN = "age_at_plan";
    public static final String EXTRA_DOSE_UNIT = "dose_unit";

    private final ImmuneRepository repository;
    private final HealthSharedRepository shared;
    private final FarmContext farmContext;

    private final MutableLiveData<ImmunePlanOverviewVO> overview = new MutableLiveData<>();
    private final MutableLiveData<List<VaccineOptionVO>> vaccines = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<DictItemVO>> methods = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> drugId = new MutableLiveData<>();
    private final MutableLiveData<String> planMode = new MutableLiveData<>(ImmuneRules.MODE_BY_DAY_AGE);
    private final MutableLiveData<String> planAgeDay = new MutableLiveData<>("");
    private final MutableLiveData<String> planDate = new MutableLiveData<>("");
    private final MutableLiveData<String> dose = new MutableLiveData<>("");
    private final MutableLiveData<String> methodCode = new MutableLiveData<>();
    private final MediatorLiveData<String> doseUnit = new MediatorLiveData<>();
    private final MediatorLiveData<String> planPreviewText = new MediatorLiveData<>();
    private final LiveData<Boolean> byDayAge;
    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    private final MutableLiveData<String> vaccineHelper = new MutableLiveData<>("从本场疫苗档案中选择");
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final SingleLiveEvent<Long> finishWithResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> resetForm = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> scrollToField = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> backWithMessage = new SingleLiveEvent<>();

    private final SubmitSupport.IdemKeeper idem = new SubmitSupport.IdemKeeper();
    private long batchId;
    @Nullable
    private Long planId;
    private int version;
    @Nullable
    private String editVaccineName;
    @Nullable
    private String editDoseUnit;
    @Nullable
    private Integer editAgeAtPlan;

    @Inject
    public ImmuneItemEditViewModel(@NonNull ImmuneRepository repository, @NonNull HealthSharedRepository shared,
                                   @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.shared = shared;
        this.farmContext = farmContext;
        byDayAge = Transformations.map(planMode, ImmuneRules.MODE_BY_DAY_AGE::equals);
        doseUnit.addSource(drugId, v -> doseUnit.setValue(computeDoseUnit()));
        doseUnit.addSource(vaccines, v -> doseUnit.setValue(computeDoseUnit()));
        planPreviewText.addSource(planMode, v -> planPreviewText.setValue(computePreview()));
        planPreviewText.addSource(planAgeDay, v -> planPreviewText.setValue(computePreview()));
        planPreviewText.addSource(planDate, v -> planPreviewText.setValue(computePreview()));
        planPreviewText.addSource(overview, v -> planPreviewText.setValue(computePreview()));
        canSubmit.addSource(submitting, v -> canSubmit.setValue(!Boolean.TRUE.equals(v)));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        idem.attach(handle);
        Object b = handle.get(RouterPath.EXTRA_BATCH_ID);
        batchId = b instanceof Number ? ((Number) b).longValue() : 0;
        Object p = handle.get(HealthExtras.EXTRA_PLAN_ID);
        planId = p instanceof Number ? ((Number) p).longValue() : null;
        if (planId != null) {
            editMode.setValue(true);
            Object v = handle.get(HealthExtras.EXTRA_VERSION);
            version = v instanceof Number ? ((Number) v).intValue() : 0;
            dose.setValue(HealthFormat.nonNull(handle.get(HealthExtras.EXTRA_DOSE)));
            methodCode.setValue(handle.get(HealthExtras.EXTRA_METHOD_CODE));
            editVaccineName = handle.get(EXTRA_VACCINE_NAME);
            editDoseUnit = handle.get(EXTRA_DOSE_UNIT);
            String mode = handle.get(EXTRA_PLAN_MODE);
            planMode.setValue(mode == null ? ImmuneRules.MODE_BY_DATE : mode);
            planDate.setValue(HealthFormat.nonNull(handle.get(EXTRA_PLAN_DATE)));
            Object age = handle.get(EXTRA_AGE_AT_PLAN);
            editAgeAtPlan = age instanceof Number ? ((Number) age).intValue() : null;
            if (editAgeAtPlan != null) {
                planAgeDay.setValue(String.valueOf(editAgeAtPlan));
            }
        }
        load();
    }

    // ===== 对外 =====

    public LiveData<ImmunePlanOverviewVO> getOverview() { return overview; }

    public LiveData<List<VaccineOptionVO>> getVaccines() { return vaccines; }

    public LiveData<List<DictItemVO>> getMethods() { return methods; }

    public MutableLiveData<Long> getDrugId() { return drugId; }

    public MutableLiveData<String> getPlanMode() { return planMode; }

    public MutableLiveData<String> getPlanAgeDay() { return planAgeDay; }

    public MutableLiveData<String> getPlanDate() { return planDate; }

    public MutableLiveData<String> getDose() { return dose; }

    public MutableLiveData<String> getMethodCode() { return methodCode; }

    public LiveData<String> getDoseUnit() { return doseUnit; }

    public LiveData<String> getPlanPreviewText() { return planPreviewText; }

    public LiveData<Boolean> getByDayAge() { return byDayAge; }

    public LiveData<Boolean> getEditMode() { return editMode; }

    public LiveData<String> getVaccineHelper() { return vaccineHelper; }

    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }

    public LiveData<Boolean> getSubmitting() { return submitting; }

    public LiveData<Boolean> getCanSubmit() { return canSubmit; }

    public SingleLiveEvent<Long> getFinishWithResult() { return finishWithResult; }

    public SingleLiveEvent<Void> getResetForm() { return resetForm; }

    public SingleLiveEvent<String> getScrollToField() { return scrollToField; }

    public SingleLiveEvent<String> getBackWithMessage() { return backWithMessage; }

    @Nullable
    public String getEditVaccineName() { return editVaccineName; }

    /** 养殖场标签（西岭鸡场） */
    @NonNull
    public String farmName() {
        FarmContextVO vo = farmContext.current();
        return vo == null || vo.getName() == null ? "" : vo.getName();
    }

    /** “3 号鸡舍 · 817 肉杂鸡 · 5 日龄 · 存栏 3,000 只” */
    @NonNull
    public String batchSummary() {
        ImmunePlanOverviewVO o = overview.getValue();
        if (o == null) {
            return "";
        }
        return HealthFormat.nonNull(o.getPenName()) + HealthFormat.DOT + HealthFormat.nonNull(o.getBreedName())
                + HealthFormat.DOT + o.getDayAge() + " 日龄" + HealthFormat.DOT + "存栏 "
                + HealthFormat.qty(o.getCurrentCount(), HealthFormat.countUnit(farmContext));
    }

    /** 提醒说明：“计划日期前 1 天和当天提醒该鸡舍的负责员工、所有者和场长。” */
    @NonNull
    public String remindText() {
        ImmunePlanOverviewVO o = overview.getValue();
        String pen = o == null ? "栏舍" : HealthFormat.nonNull(o.getPenName());
        return "计划日期前 1 天和当天提醒" + pen + "的负责员工、所有者和场长。";
    }

    // ===== 加载 =====

    /** API-D01（日龄换算与校验）+ API-D13 疫苗 + 字典 VACCINATE_METHOD（按批次品类） */
    public void load() {
        Single<Object[]> source = repository.getOverview(batchId).flatMap(o -> Single.zip(
                repository.getVaccineOptions(o.getCategoryId()),
                shared.getVaccinateMethods(o.getCategoryId()),
                (v, m) -> new Object[]{o, v, m}));
        load(source, arr -> {
            ImmunePlanOverviewVO o = (ImmunePlanOverviewVO) arr[0];
            @SuppressWarnings("unchecked")
            List<VaccineOptionVO> v = (List<VaccineOptionVO>) arr[1];
            @SuppressWarnings("unchecked")
            List<DictItemVO> m = (List<DictItemVO>) arr[2];
            overview.setValue(o);
            vaccines.setValue(v);
            methods.setValue(m);
            if (v.isEmpty() && !Boolean.TRUE.equals(editMode.getValue())) {
                putError(F_DRUG, "本场还没有疫苗档案。请先在兽药与疫苗库存中新增疫苗。");
            }
        });
    }

    @Override
    public void retry() {
        load();
    }

    // ===== 输入 =====

    public void onVaccineSelected(long id) {
        drugId.setValue(id);
        clearError(F_DRUG);
        VaccineOptionVO v = vaccine(id);
        if (v != null && methodCode.getValue() == null && v.getDefaultMethodCode() != null) {
            methodCode.setValue(v.getDefaultMethodCode());
        }
    }

    /** 切换“按日龄 / 按日期”：已填值换算后保留 */
    public void onPlanModeChanged(@NonNull String mode) {
        if (mode.equals(planMode.getValue()) || Boolean.TRUE.equals(editMode.getValue())) {
            return;
        }
        ImmunePlanOverviewVO o = overview.getValue();
        LocalDate entry = o == null ? null : DateFormatter.parseDate(o.getEntryDate());
        if (o != null && entry != null) {
            if (ImmuneRules.MODE_BY_DATE.equals(mode)) {
                Integer age = HealthFormRules.parseInt(planAgeDay.getValue());
                if (age != null) {
                    planDate.setValue(DateFormatter.toDateString(
                            ImmuneRules.planDateOf(o.getEntryAgeDays(), entry, age)));
                }
            } else {
                LocalDate d = DateFormatter.parseDate(planDate.getValue());
                if (d != null) {
                    planAgeDay.setValue(String.valueOf(ImmuneRules.ageAt(o.getEntryAgeDays(), entry, d)));
                }
            }
        }
        planMode.setValue(mode);
        clearError(F_AGE);
        clearError(F_DATE);
    }

    public void onPlanDateSelected(@Nullable String date) {
        if (date != null) {
            planDate.setValue(date);
            clearError(F_DATE);
        }
    }

    public void onMethodSelected(@Nullable String code) {
        methodCode.setValue(code);
        clearError(F_METHOD);
    }

    @Nullable
    VaccineOptionVO vaccine(@Nullable Long id) {
        List<VaccineOptionVO> list = vaccines.getValue();
        if (id == null || list == null) {
            return null;
        }
        for (VaccineOptionVO v : list) {
            if (v.getDrugId() == id) {
                return v;
            }
        }
        return null;
    }

    @NonNull
    private String computeDoseUnit() {
        VaccineOptionVO v = vaccine(drugId.getValue());
        if (v != null && v.getDoseUnit() != null) {
            return v.getDoseUnit();
        }
        return editDoseUnit == null ? "" : editDoseUnit;
    }

    /** 按日龄：“10月21日 周三”（入栏日期 + (日龄 − 入栏日龄)）；按日期：“届时 99 日龄”。以后端返回的 plan_date 为准 */
    @NonNull
    private String computePreview() {
        ImmunePlanOverviewVO o = overview.getValue();
        LocalDate entry = o == null ? null : DateFormatter.parseDate(o.getEntryDate());
        if (ImmuneRules.MODE_BY_DAY_AGE.equals(planMode.getValue())) {
            Integer age = HealthFormRules.parseInt(planAgeDay.getValue());
            if (o == null || entry == null || age == null) {
                return Boolean.TRUE.equals(editMode.getValue()) ? HealthFormat.dateWeek(planDate.getValue()) : "";
            }
            return HealthFormat.dateWeek(DateFormatter.toDateString(ImmuneRules.planDateOf(o.getEntryAgeDays(), entry,
                    age)));
        }
        LocalDate d = DateFormatter.parseDate(planDate.getValue());
        if (o == null || entry == null || d == null) {
            return editAgeAtPlan == null ? "" : editAgeAtPlan + " 日龄";
        }
        return ImmuneRules.ageAt(o.getEntryAgeDays(), entry, d) + " 日龄";
    }

    // ===== 校验与提交 =====

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        boolean edit = Boolean.TRUE.equals(editMode.getValue());
        if (!edit) {
            if (drugId.getValue() == null) {
                List<VaccineOptionVO> list = vaccines.getValue();
                e.put(F_DRUG, list == null || list.isEmpty()
                        ? "本场还没有疫苗档案。请先在兽药与疫苗库存中新增疫苗。" : HealthFormRules.MSG_VACCINE);
            }
            ImmunePlanOverviewVO o = overview.getValue();
            if (ImmuneRules.MODE_BY_DAY_AGE.equals(planMode.getValue())) {
                String err = HealthFormRules.planAgeDay(planAgeDay.getValue(), o == null ? 0 : o.getDayAge());
                if (err != null) {
                    e.put(F_AGE, err);
                }
            } else {
                String err = HealthFormRules.planDate(planDate.getValue());
                if (err != null) {
                    e.put(F_DATE, err);
                }
            }
        }
        String doseErr = HealthFormRules.dose(dose.getValue());
        if (doseErr != null) {
            e.put(F_DOSE, doseErr);
        }
        if (methodCode.getValue() == null) {
            e.put(F_METHOD, HealthFormRules.MSG_METHOD);
        }
        return e;
    }

    /**
     * @param continueAdding “保存并继续添加”
     */
    public void submit(boolean continueAdding) {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            scrollToField.setValue(errors.keySet().iterator().next());
            return;
        }
        submitting.setValue(true);
        if (planId != null) {
            ImmunePlanUpdateRequest req = new ImmunePlanUpdateRequest();
            req.dose = HealthFormRules.parseDecimal(dose.getValue());
            req.methodCode = methodCode.getValue();
            req.version = version;
            launch(repository.updatePlan(planId, req), vo -> {
                submitting.setValue(false);
                finishWithResult.setValue(vo.getId());
            }, this::onSubmitError);
            return;
        }
        ImmunePlanCreateRequest req = new ImmunePlanCreateRequest();
        req.drugId = Objects.requireNonNull(drugId.getValue());
        req.planMode = planMode.getValue();
        if (ImmuneRules.MODE_BY_DAY_AGE.equals(req.planMode)) {
            req.planAgeDay = HealthFormRules.parseInt(planAgeDay.getValue());
        } else {
            req.planDate = planDate.getValue();
        }
        req.dose = HealthFormRules.parseDecimal(dose.getValue());
        req.methodCode = methodCode.getValue();
        launch(repository.createPlan(batchId, req, idem.keyFor(req)), vo -> onCreated(vo, continueAdding),
                this::onSubmitError);
    }

    private void onCreated(@NonNull ImmunePlanVO vo, boolean continueAdding) {
        submitting.setValue(false);
        idem.reset();
        if (!continueAdding) {
            finishWithResult.setValue(vo.getId());
            return;
        }
        drugId.setValue(null);
        planAgeDay.setValue("");
        planDate.setValue("");
        dose.setValue("");
        methodCode.setValue(null);
        fieldErrors.setValue(Collections.emptyMap());
        lastCreatedId = vo.getId();
        resetForm.call();
        showMessage("已添加，可以继续添加下一项");
    }

    /** 继续添加后最近一次新增的项目（返回 D01 时高亮） */
    @Nullable
    private Long lastCreatedId;

    @Nullable
    public Long getLastCreatedId() {
        return lastCreatedId;
    }

    private void onSubmitError(@NonNull ApiException e) {
        submitting.setValue(false);
        int code = e.getCode();
        if (code == ErrorCodes.STATE_CHANGED || code == ErrorCodes.BATCH_ENDED) {
            backWithMessage.setValue(e.getMessage());
            return;
        }
        if (ErrorCodes.isNoPermission(code)) {
            showMessage(e.getMessage());
            return;
        }
        if (e.isNetwork()) {
            showMessage(SubmitSupport.MSG_NETWORK_KEEP);
            return;
        }
        handleError(e);
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

    private void putError(@NonNull String field, @NonNull String message) {
        Map<String, String> copy = new LinkedHashMap<>(fieldErrors.getValue() == null ? Collections.emptyMap()
                : fieldErrors.getValue());
        copy.put(field, message);
        fieldErrors.setValue(copy);
    }

    /** 返回键：已填写内容时询问“放弃这次登记？” */
    public boolean isDirty() {
        if (Boolean.TRUE.equals(editMode.getValue())) {
            return false;
        }
        return drugId.getValue() != null || !HealthFormat.nonNull(planAgeDay.getValue()).isEmpty()
                || !HealthFormat.nonNull(planDate.getValue()).isEmpty() || !HealthFormat.nonNull(dose.getValue()).isEmpty();
    }
}
