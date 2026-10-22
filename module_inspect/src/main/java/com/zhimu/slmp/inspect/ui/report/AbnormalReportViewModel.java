package com.zhimu.slmp.inspect.ui.report;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.inspect.InspectExtras;
import com.zhimu.slmp.inspect.ai.DirectDiagnosisInput;
import com.zhimu.slmp.inspect.ai.DirectDiagnosisRepository;
import com.zhimu.slmp.inspect.data.AbnormalReportRepository;
import com.zhimu.slmp.inspect.model.request.AbnormalReportCreateRequest;
import com.zhimu.slmp.inspect.model.vo.ReportCreateResultVO;
import com.zhimu.slmp.inspect.model.vo.ReportFormVO;
import com.zhimu.slmp.inspect.model.vo.SymptomVO;
import com.zhimu.slmp.inspect.ui.common.Args;
import com.zhimu.slmp.inspect.ui.common.ClientRequestKeeper;
import com.zhimu.slmp.inspect.ui.common.NavTarget;
import com.zhimu.slmp.media.data.MediaScene;
import com.zhimu.slmp.media.upload.MediaQueueController;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.media.util.MediaFiles;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

/**
 * I05 异常上报（模块07 文档 2.4.6）：表单上下文 API-I14（转上报带入栏舍、批次、巡检照片与手动标记原因）、
 * 按批次取症状 API-I15、提交 API-I16（client_request_id 兼作幂等键，超时查 API-I26）；照片和视频上传中时按钮保持加载，
 * 上传完成后自动提交；成功后打开 I06（替换本页）。测试直连开启时提交成功即开始 AI 诊断（{@link DirectDiagnosisRepository}）。
 * 参数：inspection_id（转上报）、pen_id（直接上报可预选）。
 */
public class AbnormalReportViewModel extends BaseViewModel {

    public static final String F_PEN = "pen_id";
    public static final String F_BATCH = "batch_id";
    public static final String F_SICK = "sick_count";
    public static final String F_DESCRIPTION = "description";
    public static final int MIN_DESCRIPTION = 10;
    public static final int MAX_DESCRIPTION = 500;
    public static final String MSG_SUBMITTED = "已提交上报，正在诊断";

    private final AbnormalReportRepository repository;
    private final DirectDiagnosisRepository direct;
    private final FarmContext farmContext;
    private final SessionManager session;
    private final MediaQueueController.Factory mediaFactory;
    @Nullable
    private MediaQueueController media;
    @Nullable
    private ClientRequestKeeper keeper;
    @Nullable
    private Observer<List<UploadItem>> mediaObserver;

    private final MutableLiveData<ReportFormVO> formCtx = new MutableLiveData<>();
    private final MutableLiveData<List<ReportFormVO.ReportPen>> pens = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> penId = new MutableLiveData<>();
    private final MutableLiveData<Long> batchId = new MutableLiveData<>();
    private final MutableLiveData<List<ReportFormVO.ReportBatch>> batches = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> maxSick = new MutableLiveData<>();
    private final MutableLiveData<String> sickHelper = new MutableLiveData<>("选择批次后显示存栏上限");
    private final MutableLiveData<String> sickCount = new MutableLiveData<>("");
    private final MutableLiveData<String> description = new MutableLiveData<>("");
    private final MutableLiveData<List<SymptomVO>> symptoms = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Set<Long>> symptomIds = new MutableLiveData<>(Collections.emptySet());
    private final MutableLiveData<String> symptomCountText = new MutableLiveData<>("可多选 · 已选 0 项");
    private final MutableLiveData<String> mediaCountText = new MutableLiveData<>("0 / 6");
    private final MutableLiveData<String> mediaHint = new MutableLiveData<>("最多 6 个，其中视频 1 段，不超过 30 秒");
    private final MutableLiveData<String> sourceText = new MutableLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> focusSick = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showDiscard = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> scrollTo = new SingleLiveEvent<>();

    @Nullable
    private Long inspectionId;
    @Nullable
    private Long argPenId;
    private boolean waitingUpload;
    private boolean finished;
    @Nullable
    private String initialSnapshot;

