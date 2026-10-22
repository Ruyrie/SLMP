package com.zhimu.slmp.breed.ui.incubation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.data.BreedRepository;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.request.CandlingRequest;
import com.zhimu.slmp.breed.model.request.HatchRequest;
import com.zhimu.slmp.breed.model.request.IncubationRequest;
import com.zhimu.slmp.breed.model.request.IncubationUpdateRequest;
import com.zhimu.slmp.breed.model.vo.CapacityConfirmVO;
import com.zhimu.slmp.breed.model.vo.FlockOptionVO;
import com.zhimu.slmp.breed.model.vo.IncubationDetailVO;
import com.zhimu.slmp.breed.model.vo.PenOptionVO;
import com.zhimu.slmp.breed.model.vo.RegisterResultVO;
import com.zhimu.slmp.breed.ui.common.BreedFormViewModel;
import com.zhimu.slmp.breed.ui.common.BreedLookup;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.ui.common.NavRequest;
import com.zhimu.slmp.breed.util.BreedFormRules;
import com.zhimu.slmp.breed.util.BreedTextFormatter;
import com.zhimu.slmp.breed.util.SubmitSupport;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.CategoryBreedVO;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.network.GsonFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * B07 禽类孵化（技术文档 2.4.8，Activity 范围，四个步骤 Fragment 共享）：入孵（API-L06 种禽群、API-B26）→
 * 照蛋（API-B30，受精率实时显示）→ 出雏（API-B31，弱雏 = 出雏 − 健雏，孵化率 = 出雏 ÷ 入孵，健雏转为新批次与入栏鸡舍）。
 * 入孵、出雏成功后重取 API-B28；照蛋直接用返回的详情。更多菜单：修改入孵 / 照蛋记录（API-B29）、查看修改记录；
 * 健雏已转为新批次后改为提交修改申请（需审核）。
 */
public class IncubationViewModel extends BreedFormViewModel {

    /** currentStep 取值：编辑入孵 / 照蛋记录 */
    public static final int STEP_EDIT = 4;
    public static final String MENU_EDIT = "EDIT";
    public static final String MENU_LOG = "LOG";
    public static final String MENU_CHANGE = "CHANGE";
    public static final String MSG_FLOCK_ENDED = "该种禽批次已结束，请重新选择";

    private final FarmContext farmContext;
    private final BaseDataRepository baseData;

    private final MutableLiveData<IncubationDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentStep = new MutableLiveData<>(1);
    private final MediatorLiveData<List<StepItem>> stepItems = new MediatorLiveData<>();
    private final MutableLiveData<List<FlockOptionVO>> flockOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> selectedFlockId = new MutableLiveData<>();
    private final MutableLiveData<List<CategoryVO>> categories = new MutableLiveData<>();
    private final MutableLiveData<String> setDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<String> setCount = new MutableLiveData<>("");
    private final MediatorLiveData<String> expectedHatchPreview = new MediatorLiveData<>();
    private final MediatorLiveData<String> hatchCardNote = new MediatorLiveData<>();
    private final MutableLiveData<String> candleDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<String> fertileCount = new MutableLiveData<>("");
    private final MediatorLiveData<String> fertilityText = new MediatorLiveData<>();
    private final MutableLiveData<String> hatchDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<String> hatchedCount = new MutableLiveData<>("");
    private final MutableLiveData<String> healthyCount = new MutableLiveData<>("");
    private final MediatorLiveData<String> weakCount = new MediatorLiveData<>();
    private final MediatorLiveData<String> hatchRateText = new MediatorLiveData<>();
    private final MediatorLiveData<String> hatchedHelper = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> transferToBatch = new MutableLiveData<>(true);
    private final MutableLiveData<Long> targetPenId = new MutableLiveData<>();
    private final MutableLiveData<List<PenOptionVO>> penOptions = new MutableLiveData<>(Collections.emptyList());
    private final MediatorLiveData<String> transferDesc = new MediatorLiveData<>();
    private final MediatorLiveData<CapacityConfirmVO> capacity = new MediatorLiveData<>();
    private final MediatorLiveData<String> capacityText = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> capacityConfirmed = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    private final SingleLiveEvent<Integer> showStepDetail = new SingleLiveEvent<>();
    private final SingleLiveEvent<List<String>> showMoreMenu = new SingleLiveEvent<>();
    private final SingleLiveEvent<NavRequest> navigate = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> reloadFlocks = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> registered = new SingleLiveEvent<>();

