package com.zhimu.slmp.herd.ui.death;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;

import com.google.gson.Gson;
import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.base.db.dao.FormDraftDao;
import com.zhimu.slmp.base.db.entity.FormDraftEntity;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.data.DictTypes;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.data.PenRepository;
import com.zhimu.slmp.herd.model.request.DeathCreateRequest;
import com.zhimu.slmp.herd.model.request.DeathUpdateRequest;
import com.zhimu.slmp.herd.model.vo.BatchDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchEventDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;
import com.zhimu.slmp.herd.model.vo.DeathResultVO;
import com.zhimu.slmp.herd.model.vo.ExecutorOptionVO;
import com.zhimu.slmp.herd.model.vo.PenStockVO;
import com.zhimu.slmp.herd.ui.common.FormSupport;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.media.data.MediaScene;
import com.zhimu.slmp.media.upload.UploadManager;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.network.model.PageVO;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * L11 死淘登记（含修改死淘记录）：批次、栏舍（多栏舍时）、数量（不超过该栏舍在栏数）、原因（“其他”时说明必填）、日期、
 * 实际执行人（员工不显示、不提交）、说明、照片（lib_media UploadManager，场景 DEATH，最多 6 张）。
 * 离开页面且有改动时保存草稿（form_draft，page_code = L11），再次进入时询问恢复（7 天内）。
 */
public class BatchDeathViewModel extends BaseViewModel {

    public static final String PAGE_CODE = "L11";
    public static final String REASON_OTHER = "OTHER";
    public static final int MAX_PHOTOS = 6;
    public static final String MSG_PHOTO_UPLOADING = "照片还在上传，请稍候";

    public static final String F_BATCH = "batch_id";
    public static final String F_PEN = "pen_id";
    public static final String F_QUANTITY = "quantity";
    public static final String F_REASON = "reason_code";
    public static final String F_DATE = "event_date";
    public static final String F_REMARK = "remark";

    static final long DRAFT_TTL_MS = TimeUnit.DAYS.toMillis(7);

    private final BatchRepository batchRepository;
    private final PenRepository penRepository;
    private final BaseDataRepository baseData;
    private final FarmContext farmContext;
    private final FormDraftDao draftDao;
    private final UploadManager.Factory uploads;
    private final Gson gson = GsonFactory.get();

    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    private final MutableLiveData<List<BatchListItemVO>> batchOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> batchId = new MutableLiveData<>();
    private final MutableLiveData<Long> penId = new MutableLiveData<>();
    private final MutableLiveData<Long> executorUserId = new MutableLiveData<>();
    private final MutableLiveData<BatchDetailVO> batch = new MutableLiveData<>();
    private final MutableLiveData<List<PenStockVO>> penStocks = new MutableLiveData<>(Collections.emptyList());
    private final LiveData<Boolean> needPenSelect;
    private final MutableLiveData<String> reasonCode = new MutableLiveData<>();
    private final LiveData<Boolean> remarkRequired;
    private final MutableLiveData<Boolean> showExecutor = new MutableLiveData<>(true);
    private final MediatorLiveData<String> penHelper = new MediatorLiveData<>();
    private final MutableLiveData<String> quantityText = new MutableLiveData<>("");
    private final MutableLiveData<String> remark = new MutableLiveData<>("");
    private final MutableLiveData<String> eventDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<List<DictItemVO>> reasons = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<ExecutorOptionVO>> executors = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final LiveData<Boolean> canSubmit;
    private final SingleLiveEvent<UiMessage> finishEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> restoreDraftEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> discardEvent = new SingleLiveEvent<>();

    private final FormSupport.IdemKeeper idem = new FormSupport.IdemKeeper();
    @Nullable
    private UploadManager photos;
    @Nullable
    private Long eventId;
    private int eventVersion;
    @Nullable
    private Long originalPenId;
    private int originalQty;
    @Nullable
    private String initialSnapshot;
    @Nullable
    private String draftKey;
    @Nullable
    private FormDraftEntity pendingDraft;
    private boolean finished;

