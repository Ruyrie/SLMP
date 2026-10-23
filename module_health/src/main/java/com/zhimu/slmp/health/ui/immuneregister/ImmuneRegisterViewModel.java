package com.zhimu.slmp.health.ui.immuneregister;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.data.HealthSharedRepository;
import com.zhimu.slmp.health.data.ImmuneRepository;
import com.zhimu.slmp.health.model.request.ImmuneRecordCreateRequest;
import com.zhimu.slmp.health.model.request.ImmuneRecordUpdateRequest;
import com.zhimu.slmp.health.model.vo.DrugLotOptionVO;
import com.zhimu.slmp.health.model.vo.DrugLotOptionsVO;
import com.zhimu.slmp.health.model.vo.ImmunePlanOptionVO;
import com.zhimu.slmp.health.model.vo.ImmuneRecordVO;
import com.zhimu.slmp.health.model.vo.ImmuneRegisterFormVO;
import com.zhimu.slmp.health.model.vo.MemberItemVO;
import com.zhimu.slmp.health.model.vo.VaccineOptionVO;
import com.zhimu.slmp.health.util.HealthFormRules;
import com.zhimu.slmp.health.util.HealthFormat;
import com.zhimu.slmp.health.util.LotPicker;
import com.zhimu.slmp.health.util.SubmitSupport;
import com.zhimu.slmp.network.model.PageVO;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * D03 登记接种（REGISTER / BACKFILL 补录漏免 / PICK 未带计划 / VIEW 查看记录 / EDIT 修改记录）。
 * 免疫计划（API-D11）、预填（API-D12）、疫苗（API-D13）、库存批号（API-M27，默认先到期先出，过期停用不可选）、
 * 剂量、接种数量（不超过存栏）、接种方式、接种日期（补录时计划日期至今天）、实际执行人（员工不显示、不提交）。
 * 提交 API-D14（Idempotency-Key，超时用 API-D41 确认），修改 API-D16。
 */
public class ImmuneRegisterViewModel extends BaseViewModel {

    public static final String MODE_REGISTER = "REGISTER";
    public static final String MODE_BACKFILL = "BACKFILL";
    public static final String MODE_PICK = "PICK";
    public static final String MODE_VIEW = "VIEW";
    public static final String MODE_EDIT = "EDIT";

    public static final String F_PLAN = "plan_id";
    public static final String F_DRUG = "drug_id";
    public static final String F_LOT = "drug_lot_id";
    public static final String F_DOSE = "dose";
    public static final String F_QUANTITY = "quantity";
    public static final String F_METHOD = "method_code";
    public static final String F_DATE = "vaccinate_date";

    static final int PLAN_PAGE_SIZE = 50;

    private final ImmuneRepository repository;
    private final HealthSharedRepository shared;
    private final FarmContext farmContext;

    private final MutableLiveData<String> mode = new MutableLiveData<>(MODE_REGISTER);
    private final MutableLiveData<List<ImmunePlanOptionVO>> planOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<ImmuneRegisterFormVO> formCtx = new MutableLiveData<>();
    private final MutableLiveData<List<VaccineOptionVO>> vaccines = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<DictItemVO>> methods = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<DrugLotOptionsVO> lotOptions = new MutableLiveData<>();
    private final MutableLiveData<DrugLotOptionVO> selectedLot = new MutableLiveData<>();
    private final MutableLiveData<String> lotError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showStockIn = new MutableLiveData<>(false);
    private final MutableLiveData<List<MemberItemVO>> executors = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<ImmuneRecordVO> record = new MutableLiveData<>();
    private final MutableLiveData<Long> planId = new MutableLiveData<>();
    private final MutableLiveData<Long> drugId = new MutableLiveData<>();
    private final MutableLiveData<Long> executorId = new MutableLiveData<>();
    private final MutableLiveData<String> dose = new MutableLiveData<>("");
    private final MutableLiveData<String> quantity = new MutableLiveData<>("");
    private final MutableLiveData<String> methodCode = new MutableLiveData<>();
    private final MutableLiveData<String> vaccinateDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MediatorLiveData<String> deductText = new MediatorLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    private final SingleLiveEvent<Void> showLotSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> goStockIn = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> finishWithSuccess = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> backToPlan = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> needChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> scrollToField = new SingleLiveEvent<>();

