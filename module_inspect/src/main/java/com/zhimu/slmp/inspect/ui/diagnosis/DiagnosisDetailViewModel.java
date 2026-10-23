package com.zhimu.slmp.inspect.ui.diagnosis;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.inspect.InspectExtras;
import com.zhimu.slmp.inspect.ai.DirectDiagnosisInput;
import com.zhimu.slmp.inspect.ai.DirectDiagnosisRepository;
import com.zhimu.slmp.inspect.data.AbnormalReportRepository;
import com.zhimu.slmp.inspect.model.vo.DiagnosisDetailVO;
import com.zhimu.slmp.inspect.model.vo.DiagnosisResultVO;
import com.zhimu.slmp.inspect.model.vo.DiagnosisStateVO;
import com.zhimu.slmp.inspect.model.vo.HandleVO;
import com.zhimu.slmp.inspect.ui.common.Args;
import com.zhimu.slmp.inspect.ui.common.InspectFormat;
import com.zhimu.slmp.inspect.ui.common.NavTarget;
import com.zhimu.slmp.network.GsonFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * I06 诊断详情（模块07 文档 2.4.7）：API-I19；诊断中每 3 秒轮询 API-I20（最多 90 秒）后再取 API-I19；
 * 重新诊断 API-I21；处理结果的修改记录 API-U14（DIAGNOSIS_HANDLE）。AI 卡固定显示“仅供参考，需执业兽医确认”。
 * 测试直连：I05 发起的直连诊断（{@link DirectDiagnosisRepository}）完成后用其结果替换诊断与上报信息并标注“测试直连”；
 * 直连失败时走原有接口。参数：report_id、poll_interval_sec、poll_max_sec、ai_state。
 */
