package com.zhimu.slmp.inspect.ui.handle;

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
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.inspect.InspectExtras;
import com.zhimu.slmp.inspect.data.AbnormalReportRepository;
import com.zhimu.slmp.inspect.model.request.HandleSaveRequest;
import com.zhimu.slmp.inspect.model.vo.DiagnosisDetailVO;
import com.zhimu.slmp.inspect.model.vo.DiagnosisResultVO;
import com.zhimu.slmp.inspect.model.vo.HandleSaveResultVO;
import com.zhimu.slmp.inspect.model.vo.HandleVO;
import com.zhimu.slmp.inspect.ui.common.Args;
import com.zhimu.slmp.inspect.ui.common.NavTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

/**
 * I07 登记 / 修改处理结果（所有者、场长，模块07 文档 2.4.8）：诊断人、处理方式、是否用药（“转为用药记录”先保存再进入 D04）、
 * 转归（治愈、死淘合计不超过发病数量，满额时先确认结案）、AI 诊断是否相符（有诊断结果时必选）；保存 API-I22。
 * 参数：report_id。
 */
public class HandleResultViewModel extends BaseViewModel {

    public static final String F_DIAGNOSTICIAN = "diagnostician";
    public static final String F_TREATMENT = "treatment";
    public static final String F_USED_DRUG = "used_drug";
    public static final String F_CURED = "cured_count";
    public static final String F_CULLED = "culled_count";
    public static final String F_AI_MATCH = "ai_match";

    private final AbnormalReportRepository repository;

    private final MutableLiveData<DiagnosisDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<String> titleText = new MutableLiveData<>("登记处理结果");
    private final MutableLiveData<String> summaryTitle = new MutableLiveData<>("");
    private final MutableLiveData<String> summarySubtitle = new MutableLiveData<>("");
    private final MutableLiveData<String> diagnostician = new MutableLiveData<>("");
    private final MutableLiveData<String> treatment = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> usedDrug = new MutableLiveData<>();
    private final MutableLiveData<String> curedCount = new MutableLiveData<>("0");
    private final MutableLiveData<String> culledCount = new MutableLiveData<>("0");
    private final MutableLiveData<String> totalText = new MutableLiveData<>("合计 0 / 0 头");
    private final MutableLiveData<Integer> progress = new MutableLiveData<>(0);
    private final MutableLiveData<String> outcomeHint = new MutableLiveData<>("");
    private final MutableLiveData<String> aiMatch = new MutableLiveData<>();
    private final MutableLiveData<Boolean> aiMatchVisible = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> converted = new MutableLiveData<>(false);
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final SingleLiveEvent<Integer> showCloseConfirm = new SingleLiveEvent<>();
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<UiMessage> finishEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showDiscard = new SingleLiveEvent<>();

    private long reportId;
    @Nullable
    private String initialSnapshot;
    private boolean finished;

    @Inject
    public HandleResultViewModel(@NonNull AbnormalReportRepository repository) {
        this.repository = repository;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = Args.getLong(handle, InspectExtras.EXTRA_REPORT_ID);
        reportId = id == null ? 0 : id;
        load(repository.getDiagnosis(reportId), this::apply);
    }

    @Override
    public void retry() {
        load(repository.getDiagnosis(reportId), this::apply);
    }

    private void apply(@NonNull DiagnosisDetailVO d) {
        detail.setValue(d);
        DiagnosisDetailVO.DiagnosisReport r = d.getReport();
        int sick = r == null ? 0 : r.getSickCount();
        summaryTitle.setValue((r == null ? "" : r.getPenName()) + " · 发病 " + sick + " 头");
        DiagnosisResultVO dx = d.getDiagnosis();
        String top = dx == null ? null : dx.topCauseName();
        summarySubtitle.setValue((r == null ? "" : r.getBatchNo()) + (top == null ? "" : " · AI：" + top));
        aiMatchVisible.setValue(dx != null);
        HandleVO h = d.getHandle();
        if (h != null && initialSnapshot == null) {
            titleText.setValue("修改处理结果");
            diagnostician.setValue(h.getDiagnostician());
            treatment.setValue(h.getTreatment());
            usedDrug.setValue(h.isUsedDrug());
            curedCount.setValue(String.valueOf(h.getCuredCount()));
            culledCount.setValue(String.valueOf(h.getCulledCount()));
            aiMatch.setValue(HandleVO.NONE.equals(h.getAiMatch()) ? null : h.getAiMatch());
            converted.setValue(h.isMedicationConverted());
        }
        outcomeHint.setValue("合计达到 " + sick + " 头后自动结案");
        recalc();
        if (initialSnapshot == null) {
            initialSnapshot = snapshot();
        }
    }

    // ===== 对外 =====

    public LiveData<DiagnosisDetailVO> getDetail() { return detail; }

    public LiveData<String> getTitleText() { return titleText; }

    /** “3 号育肥舍 · 发病 12 头” */
    public LiveData<String> getSummaryTitle() { return summaryTitle; }

