package com.zhimu.slmp.feed.ui.weigh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
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
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.feed.FeedExtras;
import com.zhimu.slmp.feed.data.WeighRepository;
import com.zhimu.slmp.feed.model.request.WeighCycleRequest;
import com.zhimu.slmp.feed.model.request.WeighItemRequest;
import com.zhimu.slmp.feed.model.request.WeighRecordCreateRequest;
import com.zhimu.slmp.feed.model.request.WeighRecordUpdateRequest;
import com.zhimu.slmp.feed.model.vo.StdPointVO;
import com.zhimu.slmp.feed.model.vo.WeighBatchOptionVO;
import com.zhimu.slmp.feed.model.vo.WeighCycleVO;
import com.zhimu.slmp.feed.model.vo.WeighFormBatchVO;
import com.zhimu.slmp.feed.model.vo.WeighFormVO;
import com.zhimu.slmp.feed.model.vo.WeighRecordVO;
import com.zhimu.slmp.feed.ui.common.FeedDraftStore;
import com.zhimu.slmp.feed.ui.common.FeedErrors;
import com.zhimu.slmp.feed.ui.common.FeedIdemKeeper;
import com.zhimu.slmp.feed.util.FeedCalc;
import com.zhimu.slmp.feed.util.FeedFormat;
import com.zhimu.slmp.feed.util.GrowthCalc;
import com.zhimu.slmp.network.model.ApiResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * G05 称重登记（新增、修改、称重周期）。入参 EXTRA_BATCH_ID 或 FeedExtras.EXTRA_WEIGH_RECORD_ID；都没有时先选批次（API-G18）。
 * 结果卡由 MediatorLiveData 即时派生：平均体重、标准体重插值（改日期时先本地预算，300 ms 防抖后取 API-G19 覆盖）、
 * 偏离与生长预判、日增重；抽样数超过存栏时禁用提交，少于建议值时显示偏少横幅仍可提交。
 * 提交带幂等键（API-G20），超时、40901 后同键重发；新增模式离开时保存草稿（page_code G05，biz_key batch:{batch_id}:{weigh_date}）。
 */
public class WeighRegisterViewModel extends BaseViewModel {

    /** 新增 / 修改 */
    public enum WeighRegisterMode {
        CREATE,
        EDIT
    }

    public static final String PAGE_CODE = "G05";
    public static final String MODE_SAMPLE = WeighRecordVO.MODE_SAMPLE;
    public static final String MODE_INDIVIDUAL = WeighRecordVO.MODE_INDIVIDUAL;
    public static final String F_DATE = "weigh_date";
    public static final String F_SAMPLE = "sample_count";
    public static final String F_TOTAL = "total_weight";
    public static final String F_ITEMS = "items";

    static final BigDecimal MAX_TOTAL = new BigDecimal("999999.9");
    static final BigDecimal MAX_ITEM = new BigDecimal("9999.9");
    static final String MSG_DRAFT = "上次没有提交的称重登记已保存为草稿";

    private final WeighRepository repository;
    private final FarmContext farmContext;
    private final FeedDraftStore drafts;
    private final FeedIdemKeeper idem = new FeedIdemKeeper();

    private final MutableLiveData<WeighRegisterMode> mode = new MutableLiveData<>(WeighRegisterMode.CREATE);
    private final MutableLiveData<List<WeighBatchOptionVO>> batchOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> needBatchPick = new MutableLiveData<>(false);
    private final MutableLiveData<WeighFormVO> form = new MutableLiveData<>();
    private final MutableLiveData<WeighRecordVO> record = new MutableLiveData<>();
    private final MutableLiveData<String> batchCardTitle = new MutableLiveData<>("");
    private final MutableLiveData<String> batchCardText = new MutableLiveData<>("");
    private final MutableLiveData<String> lastWeighText = new MutableLiveData<>("");
    private final MutableLiveData<String> inputMode = new MutableLiveData<>(MODE_SAMPLE);
    private final MutableLiveData<String> weighDate = new MutableLiveData<>();
    private final MutableLiveData<Integer> dayAge = new MutableLiveData<>();
    private final MutableLiveData<Integer> stockCount = new MutableLiveData<>();
    private final MutableLiveData<String> sampleCount = new MutableLiveData<>("");
    private final MutableLiveData<String> totalWeight = new MutableLiveData<>("");
    private final MutableLiveData<List<String>> itemRows = new MutableLiveData<>(Collections.singletonList(""));
    private final MutableLiveData<BigDecimal> avgWeight = new MutableLiveData<>();
    private final MutableLiveData<String> avgText = new MutableLiveData<>(FeedFormat.DASH);
    private final MutableLiveData<String> formulaText = new MutableLiveData<>("");
    private final MutableLiveData<BigDecimal> stdWeight = new MutableLiveData<>();
    private final MutableLiveData<String> stdLabel = new MutableLiveData<>("");
    private final MutableLiveData<String> stdText = new MutableLiveData<>(FeedFormat.DASH);
    private final MutableLiveData<String> deviationText = new MutableLiveData<>(FeedFormat.DASH);
    private final MutableLiveData<Boolean> deviationError = new MutableLiveData<>(false);
    private final MutableLiveData<GrowthPreview> growthPreview = new MutableLiveData<>();
    private final MutableLiveData<String> dailyGainLabel = new MutableLiveData<>("日增重");
    private final MutableLiveData<String> dailyGainText = new MutableLiveData<>(FeedFormat.DASH);
    private final MutableLiveData<String> dailyGainNote = new MutableLiveData<>();
    private final MutableLiveData<Boolean> sampleLow = new MutableLiveData<>(false);
    private final MutableLiveData<String> sampleLowText = new MutableLiveData<>("");
    private final MutableLiveData<String> sampleHelper = new MutableLiveData<>("");
    private final MutableLiveData<String> individualSummary = new MutableLiveData<>("");
    private final MutableLiveData<String> individualError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> canAddItem = new MutableLiveData<>(true);
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<String> blockNotice = new MutableLiveData<>();
    private final MutableLiveData<WeighCycleVO> cycle = new MutableLiveData<>();
    private final MutableLiveData<String> cycleText = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> cycleSuggested = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> cycleEditable = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> cycleSaving = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    private final SingleLiveEvent<Void> showBatchSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showCycleSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showDatePicker = new SingleLiveEvent<>();
    private final SingleLiveEvent<UiMessage> navigateToGrowth = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showDiscardDialog = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> cycleSaved = new SingleLiveEvent<>();

