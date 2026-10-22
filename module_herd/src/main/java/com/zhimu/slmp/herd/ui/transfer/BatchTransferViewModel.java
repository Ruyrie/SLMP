package com.zhimu.slmp.herd.ui.transfer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.Gson;
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
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.data.PenRepository;
import com.zhimu.slmp.herd.model.request.TransferCreateRequest;
import com.zhimu.slmp.herd.model.request.TransferUpdateRequest;
import com.zhimu.slmp.herd.model.vo.BatchDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchEventDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;
import com.zhimu.slmp.herd.model.vo.PenOptionVO;
import com.zhimu.slmp.herd.model.vo.PenStockVO;
import com.zhimu.slmp.herd.model.vo.TransferResultVO;
import com.zhimu.slmp.herd.ui.common.FormSupport;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.herd.util.CapacityCalculator;
import com.zhimu.slmp.network.GsonFactory;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * L10 转栏登记（含修改转栏记录）：转出栏舍只列本批次所在栏舍（单栏舍默认选中），转入栏舍排除转出与已停用栏舍；
 * 数量不能超过转出栏舍在栏数；转入后超过容量须勾选确认；42205 写入数量框，42206 重写横幅。
 */
public class BatchTransferViewModel extends BaseViewModel {

    public static final String F_BATCH = "batch_id";
    public static final String F_FROM = "from_pen_id";
    public static final String F_TO = "to_pen_id";
    public static final String F_QUANTITY = "quantity";
    public static final String F_DATE = "event_date";
    public static final String F_REMARK = "remark";

    private final BatchRepository batchRepository;
    private final PenRepository penRepository;
    private final FarmContext farmContext;
    private final Gson gson = GsonFactory.get();

    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    private final MutableLiveData<BatchDetailVO> batch = new MutableLiveData<>();
    private final MutableLiveData<List<BatchListItemVO>> batchOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> batchFixed = new MutableLiveData<>(true);
    private final MutableLiveData<List<PenStockVO>> fromPens = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> fromPenId = new MutableLiveData<>();
    private final MutableLiveData<Long> toPenId = new MutableLiveData<>();
    private final MutableLiveData<List<PenOptionVO>> toPenOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<String> quantityText = new MutableLiveData<>("");
    private final MutableLiveData<String> remark = new MutableLiveData<>("");
    private final MutableLiveData<String> eventDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MediatorLiveData<Integer> maxQuantity = new MediatorLiveData<>();
    private final MediatorLiveData<String> minDate = new MediatorLiveData<>();
    private final MediatorLiveData<String> fromHelper = new MediatorLiveData<>();
    private final MediatorLiveData<String> toHelper = new MediatorLiveData<>();
    private final MediatorLiveData<String> capacityBanner = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> capacityChecked = new MutableLiveData<>(false);
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final SingleLiveEvent<UiMessage> finishEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> changeRequestEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> discardEvent = new SingleLiveEvent<>();
    private final MutableLiveData<Integer> serverTick = new MutableLiveData<>(0);

    private final FormSupport.IdemKeeper idem = new FormSupport.IdemKeeper();
    @Nullable
    private Long eventId;
    private int eventVersion;
    @Nullable
    private Long originalFromPenId;
    @Nullable
    private Long originalToPenId;
    private int originalQty;
    @Nullable
    private String initialSnapshot;
    @Nullable
    private CapacityCalculator.Result serverCapacity;
    @Nullable
    private String serverKey;

    /** 批次加载结果 */
    private static final class BatchData {
        final BatchDetailVO detail;
        final List<PenStockVO> pens;
        @Nullable
        final BatchEventDetailVO event;

        BatchData(BatchDetailVO detail, List<PenStockVO> pens, @Nullable BatchEventDetailVO event) {
            this.detail = detail;
            this.pens = pens;
            this.event = event;
        }
    }