public class DiagnosisDetailViewModel extends BaseViewModel
        implements com.zhimu.slmp.inspect.ui.common.RecordLogHost {

    /** AI 卡状态 */
    public static final int AI_PLACEHOLDER = 0;
    public static final int AI_RESULT = 1;
    public static final int AI_UNAVAILABLE = 2;
    public static final int AI_FAILED = 3;

    public static final String MSG_STILL_UNAVAILABLE = "AI 诊断仍不可用，服务恢复后会自动重新诊断";
    public static final String MSG_DIRECT_FAILED = "AI 测试直连失败，已改用服务端诊断";
    static final String UNAVAILABLE_TEXT =
            "AI 诊断暂时不可用，上报已保存为待诊断。服务恢复后会自动重新诊断一次，你也可以手动重新诊断。";

    private final AbnormalReportRepository repository;
    private final DirectDiagnosisRepository direct;

    private final MutableLiveData<DiagnosisDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<DiagnosisResultVO> diagnosis = new MutableLiveData<>();
    private final MutableLiveData<Integer> aiCardState = new MutableLiveData<>(AI_PLACEHOLDER);
    private final MutableLiveData<String> emergencyNotice = new MutableLiveData<>();
    private final MutableLiveData<String> pushText = new MutableLiveData<>();
    private final MutableLiveData<String> failureText = new MutableLiveData<>();
    private final MutableLiveData<HandleVO> handle = new MutableLiveData<>();
    private final MutableLiveData<DiagnosisDetailVO.DiagnosisPermissions> permissions = new MutableLiveData<>();
    private final MutableLiveData<Boolean> polling = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> rediagnosing = new MutableLiveData<>(false);
    private final MutableLiveData<List<RecordLogVO>> recordLogs = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> sourceLabel = new MutableLiveData<>();
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showRecordLogs = new SingleLiveEvent<>();

    private long reportId;
    private int pollInterval = 3;
    private int pollMax = 90;
    /** I05 刚提交、尚未拿到结果 */
    private boolean freshSubmit;
    @Nullable
    private Disposable pollSub;
    @Nullable
    private Disposable directSub;
    /** 直连结果（重新加载 API-I19 后再次覆盖） */
    @Nullable
    private DirectDiagnosisRepository.Result directResult;

    @Inject
    public DiagnosisDetailViewModel(@NonNull AbnormalReportRepository repository,
                                    @NonNull DirectDiagnosisRepository direct) {
        this.repository = repository;
        this.direct = direct;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        Long id = Args.getLong(handle, RouterPath.EXTRA_REPORT_ID);
        reportId = id == null ? 0 : id;
        Integer interval = Args.getInt(handle, RouterPath.EXTRA_POLL_INTERVAL);
        Integer max = Args.getInt(handle, RouterPath.EXTRA_POLL_MAX);
        pollInterval = interval == null || interval <= 0 ? 3 : interval;
        pollMax = max == null || max <= 0 ? 90 : max;
        freshSubmit = DiagnosisDetailVO.AI_DIAGNOSING.equals(Args.getString(handle, RouterPath.EXTRA_AI_STATE))
                && !Boolean.TRUE.equals(handle.get("inspect_diag_done"));
        load();
    }

    // ===== 加载 =====

    public void load() {
        load(repository.getDiagnosis(reportId), this::onLoaded);
    }

    @Override
    public void retry() {
        load();
    }

    /** I07 返回后刷新 */
    public void onHandleResult() {
        launch(repository.getDiagnosis(reportId), this::onLoaded);
    }

    private void onLoaded(@NonNull DiagnosisDetailVO d) {
        if (direct.has(reportId)) {
            applyDirectOverlay(d);
            return;
        }
        if (directResult != null) {
            overlay(d, directResult);
        }
        apply(d);
        if (freshSubmit || d.isDiagnosing()) {
            freshSubmit = false;
            getSavedState().set("inspect_diag_done", true);
            startPolling(pollInterval, pollMax);
        }
    }

    /** 直连诊断中：先显示上报信息与“AI 正在诊断”，结果到达后替换 */
    private void applyDirectOverlay(@NonNull DiagnosisDetailVO base) {
        // 直连代替本次轮询；直连失败时再改回轮询服务端
        freshSubmit = false;
        getSavedState().set("inspect_diag_done", true);
        DirectDiagnosisInput input = direct.inputOf(reportId);
        if (input != null) {
            base.setReport(reportFromInput(base, input));
        }
        apply(base);
        aiCardState.setValue(AI_PLACEHOLDER);
        if (directSub != null) {
            directSub.dispose();
        }
        directSub = direct.observe(reportId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(r -> {
                    directResult = r;
                    direct.discard(reportId);
                    overlay(base, r);
                    apply(base);
                }, e -> {
                    direct.discard(reportId);
                    showMessage(MSG_DIRECT_FAILED);
                    freshSubmit = true;
                    launch(repository.getDiagnosis(reportId), this::onLoaded);
                });
        disposables.add(directSub);
    }

    private void overlay(@NonNull DiagnosisDetailVO d, @NonNull DirectDiagnosisRepository.Result r) {
        d.setReport(reportFromInput(d, r.input));
        String now = LocalDateTime.now(DateFormatter.ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        d.applyDirectDiagnosis(r.diagnosis, now);
    }

    /** 测试直连：上报信息改为 I05 实际填写的内容（Mock 的上报信息是固定的） */
    @NonNull
    private static DiagnosisDetailVO.DiagnosisReport reportFromInput(@NonNull DiagnosisDetailVO base,
                                                                   @NonNull DirectDiagnosisInput in) {
        JsonElement old = GsonFactory.get().toJsonTree(base.getReport());
        JsonObject o = old != null && old.isJsonObject() ? old.getAsJsonObject() : new JsonObject();
        o.addProperty("pen_id", in.penId);
        o.addProperty("pen_name", in.penName);
        o.addProperty("batch_id", in.batchId);
        o.addProperty("batch_no", in.batchNo);
        o.addProperty("sick_count", in.sickCount);
        o.addProperty("stock_snapshot", in.stockCount);
        o.addProperty("sick_rate", in.sickRate());
        o.addProperty("reported_at", in.reportedAt);
        o.addProperty("description", in.description);
        JsonArray symptoms = new JsonArray();
        for (String s : in.symptomNames) {
            symptoms.add(s);
        }
        o.add("symptom_names", symptoms);
        JsonArray media = new JsonArray();
        for (DirectDiagnosisInput.Media m : in.media) {
            JsonObject f = new JsonObject();
            f.addProperty("file_id", m.fileId);
            f.addProperty("url", m.localPath != null ? m.localPath : m.url);
            f.addProperty("thumb_url", m.thumbUrl != null ? m.thumbUrl : m.localPath != null ? m.localPath : m.url);
            f.addProperty("media_type", m.video ? "VIDEO" : "IMAGE");
            if (m.durationSec != null) {
                f.addProperty("duration_sec", m.durationSec);
            }
            f.addProperty("source_text", "异常上报 · " + in.penName);
            if (in.reporterName != null) {
                f.addProperty("shot_by", in.reporterName);
            }
            media.add(f);
        }
        o.add("media", media);
        o.addProperty("media_count", in.media.size());
        if (in.reporterName != null) {
            JsonObject reporter = o.has("reporter") && o.get("reporter").isJsonObject()
                    ? o.getAsJsonObject("reporter") : new JsonObject();
            reporter.addProperty("name", in.reporterName);
            o.add("reporter", reporter);
        }
        return GsonFactory.get().fromJson(o, DiagnosisDetailVO.DiagnosisReport.class);
    }

    private void apply(@NonNull DiagnosisDetailVO d) {
        detail.setValue(d);
        DiagnosisResultVO dx = d.getDiagnosis();
        diagnosis.setValue(dx);
        handle.setValue(d.getHandle());
        permissions.setValue(d.getPermissions());
        sourceLabel.setValue(dx == null ? null : dx.getSourceLabel());
        String ai = d.getAiState();
        if (DiagnosisDetailVO.AI_DONE.equals(ai) && dx != null) {
            aiCardState.setValue(AI_RESULT);
        } else if (DiagnosisDetailVO.AI_UNAVAILABLE.equals(ai)) {
            aiCardState.setValue(AI_UNAVAILABLE);
        } else if (DiagnosisDetailVO.AI_FAILED.equals(ai)) {
            aiCardState.setValue(AI_FAILED);
        } else {
            aiCardState.setValue(AI_PLACEHOLDER);
        }
        emergencyNotice.setValue(dx == null ? null : dx.getEmergencyNotice());
        pushText.setValue(dx != null && DiagnosisResultVO.URGENCY_URGENT.equals(dx.getUrgency())
                && !d.getNotifiedNames().isEmpty()
                ? "已立即推送给" + InspectFormat.join(d.getNotifiedNames(), "") + "，不受免打扰限制" : null);
        DiagnosisDetailVO.FailureInfo f = d.getLastFailure();
        if (DiagnosisDetailVO.AI_UNAVAILABLE.equals(ai)) {
            failureText.setValue(UNAVAILABLE_TEXT);
        } else if (DiagnosisDetailVO.AI_FAILED.equals(ai)) {
            failureText.setValue(d.getNoDiagnosisText() != null ? d.getNoDiagnosisText()
                    : f != null ? f.getReasonText() : "AI 暂未给出诊断");
        } else {
            failureText.setValue(null);
        }
    }

    // ===== 轮询 =====

    /** 诊断中：每 intervalSec 秒查 API-I20，结束或到 maxSec 后取 API-I19 */
    public void startPolling(int intervalSec, int maxSec) {
        if (pollSub != null && !pollSub.isDisposed()) {
            return;
        }
        polling.setValue(true);
        aiCardState.setValue(AI_PLACEHOLDER);
        long times = Math.max(1, maxSec / Math.max(1, intervalSec));
        pollSub = Observable.interval(intervalSec, intervalSec, TimeUnit.SECONDS, Schedulers.io())
                .take(times)
                .concatMapSingle(t -> repository.getDiagnosisState(reportId))
                .takeUntil(s -> !s.isDiagnosing())
                .lastElement()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onPollEnd, e -> onPollEnd(null), () -> onPollEnd(null));
        disposables.add(pollSub);
    }

    private void onPollEnd(@Nullable DiagnosisStateVO state) {
        polling.setValue(false);
        boolean wasRediagnosing = Boolean.TRUE.equals(rediagnosing.getValue());
        launch(repository.getDiagnosis(reportId), d -> {
            rediagnosing.setValue(false);
            if (directResult != null) {
                overlay(d, directResult);
            }
            apply(d);
            if (wasRediagnosing && !DiagnosisDetailVO.AI_DONE.equals(d.getAiState())) {
                showMessage(MSG_STILL_UNAVAILABLE);
            }
        }, e -> {
            rediagnosing.setValue(false);
            handleError(e);
        });
    }

    /** 重新诊断：按钮加载；成功后刷新为已诊断，仍失败时保持待诊断 */
    public void rediagnose() {
        if (Boolean.TRUE.equals(rediagnosing.getValue())) {
            return;
        }
        rediagnosing.setValue(true);
        launch(repository.rediagnose(reportId), s -> {
            if (s.isDiagnosing()) {
                startPolling(pollInterval, pollMax);
            } else {
                onPollEnd(s);
            }
        }, e -> {
            rediagnosing.setValue(false);
            handleError(e);
        });
    }

    // ===== 对外 =====

    public LiveData<DiagnosisDetailVO> getDetail() { return detail; }

    public LiveData<DiagnosisResultVO> getDiagnosis() { return diagnosis; }

    public LiveData<Integer> getAiCardState() { return aiCardState; }

    public LiveData<String> getEmergencyNotice() { return emergencyNotice; }

    public LiveData<String> getPushText() { return pushText; }

    public LiveData<String> getFailureText() { return failureText; }

    public LiveData<HandleVO> getHandle() { return handle; }

    public LiveData<DiagnosisDetailVO.DiagnosisPermissions> getPermissions() { return permissions; }

    public LiveData<Boolean> getPolling() { return polling; }

    public LiveData<Boolean> getRediagnosing() { return rediagnosing; }

    @NonNull
    @Override
    public LiveData<List<RecordLogVO>> getRecordLogs() { return recordLogs; }

    /** 测试直连的来源说明（“测试直连 · 通义千问 qwen3.8-max”），服务端结果为 null */
    public LiveData<String> getSourceLabel() { return sourceLabel; }

    public SingleLiveEvent<NavTarget> getNavEvent() { return navEvent; }

    public SingleLiveEvent<Void> getShowRecordLogs() { return showRecordLogs; }

    public long getReportId() { return reportId; }

    // ===== 跳转 =====

    /** “登记处理结果”“修改处理结果” → I07 */
    public void openHandle() {
        navEvent.setValue(NavTarget.of(RouterPath.I07).put(RouterPath.EXTRA_REPORT_ID, reportId).forResult());
    }

    /** 处理结果卡“已转为用药记录” → D05 该批次用药档案 */
    public void openMedication() {
        DiagnosisDetailVO d = detail.getValue();
        if (d != null && d.getReport() != null) {
            navEvent.setValue(NavTarget.of(RouterPath.D05).put(RouterPath.EXTRA_BATCH_ID, d.getReport().getBatchId()));
        }
    }

    /** 更多菜单“查看关联巡检” → I04 并定位到该日 */
    public void openInspection() {
        DiagnosisDetailVO d = detail.getValue();
        if (d == null || d.getReport() == null) {
            return;
        }
        DiagnosisDetailVO.DiagnosisReport r = d.getReport();
        String at = r.getInspectionRegisteredAt() != null ? r.getInspectionRegisteredAt() : r.getReportedAt();
        navEvent.setValue(NavTarget.of(RouterPath.I04).put(RouterPath.EXTRA_PEN_ID, r.getPenId())
                .put(RouterPath.EXTRA_LOCATE_DATE, at == null ? null : at.substring(0, Math.min(10, at.length()))));
    }

    /** 更多菜单“查看修改记录” */
    public void loadRecordLogs() {
        HandleVO h = handle.getValue();
        if (h == null) {
            return;
        }
        recordLogs.setValue(Collections.emptyList());
        showRecordLogs.call();
        launch(repository.getRecordLogs(h.getHandleId()),
                list -> recordLogs.setValue(list == null ? Collections.emptyList() : list),
                e -> showMessage(e.getMessage()));
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        if (detail.getValue() != null) {
            showMessage(e.getMessage());
        } else {
            super.onNoPermission(e);
        }
    }
}