    @Inject
    public AbnormalReportViewModel(@NonNull AbnormalReportRepository repository,
                                   @NonNull DirectDiagnosisRepository direct, @NonNull FarmContext farmContext,
                                   @NonNull SessionManager session, @NonNull MediaQueueController.Factory mediaFactory) {
        this.repository = repository;
        this.direct = direct;
        this.farmContext = farmContext;
        this.session = session;
        this.mediaFactory = mediaFactory;
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        media = mediaFactory.create(handle, "report_media", MediaScene.ABNORMAL, InspectExtras.PAGE_I05,
                farmContext.farmIdOrZero(), 6, 1);
        mediaObserver = list -> onMediaChanged(list == null ? Collections.emptyList() : list);
        media.getItems().observeForever(mediaObserver);
        keeper = new ClientRequestKeeper(handle, "report_client_request_id");
        inspectionId = Args.getLong(handle, InspectExtras.EXTRA_INSPECTION_ID);
        argPenId = Args.getLong(handle, RouterPath.EXTRA_PEN_ID);
        init();
    }

    private void init() {
        load(repository.getReportForm(inspectionId, inspectionId == null ? argPenId : null), this::applyForm);
    }

    @Override
    public void retry() {
        init();
    }

    private void applyForm(@NonNull ReportFormVO ctx) {
        formCtx.setValue(ctx);
        pens.setValue(ctx.getPens());
        sourceText.setValue(ReportFormVO.SOURCE_INSPECTION.equals(ctx.getSource()) ? ctx.getSourceText() : null);
        ReportFormVO.ReportPrefill prefill = ctx.getPrefill();
        Long pen = prefill != null && prefill.getPenId() != null ? prefill.getPenId() : argPenId;
        if (penId.getValue() == null && pen != null && ctx.findPen(pen) != null) {
            selectPen(pen);
            Long batch = prefill == null ? null : prefill.getBatchId();
            if (batch != null && batchId.getValue() == null) {
                selectBatch(batch);
            }
        } else if (penId.getValue() != null) {
            selectPen(penId.getValue());
        }
        if (prefill != null && prefill.getDescription() != null
                && (description.getValue() == null || description.getValue().isEmpty())) {
            description.setValue(prefill.getDescription());
        }
        if (symptoms.getValue() == null || symptoms.getValue().isEmpty()) {
            symptoms.setValue(ctx.getSymptoms());
        }
        if (media != null) {
            media.setInitialFiles(prefill == null ? null : prefill.getMedia());
        }
        if (inspectionId != null) {
            focusSick.call();
        }
        updateSymptomCount();
        if (initialSnapshot == null) {
            initialSnapshot = snapshot();
        }
    }

    // ===== 对外 =====

    public LiveData<ReportFormVO> getFormCtx() { return formCtx; }

    public LiveData<List<ReportFormVO.ReportPen>> getPens() { return pens; }

    public LiveData<Long> getPenId() { return penId; }

    public LiveData<Long> getBatchId() { return batchId; }

    public LiveData<List<ReportFormVO.ReportBatch>> getBatches() { return batches; }

    public LiveData<Integer> getMaxSick() { return maxSick; }

    /** “不能超过批次当前存栏 200 头”“选择批次后显示存栏上限” */
    public LiveData<String> getSickHelper() { return sickHelper; }

    public MutableLiveData<String> getSickCount() { return sickCount; }

    public MutableLiveData<String> getDescription() { return description; }

    public LiveData<List<SymptomVO>> getSymptoms() { return symptoms; }

    public LiveData<Set<Long>> getSymptomIds() { return symptomIds; }

    /** “可多选 · 已选 3 项” */
    public LiveData<String> getSymptomCountText() { return symptomCountText; }

    public LiveData<String> getMediaCountText() { return mediaCountText; }

    /** “最多 6 个，其中视频 1 段，不超过 30 秒 · 前 3 个来自巡检” */
    public LiveData<String> getMediaHint() { return mediaHint; }