    /** “ZM2610-031 · AI：猪流行性腹泻” */
    public LiveData<String> getSummarySubtitle() { return summarySubtitle; }

    public MutableLiveData<String> getDiagnostician() { return diagnostician; }

    public MutableLiveData<String> getTreatment() { return treatment; }

    public LiveData<Boolean> getUsedDrug() { return usedDrug; }

    public MutableLiveData<String> getCuredCount() { return curedCount; }

    public MutableLiveData<String> getCulledCount() { return culledCount; }

    /** “合计 10 / 12 头” */
    public LiveData<String> getTotalText() { return totalText; }

    /** 合计进度 0–1000 */
    public LiveData<Integer> getProgress() { return progress; }

    /** “合计达到 12 头后自动结案” */
    public LiveData<String> getOutcomeHint() { return outcomeHint; }

    public LiveData<String> getAiMatch() { return aiMatch; }

    public LiveData<Boolean> getAiMatchVisible() { return aiMatchVisible; }

    public LiveData<Boolean> getConverted() { return converted; }

    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }

    public LiveData<Boolean> getSubmitting() { return submitting; }

    public SingleLiveEvent<Integer> getShowCloseConfirm() { return showCloseConfirm; }

    public SingleLiveEvent<NavTarget> getNavEvent() { return navEvent; }

    public SingleLiveEvent<UiMessage> getFinishEvent() { return finishEvent; }

    public SingleLiveEvent<Void> getShowDiscard() { return showDiscard; }

    // ===== 输入 =====

    public void setUsedDrug(boolean used) {
        usedDrug.setValue(used);
        clearFieldError(F_USED_DRUG);
    }

    public void setAiMatch(@Nullable String value) {
        aiMatch.setValue(value);
        clearFieldError(F_AI_MATCH);
    }

    /** 治愈、死淘变化：合计与进度条实时更新；超过发病数量时两框同时报错 */
    public void recalc() {
        int sick = sickCount();
        int cured = safeInt(curedCount.getValue());
        int culled = safeInt(culledCount.getValue());
        int total = cured + culled;
        totalText.setValue("合计 " + total + " / " + sick + " 头");
        progress.setValue(sick <= 0 ? 0 : (int) Math.min(1000, total * 1000L / sick));
        if (total > sick) {
            String msg = "治愈和死淘合计不能超过发病数量 " + sick + " 头";
            Map<String, String> m = new LinkedHashMap<>(fieldErrors.getValue() == null ? Collections.emptyMap()
                    : fieldErrors.getValue());
            m.put(F_CURED, msg);
            m.put(F_CULLED, msg);
            fieldErrors.setValue(m);
        } else {
            clearFieldError(F_CURED);
            clearFieldError(F_CULLED);
        }
    }

    private int sickCount() {
        DiagnosisDetailVO d = detail.getValue();
        return d == null || d.getReport() == null ? 0 : d.getReport().getSickCount();
    }

    // ===== 校验与保存 =====

    @NonNull
    Map<String, String> validate(boolean toMedication) {
        Map<String, String> e = new LinkedHashMap<>();
        String who = trim(diagnostician.getValue());
        if (who.isEmpty()) {
            e.put(F_DIAGNOSTICIAN, "请填写诊断人");
        } else if (who.codePointCount(0, who.length()) > 20) {
            e.put(F_DIAGNOSTICIAN, "诊断人不能超过 20 个字");
        }
        String how = trim(treatment.getValue());
        if (how.isEmpty()) {
            e.put(F_TREATMENT, "请填写处理方式");
        } else if (how.codePointCount(0, how.length()) > 200) {
            e.put(F_TREATMENT, "处理方式不能超过 200 字");
        }
        if (usedDrug.getValue() == null) {
            e.put(F_USED_DRUG, "请选择是否使用药品");
        } else if (toMedication && !usedDrug.getValue()) {
            e.put(F_USED_DRUG, "转为用药记录前请选择“使用了药品”");
        }
        Integer cured = parse(curedCount.getValue());
        Integer culled = parse(culledCount.getValue());
        int sick = sickCount();
        if (cured == null) {
            e.put(F_CURED, "请填写治愈头数");
        }
        if (culled == null) {
            e.put(F_CULLED, "请填写死淘头数");
        }
        if (cured != null && culled != null && cured + culled > sick) {
            String msg = "治愈和死淘合计不能超过发病数量 " + sick + " 头";
            e.put(F_CURED, msg);
            e.put(F_CULLED, msg);
        }
        if (Boolean.TRUE.equals(aiMatchVisible.getValue()) && aiMatch.getValue() == null) {
            e.put(F_AI_MATCH, "请选择 AI 诊断是否相符");
        }
        return e;
    }

    /** 点“保存”：合计等于发病数量时先确认结案 */
    public void save() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> e = validate(false);
        fieldErrors.setValue(e);
        if (!e.isEmpty()) {
            return;
        }
        if (total() == sickCount() && sickCount() > 0) {
            showCloseConfirm.setValue(sickCount());
            return;
        }
        doSave(false, false);
    }

    /** 结案确认对话框选“保存并结案” */
    public void confirmClose() {
        doSave(true, false);
    }

    /** “转为用药记录”：先校验并保存当前处理结果，再进入 D04（带入栏舍、批次、用药原因） */
    public void convertToMedication() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> e = validate(true);
        fieldErrors.setValue(e);
        if (!e.isEmpty()) {
            return;
        }
        doSave(total() == sickCount() && sickCount() > 0, true);
    }

    private void doSave(boolean confirmClose, boolean toMedication) {
        DiagnosisDetailVO d = detail.getValue();
        if (d == null) {
            return;
        }
        HandleSaveRequest req = new HandleSaveRequest();
        req.diagnostician = trim(diagnostician.getValue());
        req.treatment = trim(treatment.getValue());
        req.usedDrug = usedDrug.getValue();
        req.curedCount = safeInt(curedCount.getValue());
        req.culledCount = safeInt(culledCount.getValue());
        req.aiMatch = d.getDiagnosis() == null ? null : aiMatch.getValue();
        req.confirmClose = confirmClose ? Boolean.TRUE : null;
        req.toMedication = toMedication ? Boolean.TRUE : null;
        HandleVO h = d.getHandle();
        req.version = h == null ? null : h.getVersion();
        submitting.setValue(true);
        launch(repository.saveHandle(reportId, req), r -> onSaved(r, toMedication, d), this::onSaveError);
    }

    private void onSaved(@NonNull HandleSaveResultVO r, boolean toMedication, @NonNull DiagnosisDetailVO d) {
        submitting.setValue(false);
        finished = true;
        if (toMedication) {
            DiagnosisDetailVO.MedicationPrefill p = r.getMedicationPrefill() != null ? r.getMedicationPrefill()
                    : d.getMedicationPrefill();
            NavTarget t = NavTarget.of(RouterPath.D04).finishCurrent();
            if (p != null) {
                t.put(RouterPath.EXTRA_PEN_ID, p.getPenId()).put(RouterPath.EXTRA_BATCH_ID, p.getBatchId())
                        .put(InspectExtras.EXTRA_REPORT_ID, p.getReportId())
                        .put(InspectExtras.EXTRA_DIAGNOSIS_ID, p.getDiagnosisId())
                        .put(InspectExtras.EXTRA_MEDICATION_REASON, p.getReason());
            }
            navEvent.setValue(t);
            return;
        }
        if (r.getDeathPrompt() != null && r.getCulledCount() > 0) {
            finishEvent.setValue(UiMessage.withPage(r.isClosed() ? "已保存处理结果，记录已结案。死淘需另行登记"
                    : "已保存处理结果。死淘需另行登记", "去登记", RouterPath.L11));
            return;
        }
        finishEvent.setValue(UiMessage.of(r.isClosed() ? "已保存处理结果，记录已结案" : "已保存处理结果"));
    }

    private void onSaveError(@NonNull ApiException e) {
        submitting.setValue(false);
        if (e.getCode() == ErrorCodes.STATE_CHANGED) {
            finished = true;
            finishEvent.setValue(UiMessage.of(e.getMessage() != null ? e.getMessage() : "这条记录已结案，不能再修改"));
            return;
        }
        if (e.getCode() == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()
                && e.getFieldErrors().containsKey("confirm_close")) {
            showCloseConfirm.setValue(sickCount());
            return;
        }
        if (ErrorCodes.isNoPermission(e.getCode())) {
            showMessage(e.getMessage());
            return;
        }
        handleError(e);
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        return true;
    }

    private int total() {
        return safeInt(curedCount.getValue()) + safeInt(culledCount.getValue());
    }

    public void clearFieldError(@NonNull String field) {
        Map<String, String> m = fieldErrors.getValue();
        if (m != null && m.containsKey(field)) {
            Map<String, String> copy = new LinkedHashMap<>(m);
            copy.remove(field);
            fieldErrors.setValue(copy);
        }
    }

    @NonNull
    private static String trim(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    @Nullable
    static Integer parse(@Nullable String s) {
        String t = trim(s);
        return t.matches("^\\d{1,6}$") ? Integer.parseInt(t) : null;
    }

    private static int safeInt(@Nullable String s) {
        Integer v = parse(s);
        return v == null ? 0 : v;
    }

    // ===== 返回 =====

    @NonNull
    private String snapshot() {
        return diagnostician.getValue() + "|" + treatment.getValue() + "|" + usedDrug.getValue() + "|"
                + curedCount.getValue() + "|" + culledCount.getValue() + "|" + aiMatch.getValue();
    }

    /** 有改动时返回：“放弃填写的处理结果？” */
    public boolean onBackPressed() {
        if (!finished && initialSnapshot != null && !Objects.equals(initialSnapshot, snapshot())) {
            showDiscard.call();
            return true;
        }
        return false;
    }
}