    /** 草稿内容 */
    static final class Draft {
        Long batchId;
        Long penId;
        String quantity;
        String reasonCode;
        String eventDate;
        Long executorUserId;
        String remark;
    }

    /** 初始化数据 */
    private static final class Init {
        final List<BatchListItemVO> batches;
        final List<DictItemVO> reasons;
        @Nullable
        final BatchEventDetailVO event;

        Init(List<BatchListItemVO> batches, List<DictItemVO> reasons, @Nullable BatchEventDetailVO event) {
            this.batches = batches;
            this.reasons = reasons;
            this.event = event;
        }
    }

    @Inject
    public BatchDeathViewModel(@NonNull BatchRepository batchRepository, @NonNull PenRepository penRepository,
                               @NonNull BaseDataRepository baseData, @NonNull FarmContext farmContext,
                               @NonNull AppDatabase database, @NonNull UploadManager.Factory uploads) {
        this.batchRepository = batchRepository;
        this.penRepository = penRepository;
        this.baseData = baseData;
        this.farmContext = farmContext;
        this.draftDao = database.formDraftDao();
        this.uploads = uploads;
        needPenSelect = Transformations.map(penStocks, list -> list != null && list.size() > 1);
        remarkRequired = Transformations.map(reasonCode, REASON_OTHER::equals);
        penHelper.addSource(penId, v -> penHelper.setValue(computePenHelper()));
        penHelper.addSource(penStocks, v -> penHelper.setValue(computePenHelper()));
        canSubmit = Transformations.map(submitting, s -> !Boolean.TRUE.equals(s));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        photos = uploads.create(handle, "photos", MediaScene.DEATH, MAX_PHOTOS);
        init(asLong(handle.get(RouterPath.EXTRA_BATCH_ID)), asLong(handle.get(HerdExtras.EXTRA_EVENT_ID)));
    }

    @Nullable
    private static Long asLong(@Nullable Object o) {
        return o instanceof Number ? ((Number) o).longValue() : null;
    }

    // ===== 对外 =====

    public LiveData<Boolean> getEditMode() { return editMode; }

    public LiveData<List<BatchListItemVO>> getBatchOptions() { return batchOptions; }

    public MutableLiveData<Long> getBatchId() { return batchId; }

    public MutableLiveData<Long> getPenId() { return penId; }

    public MutableLiveData<Long> getExecutorUserId() { return executorUserId; }

    public LiveData<BatchDetailVO> getBatch() { return batch; }

    public LiveData<List<PenStockVO>> getPenStocks() { return penStocks; }

    public LiveData<Boolean> getNeedPenSelect() { return needPenSelect; }

    public LiveData<Boolean> getRemarkRequired() { return remarkRequired; }

    public LiveData<Boolean> getShowExecutor() { return showExecutor; }

    public LiveData<String> getPenHelper() { return penHelper; }

    public MutableLiveData<String> getQuantityText() { return quantityText; }

    public MutableLiveData<String> getRemark() { return remark; }

    public MutableLiveData<String> getEventDate() { return eventDate; }

    public MutableLiveData<String> getReasonCode() { return reasonCode; }

    public LiveData<List<DictItemVO>> getReasons() { return reasons; }

    public LiveData<List<ExecutorOptionVO>> getExecutors() { return executors; }