    private final SubmitSupport.IdemKeeper idem = new SubmitSupport.IdemKeeper();
    @Nullable
    private Long recordId;
    @Nullable
    private Long todoId;
    private boolean backfill;
    @Nullable
    private Disposable lotRequest;

    @Inject
    public ImmuneRegisterViewModel(@NonNull ImmuneRepository repository, @NonNull HealthSharedRepository shared,
                                   @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.shared = shared;
        this.farmContext = farmContext;
        deductText.addSource(dose, v -> deductText.setValue(computeDeductText()));
        deductText.addSource(quantity, v -> deductText.setValue(computeDeductText()));
        deductText.addSource(selectedLot, v -> deductText.setValue(computeDeductText()));
        canSubmit.addSource(submitting, v -> canSubmit.setValue(!Boolean.TRUE.equals(v)));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        idem.attach(handle);
        Long plan = asLong(handle.get(RouterPath.EXTRA_PLAN_ID));
        if (plan == null) {
            plan = asLong(handle.get(RouterPath.EXTRA_PLAN_ITEM_ID));
        }
        Object b = handle.get(RouterPath.EXTRA_BACKFILL);
        boolean bf = Boolean.TRUE.equals(b) || "true".equals(b);
        todoId = asLong(handle.get(RouterPath.EXTRA_TODO_ID));
        init(plan, bf, asLong(handle.get(RouterPath.EXTRA_RECORD_ID)));
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

    public LiveData<String> getMode() { return mode; }

    public LiveData<List<ImmunePlanOptionVO>> getPlanOptions() { return planOptions; }

    public LiveData<ImmuneRegisterFormVO> getFormCtx() { return formCtx; }

    public LiveData<List<VaccineOptionVO>> getVaccines() { return vaccines; }

    public LiveData<List<DictItemVO>> getMethods() { return methods; }

    public LiveData<DrugLotOptionsVO> getLotOptions() { return lotOptions; }

    public LiveData<DrugLotOptionVO> getSelectedLot() { return selectedLot; }

    public LiveData<String> getLotError() { return lotError; }

    public LiveData<Boolean> getShowStockIn() { return showStockIn; }

    public LiveData<List<MemberItemVO>> getExecutors() { return executors; }

    public LiveData<ImmuneRecordVO> getRecord() { return record; }

    public MutableLiveData<Long> getPlanId() { return planId; }

    public MutableLiveData<Long> getDrugId() { return drugId; }

    public MutableLiveData<Long> getExecutorId() { return executorId; }

    public MutableLiveData<String> getDose() { return dose; }

    public MutableLiveData<String> getQuantity() { return quantity; }

    public MutableLiveData<String> getMethodCode() { return methodCode; }

    public MutableLiveData<String> getVaccinateDate() { return vaccinateDate; }

    public LiveData<String> getDeductText() { return deductText; }

    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }

    public LiveData<Boolean> getSubmitting() { return submitting; }

    public LiveData<Boolean> getCanSubmit() { return canSubmit; }

    public SingleLiveEvent<Void> getShowLotSheet() { return showLotSheet; }

    public SingleLiveEvent<Void> getGoStockIn() { return goStockIn; }

    public SingleLiveEvent<String> getFinishWithSuccess() { return finishWithSuccess; }

    public SingleLiveEvent<String> getBackToPlan() { return backToPlan; }

    public SingleLiveEvent<Long> getOpenChangeRequest() { return openChangeRequest; }

    public SingleLiveEvent<Long> getOpenRequest() { return openRequest; }

    public SingleLiveEvent<String> getNeedChangeRequest() { return needChangeRequest; }

    public SingleLiveEvent<String> getScrollToField() { return scrollToField; }

    public boolean isStaff() { return farmContext.isStaff(); }

    public boolean isEditable() {
        String m = mode.getValue();
        return !MODE_VIEW.equals(m);
    }

    /** 计划下拉可改（未带计划进入，或从待办 / D01 进入的登记） */
    public boolean isPlanChangeable() {
        String m = mode.getValue();
        return MODE_REGISTER.equals(m) || MODE_PICK.equals(m);
    }

    @Nullable
    public Long getRecordId() { return recordId; }

    // ===== 初始化 =====

