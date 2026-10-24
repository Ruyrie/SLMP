package com.zhimu.slmp.health.ui.disinfectplanedit;

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
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.data.DisinfectRepository;
import com.zhimu.slmp.health.data.HealthSharedRepository;
import com.zhimu.slmp.health.model.request.DisinfectPlanPreviewRequest;
import com.zhimu.slmp.health.model.request.DisinfectPlanSaveRequest;
import com.zhimu.slmp.health.model.vo.DisinfectPlanDetailVO;
import com.zhimu.slmp.health.model.vo.DisinfectPlanPreviewVO;
import com.zhimu.slmp.health.model.vo.DisinfectPlanVO;
import com.zhimu.slmp.health.model.vo.DisinfectantOptionVO;
import com.zhimu.slmp.health.model.vo.PenOptionVO;
import com.zhimu.slmp.health.ui.disinfectplan.DisinfectPlanListViewModel;
import com.zhimu.slmp.health.util.ConcentrationUtil;
import com.zhimu.slmp.health.util.HealthFormRules;
import com.zhimu.slmp.health.util.HealthFormat;
import com.zhimu.slmp.health.util.SubmitSupport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * D07 新建 / 修改消毒计划（所有者、场长）：多选栏舍（本场启用中栏舍，待定事项 2 复用 API-L07）、周期天数、开始与结束日期、
 * 消毒剂（API-D35，选择后预填推荐浓度与库存提醒）、浓度（1:正整数）、消毒方式（字典 DISINFECT_METHOD ∩ 消毒剂适用方式）；
 * 汇总卡（API-D30 预估）。保存：新建 API-D31（Idempotency-Key），修改 API-D32。
 */
public class DisinfectPlanEditViewModel extends BaseViewModel {

    public static final String F_PENS = "pen_ids";
    public static final String F_CYCLE = "cycle_days";
    public static final String F_START = "start_date";
    public static final String F_END = "end_date";
    public static final String F_CATALOG = "catalog_id";
    public static final String F_CONCENTRATION = "concentration";
    public static final String F_METHOD = "method_code";
    public static final String READY_TEXT = "填写栏舍、周期和起止日期后显示将生成的任务数";

    private final DisinfectRepository repository;
    private final HealthSharedRepository shared;
    private final FarmContext farmContext;

    private final MutableLiveData<DisinfectPlanDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<List<PenOptionVO>> pens = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<DictItemVO>> allMethods = new MutableLiveData<>(Collections.emptyList());
    private final MediatorLiveData<List<DictItemVO>> methods = new MediatorLiveData<>();
    private final MutableLiveData<List<DisinfectantOptionVO>> disinfectants = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<DisinfectPlanPreviewVO> preview = new MutableLiveData<>();
    private final MutableLiveData<List<Long>> penIds = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> cycleDays = new MutableLiveData<>("");
    private final MutableLiveData<String> startDate = new MutableLiveData<>("");
    private final MutableLiveData<String> endDate = new MutableLiveData<>("");
    private final MutableLiveData<String> concentration = new MutableLiveData<>("");
    private final MutableLiveData<String> methodCode = new MutableLiveData<>();
    private final MutableLiveData<Long> catalogId = new MutableLiveData<>();
    private final MediatorLiveData<String> stockHint = new MediatorLiveData<>();
    private final MediatorLiveData<String> cycleHelper = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final SingleLiveEvent<DisinfectPlanVO> finishWithResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> scrollToField = new SingleLiveEvent<>();

    private final SubmitSupport.IdemKeeper idem = new SubmitSupport.IdemKeeper();
    @Nullable
    private Long planId;
    @Nullable
    private Disposable previewRequest;
    @Nullable
    private String initialSnapshot;

    @Inject
    public DisinfectPlanEditViewModel(@NonNull DisinfectRepository repository, @NonNull HealthSharedRepository shared,
                                      @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.shared = shared;
        this.farmContext = farmContext;
        methods.addSource(allMethods, v -> methods.setValue(computeMethods()));
        methods.addSource(catalogId, v -> methods.setValue(computeMethods()));
        stockHint.addSource(catalogId, v -> stockHint.setValue(computeStockHint()));
        stockHint.addSource(disinfectants, v -> stockHint.setValue(computeStockHint()));
        cycleHelper.addSource(cycleDays, v -> {
            Integer d = HealthFormRules.parseInt(v);
            cycleHelper.setValue(d == null || d < 1 ? null : "每 " + d + " 天一次");
        });
        canSubmit.addSource(submitting, v -> canSubmit.setValue(!Boolean.TRUE.equals(v)));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        idem.attach(handle);
        Object p = handle.get(RouterPath.EXTRA_PLAN_ID);
        planId = p instanceof Number ? ((Number) p).longValue() : null;
        editMode.setValue(planId != null);
        if (farmContext.isStaff()) {
            setUiState(UiState.noPermission(DisinfectPlanListViewModel.MSG_STAFF, ErrorCodes.ROLE_FORBIDDEN));
            return;
        }
        load();
    }