    @Nullable
    private Long incubationId;
    @Nullable
    private SubmitSupport.IdemKeeper setIdem;
    @Nullable
    private SubmitSupport.IdemKeeper candleIdem;
    @Nullable
    private SubmitSupport.IdemKeeper hatchIdem;

    @Inject
    public IncubationViewModel(@NonNull BreedRepository repository, @NonNull FarmContext farmContext,
                               @NonNull BaseDataRepository baseData) {
        super(repository);
        this.farmContext = farmContext;
        this.baseData = baseData;
        stepItems.addSource(detail, d -> stepItems.setValue(buildSteps(d)));
        stepItems.addSource(currentStep, s -> stepItems.setValue(buildSteps(detail.getValue())));
        expectedHatchPreview.addSource(selectedFlockId, v -> computePreview());
        expectedHatchPreview.addSource(setDate, v -> computePreview());
        expectedHatchPreview.addSource(categories, v -> computePreview());
        hatchCardNote.addSource(selectedFlockId, v -> computeNote());
        hatchCardNote.addSource(categories, v -> computeNote());
        fertilityText.addSource(detail, v -> computeFertility());
        fertilityText.addSource(fertileCount, v -> computeFertility());
        fertilityText.addSource(currentStep, v -> computeFertility());
        weakCount.addSource(hatchedCount, v -> computeWeak());
        weakCount.addSource(healthyCount, v -> computeWeak());
        hatchRateText.addSource(hatchedCount, v -> computeRate());
        hatchRateText.addSource(detail, v -> computeRate());
        hatchedHelper.addSource(hatchRateText, v -> computeHatchedHelper());
        hatchedHelper.addSource(detail, v -> computeHatchedHelper());
        transferDesc.addSource(healthyCount, v -> computeTransferDesc());
        capacity.addSource(healthyCount, v -> computeCapacity());
        capacity.addSource(targetPenId, v -> computeCapacity());
        capacity.addSource(transferToBatch, v -> computeCapacity());
        capacity.addSource(penOptions, v -> computeCapacity());
        capacityText.addSource(capacity, c -> capacityText.setValue(capacityTextOf(c)));
        canSubmit.addSource(submitting, v -> computeCanSubmit());
        canSubmit.addSource(getUiState(), v -> computeCanSubmit());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        setIdem = new SubmitSupport.IdemKeeper(handle, "inc_set");
        candleIdem = new SubmitSupport.IdemKeeper(handle, "inc_candle");
        hatchIdem = new SubmitSupport.IdemKeeper(handle, "inc_hatch");
        Object id = handle.get(BreedExtras.EXTRA_INCUBATION_ID);
        init(id instanceof Long && (Long) id > 0 ? (Long) id : null);
    }

    private void ensureIdem() {
        if (setIdem == null) {
            SavedStateHandle h = getSavedState();
            setIdem = new SubmitSupport.IdemKeeper(h, "inc_set");
            candleIdem = new SubmitSupport.IdemKeeper(h, "inc_candle");
            hatchIdem = new SubmitSupport.IdemKeeper(h, "inc_hatch");
        }
    }

    /** 有孵化记录时取 API-B28；新建时取种禽群（API-L06） */
    public void init(@Nullable Long id) {
        ensureIdem();
        incubationId = id;
        if (farmContext.isStaff()) {
            setUiState(UiState.noPermission(null, ErrorCodes.ROLE_FORBIDDEN));
            return;
        }
        loadCategories();
        if (id == null) {
            currentStep.setValue(1);
            load(repository.getFlocks(), flockOptions::setValue);
        } else {
            load(repository.getIncubation(id), this::applyDetail);
        }
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return false;
    }

    @Override
    public void retry() {
        resetContentLoaded();
        init(incubationId);
    }

    private void loadCategories() {
        launch(baseData.getCategories(), categories::setValue, e -> {
            // 孵化期取不到时推算卡显示“提交后按品种孵化期推算”
        });
    }

    private void applyDetail(@NonNull IncubationDetailVO d) {
        detail.setValue(d);
        incubationId = d.getIncubationId();
        editMode.setValue(false);
        currentStep.setValue(d.getCurrentStep());
        if (d.getCurrentStep() == 3) {
            loadPens(d.getBreedName());
        }
    }