    @Inject
    public BatchTransferViewModel(@NonNull BatchRepository batchRepository, @NonNull PenRepository penRepository,
                                  @NonNull FarmContext farmContext) {
        this.batchRepository = batchRepository;
        this.penRepository = penRepository;
        this.farmContext = farmContext;
        maxQuantity.addSource(fromPenId, v -> maxQuantity.setValue(computeMax()));
        maxQuantity.addSource(fromPens, v -> maxQuantity.setValue(computeMax()));
        minDate.addSource(fromPenId, v -> minDate.setValue(computeMinDate()));
        minDate.addSource(batch, v -> minDate.setValue(computeMinDate()));
        fromHelper.addSource(maxQuantity, v -> fromHelper.setValue(v == null ? null
                : "本批次在栏 " + HerdFormat.qty(v, unit())));
        toHelper.addSource(toPenId, v -> toHelper.setValue(computeToHelper()));
        toHelper.addSource(toPenOptions, v -> toHelper.setValue(computeToHelper()));
        capacityBanner.addSource(toPenId, v -> updateBanner());
        capacityBanner.addSource(toPenOptions, v -> updateBanner());
        capacityBanner.addSource(quantityText, v -> updateBanner());
        capacityBanner.addSource(serverTick, v -> updateBanner());
        canSubmit.addSource(capacityBanner, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(capacityChecked, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(submitting, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(fromPenId, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(toPenId, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(quantityText, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(fieldErrors, v -> canSubmit.setValue(isSubmittable()));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        init(asLong(handle.get(RouterPath.EXTRA_BATCH_ID)), asLong(handle.get(HerdExtras.EXTRA_EVENT_ID)));
    }

    @Nullable
    private static Long asLong(@Nullable Object o) {
        return o instanceof Number ? ((Number) o).longValue() : null;
    }

    // ===== 对外 =====

    public LiveData<Boolean> getEditMode() { return editMode; }

    public LiveData<BatchDetailVO> getBatch() { return batch; }

    public LiveData<List<BatchListItemVO>> getBatchOptions() { return batchOptions; }

    /** 从 L06 进入时批次固定；从 T02 等入口进入时显示批次下拉 */
    public LiveData<Boolean> getBatchFixed() { return batchFixed; }

    public LiveData<List<PenStockVO>> getFromPens() { return fromPens; }

    public MutableLiveData<Long> getFromPenId() { return fromPenId; }

    public MutableLiveData<Long> getToPenId() { return toPenId; }

    public LiveData<List<PenOptionVO>> getToPenOptions() { return toPenOptions; }

    public MutableLiveData<String> getQuantityText() { return quantityText; }

    public MutableLiveData<String> getRemark() { return remark; }

    public MutableLiveData<String> getEventDate() { return eventDate; }

    public LiveData<Integer> getMaxQuantity() { return maxQuantity; }

    public LiveData<String> getMinDate() { return minDate; }

    public LiveData<String> getFromHelper() { return fromHelper; }

    public LiveData<String> getToHelper() { return toHelper; }

    public LiveData<String> getCapacityBanner() { return capacityBanner; }

    public MutableLiveData<Boolean> getCapacityChecked() { return capacityChecked; }

    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }

    public LiveData<Boolean> getCanSubmit() { return canSubmit; }

    public LiveData<Boolean> getSubmitting() { return submitting; }

    public SingleLiveEvent<UiMessage> getFinishEvent() { return finishEvent; }

    public SingleLiveEvent<Long> getChangeRequestEvent() { return changeRequestEvent; }

    public SingleLiveEvent<Void> getDiscardEvent() { return discardEvent; }

    // ===== 初始化 =====

    /** API-L15 批次、API-L17 各栏舍在栏数；编辑时 API-L19 带入原值；没有批次时 API-L06 在养批次下拉 */
    public void init(@Nullable Long batchId, @Nullable Long eventId) {
        this.eventId = eventId;
        editMode.setValue(eventId != null);
        if (batchId == null) {
            batchFixed.setValue(false);
            load(batchRepository.getActiveBatches(), page -> {
                batchOptions.setValue(page.getList() == null ? Collections.emptyList() : page.getList());
                initialSnapshot = snapshot();
            });
            return;
        }
        batchFixed.setValue(true);
        load(loadBatch(batchId, eventId), this::applyBatch);
    }

    @NonNull
    private Single<BatchData> loadBatch(long batchId, @Nullable Long eventId) {
        Single<BatchDetailVO> detail = batchRepository.getBatchDetail(batchId);
        Single<List<PenStockVO>> pens = batchRepository.getPenStocks(batchId);
        return eventId == null ? Single.zip(detail, pens, (d, p) -> new BatchData(d, p, null))
                : Single.zip(detail, pens, batchRepository.getEventDetail(eventId), BatchData::new);
    }

    private void applyBatch(@NonNull BatchData data) {
        batch.setValue(data.detail);
        BatchEventDetailVO e = data.event;
        List<PenStockVO> pens = data.pens;
        if (e != null && e.getPen() != null) {
            // 编辑：转出栏舍可能已无在栏，补进选项
            originalFromPenId = e.getPen().getPenId();
            originalToPenId = e.getToPen() == null ? null : e.getToPen().getPenId();
            originalQty = e.getQuantity();
            eventVersion = e.getVersion();
            quantityText.setValue(String.valueOf(e.getQuantity()));
            eventDate.setValue(e.getEventDate());
            remark.setValue(e.getRemark() == null ? "" : e.getRemark());
        }
        fromPens.setValue(pens);
        Long from = originalFromPenId;
        if (from == null && pens.size() == 1) {
            from = pens.get(0).getPenId();
        }
        if (from != null) {
            onFromPenSelected(from);
        }
        if (originalToPenId != null) {
            toPenId.setValue(originalToPenId);
        }
        initialSnapshot = snapshot();
    }

    @Override
    public void retry() {
        BatchDetailVO d = batch.getValue();
        init(d == null ? null : d.getBatchId(), eventId);
    }

    // ===== 选择 =====

    /** 没有批次上下文时选择批次 */
    public void onBatchSelected(long batchId) {
        clearFieldError(F_BATCH);
        fromPenId.setValue(null);
        toPenId.setValue(null);
        launch(loadBatch(batchId, null), this::applyBatch);
    }

    /** 转出栏舍：带 exclude_pen_id 与 category_id 重取转入栏舍（API-L07） */
    public void onFromPenSelected(long penId) {
        fromPenId.setValue(penId);
        clearFieldError(F_FROM);
        if (Objects.equals(toPenId.getValue(), penId)) {
            toPenId.setValue(null);
        }
        BatchDetailVO d = batch.getValue();
        Long categoryId = d == null ? null : d.getCategoryId();
        launch(penRepository.getPenOptions(categoryId, penId), toPenOptions::setValue);
        validateQuantityLive();
    }

    public void onToPenSelected(long penId) {
        toPenId.setValue(penId);
        clearFieldError(F_TO);
    }

    public void onDateSelected(@Nullable String date) {
        if (date != null) {
            eventDate.setValue(date);
            clearFieldError(F_DATE);
        }
    }

    /** 数量变化：超过上限时输入框立即变为错误态 */
    public void validateQuantityLive() {
        Integer q = FormSupport.parseInt(quantityText.getValue());
        Integer max = computeMax();
        if (q != null && max != null && q > max) {
            putFieldError(F_QUANTITY, quantityLimitText(max));
        } else {
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
        if (!message.equals(copy.get(field))) {
            copy.put(field, message);
            fieldErrors.setValue(copy);
        }
    }

    // ===== 派生 =====

    @NonNull
    String unit() {
        BatchDetailVO d = batch.getValue();
        return HerdFormat.unit(d == null ? null : d.getUnitText());
    }

    @Nullable
    private PenStockVO selectedFrom() {
        Long id = fromPenId.getValue();
        List<PenStockVO> list = fromPens.getValue();
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

    @Nullable
    private PenOptionVO selectedTo() {
        Long id = toPenId.getValue();
        List<PenOptionVO> list = toPenOptions.getValue();
        if (id == null || list == null) {
            return null;
        }
        for (PenOptionVO p : list) {
            if (p.getPenId() == id) {
                return p;
            }
        }
        return null;
    }

    /** 转出栏舍在栏数（编辑且转出栏舍未变时加原数量） */
    @Nullable
    Integer computeMax() {
        PenStockVO from = selectedFrom();
        Long id = fromPenId.getValue();
        if (from == null) {
            return id != null && Objects.equals(id, originalFromPenId) ? originalQty : null;
        }
        int extra = Objects.equals(from.getPenId(), originalFromPenId) ? originalQty : 0;
        return from.getStockCount() + extra;
    }

    /** 入栏日期与进入转出栏舍日期中的较晚者 */
    @Nullable
    String computeMinDate() {
        BatchDetailVO d = batch.getValue();
        String entry = d == null ? null : d.getEntryDate();
        PenStockVO from = selectedFrom();
        String first = from == null ? null : from.getFirstInDate();
        LocalDate a = DateFormatter.parseDate(entry);
        LocalDate b = DateFormatter.parseDate(first);
        if (a == null) {
            return first;
        }
        if (b == null) {
            return entry;
        }
        return b.isAfter(a) ? first : entry;
    }

    @Nullable
    private String computeToHelper() {
        PenOptionVO to = selectedTo();
        return to == null ? null : HerdFormat.stockOfCapacity(to.getStockCount(), to.getCapacity(), unit());
    }

    @NonNull
    String quantityLimitText(int max) {
        return "不能超过转出栏舍的在栏数 " + HerdFormat.qty(max, unit());
    }

    @Nullable
    String computeBanner() {
        PenOptionVO to = selectedTo();
        if (to == null) {
            return null;
        }
        String key = to.getPenId() + "|" + quantityText.getValue();
        if (serverCapacity != null && key.equals(serverKey)) {
            return CapacityCalculator.bannerText(to.getPenName(), serverCapacity, unit(), "转入", "确认后才能保存。");
        }
        Integer qty = FormSupport.parseInt(quantityText.getValue());
        if (qty == null || qty <= 0) {
            return null;
        }
        int original = Objects.equals(to.getPenId(), originalToPenId) ? originalQty : 0;
        CapacityCalculator.Result r = CapacityCalculator.calc(to.getStockCount(), to.getCapacity(), qty, original);
        return CapacityCalculator.bannerText(to.getPenName(), r, unit(), "转入", "确认后才能保存。");
    }

    private void updateBanner() {
        String old = capacityBanner.getValue();
        String now = computeBanner();
        capacityBanner.setValue(now);
        if (now == null && old != null) {
            capacityChecked.setValue(false);
        }
    }

    boolean isSubmittable() {
        if (Boolean.TRUE.equals(submitting.getValue()) || batch.getValue() == null || fromPenId.getValue() == null
                || toPenId.getValue() == null || HerdFormat.isEmpty(quantityText.getValue())) {
            return false;
        }
        Map<String, String> errors = fieldErrors.getValue();
        if (errors != null && errors.containsKey(F_QUANTITY)) {
            return false;
        }
        return computeBanner() == null || Boolean.TRUE.equals(capacityChecked.getValue());
    }

    // ===== 提交 =====

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        if (batch.getValue() == null) {
            e.put(F_BATCH, "请选择批次");
        }
        if (fromPenId.getValue() == null) {
            e.put(F_FROM, "请选择转出栏舍");
        }
        if (toPenId.getValue() == null) {
            e.put(F_TO, "请选择转入栏舍");
        } else if (Objects.equals(toPenId.getValue(), fromPenId.getValue())) {
            e.put(F_TO, "转入栏舍不能与转出栏舍相同");
        }
        String q = quantityText.getValue() == null ? "" : quantityText.getValue().trim();
        Integer qty = FormSupport.parseInt(q);
        Integer max = computeMax();
        if (q.isEmpty()) {
            e.put(F_QUANTITY, "请填写数量");
        } else if (qty == null || qty < 1) {
            e.put(F_QUANTITY, "数量需为大于 0 的整数");
        } else if (max != null && qty > max) {
            e.put(F_QUANTITY, quantityLimitText(max));
        }
        LocalDate date = DateFormatter.parseDate(eventDate.getValue());
        LocalDate min = DateFormatter.parseDate(computeMinDate());
        if (date == null || date.isAfter(DateFormatter.today())) {
            e.put(F_DATE, "转栏日期不能晚于今天");
        } else if (min != null && date.isBefore(min)) {
            e.put(F_DATE, "转栏日期不能早于入栏日期 " + HerdFormat.date(computeMinDate()));
        }
        String r = remark.getValue();
        if (r != null && r.trim().codePointCount(0, r.trim().length()) > 100) {
            e.put(F_REMARK, "备注最多 100 个字");
        }
        return e;
    }

    /** API-L21 新增（Idempotency-Key）/ API-L22 修改 */
    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            return;
        }
        String banner = computeBanner();
        boolean confirmed = Boolean.TRUE.equals(capacityChecked.getValue());
        if (banner != null && !confirmed) {
            return;
        }
        BatchDetailVO d = Objects.requireNonNull(batch.getValue());
        int qty = Objects.requireNonNull(FormSupport.parseInt(quantityText.getValue()));
        String note = remark.getValue() == null || remark.getValue().trim().isEmpty() ? null : remark.getValue().trim();
        Boolean capacity = banner != null ? Boolean.TRUE : null;
        submitting.setValue(true);
        if (eventId != null) {
            TransferUpdateRequest req = new TransferUpdateRequest();
            req.fromPenId = Objects.requireNonNull(fromPenId.getValue());
            req.toPenId = Objects.requireNonNull(toPenId.getValue());
            req.quantity = qty;
            req.eventDate = eventDate.getValue();
            req.remark = note;
            req.capacityConfirmed = capacity;
            req.version = eventVersion;
            launch(batchRepository.updateTransfer(eventId, req), this::onSaved, this::onSubmitError);
        } else {
            TransferCreateRequest req = new TransferCreateRequest();
            req.batchId = d.getBatchId();
            req.fromPenId = Objects.requireNonNull(fromPenId.getValue());
            req.toPenId = Objects.requireNonNull(toPenId.getValue());
            req.quantity = qty;
            req.eventDate = eventDate.getValue();
            req.remark = note;
            req.capacityConfirmed = capacity;
            PenStockVO from = selectedFrom();
            req.seenStockCount = from == null ? null : from.getStockCount();
            launch(batchRepository.createTransfer(req, idem.keyFor(req)), this::onSaved, this::onSubmitError);
        }
    }

    private void onSaved(@NonNull TransferResultVO r) {
        submitting.setValue(false);
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.notifyTodoChanged(new TodoChange(null, HerdExtras.BIZ_BATCH_TRANSFER, r.getEventId(),
                    farmContext.farmIdOrZero()));
        }
        String msg = r.getMessage() != null ? r.getMessage() : "已转栏 " + HerdFormat.qty(r.getQuantity(), unit());
        finishEvent.setValue(UiMessage.of(msg));
    }

    private void onSubmitError(@NonNull ApiException e) {
        int code = e.getCode();
        if (code == ErrorCodes.COUNT_EXCEEDED) {
            submitting.setValue(false);
            putFieldError(F_QUANTITY, e.getMessage());
            return;
        }
        if (code == ErrorCodes.CAPACITY_CONFIRM) {
            submitting.setValue(false);
            serverCapacity = new CapacityCalculator.Result(dataInt(e, "capacity"), dataInt(e, "current"),
                    dataInt(e, "after"), dataInt(e, "over"));
            PenOptionVO to = selectedTo();
            serverKey = (to == null ? "" : to.getPenId()) + "|" + quantityText.getValue();
            capacityChecked.setValue(false);
            Integer tick = serverTick.getValue();
            serverTick.setValue(tick == null ? 1 : tick + 1);
            return;
        }
        if (code == ErrorCodes.CHANGE_REQUEST_REQUIRED) {
            submitting.setValue(false);
            showMessage(e.getMessage());
            changeRequestEvent.setValue(eventId);
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
                onSaved(gson.fromJson(vo.getResult(), TransferResultVO.class));
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

    public boolean onBackPressed() {
        if (initialSnapshot != null && !initialSnapshot.equals(snapshot())) {
            discardEvent.call();
            return true;
        }
        return false;
    }

    @NonNull
    private String snapshot() {
        BatchDetailVO d = batch.getValue();
        return (d == null ? "" : d.getBatchId()) + "|" + fromPenId.getValue() + "|" + toPenId.getValue() + "|"
                + quantityText.getValue() + "|" + eventDate.getValue() + "|" + remark.getValue();
    }

    private static int dataInt(@NonNull ApiException e, @NonNull String key) {
        Integer v = FormSupport.parseInt(e.dataString(key));
        return v == null ? 0 : v;
    }
}