    // ===== 对外 =====

    public LiveData<DisinfectPlanDetailVO> getDetail() { return detail; }

    public LiveData<List<PenOptionVO>> getPens() { return pens; }

    public LiveData<List<DictItemVO>> getMethods() { return methods; }

    public LiveData<List<DisinfectantOptionVO>> getDisinfectants() { return disinfectants; }

    public LiveData<DisinfectPlanPreviewVO> getPreview() { return preview; }

    public MutableLiveData<List<Long>> getPenIds() { return penIds; }

    public MutableLiveData<String> getCycleDays() { return cycleDays; }

    public MutableLiveData<String> getStartDate() { return startDate; }

    public MutableLiveData<String> getEndDate() { return endDate; }

    public MutableLiveData<String> getConcentration() { return concentration; }

    public MutableLiveData<String> getMethodCode() { return methodCode; }

    public MutableLiveData<Long> getCatalogId() { return catalogId; }

    public LiveData<String> getStockHint() { return stockHint; }

    public LiveData<String> getCycleHelper() { return cycleHelper; }

    public LiveData<Boolean> getEditMode() { return editMode; }

    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }

    public LiveData<Boolean> getSubmitting() { return submitting; }

    public LiveData<Boolean> getCanSubmit() { return canSubmit; }

    public SingleLiveEvent<DisinfectPlanVO> getFinishWithResult() { return finishWithResult; }

    public SingleLiveEvent<String> getScrollToField() { return scrollToField; }

    // ===== 加载 =====

    public void load() {
        Single<List<PenOptionVO>> penList = shared.getActivePens();
        Single<List<DisinfectantOptionVO>> options = repository.getDisinfectants(null);
        Single<List<DictItemVO>> methodList = shared.getDisinfectMethods().onErrorReturnItem(Collections.emptyList());
        Single<DisinfectPlanDetailVO> plan = planId == null ? Single.just(new DisinfectPlanDetailVO())
                : repository.getPlan(planId);
        load(Single.zip(penList, options, methodList, plan, (a, b, c, d) -> new Object[]{a, b, c, d}), arr -> {
            @SuppressWarnings("unchecked")
            List<PenOptionVO> p = (List<PenOptionVO>) arr[0];
            @SuppressWarnings("unchecked")
            List<DisinfectantOptionVO> o = (List<DisinfectantOptionVO>) arr[1];
            @SuppressWarnings("unchecked")
            List<DictItemVO> m = (List<DictItemVO>) arr[2];
            pens.setValue(p);
            disinfectants.setValue(o);
            allMethods.setValue(m);
            if (planId != null) {
                applyDetail((DisinfectPlanDetailVO) arr[3]);
            }
            initialSnapshot = snapshot();
        });
    }

    @Override
    public void retry() {
        load();
    }

    private void applyDetail(@NonNull DisinfectPlanDetailVO d) {
        detail.setValue(d);
        penIds.setValue(new ArrayList<>(d.getPenIds()));
        cycleDays.setValue(String.valueOf(d.getCycleDays()));
        startDate.setValue(d.getStartDate());
        endDate.setValue(d.getEndDate());
        catalogId.setValue(d.getCatalogId());
        concentration.setValue(HealthFormat.nonNull(d.getConcentration()));
        methodCode.setValue(d.getMethodCode());
        requestPreview();
    }

    // ===== 输入 =====

    /** 栏舍 Chip 多选切换 */
    public void togglePen(long penId) {
        List<Long> current = new ArrayList<>(penIds.getValue() == null ? Collections.emptyList() : penIds.getValue());
        if (current.contains(penId)) {
            current.remove(Long.valueOf(penId));
        } else {
            current.add(penId);
        }
        penIds.setValue(current);
        clearError(F_PENS);
        requestPreview();
    }

    public boolean isPenSelected(long penId) {
        List<Long> ids = penIds.getValue();
        return ids != null && ids.contains(penId);
    }

    public void onStartDateSelected(@Nullable String date) {
        if (date != null) {
            startDate.setValue(date);
            clearError(F_START);
            requestPreview();
        }
    }

    public void onEndDateSelected(@Nullable String date) {
        if (date != null) {
            endDate.setValue(date);
            clearError(F_END);
            requestPreview();
        }
    }

    /** 选择消毒剂：预填推荐浓度，方式取消毒剂适用的方式，库存提醒 */
    public void selectDisinfectant(long id) {
        DisinfectantOptionVO o = disinfectant(id);
        if (o == null) {
            return;
        }
        DisinfectantOptionVO previous = disinfectant(catalogId.getValue());
        catalogId.setValue(id);
        clearError(F_CATALOG);
        String current = concentration.getValue();
        boolean untouched = current == null || current.trim().isEmpty()
                || (previous != null && Objects.equals(current, previous.getRecommendedConcentration()));
        if (untouched && o.getRecommendedConcentration() != null) {
            concentration.setValue(o.getRecommendedConcentration());
            clearError(F_CONCENTRATION);
        }
        if (methodCode.getValue() != null && !o.getMethodCodes().isEmpty()
                && !o.getMethodCodes().contains(methodCode.getValue())) {
            methodCode.setValue(null);
        }
    }

    public void onMethodSelected(@Nullable String code) {
        methodCode.setValue(code);
        clearError(F_METHOD);
    }

    @Nullable
    DisinfectantOptionVO disinfectant(@Nullable Long id) {
        List<DisinfectantOptionVO> list = disinfectants.getValue();
        if (id == null || list == null) {
            return null;
        }
        for (DisinfectantOptionVO o : list) {
            if (o.getCatalogId() == id) {
                return o;
            }
        }
        return null;
    }

    @NonNull
    private List<DictItemVO> computeMethods() {
        List<DictItemVO> all = allMethods.getValue() == null ? Collections.emptyList() : allMethods.getValue();
        DisinfectantOptionVO o = disinfectant(catalogId.getValue());
        if (o == null || o.getMethodCodes().isEmpty()) {
            return all;
        }
        List<DictItemVO> out = new ArrayList<>();
        for (DictItemVO m : all) {
            if (o.getMethodCodes().contains(m.getCode())) {
                out.add(m);
            }
        }
        return out;
    }

    @Nullable
    private String computeStockHint() {
        DisinfectantOptionVO o = disinfectant(catalogId.getValue());
        return o == null ? null : o.getStockHintText();
    }

    /** 库存提醒是否用 warning 色（临期、无库存） */
    public boolean isStockWarning() {
        DisinfectantOptionVO o = disinfectant(catalogId.getValue());
        return o != null && !"NORMAL".equals(o.getStockHintType());
    }

    // ===== 预估 =====

    /** 栏舍、周期、起止日期变化后（页面防抖 300 毫秒）调 API-D30；不全时不请求，显示 ready_text */
    public void requestPreview() {
        if (previewRequest != null) {
            previewRequest.dispose();
        }
        DisinfectPlanPreviewRequest req = new DisinfectPlanPreviewRequest();
        req.penIds = penIds.getValue();
        req.cycleDays = HealthFormRules.parseInt(cycleDays.getValue());
        req.startDate = HealthFormRules.trimToNull(startDate.getValue());
        req.endDate = HealthFormRules.trimToNull(endDate.getValue());
        req.planId = planId;
        if (req.penIds == null || req.penIds.isEmpty() || req.cycleDays == null || req.cycleDays < 1
                || req.startDate == null || req.endDate == null) {
            preview.setValue(null);
            return;
        }
        previewRequest = launch(repository.previewPlan(req), preview::setValue, e -> preview.setValue(null));
    }

    /** 汇总卡标题：“将生成 22 条消毒任务”；修改时“修改后将重新生成……” */
    @NonNull
    public String summaryTitle() {
        DisinfectPlanPreviewVO p = preview.getValue();
        if (p == null || !p.isReady()) {
            return p != null && p.getReadyText() != null ? p.getReadyText() : READY_TEXT;
        }
        if (Boolean.TRUE.equals(editMode.getValue()) && p.getRegenerateFrom() != null) {
            return "修改后将重新生成 " + HealthFormat.date(p.getRegenerateFrom()) + "以后未执行的任务";
        }
        return "将生成 " + (p.getTaskCount() == null ? 0 : p.getTaskCount()) + " 条消毒任务";
    }

    /** 汇总卡正文：remind_text（“10月20日至 12月31日共 11 次……”）；修改时注明已执行和已逾期的任务不变 */
    @Nullable
    public String summaryText() {
        DisinfectPlanPreviewVO p = preview.getValue();
        if (p == null || !p.isReady()) {
            return null;
        }
        if (Boolean.TRUE.equals(editMode.getValue()) && p.getRegenerateFrom() != null) {
            return "已执行和已逾期的任务不变。" + HealthFormat.nonNull(p.getRemindText());
        }
        if (p.getRemindText() != null) {
            return p.getRemindText();
        }
        return HealthFormat.date(startDate.getValue()) + "至 " + HealthFormat.date(endDate.getValue()) + "共 "
                + (p.getTimes() == null ? 0 : p.getTimes()) + " 次，每次每个栏舍 1 条。";
    }

    /** 栏舍选择说明：pen_text（“已选 2 个栏舍……任务由你和刘芳执行。”）；预估未返回时只写数量 */
    @Nullable
    public String penText() {
        DisinfectPlanPreviewVO p = preview.getValue();
        if (p != null && p.getPenText() != null) {
            return p.getPenText();
        }
        List<Long> ids = penIds.getValue();
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return "已选 " + ids.size() + " 个栏舍，系统为每个栏舍各生成一条任务。";
    }

    // ===== 校验与提交 =====

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        List<Long> ids = penIds.getValue();
        if (ids == null || ids.isEmpty()) {
            e.put(F_PENS, "请至少选择 1 个栏舍");
        }
        String cycle = HealthFormRules.cycleDays(cycleDays.getValue());
        if (cycle != null) {
            e.put(F_CYCLE, cycle);
        }
        boolean edit = Boolean.TRUE.equals(editMode.getValue());
        DisinfectPlanDetailVO d = detail.getValue();
        LocalDate originalStart = d == null ? null : DateFormatter.parseDate(d.getStartDate());
        boolean skipPast = edit && originalStart != null && originalStart.isBefore(DateFormatter.today())
                && Objects.equals(d.getStartDate(), startDate.getValue());
        String start = HealthFormRules.startDate(startDate.getValue(), skipPast);
        if (start != null) {
            e.put(F_START, start);
        }
        String end = HealthFormRules.endDate(startDate.getValue(), endDate.getValue());
        if (end != null) {
            e.put(F_END, end);
        }
        if (catalogId.getValue() == null) {
            e.put(F_CATALOG, "请选择消毒剂");
        }
        String c = HealthFormRules.concentration(concentration.getValue());
        if (c != null) {
            e.put(F_CONCENTRATION, c);
        }
        if (methodCode.getValue() == null) {
            e.put(F_METHOD, HealthFormRules.MSG_DISINFECT_METHOD);
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
        DisinfectPlanSaveRequest req = new DisinfectPlanSaveRequest();
        req.penIds = new ArrayList<>(Objects.requireNonNull(penIds.getValue()));
        req.cycleDays = Objects.requireNonNull(HealthFormRules.parseInt(cycleDays.getValue()));
        req.startDate = startDate.getValue();
        req.endDate = endDate.getValue();
        req.catalogId = Objects.requireNonNull(catalogId.getValue());
        req.concentration = ConcentrationUtil.normalize(concentration.getValue());
        req.methodCode = methodCode.getValue();
        submitting.setValue(true);
        if (planId != null) {
            DisinfectPlanDetailVO d = detail.getValue();
            req.dueTime = d == null ? null : d.getDueTime();
            req.version = d == null ? 0 : d.getVersion();
            launch(repository.updatePlan(planId, req), this::onSaved, this::onSubmitError);
        } else {
            launch(repository.createPlan(req, idem.keyFor(req)), this::onSaved, this::onSubmitError);
        }
    }

    private void onSaved(@NonNull DisinfectPlanVO vo) {
        submitting.setValue(false);
        idem.reset();
        finishWithResult.setValue(vo);
    }

    /** 保存成功的提示：服务端 toast（“已保存并启用，生成 22 条消毒任务”），修改为“已保存修改” */
    @NonNull
    public String successMessage(@NonNull DisinfectPlanVO vo) {
        if (vo.getToast() != null) {
            return vo.getToast();
        }
        if (Boolean.TRUE.equals(editMode.getValue())) {
            return "已保存修改";
        }
        return vo.getTaskCount() == null ? "已保存并启用" : "已保存并启用，生成 " + vo.getTaskCount() + " 条消毒任务";
    }

    private void onSubmitError(@NonNull ApiException e) {
        submitting.setValue(false);
        if (ErrorCodes.isNoPermission(e.getCode())) {
            showMessage(e.getMessage());
            return;
        }
        if (e.isNetwork()) {
            showMessage(e.keptMessage(SubmitSupport.MSG_NETWORK_KEEP));
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

    @NonNull
    private String snapshot() {
        return penIds.getValue() + "|" + cycleDays.getValue() + "|" + startDate.getValue() + "|" + endDate.getValue()
                + "|" + catalogId.getValue() + "|" + concentration.getValue() + "|" + methodCode.getValue();
    }

    public boolean isDirty() {
        return initialSnapshot != null && !initialSnapshot.equals(snapshot());
    }
}