    private void loadPens(@Nullable String breedName) {
        List<CategoryVO> list = categories.getValue();
        if (list != null) {
            loadPensWith(list, breedName);
            return;
        }
        launch(baseData.getCategories(), cats -> {
            categories.setValue(cats);
            loadPensWith(cats, breedName);
        }, e -> loadPensWith(Collections.emptyList(), breedName));
    }

    private void loadPensWith(@NonNull List<CategoryVO> cats, @Nullable String breedName) {
        CategoryVO c = BreedLookup.categoryOfBreed(cats, null, breedName);
        launch(repository.getPenOptions(c == null ? null : c.getId()), penOptions::setValue);
    }

    // ===== 步骤条 =====

    @NonNull
    List<StepItem> buildSteps(@Nullable IncubationDetailVO d) {
        List<StepItem> list = new ArrayList<>();
        Integer step = currentStep.getValue();
        int cur = step == null ? 1 : step;
        if (d == null) {
            list.add(new StepItem(1, "入孵", StepItem.CURRENT, "待登记", null));
            list.add(new StepItem(2, "照蛋", StepItem.PENDING, null, null));
            list.add(new StepItem(3, "出雏", StepItem.PENDING, null, null));
            return list;
        }
        list.add(new StepItem(1, "入孵", StepItem.DONE, BreedTextFormatter.md(d.getSetDate()),
                BreedTextFormatter.qty(d.getSetCount(), BreedTextFormatter.UNIT_EGG)));
        if (d.getCandleDate() != null) {
            list.add(new StepItem(2, "照蛋", StepItem.DONE, BreedTextFormatter.md(d.getCandleDate()),
                    "受精蛋 " + BreedTextFormatter.qty(d.getFertileCount(), BreedTextFormatter.UNIT_EGG)));
        } else {
            list.add(new StepItem(2, "照蛋", cur == 2 ? StepItem.CURRENT : StepItem.PENDING,
                    cur == 2 ? "待登记" : null, null));
        }
        if (d.getHatchDate() != null) {
            list.add(new StepItem(3, "出雏", StepItem.DONE, BreedTextFormatter.md(d.getHatchDate()),
                    "出雏 " + BreedTextFormatter.qty(d.getHatchedCount(), BreedTextFormatter.UNIT_BIRD)));
        } else {
            String expected = d.getDaysToHatch() == 0 ? "预计今天" : "预计 " + BreedTextFormatter.md(d.getExpectedHatchDate());
            boolean current = d.getCandleDate() != null;
            list.add(new StepItem(3, "出雏", current ? StepItem.CURRENT : StepItem.PENDING, expected,
                    current ? "待登记" : null));
        }
        return list;
    }

    /** 点已完成的节点：只读显示该步登记内容 */
    public void openStep(int step) {
        List<StepItem> items = stepItems.getValue();
        if (items != null && step >= 1 && step <= items.size() && items.get(step - 1).getState() == StepItem.DONE) {
            showStepDetail.setValue(step);
        }
    }

    // ===== 推算与派生 =====

    @Nullable
    private FlockOptionVO selectedFlock() {
        Long id = selectedFlockId.getValue();
        List<FlockOptionVO> list = flockOptions.getValue();
        if (id == null || list == null) {
            return null;
        }
        for (FlockOptionVO f : list) {
            if (f.getBatchId() == id) {
                return f;
            }
        }
        return null;
    }

    /** 孵化期：按种禽群品种在品类品种字典中查找（待定事项 3，无数据时为 null） */
    @Nullable
    Integer incubationDays() {
        FlockOptionVO f = selectedFlock();
        if (f == null) {
            return null;
        }
        CategoryBreedVO b = BreedLookup.breed(categories.getValue(), f.getBreedId(), null);
        return b == null ? null : b.getIncubationDays();
    }

    private void computePreview() {
        LocalDate d = BreedFormRules.expectedHatchDate(setDate.getValue(), incubationDays());
        expectedHatchPreview.setValue(d == null ? null : DateFormatter.toDateString(d));
    }

    private void computeNote() {
        FlockOptionVO f = selectedFlock();
        Integer days = incubationDays();
        hatchCardNote.setValue(f == null ? null : days == null ? "提交后按品种孵化期推算"
                : "按" + BreedTextFormatter.nz(f.getBreedName()) + "孵化期 " + days + " 天推算");
    }