    /** 进入参数：plan_id（可空）、backfill、record_id（查看） */
    public void init(@Nullable Long plan, boolean backfill, @Nullable Long recordId) {
        this.recordId = recordId;
        this.backfill = backfill;
        if (recordId != null) {
            mode.setValue(MODE_VIEW);
            Single<List<DictItemVO>> methodList = shared.getVaccinateMethods(null)
                    .onErrorReturnItem(Collections.emptyList());
            load(Single.zip(repository.getRecord(recordId), methodList, (r, m) -> new Object[]{r, m}), arr -> {
                ImmuneRecordVO r = (ImmuneRecordVO) arr[0];
                @SuppressWarnings("unchecked")
                List<DictItemVO> m = (List<DictItemVO>) arr[1];
                methods.setValue(m);
                showRecord(r);
            });
            return;
        }
        mode.setValue(plan == null ? MODE_PICK : backfill ? MODE_BACKFILL : MODE_REGISTER);
        Single<PageVO<ImmunePlanOptionVO>> options = repository.getPlanOptions(null, 1, PLAN_PAGE_SIZE)
                .onErrorReturnItem(new PageVO<>());
        Single<List<VaccineOptionVO>> vaccineList = repository.getVaccineOptions(null);
        Single<List<DictItemVO>> methodList = shared.getVaccinateMethods(null)
                .onErrorReturnItem(Collections.emptyList());
        Single<List<MemberItemVO>> members = isStaff() ? Single.just(Collections.emptyList())
                : shared.getMembers().onErrorReturnItem(Collections.emptyList());
        Single<ImmuneRegisterFormVO> form = plan == null ? Single.just(new ImmuneRegisterFormVO())
                : repository.getRegisterForm(plan, backfill);
        load(Single.zip(options, vaccineList, methodList, members, form, Init::new), init -> {
            planOptions.setValue(init.options.getList());
            vaccines.setValue(init.vaccines);
            methods.setValue(init.methods);
            executors.setValue(init.members);
            if (plan != null) {
                applyForm(init.form);
            }
        });
    }

    /** 并行加载的结果 */
    private static final class Init {
        final PageVO<ImmunePlanOptionVO> options;
        final List<VaccineOptionVO> vaccines;
        final List<DictItemVO> methods;
        final List<MemberItemVO> members;
        final ImmuneRegisterFormVO form;

        Init(PageVO<ImmunePlanOptionVO> options, List<VaccineOptionVO> vaccines, List<DictItemVO> methods,
             List<MemberItemVO> members, ImmuneRegisterFormVO form) {
            this.options = options;
            this.vaccines = vaccines;
            this.methods = methods;
            this.members = members;
            this.form = form;
        }
    }

    @Override
    public void retry() {
        ImmunePlanOptionVO plan = formCtx.getValue() == null ? null : formCtx.getValue().getPlan();
        init(plan == null ? planId.getValue() : Long.valueOf(plan.getPlanId()), backfill, recordId);
    }