    @Nullable
    private Long batchId;
    @Nullable
    private Long weighRecordId;
    private boolean fromGrowth;
    @Nullable
    private String initialSnapshot;
    @Nullable
    private Draft pendingDraft;
    private boolean finished;
    private boolean resent;
    @Nullable
    private Disposable dateRequest;
    @Nullable
    private String lastSample;
    @Nullable
    private String lastTotal;

    /** 草稿：两种录入方式各自的数据 + 待确认的幂等键 */
    static final class Draft {
        String mode;
        String sampleCount;
        String totalWeight;
        List<String> items;
        String idemKey;
        String idemSnapshot;
    }

    @Inject
    public WeighRegisterViewModel(@NonNull WeighRepository repository, @NonNull FarmContext farmContext,
                                  @NonNull AppDatabase database) {
        this.repository = repository;
        this.farmContext = farmContext;
        this.drafts = new FeedDraftStore(database.formDraftDao());
        canSubmit.setValue(false);
        canSubmit.addSource(inputMode, v -> recompute());
        canSubmit.addSource(sampleCount, v -> recompute());
        canSubmit.addSource(totalWeight, v -> recompute());
        canSubmit.addSource(itemRows, v -> recompute());
        canSubmit.addSource(dayAge, v -> recompute());
        canSubmit.addSource(form, v -> recompute());
        canSubmit.addSource(record, v -> recompute());
        canSubmit.addSource(submitting, v -> recompute());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        idem.attach(handle);
        fromGrowth = FeedExtras.readBoolean(handle.get(FeedExtras.EXTRA_FROM_GROWTH));
        init(FeedExtras.readId(handle.get(RouterPath.EXTRA_BATCH_ID)),
                FeedExtras.readId(handle.get(FeedExtras.EXTRA_WEIGH_RECORD_ID)));
    }

    // ===================== 对外 =====================

    public LiveData<WeighRegisterMode> getMode() {
        return mode;
    }

    public LiveData<List<WeighBatchOptionVO>> getBatchOptions() {
        return batchOptions;
    }

    public LiveData<Boolean> getNeedBatchPick() {
        return needBatchPick;
    }

    public LiveData<WeighFormVO> getForm() {
        return form;
    }

    public LiveData<WeighRecordVO> getRecord() {
        return record;
    }

    public LiveData<String> getBatchCardTitle() {
        return batchCardTitle;
    }

    public LiveData<String> getBatchCardText() {
        return batchCardText;
    }

    public LiveData<String> getLastWeighText() {
        return lastWeighText;
    }

    public MutableLiveData<String> getInputMode() {
        return inputMode;
    }

    public MutableLiveData<String> getWeighDate() {
        return weighDate;
    }

    public LiveData<Integer> getDayAge() {
        return dayAge;
    }

    public LiveData<Integer> getStockCount() {
        return stockCount;
    }

    public MutableLiveData<String> getSampleCount() {
        return sampleCount;
    }

    public MutableLiveData<String> getTotalWeight() {
        return totalWeight;
    }

    public LiveData<List<String>> getItemRows() {
        return itemRows;
    }

    public LiveData<BigDecimal> getAvgWeight() {
        return avgWeight;
    }

    public LiveData<String> getAvgText() {
        return avgText;
    }

    public LiveData<String> getFormulaText() {
        return formulaText;
    }

    public LiveData<BigDecimal> getStdWeight() {
        return stdWeight;
    }

    public LiveData<String> getStdLabel() {
        return stdLabel;
    }

    public LiveData<String> getStdText() {
        return stdText;
    }

    public LiveData<String> getDeviationText() {
        return deviationText;
    }

    public LiveData<Boolean> getDeviationError() {
        return deviationError;
    }

    public LiveData<GrowthPreview> getGrowthPreview() {
        return growthPreview;
    }

    public LiveData<String> getDailyGainLabel() {
        return dailyGainLabel;
    }

    public LiveData<String> getDailyGainText() {
        return dailyGainText;
    }

    public LiveData<String> getDailyGainNote() {
        return dailyGainNote;
    }

    public LiveData<Boolean> getSampleLow() {
        return sampleLow;
    }

    public LiveData<String> getSampleLowText() {
        return sampleLowText;
    }

    public LiveData<String> getSampleHelper() {
        return sampleHelper;
    }

    public LiveData<String> getIndividualSummary() {
        return individualSummary;
    }

    public LiveData<String> getIndividualError() {
        return individualError;
    }

    public LiveData<Boolean> getCanAddItem() {
        return canAddItem;
    }