    /** “已从今天 09:12 的巡检带入栏舍、批次和照片”（直接上报为 null） */
    public LiveData<String> getSourceText() { return sourceText; }

    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }

    public LiveData<Boolean> getSubmitting() { return submitting; }

    public SingleLiveEvent<NavTarget> getNavEvent() { return navEvent; }

    public SingleLiveEvent<Void> getFocusSick() { return focusSick; }

    public SingleLiveEvent<Void> getShowDiscard() { return showDiscard; }

    public SingleLiveEvent<String> getScrollTo() { return scrollTo; }

    @Nullable
    public MediaQueueController getMedia() { return media; }

    // ===== 栏舍、批次、症状 =====

    /** 改选栏舍后清空批次；只有一个在栏批次时自动选中 */
    public void selectPen(long id) {
        ReportFormVO ctx = formCtx.getValue();
        ReportFormVO.ReportPen pen = ctx == null ? null : ctx.findPen(id);
        boolean changed = !Objects.equals(penId.getValue(), id);
        penId.setValue(id);
        clearFieldError(F_PEN);
        List<ReportFormVO.ReportBatch> list = pen == null ? Collections.emptyList() : pen.getBatches();
        batches.setValue(list);
        if (changed) {
            batchId.setValue(null);
            maxSick.setValue(null);
            sickHelper.setValue("选择批次后显示存栏上限");
        }
        if (list.isEmpty()) {
            putFieldError(F_BATCH, "这个栏舍没有在栏批次");
        } else {
            clearFieldError(F_BATCH);
            if (list.size() == 1) {
                selectBatch(list.get(0).getBatchId());
            }
        }
    }

    /** 选定批次：发病数量上限为该批次存栏；重新取症状 */
    public void selectBatch(long id) {
        List<ReportFormVO.ReportBatch> list = batches.getValue();
        ReportFormVO.ReportBatch batch = null;
        if (list != null) {
            for (ReportFormVO.ReportBatch b : list) {
                if (b.getBatchId() == id) {
                    batch = b;
                }
            }
        }
        if (batch == null) {
            return;
        }
        boolean changed = !Objects.equals(batchId.getValue(), id);
        batchId.setValue(id);
        clearFieldError(F_BATCH);
        maxSick.setValue(batch.getStockCount());
        sickHelper.setValue("不能超过批次当前存栏 " + batch.getStockCount() + " 头");
        validateSick();
        if (changed) {
            launch(repository.getSymptoms(id), s -> {
                symptoms.setValue(s == null ? Collections.emptyList() : s);
                // 换品类后不存在的症状去掉
                Set<Long> keep = new LinkedHashSet<>();
                Set<Long> cur = symptomIds.getValue() == null ? Collections.emptySet() : symptomIds.getValue();
                if (s != null) {
                    for (SymptomVO v : s) {
                        if (cur.contains(v.getSymptomId())) {
                            keep.add(v.getSymptomId());
                        }
                    }
                }
                symptomIds.setValue(keep);
                updateSymptomCount();
            }, e -> {
            });
        }
    }

    public void toggleSymptom(long id) {
        Set<Long> copy = new LinkedHashSet<>(symptomIds.getValue() == null ? Collections.emptySet()
                : symptomIds.getValue());
        if (!copy.remove(id)) {
            copy.add(id);
        }
        symptomIds.setValue(copy);
        clearFieldError(F_DESCRIPTION);
        updateSymptomCount();
    }

    private void updateSymptomCount() {
        int n = symptomIds.getValue() == null ? 0 : symptomIds.getValue().size();
        symptomCountText.setValue("可多选 · 已选 " + n + " 项");
    }

    /** 发病数量实时与存栏上限比较 */
    public void validateSick() {
        Integer max = maxSick.getValue();
        Integer v = parseInt(sickCount.getValue());
        if (max != null && v != null && v > max) {
            putFieldError(F_SICK, "不能超过批次当前存栏 " + max + " 头", false);
        } else {
            clearFieldError(F_SICK);
        }
    }

    // ===== 照片和视频 =====

    private void onMediaChanged(@NonNull List<UploadItem> list) {
        mediaCountText.setValue(String.format(Locale.ROOT, "%d / %d", list.size(), 6));
        int remote = media == null ? 0 : media.getRemoteCount();
        mediaHint.setValue("最多 6 个，其中视频 1 段，不超过 30 秒" + (remote > 0 ? " · 前 " + remote + " 个来自巡检" : ""));
        if (waitingUpload && media != null && !media.hasUploading()) {
            waitingUpload = false;
            submitting.setValue(false);
            submit();
        }
    }

    // ===== 提交 =====

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        if (penId.getValue() == null) {
            e.put(F_PEN, "请选择栏舍");
        }
        if (batchId.getValue() == null) {
            e.put(F_BATCH, batches.getValue() != null && batches.getValue().isEmpty() && penId.getValue() != null
                    ? "这个栏舍没有在栏批次" : "请选择批次");
        }
        String s = sickCount.getValue() == null ? "" : sickCount.getValue().trim();
        Integer v = parseInt(s);
        Integer max = maxSick.getValue();
        if (s.isEmpty()) {
            e.put(F_SICK, "请填写发病数量");
        } else if (v == null || v < 1) {
            e.put(F_SICK, "发病数量至少为 1 头");
        } else if (max != null && v > max) {
            e.put(F_SICK, "不能超过批次当前存栏 " + max + " 头");
        }
        String d = description.getValue() == null ? "" : description.getValue().trim();
        boolean noSymptom = symptomIds.getValue() == null || symptomIds.getValue().isEmpty();
        if (d.codePointCount(0, d.length()) > MAX_DESCRIPTION) {
            e.put(F_DESCRIPTION, "描述不能超过 500 字");
        } else if (noSymptom && d.codePointCount(0, d.length()) < MIN_DESCRIPTION) {
            e.put(F_DESCRIPTION, "请至少勾选一项症状，或描述不少于 10 个字");
        }
        return e;
    }

    /** 点“提交并诊断”：依次校验；照片视频上传中时按钮保持加载，完成后自动提交 */
    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue()) || formCtx.getValue() == null) {
            return;
        }
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            scrollTo.setValue(errors.keySet().iterator().next());
            return;
        }
        MediaQueueController grid = media;
        if (grid != null && grid.hasUploading()) {
            submitting.setValue(true);
            waitingUpload = true;
            return;
        }
        if (grid != null && grid.hasFailed()) {
            showMessage("有 " + grid.getFailedCount() + " 个照片上传失败，请重试或删除");
            return;
        }
        AbnormalReportCreateRequest req = new AbnormalReportCreateRequest();
        req.penId = Objects.requireNonNull(penId.getValue());
        req.batchId = Objects.requireNonNull(batchId.getValue());
        req.inspectionId = inspectionId;
        req.sickCount = Objects.requireNonNull(parseInt(sickCount.getValue()));
        req.symptomIds = symptomIds.getValue() == null || symptomIds.getValue().isEmpty() ? null
                : new ArrayList<>(symptomIds.getValue());
        String d = description.getValue() == null ? "" : description.getValue().trim();
        req.description = d.isEmpty() ? null : d;
        req.mediaFileIds = grid == null ? Collections.emptyList() : grid.getFileIds();
        req.clientRequestId = keeper == null ? null : keeper.idFor(req);
        submitting.setValue(true);
        DirectDiagnosisInput input = buildDirectInput(req);
        launch(repository.createReport(req), r -> onCreated(r, input), e -> onSubmitError(e, input));
    }

    private void onCreated(@NonNull ReportCreateResultVO r, @NonNull DirectDiagnosisInput input) {
        submitting.setValue(false);
        finished = true;
        if (media != null) {
            media.clearAfterSubmit();
        }
        if (keeper != null) {
            keeper.reset();
        }
        if (direct.isEnabled()) {
            // 测试直连：用上报的照片、视频关键帧、症状和批次信息直接请求模型（I06 显示“测试直连”）
            direct.start(r.getReportId(), input);
        }
        GlobalEvents.postMessage(UiMessage.of(MSG_SUBMITTED));
        navEvent.setValue(NavTarget.of(RouterPath.I06)
                .put(InspectExtras.EXTRA_REPORT_ID, r.getReportId())
                .put(InspectExtras.EXTRA_POLL_INTERVAL, r.getPollIntervalSec())
                .put(InspectExtras.EXTRA_POLL_MAX, r.getPollMaxSec())
                .put(InspectExtras.EXTRA_AI_STATE, r.getAiState())
                .finishCurrent());
    }

    private void onSubmitError(@NonNull ApiException e, @NonNull DirectDiagnosisInput input) {
        if (isTimeout(e) && keeper != null && keeper.current() != null) {
            launch(repository.findReportByRequest(keeper.current()), r -> onCreated(r, input), err -> {
                submitting.setValue(false);
                showMessage("提交没有完成，请重试");
            });
            return;
        }
        submitting.setValue(false);
        if (e.getCode() == ErrorCodes.STATE_CHANGED) {
            // 这条巡检已上报：带 report_id 时直接打开诊断
            Long reportId = Args.toLong(e.dataString("report_id"));
            showMessage(e.getMessage());
            if (reportId != null) {
                finished = true;
                navEvent.setValue(NavTarget.of(RouterPath.I06).put(InspectExtras.EXTRA_REPORT_ID, reportId)
                        .finishCurrent());
            }
            return;
        }
        if (e.isNetwork()) {
            showMessage(UiMessage.withAction("网络连接失败，已保留填写的内容", "重试", "inspect_report_retry"));
            return;
        }
        handleError(e);
    }

    /** 测试直连的诊断输入：本地成品文件优先，其次服务端地址 */
    @NonNull
    DirectDiagnosisInput buildDirectInput(@NonNull AbnormalReportCreateRequest req) {
        DirectDiagnosisInput in = new DirectDiagnosisInput();
        ReportFormVO ctx = formCtx.getValue();
        ReportFormVO.ReportPen pen = ctx == null ? null : ctx.findPen(req.penId);
        ReportFormVO.ReportBatch batch = pen == null ? null : pen.findBatch(req.batchId);
        in.penId = req.penId;
        in.penName = pen == null ? "" : pen.getPenName();
        in.batchId = req.batchId;
        in.batchNo = batch == null ? "" : batch.getBatchNo();
        in.breedTitle = batch == null ? null : batch.getBreedTitle();
        in.dayAge = batch == null ? null : batch.getDayAge();
        in.stockCount = batch == null ? 0 : batch.getStockCount();
        in.sickCount = req.sickCount;
        in.description = req.description;
        List<String> names = new ArrayList<>();
        for (SymptomVO s : symptoms.getValue() == null ? Collections.<SymptomVO>emptyList() : symptoms.getValue()) {
            if (req.symptomIds != null && req.symptomIds.contains(s.getSymptomId())) {
                names.add(s.getName());
            }
        }
        in.symptomNames = names;
        in.source = ctx == null ? ReportFormVO.SOURCE_DIRECT : ctx.getSource();
        in.inspectionId = req.inspectionId;
        ReportFormVO.ReportPrefill prefill = ctx == null ? null : ctx.getPrefill();
        in.inspectionSummary = prefill == null ? null : prefill.getDescription();
        in.reportedAt = LocalDateTime.now(DateFormatter.ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        AccountUserVO user = session.currentUser();
        in.reporterName = user == null ? null : user.getName();
        if (media != null) {
            for (UploadItem item : media.current()) {
                String local = item.getLocalPath();
                if (local == null && !item.isRemote()) {
                    local = MediaFiles.toPath(item.getSourceUri());
                }
                in.media.add(new DirectDiagnosisInput.Media(item.isVideo(), local, item.getUrl(), item.getThumbUrl(),
                        item.getDurationSec(), item.getFileId()));
            }
        }
        return in;
    }

    static boolean isTimeout(@NonNull ApiException e) {
        Throwable c = e.getCause();
        while (c != null) {
            if (c instanceof SocketTimeoutException) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        return true;
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        if (formCtx.getValue() != null) {
            showMessage(e.getMessage());
        } else {
            super.onNoPermission(e);
        }
    }

    public void clearFieldError(@NonNull String field) {
        Map<String, String> m = fieldErrors.getValue();
        if (m != null && m.containsKey(field)) {
            Map<String, String> copy = new LinkedHashMap<>(m);
            copy.remove(field);
            fieldErrors.setValue(copy);
        }
    }

    private void putFieldError(@NonNull String field, @NonNull String message) {
        putFieldError(field, message, true);
    }

    private void putFieldError(@NonNull String field, @NonNull String message, boolean scroll) {
        Map<String, String> copy = new LinkedHashMap<>(fieldErrors.getValue() == null ? Collections.emptyMap()
                : fieldErrors.getValue());
        copy.put(field, message);
        fieldErrors.setValue(copy);
        if (scroll) {
            scrollTo.setValue(field);
        }
    }

    @Nullable
    static Integer parseInt(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (!t.matches("^\\d{1,7}$")) {
            return null;
        }
        return Integer.parseInt(t);
    }

    // ===== 返回 =====

    @NonNull
    private String snapshot() {
        return penId.getValue() + "|" + batchId.getValue() + "|" + sickCount.getValue() + "|" + description.getValue()
                + "|" + symptomIds.getValue() + "|" + (media == null ? 0 : media.current().size());
    }

    /** 有改动时返回：“放弃这次上报？”；返回 true 表示已拦截 */
    public boolean onBackPressed() {
        if (!finished && initialSnapshot != null && !initialSnapshot.equals(snapshot())) {
            showDiscard.call();
            return true;
        }
        return false;
    }

    @Override
    protected void onCleared() {
        if (media != null) {
            if (mediaObserver != null) {
                media.getItems().removeObserver(mediaObserver);
            }
            media.release();
        }
        super.onCleared();
    }
}