    /** 照片网格（MediaPickerView.bind） */
    @NonNull
    public UploadManager getPhotos() { return Objects.requireNonNull(photos); }

    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }

    public LiveData<Boolean> getCanSubmit() { return canSubmit; }

    public LiveData<Boolean> getSubmitting() { return submitting; }

    public SingleLiveEvent<UiMessage> getFinishEvent() { return finishEvent; }

    public SingleLiveEvent<Void> getRestoreDraftEvent() { return restoreDraftEvent; }

    public SingleLiveEvent<Void> getDiscardEvent() { return discardEvent; }

    boolean isStaff() {
        return farmContext.isStaff();
    }

    // ===== 初始化 =====

    /** API-L06 在养批次、API-U10 死淘原因；编辑时 API-L19 带入原值；带入批次时 API-L15、L17 */
    public void init(@Nullable Long presetBatchId, @Nullable Long eventId) {
        this.eventId = eventId;
        boolean edit = eventId != null;
        editMode.setValue(edit);
        showExecutor.setValue(!isStaff());
        draftKey = presetBatchId == null ? "" : String.valueOf(presetBatchId);
        Single<List<BatchListItemVO>> batches = batchRepository.getActiveBatches().map(BatchDeathViewModel::listOf);
        Single<List<DictItemVO>> reasonList = baseData.getDicts(DictTypes.DEATH_REASON, null);
        Single<Init> source = edit
                ? Single.zip(batches, reasonList, batchRepository.getEventDetail(eventId), Init::new)
                : Single.zip(batches, reasonList, (b, r) -> new Init(b, r, null));
        load(source, init -> {
            batchOptions.setValue(init.batches);
            reasons.setValue(init.reasons);
            BatchEventDetailVO e = init.event;
            Long target = presetBatchId;
            if (e != null) {
                target = e.getBatchId();
                eventVersion = e.getVersion();
                originalQty = e.getQuantity();
                originalPenId = e.getPen() == null ? null : e.getPen().getPenId();
                quantityText.setValue(String.valueOf(e.getQuantity()));
                reasonCode.setValue(e.getReasonCode());
                eventDate.setValue(e.getEventDate());
                remark.setValue(e.getRemark() == null ? "" : e.getRemark());
                if (e.getExecutor() != null) {
                    executorUserId.setValue(e.getExecutor().getUserId());
                }
                if (photos != null) {
                    photos.setInitialFiles(e.getPhotos());
                }
            }
            if (target != null) {
                selectBatch(target, originalPenId);
            }
            initialSnapshot = snapshot();
            if (!edit) {
                checkDraft();
            }
        });
    }

    @NonNull
    private static List<BatchListItemVO> listOf(@NonNull PageVO<BatchListItemVO> page) {
        return page.getList() == null ? Collections.emptyList() : page.getList();
    }

    @Override
    public void retry() {
        init(batchId.getValue(), eventId);
    }

    // ===== 选择 =====

    public void onBatchSelected(long id) {
        clearFieldError(F_BATCH);
        selectBatch(id, null);
    }

    private void selectBatch(long id, @Nullable Long presetPen) {
        batchId.setValue(id);
        if (presetPen == null) {
            penId.setValue(null);
        }
        launch(Single.zip(batchRepository.getBatchDetail(id), batchRepository.getPenStocks(id),
                (d, p) -> new Object[]{d, p}), pair -> {
            batch.setValue((BatchDetailVO) pair[0]);
            @SuppressWarnings("unchecked")
            List<PenStockVO> pens = (List<PenStockVO>) pair[1];
            penStocks.setValue(pens);
            Long pen = presetPen;
            if (pen == null && pens.size() == 1) {
                pen = pens.get(0).getPenId();
            }
            if (pen != null) {
                onPenSelected(pen);
            }
        });
    }

    /** 栏舍：养殖户取实际执行人选项（API-L25），默认本人 */
    public void onPenSelected(long id) {
        penId.setValue(id);
        clearFieldError(F_PEN);
        validateQuantity();
        if (isStaff()) {
            return;
        }
        launch(penRepository.getExecutors(id), list -> {
            executors.setValue(list);
            Long current = executorUserId.getValue();
            boolean found = false;
            for (ExecutorOptionVO o : list) {
                if (current != null && o.getUserId() == current) {
                    found = true;
                }
            }
            if (!found) {
                for (ExecutorOptionVO o : list) {
                    if (o.isMe()) {
                        executorUserId.setValue(o.getUserId());
                        break;
                    }
                }
            }
        });
    }

    public void onExecutorSelected(long userId) {
        executorUserId.setValue(userId);
    }

    public void onReasonSelected(@Nullable String code) {
        reasonCode.setValue(code);
        clearFieldError(F_REASON);
        if (!REASON_OTHER.equals(code)) {
            clearFieldError(F_REMARK);
        }
    }

    public void onDateSelected(@Nullable String date) {
        if (date != null) {
            eventDate.setValue(date);
            clearFieldError(F_DATE);
        }
    }

    // ===== 派生 =====

    @Nullable
    PenStockVO selectedPen() {
        Long id = penId.getValue();
        List<PenStockVO> list = penStocks.getValue();
        if (id == null || list == null) {
            return null;
        }
        for (PenStockVO p : list) {
            if (p.getPenId() == id) {
                return p;
            }
        }
        return null;
    }

    @NonNull
    String unit() {
        BatchDetailVO d = batch.getValue();
        return HerdFormat.unit(d == null ? null : d.getUnitText());
    }

    @Nullable
    private String computePenHelper() {
        PenStockVO p = selectedPen();
        return p == null ? null : p.getPenName() + HerdFormat.DOT + "在栏 " + HerdFormat.qty(p.getStockCount(), unit());
    }

    /** 该栏舍可登记的上限（编辑且栏舍未变时加原数量） */
    @Nullable
    Integer maxQuantity() {
        PenStockVO p = selectedPen();
        if (p == null) {
            return null;
        }
        return p.getStockCount() + (Objects.equals(p.getPenId(), originalPenId) ? originalQty : 0);
    }

    @NonNull
    private String limitText(@NonNull PenStockVO p, int max) {
        return "不能超过 " + p.getPenName() + "的在栏数 " + HerdFormat.qty(max, unit());
    }

    /** 停止输入 500 毫秒或失焦后调用：超出时整框错误态 */
    public void validateQuantity() {
        String q = quantityText.getValue() == null ? "" : quantityText.getValue().trim();
        Integer qty = FormSupport.parseInt(q);
        PenStockVO p = selectedPen();
        Integer max = maxQuantity();
        if (p != null && max != null && qty != null && qty > max) {
            putFieldError(F_QUANTITY, limitText(p, max));
        } else if (q.isEmpty() || (qty != null && qty >= 1)) {
            clearFieldError(F_QUANTITY);
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
        Map<String, String> copy = new LinkedHashMap<>(fieldErrors.getValue() == null ? Collections.emptyMap()
                : fieldErrors.getValue());
        copy.put(field, message);
        fieldErrors.setValue(copy);
    }

    // ===== 校验与提交 =====

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        if (batchId.getValue() == null) {
            e.put(F_BATCH, "请选择批次");
        }
        List<PenStockVO> pens = penStocks.getValue();
        if (pens != null && pens.size() > 1 && penId.getValue() == null) {
            e.put(F_PEN, "请选择栏舍");
        }
        String q = quantityText.getValue() == null ? "" : quantityText.getValue().trim();
        Integer qty = FormSupport.parseInt(q);
        PenStockVO p = selectedPen();
        Integer max = maxQuantity();
        if (q.isEmpty()) {
            e.put(F_QUANTITY, "请填写数量");
        } else if (qty == null || qty < 1) {
            e.put(F_QUANTITY, "数量需为大于 0 的整数");
        } else if (p != null && max != null && qty > max) {
            e.put(F_QUANTITY, limitText(p, max));
        }
        if (reasonCode.getValue() == null) {
            e.put(F_REASON, "请选择死淘原因");
        }
        LocalDate date = DateFormatter.parseDate(eventDate.getValue());
        LocalDate min = minDate();
        if (date == null || date.isAfter(DateFormatter.today())) {
            e.put(F_DATE, "日期不能晚于今天");
        } else if (min != null && date.isBefore(min)) {
            e.put(F_DATE, "日期不能早于入栏日期 " + HerdFormat.date(DateFormatter.toDateString(min)));
        }
        String r = remark.getValue() == null ? "" : remark.getValue().trim();
        if (REASON_OTHER.equals(reasonCode.getValue()) && r.isEmpty()) {
            e.put(F_REMARK, "选择‘其他’时请写明原因");
        } else if (r.codePointCount(0, r.length()) > 200) {
            e.put(F_REMARK, "说明最多 200 个字");
        }
        return e;
    }

    /** 入栏日期与进入该栏舍日期中的较晚者 */
    @Nullable
    LocalDate minDate() {
        BatchDetailVO d = batch.getValue();
        LocalDate entry = d == null ? null : DateFormatter.parseDate(d.getEntryDate());
        PenStockVO p = selectedPen();
        LocalDate first = p == null ? null : DateFormatter.parseDate(p.getFirstInDate());
        if (entry == null) {
            return first;
        }
        return first != null && first.isAfter(entry) ? first : entry;
    }

    /** API-L23 新增（Idempotency-Key）/ API-L24 修改 */
    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
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
        int qty = Objects.requireNonNull(FormSupport.parseInt(quantityText.getValue()));
        String note = remark.getValue() == null || remark.getValue().trim().isEmpty() ? null : remark.getValue().trim();
        Long executor = isStaff() ? null : executorUserId.getValue();
        submitting.setValue(true);
        if (eventId != null) {
            DeathUpdateRequest req = new DeathUpdateRequest();
            req.penId = penId.getValue();
            req.quantity = qty;
            req.reasonCode = reasonCode.getValue();
            req.eventDate = eventDate.getValue();
            req.executorUserId = executor;
            req.remark = note;
            req.fileIds = fileIds;
            req.version = eventVersion;
            launch(batchRepository.updateDeath(eventId, req), this::onSaved, this::onSubmitError);
        } else {
            DeathCreateRequest req = new DeathCreateRequest();
            req.batchId = Objects.requireNonNull(batchId.getValue());
            req.penId = penId.getValue();
            req.quantity = qty;
            req.reasonCode = reasonCode.getValue();
            req.eventDate = eventDate.getValue();
            req.executorUserId = executor;
            req.remark = note;
            req.fileIds = fileIds.isEmpty() ? null : fileIds;
            PenStockVO p = selectedPen();
            req.seenStockCount = p == null ? null : p.getStockCount();
            launch(batchRepository.createDeath(req, idem.keyFor(req)), this::onSaved, this::onSubmitError);
        }
    }

    private void onSaved(@NonNull DeathResultVO r) {
        submitting.setValue(false);
        finished = true;
        discardDraft();
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.notifyTodoChanged(new TodoChange(null, HerdExtras.BIZ_BATCH_DEATH, r.getEventId(),
                    farmContext.farmIdOrZero()));
        }
        String msg = r.getMessage() != null ? r.getMessage()
                : "已登记死淘 " + HerdFormat.qty(r.getQuantity(), unit()) + "，存栏已更新";
        finishEvent.setValue(UiMessage.of(msg));
    }

    private void onSubmitError(@NonNull ApiException e) {
        int code = e.getCode();
        if (code == ErrorCodes.COUNT_EXCEEDED) {
            // 以服务器在栏数为准：“3 号育肥舍的在栏数已变为 195 头，不能超过 195 头”
            submitting.setValue(false);
            putFieldError(F_QUANTITY, e.getMessage());
            return;
        }
        if (code == ErrorCodes.PEN_FORBIDDEN) {
            // 员工负责栏舍已调整：提示后返回上一页刷新
            submitting.setValue(false);
            finished = true;
            finishEvent.setValue(UiMessage.of(e.getMessage()));
            return;
        }
        if (eventId == null && FormSupport.isTimeout(e)) {
            checkByRequest();
            return;
        }
        submitting.setValue(false);
        if (e.isNetwork()) {
            showMessage(FormSupport.MSG_NETWORK_KEEP);
            return;
        }
        handleError(e);
    }

    /** API-L26：超时后确认是否已保存 */
    public void checkByRequest() {
        String key = idem.current();
        if (key == null) {
            submitting.setValue(false);
            return;
        }
        launch(batchRepository.checkByRequest(key), vo -> {
            if (vo.isExists() && vo.getResult() != null) {
                onSaved(gson.fromJson(vo.getResult(), DeathResultVO.class));
            } else {
                submitting.setValue(false);
                showMessage(FormSupport.MSG_SUBMIT_FAILED);
            }
        }, err -> {
            submitting.setValue(false);
            showMessage(FormSupport.MSG_SUBMIT_FAILED);
        });
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        return true;
    }

    // ===== 返回与草稿 =====

    public boolean isDirty() {
        return initialSnapshot != null && !initialSnapshot.equals(snapshot());
    }

    /** 有改动时返回：“放弃这次登记？”；返回 true 表示已拦截 */
    public boolean onBackPressed() {
        if (isDirty()) {
            discardEvent.call();
            return true;
        }
        return false;
    }

    /** 选“放弃”：删除草稿后关闭 */
    public void onDiscardConfirmed() {
        finished = true;
        discardDraft();
    }

    @NonNull
    private String snapshot() {
        return batchId.getValue() + "|" + penId.getValue() + "|" + quantityText.getValue() + "|" + reasonCode.getValue()
                + "|" + eventDate.getValue() + "|" + executorUserId.getValue() + "|" + remark.getValue();
    }

    /** 页面 onStop 且有改动时保存草稿（编辑模式不保存） */
    public void saveDraft() {
        if (finished || eventId != null || !isDirty() || draftKey == null) {
            return;
        }
        Draft d = new Draft();
        d.batchId = batchId.getValue();
        d.penId = penId.getValue();
        d.quantity = quantityText.getValue();
        d.reasonCode = reasonCode.getValue();
        d.eventDate = eventDate.getValue();
        d.executorUserId = executorUserId.getValue();
        d.remark = remark.getValue();
        FormDraftEntity entity = new FormDraftEntity();
        entity.pageCode = PAGE_CODE;
        entity.farmId = farmContext.farmIdOrZero();
        entity.bizKey = draftKey;
        entity.json = gson.toJson(d);
        entity.updatedAt = System.currentTimeMillis();
        disposables.add(draftDao.save(entity).subscribeOn(Schedulers.io()).subscribe(() -> {
        }, t -> {
        }));
    }

    private void checkDraft() {
        if (draftKey == null) {
            return;
        }
        launch(draftDao.get(PAGE_CODE, farmContext.farmIdOrZero(), draftKey).toSingle(), entity -> {
            if (System.currentTimeMillis() - entity.updatedAt <= DRAFT_TTL_MS) {
                pendingDraft = entity;
                restoreDraftEvent.call();
            } else {
                discardDraft();
            }
        }, e -> {
            // 没有草稿（Maybe 为空）
        });
    }

    /** 恢复草稿 */
    public void restoreDraft() {
        FormDraftEntity entity = pendingDraft;
        pendingDraft = null;
        if (entity == null) {
            return;
        }
        Draft d = gson.fromJson(entity.json, Draft.class);
        if (d == null) {
            return;
        }
        quantityText.setValue(d.quantity == null ? "" : d.quantity);
        reasonCode.setValue(d.reasonCode);
        if (d.eventDate != null) {
            eventDate.setValue(d.eventDate);
        }
        remark.setValue(d.remark == null ? "" : d.remark);
        if (d.executorUserId != null) {
            executorUserId.setValue(d.executorUserId);
        }
        if (d.batchId != null) {
            selectBatch(d.batchId, d.penId);
        }
    }

    /** 放弃或提交成功后删除草稿 */
    public void discardDraft() {
        pendingDraft = null;
        if (draftKey == null) {
            return;
        }
        disposables.add(draftDao.delete(PAGE_CODE, farmContext.farmIdOrZero(), draftKey)
                .subscribeOn(Schedulers.io()).subscribe(() -> {
                }, t -> {
                }));
    }

    @Override
    protected void onCleared() {
        if (photos != null) {
            photos.release();
        }
        super.onCleared();
    }
}