    public LiveData<Map<String, String>> getFieldErrors() {
        return fieldErrors;
    }

    public LiveData<String> getBlockNotice() {
        return blockNotice;
    }

    public LiveData<WeighCycleVO> getCycle() {
        return cycle;
    }

    public LiveData<String> getCycleText() {
        return cycleText;
    }

    public LiveData<Boolean> getCycleSuggested() {
        return cycleSuggested;
    }

    public LiveData<Boolean> getCycleEditable() {
        return cycleEditable;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public LiveData<Boolean> getCycleSaving() {
        return cycleSaving;
    }

    public SingleLiveEvent<Void> getShowBatchSheet() {
        return showBatchSheet;
    }

    public SingleLiveEvent<Void> getShowCycleSheet() {
        return showCycleSheet;
    }

    public SingleLiveEvent<Void> getShowDatePicker() {
        return showDatePicker;
    }

    /** 提交成功：来源为 G06 时 setResult 后返回，否则打开 G06 后关闭本页；消息为成功提示 */
    public SingleLiveEvent<UiMessage> getNavigateToGrowth() {
        return navigateToGrowth;
    }

    public SingleLiveEvent<Void> getShowDiscardDialog() {
        return showDiscardDialog;
    }

    public SingleLiveEvent<Void> getCycleSaved() {
        return cycleSaved;
    }

    @Nullable
    public Long currentBatchId() {
        return batchId;
    }

    public boolean isFromGrowth() {
        return fromGrowth;
    }

    /** 日期选择范围：入栏日期至今天 */
    @Nullable
    public LocalDate minDate() {
        WeighFormVO f = form.getValue();
        return f == null || f.getBatch() == null ? null : DateFormatter.parseDate(f.getBatch().getEntryDate());
    }

    // ===================== 初始化 =====================

    public void init(@Nullable Long batch, @Nullable Long recordId) {
        this.batchId = batch;
        this.weighRecordId = recordId;
        disposables.add(drafts.deleteExpired().subscribe(() -> {
        }, t -> {
        }));
        if (recordId != null) {
            load(repository.getWeighRecord(recordId).flatMap(r -> repository.getWeighForm(r.getBatchId(),
                    r.getWeighDate()).map(f -> new Object[]{f, r})), pair -> {
                WeighRecordVO r = (WeighRecordVO) pair[1];
                batchId = r.getBatchId();
                applyForm((WeighFormVO) pair[0]);
                applyRecord(r);
            });
        } else if (batch != null) {
            load(formWithRecord(batch, null), this::applyLoaded);
        } else {
            load(repository.getBatchOptions(false), list -> {
                batchOptions.setValue(list);
                needBatchPick.setValue(true);
                batchCardTitle.setValue("选择批次");
                batchCardText.setValue("");
                lastWeighText.setValue("");
                if (list.size() == 1) {
                    selectBatch(list.get(0).getBatchId());
                } else {
                    showBatchSheet.call();
                }
            });
        }
    }

    @NonNull
    private Single<Object[]> formWithRecord(long batch, @Nullable String date) {
        return repository.getWeighForm(batch, date).flatMap(f -> {
            Long existing = f.getExistingRecordId();
            if (existing == null) {
                return Single.just(new Object[]{f, null});
            }
            return repository.getWeighRecord(existing).map(r -> new Object[]{f, r});
        });
    }

    private void applyLoaded(@NonNull Object[] pair) {
        applyForm((WeighFormVO) pair[0]);
        WeighRecordVO r = (WeighRecordVO) pair[1];
        if (r != null) {
            discardDraft();
            applyRecord(r);
        } else {
            mode.setValue(WeighRegisterMode.CREATE);
            record.setValue(null);
            weighRecordId = null;
            initialSnapshot = snapshot();
            recompute();
            if (blockNotice.getValue() == null) {
                checkDraft();
            }
        }
    }

    @Override
    public void retry() {
        init(batchId, weighRecordId);
    }

    /** 未带批次时选择批次（API-G19） */
    public void selectBatch(long id) {
        batchId = id;
        needBatchPick.setValue(false);
        load(formWithRecord(id, null), this::applyLoaded);
    }

    private void applyForm(@NonNull WeighFormVO f) {
        form.setValue(f);
        WeighFormBatchVO b = f.getBatch();
        needBatchPick.setValue(false);
        batchCardTitle.setValue(b == null ? "" : FeedFormat.join(b.getBatchNo(), b.getPenNames()));
        weighDate.setValue(f.getWeighDate() == null ? DateFormatter.todayString() : f.getWeighDate());
        dayAge.setValue(f.getDayAge());
        stockCount.setValue(f.getStockCount());
        lastWeighText.setValue(lastWeighTextOf(f));
        blockNotice.setValue(f.isCanRegister() ? null
                : f.getBlockMessage() == null ? "批次已结束，不能再登记称重。" : f.getBlockMessage());
        cycle.setValue(f.getWeighCycle());
        cycleEditable.setValue(f.isCanEditCycle());
        applyCycle(f.getWeighCycle());
        refreshBatchText();
    }

    private void applyRecord(@NonNull WeighRecordVO r) {
        record.setValue(r);
        weighRecordId = r.getId();
        mode.setValue(WeighRegisterMode.EDIT);
        weighDate.setValue(r.getWeighDate());
        if (r.getDayAge() != null) {
            dayAge.setValue(r.getDayAge());
        }
        stockCount.setValue(r.getStockCount());
        String m = MODE_INDIVIDUAL.equals(r.getMode()) ? MODE_INDIVIDUAL : MODE_SAMPLE;
        inputMode.setValue(m);
        if (MODE_SAMPLE.equals(m)) {
            sampleCount.setValue(r.getSampleCount() == null ? "" : String.valueOf(r.getSampleCount()));
            totalWeight.setValue(r.getTotalWeight() == null ? "" : plain(r.getTotalWeight()));
        } else {
            List<String> rows = new ArrayList<>();
            for (WeighRecordVO.WeighItemVO it : r.getItems()) {
                rows.add(it.getWeight() == null ? "" : plain(it.getWeight()));
            }
            itemRows.setValue(rows.isEmpty() ? Collections.singletonList("") : rows);
        }
        refreshBatchText();
        initialSnapshot = snapshot();
        recompute();
    }

    private void refreshBatchText() {
        WeighFormVO f = form.getValue();
        WeighFormBatchVO b = f == null ? null : f.getBatch();
        Integer age = dayAge.getValue();
        Integer stock = stockCount.getValue();
        batchCardText.setValue(FeedFormat.join(b == null ? null : b.getBreedName(), age == null ? null : age + " 日龄",
                stock == null ? null : "存栏 " + FeedFormat.head(stock)));
        sampleHelper.setValue(stock == null ? "" : "抽样数不能超过存栏 " + FeedFormat.head(stock));
    }

    /** “上次称重 10月5日 · 平均 48.6 千克”；首次称重“首次称重 · 入栏平均 45.0 千克”或“首次称重 · 入栏未填平均体重” */
    @NonNull
    static String lastWeighTextOf(@NonNull WeighFormVO f) {
        WeighFormVO.WeighLastVO last = f.getLastWeigh();
        if (!f.isFirstWeigh() && last != null && last.getDate() != null) {
            return "上次称重 " + FeedFormat.date(last.getDate()) + FeedFormat.DOT + "平均 " + FeedFormat.kg1(last.getAvgWeight());
        }
        BigDecimal entry = f.getBatch() == null ? null : f.getBatch().getEntryAvgWeight();
        return entry == null ? "首次称重" + FeedFormat.DOT + "入栏未填平均体重"
                : "首次称重" + FeedFormat.DOT + "入栏平均 " + FeedFormat.kg1(entry);
    }

    private void applyCycle(@Nullable WeighCycleVO c) {
        cycle.setValue(c);
        if (c == null || c.getPerMonth() == null) {
            cycleText.setValue("");
            cycleSuggested.setValue(false);
            return;
        }
        cycleText.setValue(c.getPerMonth() + " 次 / 月");
        cycleSuggested.setValue(!c.isCustomized());
    }

    // ===================== 输入 =====================

    public void selectInputMode(@NonNull String m) {
        if (!m.equals(inputMode.getValue())) {
            inputMode.setValue(m);
            fieldErrors.setValue(Collections.emptyMap());
        }
    }

    public void addItemRow() {
        List<String> rows = new ArrayList<>(rowsOrEmpty());
        Integer stock = stockLimit();
        if (stock != null && rows.size() >= stock) {
            return;
        }
        rows.add("");
        itemRows.setValue(rows);
    }

    public void removeItemRow(int index) {
        List<String> rows = new ArrayList<>(rowsOrEmpty());
        if (index < 0 || index >= rows.size()) {
            return;
        }
        rows.remove(index);
        if (rows.isEmpty()) {
            rows.add("");
        }
        itemRows.setValue(rows);
        fieldErrors.setValue(Collections.emptyMap());
    }

    public void setItemWeight(int index, @NonNull String text) {
        List<String> rows = new ArrayList<>(rowsOrEmpty());
        if (index < 0 || index >= rows.size() || text.equals(rows.get(index))) {
            return;
        }
        rows.set(index, text);
        itemRows.setValue(rows);
        clearFieldError(itemKey(index));
    }

    @NonNull
    private List<String> rowsOrEmpty() {
        List<String> rows = itemRows.getValue();
        return rows == null ? Collections.emptyList() : rows;
    }

    @NonNull
    static String itemKey(int index) {
        return "items[" + index + "].weight";
    }

    /** 点称重日期（修改模式日期不可改） */
    public void onDateClick() {
        if (mode.getValue() != WeighRegisterMode.EDIT) {
            showDatePicker.call();
        }
    }

    /** 改日期：先按入栏日龄本地预算日龄与标准体重，300 ms 防抖后取 API-G19；所选日期已有记录时切换为修改 */
    public void onDateSelected(@Nullable String date) {
        if (date == null || date.equals(weighDate.getValue()) || batchId == null) {
            return;
        }
        weighDate.setValue(date);
        clearFieldError(F_DATE);
        WeighFormVO f = form.getValue();
        if (f != null && f.getBatch() != null) {
            Integer est = GrowthCalc.estimateDayAge(f.getBatch().getEntryAgeDays(), f.getBatch().getEntryDate(), date);
            if (est != null) {
                dayAge.setValue(est);
            }
        }
        refreshBatchText();
        if (dateRequest != null) {
            dateRequest.dispose();
        }
        long id = batchId;
        dateRequest = launch(Single.timer(300, TimeUnit.MILLISECONDS).flatMap(t -> formWithRecord(id, date)), pair -> {
            WeighFormVO nf = (WeighFormVO) pair[0];
            WeighRecordVO r = (WeighRecordVO) pair[1];
            applyForm(nf);
            if (r != null) {
                applyRecord(r);
            } else {
                recompute();
            }
        });
    }

    // ===================== 即时计算 =====================

    @Nullable
    Integer stockLimit() {
        WeighRecordVO r = record.getValue();
        if (mode.getValue() == WeighRegisterMode.EDIT && r != null && r.getStockCount() != null) {
            return r.getStockCount();
        }
        return stockCount.getValue();
    }

    /** 当前方式的头数与总重；没有有效输入时为 null */
    @Nullable
    private Object[] countAndTotal() {
        if (MODE_INDIVIDUAL.equals(inputMode.getValue())) {
            List<BigDecimal> weights = new ArrayList<>();
            for (String s : rowsOrEmpty()) {
                BigDecimal w = FeedCalc.parse(s);
                if (w != null && w.signum() > 0) {
                    weights.add(w);
                }
            }
            if (weights.isEmpty()) {
                return null;
            }
            return new Object[]{weights.size(), GrowthCalc.sum(weights)};
        }
        Integer count = parseInt(sampleCount.getValue());
        BigDecimal total = FeedCalc.parse(totalWeight.getValue());
        if (count == null || count <= 0 || total == null || total.signum() <= 0) {
            return null;
        }
        return new Object[]{count, total};
    }

    @VisibleForTesting
    void recompute() {
        WeighFormVO f = form.getValue();
        WeighRecordVO r = record.getValue();
        Integer stock = stockLimit();
        Integer age = dayAge.getValue();
        boolean individual = MODE_INDIVIDUAL.equals(inputMode.getValue());
        // 输入改动后清掉对应字段的错误（超过存栏的错误下面重新判断）
        if (!java.util.Objects.equals(lastSample, sampleCount.getValue())) {
            lastSample = sampleCount.getValue();
            clearFieldError(F_SAMPLE);
        }
        if (!java.util.Objects.equals(lastTotal, totalWeight.getValue())) {
            lastTotal = totalWeight.getValue();
            clearFieldError(F_TOTAL);
        }

        // 平均体重与算法说明
        Object[] ct = countAndTotal();
        Integer count = ct == null ? null : (Integer) ct[0];
        BigDecimal total = ct == null ? null : (BigDecimal) ct[1];
        BigDecimal avg = GrowthCalc.average(total, count);
        avgWeight.setValue(avg);
        avgText.setValue(avg == null ? FeedFormat.DASH : FeedFormat.num1(avg));
        if (avg == null) {
            formulaText.setValue("");
        } else if (individual) {
            formulaText.setValue("合计 " + FeedFormat.kg(total) + " ÷ " + FeedFormat.head(count));
        } else {
            formulaText.setValue(FeedFormat.kg(total) + " ÷ " + FeedFormat.head(count));
        }

        // 标准体重（相邻两点插值，超出首末点为无标准）
        BigDecimal std = null;
        boolean stdAvailable = f != null && f.isStdAvailable();
        if (stdAvailable && age != null) {
            std = GrowthCalc.interpolate(f.getStdPoints(), age);
            if (std == null && f.getStdWeightAtDate() != null && age.equals(f.getDayAge())) {
                std = f.getStdWeightAtDate();
            }
        }
        stdWeight.setValue(std);
        stdLabel.setValue(age == null ? "标准体重" : "标准体重（" + age + " 日龄）");
        stdText.setValue(std == null ? FeedFormat.NO_DATA : FeedFormat.kg1(std));
        BigDecimal allowed = r != null && r.getAllowedDeviation() != null ? r.getAllowedDeviation()
                : f == null ? null : f.getAllowedDeviation();
        BigDecimal dev = GrowthCalc.deviation(avg, std);
        boolean out = GrowthCalc.outOfRange(dev, allowed);
        if (std == null) {
            deviationText.setValue(FeedFormat.NO_DATA);
        } else if (dev == null) {
            deviationText.setValue(FeedFormat.DASH);
        } else {
            deviationText.setValue(FeedFormat.signedPercent(dev) + (allowed == null ? ""
                    : "（允许 ±" + FeedFormat.thresholdPercent(allowed) + "%）"));
        }
        deviationError.setValue(out);

        // 生长预判
        String status = GrowthCalc.growthStatus(avg, std, allowed);
        if (avg == null) {
            growthPreview.setValue(null);
        } else {
            String current = f == null ? null : f.getCurrentGrowthStatus();
            growthPreview.setValue(new GrowthPreview(status, GrowthCalc.statusTag(status),
                    GrowthCalc.predictNote(status, current)));
        }

        // 日增重
        String baseDate = null;
        BigDecimal baseWeight = null;
        boolean intake = false;
        if (mode.getValue() == WeighRegisterMode.EDIT && r != null) {
            baseDate = r.getGainBaseDate();
            baseWeight = r.getGainBaseWeight();
            intake = "INTAKE".equals(r.getGainBaseType());
        } else if (f != null) {
            WeighFormVO.WeighLastVO last = f.getLastWeigh();
            if (!f.isFirstWeigh() && last != null && last.getAvgWeight() != null) {
                baseDate = last.getDate();
                baseWeight = last.getAvgWeight();
            } else if (f.getBatch() != null && f.getBatch().getEntryAvgWeight() != null) {
                baseDate = f.getBatch().getEntryDate();
                baseWeight = f.getBatch().getEntryAvgWeight();
                intake = true;
            }
        }
        if (baseDate == null || baseWeight == null) {
            dailyGainLabel.setValue("日增重");
            dailyGainText.setValue(FeedFormat.DASH);
            dailyGainNote.setValue("入栏时没有填写平均体重，首次称重不计算日增重");
        } else {
            dailyGainLabel.setValue("日增重（与" + (intake ? "入栏 " : " ") + FeedFormat.date(baseDate) + " 比）");
            BigDecimal gain = GrowthCalc.dailyGain(avg, baseWeight, weighDate.getValue(), baseDate);
            dailyGainText.setValue(gain == null ? FeedFormat.DASH : FeedFormat.kg2(gain));
            dailyGainNote.setValue(null);
        }

        // 抽样偏少与超过存栏
        Integer suggested = f == null ? null : f.getSuggestedSample();
        if (suggested == null && f != null) {
            suggested = GrowthCalc.suggestedSample(stock, f.getSampleRatio());
        }
        Integer enteredCount = individual ? Integer.valueOf(rowsOrEmpty().size()) : parseInt(sampleCount.getValue());
        boolean low = count != null && suggested != null && count < suggested;
        sampleLow.setValue(low);
        sampleLowText.setValue(suggested == null ? "" : "建议至少抽 " + FeedFormat.head(suggested) + "（存栏的 "
                + FeedFormat.thresholdPercent(f == null ? null : f.getSampleRatio()) + "%），否则平均体重可能不准。仍可以保存。");
        boolean over = stock != null && enteredCount != null && enteredCount > stock;
        String overText = stock == null ? null : (individual ? "录入头数不能超过存栏 " : "不能超过存栏 ")
                + FeedFormat.head(stock);
        if (individual) {
            individualError.setValue(over ? overText : null);
            canAddItem.setValue(stock == null || rowsOrEmpty().size() < stock);
            BigDecimal sum = total == null ? BigDecimal.ZERO : total;
            individualSummary.setValue("已录 " + FeedFormat.head(count == null ? 0 : count) + FeedFormat.DOT + "合计 "
                    + FeedFormat.kg(sum));
        } else {
            individualError.setValue(null);
            if (over) {
                putFieldError(F_SAMPLE, overText);
            } else {
                Map<String, String> m = fieldErrors.getValue();
                if (m != null && overText != null && overText.equals(m.get(F_SAMPLE))) {
                    clearFieldError(F_SAMPLE);
                }
            }
        }

        boolean ready = f != null && blockNotice.getValue() == null && batchId != null;
        canSubmit.setValue(ready && !over && !Boolean.TRUE.equals(submitting.getValue()));
    }

    @Nullable
    private static Integer parseInt(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim().replace(",", "");
        if (s.isEmpty() || !s.matches("^\\d{1,9}$")) {
            return null;
        }
        return Integer.parseInt(s);
    }

    // ===================== 校验与提交 =====================

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        LocalDate d = DateFormatter.parseDate(weighDate.getValue());
        LocalDate min = minDate();
        if (d == null) {
            e.put(F_DATE, "请选择称重日期");
        } else if (d.isAfter(DateFormatter.today())) {
            e.put(F_DATE, "称重日期不能晚于今天");
        } else if (min != null && d.isBefore(min)) {
            e.put(F_DATE, "称重日期不能早于入栏日期 " + FeedFormat.date(DateFormatter.toDateString(min)));
        }
        Integer stock = stockLimit();
        if (MODE_INDIVIDUAL.equals(inputMode.getValue())) {
            List<String> rows = rowsOrEmpty();
            for (int i = 0; i < rows.size(); i++) {
                String s = rows.get(i) == null ? "" : rows.get(i).trim();
                BigDecimal w = FeedCalc.parse(s);
                if (s.isEmpty()) {
                    e.put(itemKey(i), "请填写第 " + (i + 1) + " 头的体重");
                } else if (w == null || w.signum() <= 0) {
                    e.put(itemKey(i), "体重要大于 0 千克");
                } else if (w.stripTrailingZeros().scale() > 1) {
                    e.put(itemKey(i), "体重最多 1 位小数");
                } else if (w.compareTo(MAX_ITEM) > 0) {
                    e.put(itemKey(i), "体重不能超过 9,999.9 千克");
                }
            }
            if (stock != null && rows.size() > stock) {
                e.put(F_ITEMS, "录入头数不能超过存栏 " + FeedFormat.head(stock));
            }
        } else {
            String c = sampleCount.getValue() == null ? "" : sampleCount.getValue().trim();
            Integer count = parseInt(c);
            if (c.isEmpty()) {
                e.put(F_SAMPLE, "请填写抽样数");
            } else if (count == null || count < 1) {
                e.put(F_SAMPLE, "抽样数至少 1 头");
            } else if (stock != null && count > stock) {
                e.put(F_SAMPLE, "不能超过存栏 " + FeedFormat.head(stock));
            }
            String t = totalWeight.getValue() == null ? "" : totalWeight.getValue().trim();
            BigDecimal total = FeedCalc.parse(t);
            if (t.isEmpty()) {
                e.put(F_TOTAL, "请填写总重");
            } else if (total == null || total.signum() <= 0) {
                e.put(F_TOTAL, "总重要大于 0 千克");
            } else if (total.stripTrailingZeros().scale() > 1) {
                e.put(F_TOTAL, "总重最多 1 位小数");
            } else if (total.compareTo(MAX_TOTAL) > 0) {
                e.put(F_TOTAL, "总重不能超过 999,999.9 千克");
            }
        }
        return e;
    }