    private void computeFertility() {
        IncubationDetailVO d = detail.getValue();
        if (d == null) {
            fertilityText.setValue(null);
            return;
        }
        if (d.getFertileCount() != null && d.getCandleDate() != null) {
            BigDecimal rate = d.getFertilityRate() != null ? d.getFertilityRate()
                    : BreedTextFormatter.ratio(d.getFertileCount(), d.getSetCount());
            fertilityText.setValue(BreedTextFormatter.percent(rate) + "（" + BreedTextFormatter.num(d.getFertileCount())
                    + " ÷ " + BreedTextFormatter.num(d.getSetCount()) + "）");
            return;
        }
        Integer f = SubmitSupport.parseInt(fertileCount.getValue());
        fertilityText.setValue(f == null ? "—" : BreedTextFormatter.fertilityText(f, d.getSetCount()));
    }

    private void computeWeak() {
        Integer h = SubmitSupport.parseInt(hatchedCount.getValue());
        Integer g = SubmitSupport.parseInt(healthyCount.getValue());
        weakCount.setValue(h == null || g == null || g > h ? "" : BreedTextFormatter.num(h - g));
    }

    private void computeRate() {
        IncubationDetailVO d = detail.getValue();
        Integer h = SubmitSupport.parseInt(hatchedCount.getValue());
        hatchRateText.setValue(d == null || h == null ? null
                : BreedTextFormatter.percent(BreedTextFormatter.ratio(h, d.getSetCount())));
    }

    private void computeHatchedHelper() {
        IncubationDetailVO d = detail.getValue();
        if (d == null || d.getFertileCount() == null) {
            hatchedHelper.setValue(null);
            return;
        }
        String limit = "不能多于受精蛋数 " + BreedTextFormatter.qty(d.getFertileCount(), BreedTextFormatter.UNIT_EGG);
        String rate = hatchRateText.getValue();
        hatchedHelper.setValue(rate == null || rate.isEmpty() ? limit : limit + BreedTextFormatter.DOT + "孵化率 " + rate);
    }

    private void computeTransferDesc() {
        Integer g = SubmitSupport.parseInt(healthyCount.getValue());
        transferDesc.setValue((g == null ? "—" : BreedTextFormatter.qty(g, BreedTextFormatter.UNIT_BIRD))
                + BreedTextFormatter.DOT + "入栏日龄 0" + BreedTextFormatter.DOT + "来源自繁");
    }