    /** 40903（已被他人登记）：提示后返回 D01 刷新；40303（员工补录漏免）：整页无权查看 */
    @Override
    protected void applyErrorState(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.STATE_CHANGED) {
            backToPlan.setValue(e.getMessage());
            return;
        }
        super.applyErrorState(e);
    }

    /** 用 API-D12 预填：疫苗、批号（默认有效期最早）、剂量、数量（默认等于存栏）、方式、日期、执行人 */
    void applyForm(@NonNull ImmuneRegisterFormVO form) {
        formCtx.setValue(form);
        ImmunePlanOptionVO plan = form.getPlan();
        if (plan != null) {
            planId.setValue(plan.getPlanId());
        }
        backfill = form.isBackfill() || backfill;
        if (form.isBackfill()) {
            mode.setValue(MODE_BACKFILL);
        }
        dose.setValue(HealthFormat.plain(form.getDose() != null ? form.getDose() : form.getTemplateDose()));
        quantity.setValue(String.valueOf(form.getQuantityDefault()));
        methodCode.setValue(form.getMethodCode());
        vaccinateDate.setValue(form.getDateDefault() != null ? form.getDateDefault() : DateFormatter.todayString());
        if (!isStaff() && form.getDefaultExecutorId() != null) {
            executorId.setValue(form.getDefaultExecutorId());
        }
        fieldErrors.setValue(Collections.emptyMap());
        drugId.setValue(form.getDrugId());
        lotOptions.setValue(null);
        selectedLot.setValue(form.getDefaultLot());
        if (form.getDrugId() != null) {
            queryLots(form.getDrugId(), form.getDefaultLot() == null ? null : form.getDefaultLot().getLotId());
        } else {
            setLotError(form.getNoLotText() != null ? form.getNoLotText()
                    : LotPicker.noLotText(form.getVaccineName(), isStaff()));
        }
    }

    // ===== 选择 =====

    /** 计划下拉：更换计划后重新预填（API-D12） */
    public void selectPlan(long id) {
        Long current = planId.getValue();
        if (current != null && current == id && formCtx.getValue() != null) {
            return;
        }
        ImmunePlanOptionVO option = planOption(id);
        boolean bf = option != null && "MISSED".equals(option.getStatus());
        planId.setValue(id);
        clearError(F_PLAN);
        launch(repository.getRegisterForm(id, bf), this::applyForm, e -> {
            if (e.getCode() == ErrorCodes.STATE_CHANGED || ErrorCodes.isNoPermission(e.getCode())) {
                showMessage(e.getMessage());
                planId.setValue(current);
                return;
            }
            handleError(e);
        });
    }

    @Nullable
    ImmunePlanOptionVO planOption(@Nullable Long id) {
        List<ImmunePlanOptionVO> list = planOptions.getValue();
        if (id == null || list == null) {
            return null;
        }
        for (ImmunePlanOptionVO o : list) {
            if (o.getPlanId() == id) {
                return o;
            }
        }
        return null;
    }

    /** 更换疫苗后库存批号重新按有效期最早预选 */
    public void selectVaccine(long id) {
        if (Objects.equals(drugId.getValue(), id) && lotOptions.getValue() != null) {
            return;
        }
        drugId.setValue(id);
        clearError(F_DRUG);
        selectedLot.setValue(null);
        queryLots(id, null);
    }

    /** 批号面板选中（只接受可选批号；不可选的项在面板中无响应） */
    public void selectLot(long lotId) {
        DrugLotOptionVO lot = LotPicker.find(lotOptions.getValue(), lotId);
        if (lot == null) {
            return;
        }
        selectedLot.setValue(lot);
        clearError(F_LOT);
        refreshLotError();
    }

    public void onLotFieldClick() {
        if (isEditable() && lotOptions.getValue() != null) {
            showLotSheet.call();
        }
    }

    public void onExecutorSelected(long userId) {
        executorId.setValue(userId);
    }

    public void onMethodSelected(@Nullable String code) {
        methodCode.setValue(code);
        clearError(F_METHOD);
    }

    public void onDateSelected(@Nullable String date) {
        if (date != null) {
            vaccinateDate.setValue(date);
            clearError(F_DATE);
        }
    }

    public void onStockInClick() {
        goStockIn.call();
    }

    /** 剂量或接种数量停止输入 500 毫秒后：校验数量、按 need_qty 重查批号（API-M27） */
    public void onDoseOrQuantityChanged() {
        validateQuantityLive();
        Long drug = drugId.getValue();
        if (drug != null && isEditable()) {
            DrugLotOptionVO lot = selectedLot.getValue();
            queryLots(drug, lot == null ? null : lot.getLotId());
        }
    }

    /** 数量超过存栏时字段立即变为错误态 */
    public void validateQuantityLive() {
        ImmuneRegisterFormVO f = formCtx.getValue();
        String q = HealthFormat.nonNull(quantity.getValue()).trim();
        if (f == null || q.isEmpty()) {
            return;
        }
        String err = HealthFormRules.vaccinateQuantity(q, f.getCurrentCount(), unit());
        if (err != null) {
            putError(F_QUANTITY, err);
        } else {
            clearError(F_QUANTITY);
        }
        clearError(F_DOSE);
    }

    /** API-M27（purpose 接种，need_qty = 剂量 × 数量）；keepLotId 仍可选时保留，否则取默认批号 */
    void queryLots(long drug, @Nullable Long keepLotId) {
        if (lotRequest != null) {
            lotRequest.dispose();
        }
        lotRequest = launch(shared.getLotOptions(drug, HealthExtras.PURPOSE_VACCINATE, needQty()), options -> {
            lotOptions.setValue(options);
            DrugLotOptionVO keep = LotPicker.find(options, keepLotId);
            selectedLot.setValue(keep != null ? keep : LotPicker.defaultLot(options));
            refreshLotError();
        }, e -> {
            if (ErrorCodes.isNoPermission(e.getCode())) {
                showMessage(e.getMessage());
                return;
            }
            showMessage(e.isNetwork() ? ApiException.MSG_NETWORK : e.getMessage());
        });
    }

    /** 没有可用批号或结存不足时的错误文字 */
    void refreshLotError() {
        DrugLotOptionsVO options = lotOptions.getValue();
        if (options == null) {
            return;
        }
        if (options.getSelectable().isEmpty()) {
            ImmuneRegisterFormVO f = formCtx.getValue();
            String text = f != null && Objects.equals(f.getDrugId(), drugId.getValue()) && f.getNoLotText() != null
                    ? f.getNoLotText() : LotPicker.noLotText(vaccineName(), isStaff());
            setLotError(text);
            return;
        }
        DrugLotOptionVO lot = selectedLot.getValue();
        BigDecimal need = needQty();
        if (lot != null && LotPicker.isInsufficient(lot, need)) {
            lotError.setValue(LotPicker.insufficientText(lot, need, stockUnit()));
            showStockIn.setValue(false);
            return;
        }
        lotError.setValue(null);
        showStockIn.setValue(false);
    }

    private void setLotError(@NonNull String text) {
        lotError.setValue(text);
        showStockIn.setValue(!isStaff());
    }

    // ===== 派生 =====

    /** 本次扣减量 = 剂量 × 接种数量 */
    @Nullable
    BigDecimal needQty() {
        BigDecimal d = HealthFormRules.parseDecimal(dose.getValue());
        Integer q = HealthFormRules.parseInt(quantity.getValue());
        if (d == null || q == null || d.signum() <= 0 || q <= 0) {
            return null;
        }
        return d.multiply(BigDecimal.valueOf(q));
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

    @Nullable
    String vaccineName() {
        VaccineOptionVO v = vaccine(drugId.getValue());
        if (v != null) {
            return v.getName();
        }
        ImmuneRegisterFormVO f = formCtx.getValue();
        if (f != null) {
            return f.getVaccineName();
        }
        ImmuneRecordVO r = record.getValue();
        return r == null ? null : r.getVaccineName();
    }

    /** 剂量单位“头份 / 头” */
    @NonNull
    public String doseUnit() {
        VaccineOptionVO v = vaccine(drugId.getValue());
        if (v != null && v.getDoseUnit() != null) {
            return v.getDoseUnit();
        }
        ImmuneRegisterFormVO f = formCtx.getValue();
        if (f != null && f.getDoseUnit() != null) {
            return f.getDoseUnit();
        }
        ImmuneRecordVO r = record.getValue();
        return r == null ? "" : HealthFormat.nonNull(r.getDoseUnit());
    }

    /** 记账单位“头份” */
    @NonNull
    public String stockUnit() {
        VaccineOptionVO v = vaccine(drugId.getValue());
        if (v != null && v.getStockUnit() != null) {
            return v.getStockUnit();
        }
        return HealthFormat.stockPart(doseUnit());
    }

    @NonNull
    public String unit() {
        return HealthFormat.countUnit(farmContext);
    }

    /** “登记后自动扣减批号 2025081 共 200 头份，对应待办自动完成。” */
    @Nullable
    String computeDeductText() {
        DrugLotOptionVO lot = selectedLot.getValue();
        BigDecimal need = needQty();
        if (lot == null || need == null) {
            return null;
        }
        return "登记后自动扣减批号 " + lot.getLotNo() + " 共 " + HealthFormat.qty(need, stockUnit())
                + "，对应待办自动完成。";
    }

    /** 批号字段辅助文字：默认批号“有效期至 2026-12-30 · 已默认选择有效期最早的批号”，手动改选后只写有效期 */
    @Nullable
    public String lotHelper() {
        DrugLotOptionVO lot = selectedLot.getValue();
        if (lot == null) {
            ImmuneRecordVO r = record.getValue();
            return r == null ? null : "有效期至 " + HealthFormat.nonNull(r.getLotExpireDate());
        }
        return LotPicker.helperText(lot, LotPicker.isDefault(lotOptions.getValue(), lot) && isEditable()
                && !MODE_EDIT.equals(mode.getValue()));
    }

    /** 默认批号显示“先到期先出”标签 */
    public boolean isDefaultLotSelected() {
        DrugLotOptionVO lot = selectedLot.getValue();
        return lot != null && LotPicker.isDefault(lotOptions.getValue(), lot) && !MODE_VIEW.equals(mode.getValue());
    }

    /** 计划辅助文字：“ZM2610-031 · 存栏 200 头 · 计划日期今天” */
    @Nullable
    public String planHelper() {
        ImmuneRegisterFormVO f = formCtx.getValue();
        ImmunePlanOptionVO p = f == null ? null : f.getPlan();
        if (p == null) {
            p = planOption(planId.getValue());
        }
        if (p == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (p.getBatchNo() != null) {
            sb.append(p.getBatchNo()).append(HealthFormat.DOT);
        }
        sb.append("存栏 ").append(HealthFormat.qty(p.getCurrentCount(), unit())).append(HealthFormat.DOT)
                .append("计划日期").append(p.getPlanDateText() != null ? p.getPlanDateText() : HealthFormat.date(p.getPlanDate()));
        return sb.toString();
    }

    /** “3 号育肥舍 · 猪瘟活疫苗 加强免疫” */
    @NonNull
    public static String planTitle(@NonNull ImmunePlanOptionVO p) {
        return HealthFormat.nonNull(p.getPenName()) + HealthFormat.DOT + HealthFormat.nonNull(p.getVaccineName())
                + (p.getImmuneRound() == null ? "" : " " + p.getImmuneRound());
    }

    /** 登记人（员工“登记人：李明（系统自动记录）”；养殖户在执行人下方“登记人：王建国”） */
    @NonNull
    public String creatorName() {
        ImmuneRegisterFormVO f = formCtx.getValue();
        if (f != null && f.getCreatorName() != null) {
            return f.getCreatorName();
        }
        ImmuneRecordVO r = record.getValue();
        return r == null ? "" : HealthFormat.nonNull(r.getCreatorName());
    }

    // ===== 校验与提交 =====

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        boolean edit = MODE_EDIT.equals(mode.getValue());
        if (!edit && planId.getValue() == null) {
            e.put(F_PLAN, "请选择免疫计划");
        }
        if (!edit && drugId.getValue() == null) {
            e.put(F_DRUG, HealthFormRules.MSG_VACCINE);
        }
        DrugLotOptionVO lot = selectedLot.getValue();
        DrugLotOptionsVO options = lotOptions.getValue();
        if (lot == null || (options != null && !LotPicker.isSelectable(options, lot.getLotId()))) {
            String err = lotError.getValue();
            e.put(F_LOT, err != null ? err : HealthFormRules.MSG_LOT);
        } else if (LotPicker.isInsufficient(lot, needQty())) {
            e.put(F_LOT, LotPicker.insufficientText(lot, needQty(), stockUnit()));
        }
        String doseErr = HealthFormRules.dose(dose.getValue());
        if (doseErr != null) {
            e.put(F_DOSE, doseErr);
        }
        ImmuneRegisterFormVO f = formCtx.getValue();
        if (f != null) {
            String q = HealthFormRules.vaccinateQuantity(quantity.getValue(), f.getCurrentCount(), unit());
            if (q != null) {
                e.put(F_QUANTITY, q);
            }
        } else {
            Integer q = HealthFormRules.parseInt(quantity.getValue());
            if (q == null || q <= 0) {
                e.put(F_QUANTITY, HealthFormRules.MSG_QUANTITY);
            }
        }
        if (methodCode.getValue() == null) {
            e.put(F_METHOD, HealthFormRules.MSG_METHOD);
        }
        String dateErr = HealthFormRules.vaccinateDate(vaccinateDate.getValue(), f == null ? null : f.getDateMin(),
                f == null ? null : f.getDateMax());
        if (dateErr != null) {
            e.put(F_DATE, dateErr);
        }
        return e;
    }

    /** 底部“登记接种”/“保存修改” */
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
        DrugLotOptionVO lot = Objects.requireNonNull(selectedLot.getValue());
        submitting.setValue(true);
        if (MODE_EDIT.equals(mode.getValue()) && recordId != null) {
            ImmuneRecordVO r = record.getValue();
            ImmuneRecordUpdateRequest req = new ImmuneRecordUpdateRequest();
            req.drugLotId = lot.getLotId();
            req.dose = HealthFormRules.parseDecimal(dose.getValue());
            req.quantity = Objects.requireNonNull(HealthFormRules.parseInt(quantity.getValue()));
            req.methodCode = methodCode.getValue();
            req.vaccinateDate = vaccinateDate.getValue();
            req.executorId = executor;
            req.version = r == null ? 0 : r.getVersion();
            launch(repository.updateRecord(recordId, req), vo -> {
                submitting.setValue(false);
                record.setValue(vo);
                finishWithSuccess.setValue("已保存修改");
            }, this::onSubmitError);
            return;
        }
        ImmuneRecordCreateRequest req = buildCreateRequest(lot, executor);
        launch(repository.createRecord(req, idem.keyFor(req)), this::onCreated, this::onSubmitError);
    }

    @NonNull
    ImmuneRecordCreateRequest buildCreateRequest(@NonNull DrugLotOptionVO lot, @Nullable Long executor) {
        ImmuneRecordCreateRequest req = new ImmuneRecordCreateRequest();
        req.planId = Objects.requireNonNull(planId.getValue());
        req.drugId = Objects.requireNonNull(drugId.getValue());
        req.drugLotId = lot.getLotId();
        req.dose = HealthFormRules.parseDecimal(dose.getValue());
        req.quantity = Objects.requireNonNull(HealthFormRules.parseInt(quantity.getValue()));
        req.methodCode = methodCode.getValue();
        req.vaccinateDate = vaccinateDate.getValue();
        req.executorId = executor;
        return req;
    }

    private void onCreated(@NonNull ImmuneRecordVO vo) {
        submitting.setValue(false);
        idem.reset();
        notifyTodo(vo.getId());
        finishWithSuccess.setValue(Boolean.FALSE.equals(vo.getTodoCompleted()) ? "已登记接种" : "已登记接种，待办已自动完成");
    }

    private void notifyTodo(long id) {
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.notifyTodoChanged(new TodoChange(todoId, HealthExtras.BIZ_IMMUNE_RECORD, id,
                    farmContext.farmIdOrZero()));
        }
    }

    private void onSubmitError(@NonNull ApiException e) {
        int code = e.getCode();
        boolean edit = MODE_EDIT.equals(mode.getValue());
        if (!edit && SubmitSupport.isTimeout(e)) {
            confirmByRequestId();
            return;
        }
        submitting.setValue(false);
        switch (code) {
            case ErrorCodes.COUNT_EXCEEDED:
                putError(F_QUANTITY, e.getMessage());
                scrollToField.setValue(F_QUANTITY);
                return;
            case ErrorCodes.STOCK_NOT_ENOUGH:
            case ErrorCodes.LOT_UNAVAILABLE:
                onLotRejected(e);
                return;
            case ErrorCodes.BANNED_DRUG:
                putError(F_DRUG, e.getMessage());
                scrollToField.setValue(F_DRUG);
                return;
            case ErrorCodes.STATE_CHANGED:
                backToPlan.setValue(e.getMessage());
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

    /** 42201 / 42204：显示在批号字段并重查 API-M27；并发扣减（“库存已变化”）用 Snackbar 带“刷新” */
    private void onLotRejected(@NonNull ApiException e) {
        String msg = e.getMessage();
        putError(F_LOT, msg);
        scrollToField.setValue(F_LOT);
        if (msg.contains("库存已变化")) {
            showMessage(UiMessage.withAction(msg, "刷新", SubmitSupport.ACTION_REFRESH_LOTS));
        } else {
            refreshLots();
        }
    }

    /** Snackbar“刷新”：重查批号 */
    public void refreshLots() {
        Long drug = drugId.getValue();
        if (drug != null) {
            DrugLotOptionVO lot = selectedLot.getValue();
            queryLots(drug, lot == null ? null : lot.getLotId());
        }
    }

    /** API-D41：超时后按幂等键确认是否已保存；DONE 按成功处理，NOT_FOUND 提示“提交没有成功，请重试”（“重试”沿用原键） */
    public void confirmByRequestId() {
        String key = idem.current();
        if (key == null) {
            submitting.setValue(false);
            return;
        }
        launch(repository.getRequestStatus(key, HealthExtras.BIZ_IMMUNE_RECORD), status -> {
            if ("DONE".equals(status.getStatus())) {
                submitting.setValue(false);
                idem.reset();
                Long id = status.getBizIds().isEmpty() ? null : status.getBizIds().get(0);
                notifyTodo(id == null ? 0 : id);
                finishWithSuccess.setValue("已登记接种，待办已自动完成");
            } else {
                submitting.setValue(false);
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
            refreshLots();
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

    // ===== 查看与修改 =====

    /** 查看状态：字段只读回填 */
    private void showRecord(@NonNull ImmuneRecordVO r) {
        record.setValue(r);
        dose.setValue(HealthFormat.plain(r.getDose()));
        quantity.setValue(String.valueOf(r.getQuantity()));
        methodCode.setValue(r.getMethodCode());
        vaccinateDate.setValue(r.getVaccinateDate());
        planId.setValue(r.getPlanId());
    }

    /** 查看状态底部操作：EDIT“修改记录”、CHANGE_REQUEST“提交修改申请”（需审核，U01）、VIEW_REQUEST“查看修改申请”（U02） */
    public void onViewAction(@NonNull String action) {
        ImmuneRecordVO r = record.getValue();
        if (r == null) {
            return;
        }
        switch (action) {
            case HealthExtras.ACTION_EDIT:
                enterEdit();
                break;
            case HealthExtras.ACTION_CHANGE_REQUEST:
                openChangeRequest.setValue(r.getId());
                break;
            case HealthExtras.ACTION_VIEW_REQUEST:
                openRequest.setValue(r.getChangeState() == null ? null : r.getChangeState().getPendingRequestId());
                break;
            default:
                break;
        }
    }

    /** 进入修改：按记录回填，取该疫苗的批号选项与执行人 */
    public void enterEdit() {
        ImmuneRecordVO r = record.getValue();
        if (r == null) {
            return;
        }
        mode.setValue(MODE_EDIT);
        dose.setValue(HealthFormat.plain(r.getDose()));
        quantity.setValue(String.valueOf(r.getQuantity()));
        methodCode.setValue(r.getMethodCode());
        vaccinateDate.setValue(r.getVaccinateDate());
        fieldErrors.setValue(Collections.emptyMap());
        Single<List<VaccineOptionVO>> vaccineList = repository.getVaccineOptions(null)
                .onErrorReturnItem(Collections.emptyList());
        Single<List<DictItemVO>> methodList = shared.getVaccinateMethods(null).onErrorReturnItem(Collections.emptyList());
        Single<List<MemberItemVO>> members = isStaff() ? Single.just(Collections.emptyList())
                : shared.getMembers().onErrorReturnItem(Collections.emptyList());
        launch(Single.zip(vaccineList, methodList, members, (v, m, mem) -> new Object[]{v, m, mem}), arr -> {
            @SuppressWarnings("unchecked")
            List<VaccineOptionVO> v = (List<VaccineOptionVO>) arr[0];
            @SuppressWarnings("unchecked")
            List<DictItemVO> m = (List<DictItemVO>) arr[1];
            @SuppressWarnings("unchecked")
            List<MemberItemVO> mem = (List<MemberItemVO>) arr[2];
            vaccines.setValue(v);
            methods.setValue(m);
            executors.setValue(mem);
            Long executor = r.getExecutorId();
            if (executor == null) {
                for (MemberItemVO member : mem) {
                    if (Objects.equals(member.getName(), r.getExecutorName())) {
                        executor = member.getUserId();
                        break;
                    }
                }
            }
            executorId.setValue(executor);
            Long drug = r.getDrugId();
            if (drug == null) {
                for (VaccineOptionVO vo : v) {
                    if (Objects.equals(vo.getName(), r.getVaccineName())) {
                        drug = vo.getDrugId();
                        break;
                    }
                }
            }
            drugId.setValue(drug);
            if (drug != null) {
                queryLots(drug, r.getDrugLotId());
            }
        });
    }

    /** 40307（养殖户修改非当天记录）：确认后打开 U01 */
    public void onChangeRequestConfirmed() {
        ImmuneRecordVO r = record.getValue();
        if (r != null) {
            openChangeRequest.setValue(r.getId());
        }
    }

    /** 返回键：新登记且改动过时询问“放弃这次登记？” */
    public boolean isDirty() {
        String m = mode.getValue();
        if (MODE_VIEW.equals(m)) {
            return false;
        }
        ImmuneRegisterFormVO f = formCtx.getValue();
        if (MODE_EDIT.equals(m)) {
            return true;
        }
        if (f == null) {
            return planId.getValue() != null;
        }
        return !Objects.equals(HealthFormat.plain(f.getDose()), dose.getValue())
                || !Objects.equals(String.valueOf(f.getQuantityDefault()), quantity.getValue())
                || !Objects.equals(f.getMethodCode(), methodCode.getValue())
                || !Objects.equals(f.getDateDefault(), vaccinateDate.getValue());
    }
}