    /** 只提交当前选中方式的字段 */
    @Nullable
    @VisibleForTesting
    WeighRecordCreateRequest buildCreateRequest() {
        WeighRecordCreateRequest req = new WeighRecordCreateRequest();
        req.weighDate = weighDate.getValue();
        req.mode = MODE_INDIVIDUAL.equals(inputMode.getValue()) ? MODE_INDIVIDUAL : MODE_SAMPLE;
        if (MODE_SAMPLE.equals(req.mode)) {
            req.sampleCount = parseInt(sampleCount.getValue());
            req.totalWeight = normalize(FeedCalc.parse(totalWeight.getValue()));
        } else {
            req.items = itemsRequest();
        }
        return req;
    }

    @NonNull
    private List<WeighItemRequest> itemsRequest() {
        List<WeighItemRequest> items = new ArrayList<>();
        for (String s : rowsOrEmpty()) {
            items.add(new WeighItemRequest(normalize(FeedCalc.parse(s))));
        }
        return items;
    }

    @Nullable
    private static BigDecimal normalize(@Nullable BigDecimal v) {
        if (v == null) {
            return null;
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    /** “提交称重”（API-G20）/“保存修改”（API-G22） */
    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue()) || batchId == null) {
            return;
        }
        Map<String, String> errors = validate();
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            return;
        }
        WeighRecordVO r = record.getValue();
        submitting.setValue(true);
        if (mode.getValue() == WeighRegisterMode.EDIT && r != null) {
            WeighRecordUpdateRequest req = new WeighRecordUpdateRequest();
            WeighRecordCreateRequest c = buildCreateRequest();
            req.mode = c.mode;
            req.sampleCount = c.sampleCount;
            req.totalWeight = c.totalWeight;
            req.items = c.items;
            req.version = r.getVersion();
            launch(repository.updateWeighRecord(r.getId(), req), resp -> onSaved(resp, true), this::onSubmitError);
            return;
        }
        WeighRecordCreateRequest req = buildCreateRequest();
        resent = false;
        send(req, idem.keyFor(req));
    }

    private void send(@NonNull WeighRecordCreateRequest req, @NonNull String key) {
        long id = batchId == null ? 0L : batchId;
        launch(repository.createWeighRecord(id, req, key), resp -> onSaved(resp, false), e -> {
            boolean retryable = FeedErrors.isTimeout(e) || e.getCode() == ErrorCodes.DUPLICATE_SUBMIT;
            String current = idem.current();
            if (retryable && !resent && current != null) {
                // 超时、40901：同键重发一次（40901 等 2 秒）
                resent = true;
                long delay = e.getCode() == ErrorCodes.DUPLICATE_SUBMIT ? 2000 : 0;
                launch(Single.timer(delay, TimeUnit.MILLISECONDS).flatMap(t ->
                                repository.createWeighRecord(id, req, current)), resp -> onSaved(resp, false),
                        this::onSubmitError);
                return;
            }
            onSubmitError(e);
        });
    }

    private void onSaved(@NonNull ApiResponse<WeighRecordVO> resp, boolean edit) {
        submitting.setValue(false);
        finished = true;
        idem.clear();
        discardDraft();
        WeighRecordVO r = resp.getData();
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.notifyTodoChanged(new TodoChange(null, FeedExtras.BIZ_WEIGH_RECORD, r == null ? null : r.getId(),
                    farmContext.farmIdOrZero()));
        }
        String msg = resp.getMessage();
        if (msg == null || msg.isEmpty() || "ok".equalsIgnoreCase(msg)) {
            msg = edit ? "已修改称重记录，生长曲线已重新计算" : successMessage(r == null ? null : r.getGrowthChange());
        }
        navigateToGrowth.setValue(UiMessage.of(msg));
    }

    /** 按 growth_change 的成功提示 */
    @NonNull
    static String successMessage(@Nullable String growthChange) {
        if ("SET_SLOW".equals(growthChange)) {
            return "已登记称重，批次已标为生长偏慢";
        }
        if ("SET_FAST".equals(growthChange)) {
            return "已登记称重，批次已标为生长偏快";
        }
        if ("CLEARED".equals(growthChange)) {
            return "已登记称重，生长偏慢标记已取消";
        }
        return "已登记称重，待办已自动完成";
    }

    private void onSubmitError(@NonNull ApiException e) {
        submitting.setValue(false);
        int code = e.getCode();
        if (e.isNetwork()) {
            showMessage(FeedErrors.MSG_NETWORK_KEEP);
            return;
        }
        switch (code) {
            case ErrorCodes.PEN_FORBIDDEN:
                idem.clear();
                String detail = e.dataString("detail");
                setUiState(UiState.noPermission(detail != null ? detail : e.getMessage(), code));
                return;
            case ErrorCodes.VERSION_CONFLICT:
                showMessage(UiMessage.withAction(FeedErrors.MSG_VERSION, "刷新", FeedErrors.ACTION_REFRESH));
                return;
            case ErrorCodes.STATE_CHANGED:
                idem.clear();
                blockNotice.setValue(e.getMessage());
                discardDraft();
                recompute();
                return;
            case ErrorCodes.ROLE_FORBIDDEN:
            case ErrorCodes.CHANGE_REQUEST_REQUIRED:
            case ErrorCodes.PENDING_EXISTS:
                idem.clear();
                showMessage(e.getMessage());
                return;
            default:
                if (code == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()) {
                    idem.clear();
                    fieldErrors.setValue(new LinkedHashMap<>(e.getFieldErrors()));
                    return;
                }
                idem.clear();
                handleError(e);
        }
    }

    @Override
    protected boolean onFieldErrors(@NonNull Map<String, String> errors) {
        fieldErrors.setValue(new LinkedHashMap<>(errors));
        return true;
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        String detail = e.dataString("detail");
        setUiState(UiState.noPermission(detail != null ? detail : e.getMessage(), e.getCode()));
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
        Map<String, String> m = fieldErrors.getValue();
        if (m != null && message.equals(m.get(field))) {
            return;
        }
        Map<String, String> copy = new LinkedHashMap<>(m == null ? Collections.emptyMap() : m);
        copy.put(field, message);
        fieldErrors.setValue(copy);
    }

    @Nullable
    public String pendingIdemKey() {
        return idem.current();
    }

    // ===================== 称重周期 =====================

    /** 点周期行：所有者、场长打开“称重周期”面板；员工只读 */
    public void onCycleClick() {
        if (Boolean.TRUE.equals(cycleEditable.getValue())) {
            showCycleSheet.call();
        }
    }

    /** 1–8 次 / 月；返回错误文案，通过时为 null */
    @Nullable
    public static String checkCycle(@Nullable Integer perMonth) {
        if (perMonth == null || perMonth < 1 || perMonth > 8) {
            return "称重周期要在 1 到 8 次 / 月之间";
        }
        return null;
    }

    /** API-G23（version 来源见待定事项 1，暂不传） */
    public void saveCycle(int perMonth) {
        updateCycle(WeighCycleRequest.perMonth(perMonth, null));
    }

    public void resetCycle() {
        updateCycle(WeighCycleRequest.reset(null));
    }

    private void updateCycle(@NonNull WeighCycleRequest req) {
        if (batchId == null || Boolean.TRUE.equals(cycleSaving.getValue())) {
            return;
        }
        cycleSaving.setValue(true);
        launch(repository.updateWeighCycle(batchId, req), resp -> {
            cycleSaving.setValue(false);
            applyCycle(resp.getData());
            cycleSaved.call();
            String msg = resp.getMessage();
            showMessage(msg == null || msg.isEmpty() || "ok".equalsIgnoreCase(msg)
                    ? "已修改称重周期，下次称重日期已重新推算" : msg);
        }, e -> {
            cycleSaving.setValue(false);
            showMessage(e.isNetwork() ? ApiException.MSG_NETWORK : e.getMessage());
        });
    }

    // ===================== 返回与草稿 =====================

    @NonNull
    private String snapshot() {
        return inputMode.getValue() + "|" + sampleCount.getValue() + "|" + totalWeight.getValue() + "|"
                + rowsOrEmpty() + "|" + weighDate.getValue();
    }

    public boolean isDirty() {
        return blockNotice.getValue() == null && initialSnapshot != null && !initialSnapshot.equals(snapshot());
    }

    public boolean onBack() {
        if (!finished && isDirty()) {
            showDiscardDialog.call();
            return true;
        }
        return false;
    }

    public void onDiscardConfirmed() {
        finished = true;
        idem.clear();
        discardDraft();
    }

    @Nullable
    private String draftKey() {
        String date = weighDate.getValue();
        return batchId == null || date == null ? null : "batch:" + batchId + ":" + date;
    }

    public void saveDraft() {
        String key = draftKey();
        if (finished || key == null || mode.getValue() != WeighRegisterMode.CREATE || blockNotice.getValue() != null
                || (!isDirty() && idem.current() == null)) {
            return;
        }
        Draft d = new Draft();
        d.mode = inputMode.getValue();
        d.sampleCount = sampleCount.getValue();
        d.totalWeight = totalWeight.getValue();
        d.items = new ArrayList<>(rowsOrEmpty());
        d.idemKey = idem.current();
        d.idemSnapshot = idem.snapshot();
        disposables.add(drafts.save(PAGE_CODE, farmContext.farmIdOrZero(), key, d).subscribe(() -> {
        }, t -> {
        }));
    }

    private void checkDraft() {
        String key = draftKey();
        if (key == null) {
            return;
        }
        launch(drafts.find(PAGE_CODE, farmContext.farmIdOrZero(), key, Draft.class).toSingle(), d -> {
            pendingDraft = d;
            showMessage(UiMessage.withAction(MSG_DRAFT, "恢复", FeedErrors.ACTION_RESTORE_DRAFT));
        }, e -> {
            // 没有草稿
        });
    }

    /** 恢复草稿：再次提交沿用草稿中的幂等键 */
    public void restoreDraft() {
        Draft d = pendingDraft;
        pendingDraft = null;
        if (d == null) {
            return;
        }
        inputMode.setValue(MODE_INDIVIDUAL.equals(d.mode) ? MODE_INDIVIDUAL : MODE_SAMPLE);
        sampleCount.setValue(d.sampleCount == null ? "" : d.sampleCount);
        totalWeight.setValue(d.totalWeight == null ? "" : d.totalWeight);
        itemRows.setValue(d.items == null || d.items.isEmpty() ? Collections.singletonList("") : d.items);
        idem.restore(d.idemKey, d.idemSnapshot);
        recompute();
    }

    public void discardDraft() {
        pendingDraft = null;
        String key = draftKey();
        if (key == null) {
            return;
        }
        disposables.add(drafts.delete(PAGE_CODE, farmContext.farmIdOrZero(), key).subscribe(() -> {
        }, t -> {
        }));
    }

    @NonNull
    private static String plain(@NonNull BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0).toPlainString() : s.toPlainString();
    }

    /** 测试用：当前标准点 */
    @NonNull
    List<StdPointVO> stdPoints() {
        WeighFormVO f = form.getValue();
        return f == null ? Collections.emptyList() : f.getStdPoints();
    }
}