    @Nullable
    private PenOptionVO selectedPen() {
        Long id = targetPenId.getValue();
        List<PenOptionVO> list = penOptions.getValue();
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

    private void computeCapacity() {
        PenOptionVO p = selectedPen();
        Integer g = SubmitSupport.parseInt(healthyCount.getValue());
        CapacityConfirmVO c = null;
        if (Boolean.TRUE.equals(transferToBatch.getValue()) && p != null && g != null && g > 0 && p.getCapacity() > 0
                && p.getStockCount() + g > p.getCapacity()) {
            c = CapacityConfirmVO.local(p.getPenId(), p.getPenName(), BreedTextFormatter.UNIT_BIRD, p.getCapacity(),
                    p.getStockCount(), g);
        }
        if ((capacity.getValue() == null) != (c == null)) {
            capacityConfirmed.setValue(false);
        }
        capacity.setValue(c);
    }

    @Nullable
    static String capacityTextOf(@Nullable CapacityConfirmVO c) {
        if (c == null) {
            return null;
        }
        String unit = c.getUnit() == null ? BreedTextFormatter.UNIT_BIRD : c.getUnit();
        return "入栏后超过栏舍容量。" + BreedTextFormatter.nz(c.getPenName()) + "容量 "
                + BreedTextFormatter.qty(c.getCapacity(), unit) + "，现有 " + BreedTextFormatter.qty(c.getCurrentCount(), unit)
                + "，入栏后共 " + BreedTextFormatter.qty(c.getAfterCount(), unit) + "，超出 "
                + BreedTextFormatter.qty(c.getOverCount(), unit) + "。";
    }

    private void computeCanSubmit() {
        UiState s = getUiState().getValue();
        canSubmit.setValue(s != null && s.is(UiState.Status.CONTENT) && !Boolean.TRUE.equals(submitting.getValue()));
    }

    // ===== 字段 =====

    public void onFlockPicked(long id) {
        dirty = true;
        selectedFlockId.setValue(id);
        clearFieldError(BreedFormRules.F_FLOCK);
    }

    public void onPenPicked(long id) {
        dirty = true;
        targetPenId.setValue(id);
        clearFieldError(BreedFormRules.F_TARGET_PEN, BreedFormRules.F_CAPACITY);
    }

    public void onTransferChanged(boolean on) {
        if (!Boolean.valueOf(on).equals(transferToBatch.getValue())) {
            dirty = true;
            transferToBatch.setValue(on);
            clearFieldError(BreedFormRules.F_TRANSFER, BreedFormRules.F_TARGET_PEN, BreedFormRules.F_CAPACITY);
        }
    }

    public void onCapacityChecked(boolean checked) {
        if (!Boolean.valueOf(checked).equals(capacityConfirmed.getValue())) {
            capacityConfirmed.setValue(checked);
            clearFieldError(BreedFormRules.F_CAPACITY);
        }
    }

    /** 日期选择结果：field 为 set_date / candle_date / hatch_date */
    public void onDatePicked(@NonNull String field, @Nullable String date) {
        if (date == null) {
            return;
        }
        dirty = true;
        if (BreedFormRules.F_SET_DATE.equals(field)) {
            setDate.setValue(date);
        } else if (BreedFormRules.F_CANDLE_DATE.equals(field)) {
            candleDate.setValue(date);
        } else {
            hatchDate.setValue(date);
        }
        clearFieldError(field);
    }

    public void onFieldEdited(@NonNull String field) {
        dirty = true;
        clearFieldError(field);
        if (BreedFormRules.F_HATCHED.equals(field)) {
            clearFieldError(BreedFormRules.F_HEALTHY);
        }
    }

    /** 日期选择器下限 */
    @Nullable
    public LocalDate minDate(@NonNull String field) {
        IncubationDetailVO d = detail.getValue();
        if (d == null) {
            return null;
        }
        if (BreedFormRules.F_CANDLE_DATE.equals(field)) {
            return DateFormatter.parseDate(d.getSetDate());
        }
        if (BreedFormRules.F_HATCH_DATE.equals(field)) {
            return DateFormatter.parseDate(d.getCandleDate());
        }
        return null;
    }

    // ===== 提交 =====

    /** 底部主按钮：按当前步骤提交 */
    public void submit() {
        Integer step = currentStep.getValue();
        if (step == null || Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        switch (step) {
            case 1:
                submitSet();
                break;
            case 2:
                submitCandle();
                break;
            case 3:
                submitHatch();
                break;
            case STEP_EDIT:
                submitEdit();
                break;
            default:
                break;
        }
    }

    @Override
    protected void retrySubmit() {
        submit();
    }

    /** 第 1 步“登记入孵”（API-B26），成功后重取 API-B28 */
    public void submitSet() {
        ensureIdem();
        Map<String, String> errors = BreedFormRules.incubationSet(selectedFlockId.getValue(), setDate.getValue(),
                setCount.getValue());
        if (!errors.isEmpty()) {
            setErrors(errors);
            return;
        }
        setErrors(Collections.emptyMap());
        IncubationRequest req = new IncubationRequest().setBreedingBatchId(selectedFlockId.getValue())
                .setSetDate(setDate.getValue()).setSetCount(SubmitSupport.parseInt(setCount.getValue()));
        beginSubmit();
        launch(repository.createIncubation(req, setIdem.keyFor(req)), this::onSetSaved, e -> {
            if (e.getCode() == ErrorCodes.STATE_CHANGED) {
                endSubmit();
                showMessage(e.getMessage() != null ? e.getMessage() : MSG_FLOCK_ENDED);
                selectedFlockId.setValue(null);
                reloadFlocks.call();
                launch(repository.getFlocks(), flockOptions::setValue);
                return;
            }
            onSubmitError(e, BreedCodes.TYPE_INCUBATION, setIdem, this::onSetSaved);
        });
    }

    private void onSetSaved(@NonNull RegisterResultVO r) {
        Long id = r.getIncubationId() != null ? r.getIncubationId() : r.getRecordId();
        String msg = BreedTextFormatter.setDone(r.getExpectedHatchDate());
        BreedNav.notifyTodo(null, BreedCodes.BIZ_INCUBATION, id, farmContext.farmIdOrZero());
        reloadAfter(id, UiMessage.of(msg));
    }

    /** 第 2 步“登记照蛋”（API-B30），直接用返回的详情 */
    public void submitCandle() {
        ensureIdem();
        IncubationDetailVO d = detail.getValue();
        if (d == null) {
            return;
        }
        Map<String, String> errors = BreedFormRules.candling(candleDate.getValue(), d.getSetDate(),
                fertileCount.getValue(), d.getSetCount());
        if (!errors.isEmpty()) {
            setErrors(errors);
            return;
        }
        setErrors(Collections.emptyMap());
        CandlingRequest req = new CandlingRequest().setCandleDate(candleDate.getValue())
                .setFertileCount(SubmitSupport.parseInt(fertileCount.getValue())).setVersion(d.getVersion());
        beginSubmit();
        launch(repository.candle(d.getIncubationId(), req, candleIdem.keyFor(req)), vo -> {
            endSubmit();
            dirty = false;
            applyDetail(vo);
            registered.call();
            BreedNav.notifyTodo(null, BreedCodes.BIZ_INCUBATION, vo.getIncubationId(), farmContext.farmIdOrZero());
            String rate = vo.getFertileCount() == null ? "" : "，受精率 "
                    + BreedTextFormatter.percent(vo.getFertilityRate() != null ? vo.getFertilityRate()
                    : BreedTextFormatter.ratio(vo.getFertileCount(), vo.getSetCount()));
            showMessage("已登记照蛋" + rate);
        }, e -> onSubmitError(e, null, null, null));
    }

    /** 第 3 步“登记出雏”（API-B31），成功后重取 API-B28 */
    public void submitHatch() {
        ensureIdem();
        IncubationDetailVO d = detail.getValue();
        if (d == null) {
            return;
        }
        boolean transfer = Boolean.TRUE.equals(transferToBatch.getValue());
        int fertile = d.getFertileCount() == null ? d.getSetCount() : d.getFertileCount();
        Map<String, String> errors = BreedFormRules.hatch(hatchDate.getValue(), d.getCandleDate(), hatchedCount.getValue(),
                healthyCount.getValue(), fertile, transfer, targetPenId.getValue(), capacity.getValue() != null,
                Boolean.TRUE.equals(capacityConfirmed.getValue()));
        if (!errors.isEmpty()) {
            setErrors(errors);
            return;
        }
        setErrors(Collections.emptyMap());
        HatchRequest req = new HatchRequest().setHatchDate(hatchDate.getValue())
                .setHatchedCount(SubmitSupport.parseInt(hatchedCount.getValue()))
                .setHealthyCount(SubmitSupport.parseInt(healthyCount.getValue()))
                .setTransferToBatch(transfer)
                .setTargetPenId(transfer ? targetPenId.getValue() : null)
                .setCapacityConfirmed(transfer && capacity.getValue() != null
                        && Boolean.TRUE.equals(capacityConfirmed.getValue()) ? Boolean.TRUE : null)
                .setVersion(d.getVersion());
        beginSubmit();
        launch(repository.hatch(d.getIncubationId(), req, hatchIdem.keyFor(req)), this::onHatchSaved, e -> {
            if (e.getCode() == ErrorCodes.CAPACITY_CONFIRM) {
                endSubmit();
                if (e.getData() != null && e.getData().isJsonObject()) {
                    capacity.setValue(GsonFactory.get().fromJson(e.getData(), CapacityConfirmVO.class));
                }
                capacityConfirmed.setValue(false);
                putFieldError(BreedFormRules.F_CAPACITY, BreedFormRules.MSG_CAPACITY);
                scrollToError.setValue(BreedFormRules.F_CAPACITY);
                return;
            }
            onSubmitError(e, BreedCodes.TYPE_HATCH, hatchIdem, this::onHatchSaved);
        });
    }

    private void onHatchSaved(@NonNull RegisterResultVO r) {
        IncubationDetailVO d = detail.getValue();
        Long id = r.getIncubationId() != null ? r.getIncubationId() : d == null ? null : d.getIncubationId();
        String text = BreedTextFormatter.hatchDone(r.getBatchId() != null);
        UiMessage msg = r.getBatchId() != null ? UiMessage.withAction(text, "查看批次",
                BreedNav.ACTION_VIEW_BATCH + r.getBatchId()) : UiMessage.of(text);
        BreedNav.notifyTodo(null, BreedCodes.BIZ_INCUBATION, id, farmContext.farmIdOrZero());
        reloadAfter(id, msg);
    }

    private void reloadAfter(@Nullable Long id, @NonNull UiMessage msg) {
        dirty = false;
        registered.call();
        if (id == null) {
            endSubmit();
            showMessage(msg);
            return;
        }
        launch(repository.getIncubation(id), vo -> {
            endSubmit();
            applyDetail(vo);
            showMessage(msg);
        }, e -> {
            endSubmit();
            incubationId = id;
            showMessage(msg);
        });
    }

    // ===== 更多菜单与修改 =====

    public void onMoreClick() {
        IncubationDetailVO d = detail.getValue();
        if (d == null) {
            return;
        }
        List<String> menu = new ArrayList<>();
        if (d.isCanEdit() && d.getHatchDate() == null) {
            menu.add(MENU_EDIT);
        }
        menu.add(MENU_LOG);
        if (d.isCanRequestChange()) {
            menu.add(MENU_CHANGE);
        }
        showMoreMenu.setValue(menu);
    }

    public void onMenu(@NonNull String item) {
        IncubationDetailVO d = detail.getValue();
        if (d == null) {
            return;
        }
        switch (item) {
            case MENU_EDIT:
                setDate.setValue(d.getSetDate());
                setCount.setValue(String.valueOf(d.getSetCount()));
                candleDate.setValue(d.getCandleDate() == null ? DateFormatter.todayString() : d.getCandleDate());
                fertileCount.setValue(d.getFertileCount() == null ? "" : String.valueOf(d.getFertileCount()));
                setErrors(Collections.emptyMap());
                editMode.setValue(true);
                currentStep.setValue(STEP_EDIT);
                break;
            case MENU_LOG:
                navigate.setValue(NavRequest.of(BreedCodes.BIZ_INCUBATION).put(RouterPath.EXTRA_BIZ_ID,
                        d.getIncubationId()));
                break;
            case MENU_CHANGE:
                if (d.getChangeState() != null && d.getChangeState().getPendingRequestId() != null) {
                    showMessage("这条记录已有修改申请在审核中");
                    return;
                }
                navigate.setValue(NavRequest.of(RouterPath.U01).put(RouterPath.EXTRA_BIZ_TYPE, BreedCodes.BIZ_INCUBATION)
                        .put(RouterPath.EXTRA_BIZ_ID, d.getIncubationId()));
                break;
            default:
                break;
        }
    }

    /** 取消修改：回到当前步骤 */
    public boolean cancelEdit() {
        if (!Boolean.TRUE.equals(editMode.getValue())) {
            return false;
        }
        IncubationDetailVO d = detail.getValue();
        editMode.setValue(false);
        setErrors(Collections.emptyMap());
        currentStep.setValue(d == null ? 1 : d.getCurrentStep());
        return true;
    }

    /** 修改入孵 / 照蛋记录（API-B29） */
    public void submitEdit() {
        IncubationDetailVO d = detail.getValue();
        if (d == null) {
            return;
        }
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        Map<String, String> setErr = BreedFormRules.incubationSet(d.getBreedingBatchId(), setDate.getValue(),
                setCount.getValue());
        errors.putAll(setErr);
        Integer count = SubmitSupport.parseInt(setCount.getValue());
        boolean candled = d.getCandleDate() != null;
        if (candled) {
            errors.putAll(BreedFormRules.candling(candleDate.getValue(), setDate.getValue(), fertileCount.getValue(),
                    count == null ? d.getSetCount() : count));
        }
        if (!errors.isEmpty()) {
            setErrors(errors);
            return;
        }
        setErrors(Collections.emptyMap());
        IncubationUpdateRequest req = new IncubationUpdateRequest().setSetDate(setDate.getValue()).setSetCount(count)
                .setCandleDate(candled ? candleDate.getValue() : null)
                .setFertileCount(candled ? SubmitSupport.parseInt(fertileCount.getValue()) : null)
                .setVersion(d.getVersion());
        beginSubmit();
        launch(repository.updateIncubation(d.getIncubationId(), req), vo -> {
            endSubmit();
            dirty = false;
            applyDetail(vo);
            registered.call();
            BreedNav.notifyTodo(null, BreedCodes.BIZ_INCUBATION, vo.getIncubationId(), farmContext.farmIdOrZero());
            showMessage(SubmitSupport.MSG_SAVED_EDIT);
        }, e -> {
            if (e.getCode() == ErrorCodes.CHANGE_REQUEST_REQUIRED) {
                endSubmit();
                showMessage(UiMessage.withAction(e.getMessage(), "提交修改申请", ACTION_CHANGE));
                return;
            }
            onSubmitError(e, null, null, null);
        });
    }

    public static final String ACTION_CHANGE = "breed_inc_change";

    @Override
    public void onMessageAction(@NonNull String actionId) {
        if (ACTION_CHANGE.equals(actionId)) {
            onMenu(MENU_CHANGE);
            return;
        }
        super.onMessageAction(actionId);
    }

    @Override
    protected boolean isKnownField(@NonNull String field) {
        return !"version".equals(field);
    }

    /** 来源行：“9月28日入孵 · 1 号种鸡舍 · ZM2603-015”；新建时为空 */
    @Nullable
    public static String sourceText(@Nullable IncubationDetailVO d) {
        if (d == null) {
            return null;
        }
        return BreedTextFormatter.md(d.getSetDate()) + "入孵" + BreedTextFormatter.DOT + BreedTextFormatter.nz(d.getFlockText());
    }

    @Nullable
    public String farmName() {
        IncubationDetailVO d = detail.getValue();
        if (d != null && d.getFarmName() != null) {
            return d.getFarmName();
        }
        FarmContextVO c = farmContext.current();
        return c == null ? null : c.getName();
    }

    // ===== 对外 LiveData =====

    public LiveData<IncubationDetailVO> getDetail() {
        return detail;
    }

    public LiveData<Integer> getCurrentStep() {
        return currentStep;
    }

    public LiveData<List<StepItem>> getStepItems() {
        return stepItems;
    }

    public LiveData<List<FlockOptionVO>> getFlockOptions() {
        return flockOptions;
    }

    public LiveData<Long> getSelectedFlockId() {
        return selectedFlockId;
    }

    public MutableLiveData<String> getSetDate() {
        return setDate;
    }

    public MutableLiveData<String> getSetCount() {
        return setCount;
    }

    public LiveData<String> getExpectedHatchPreview() {
        return expectedHatchPreview;
    }

    public LiveData<String> getHatchCardNote() {
        return hatchCardNote;
    }

    public MutableLiveData<String> getCandleDate() {
        return candleDate;
    }

    public MutableLiveData<String> getFertileCount() {
        return fertileCount;
    }

    public LiveData<String> getFertilityText() {
        return fertilityText;
    }

    public MutableLiveData<String> getHatchDate() {
        return hatchDate;
    }

    public MutableLiveData<String> getHatchedCount() {
        return hatchedCount;
    }

    public MutableLiveData<String> getHealthyCount() {
        return healthyCount;
    }

    public LiveData<String> getWeakCount() {
        return weakCount;
    }

    public LiveData<String> getHatchRateText() {
        return hatchRateText;
    }

    public LiveData<String> getHatchedHelper() {
        return hatchedHelper;
    }

    public LiveData<Boolean> getTransferToBatch() {
        return transferToBatch;
    }

    public LiveData<Long> getTargetPenId() {
        return targetPenId;
    }

    public LiveData<List<PenOptionVO>> getPenOptions() {
        return penOptions;
    }

    public LiveData<String> getTransferDesc() {
        return transferDesc;
    }

    public LiveData<CapacityConfirmVO> getCapacity() {
        return capacity;
    }

    public LiveData<String> getCapacityText() {
        return capacityText;
    }

    public LiveData<Boolean> getCapacityConfirmed() {
        return capacityConfirmed;
    }

    public LiveData<Boolean> getEditMode() {
        return editMode;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public LiveData<Integer> getShowStepDetail() {
        return showStepDetail;
    }

    public LiveData<List<String>> getShowMoreMenu() {
        return showMoreMenu;
    }

    /** action 为 RouterPath 常量；为 INCUBATION 时打开本组件的修改记录页 */
    public LiveData<NavRequest> getNavigate() {
        return navigate;
    }

    public LiveData<Void> getReloadFlocks() {
        return reloadFlocks;
    }

    /** 有登记成功：关闭页面时 setResult，上一页刷新 */
    public LiveData<Void> getRegistered() {
        return registered;
    }
}
