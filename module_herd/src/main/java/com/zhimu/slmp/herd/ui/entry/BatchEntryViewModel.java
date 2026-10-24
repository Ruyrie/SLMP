package com.zhimu.slmp.herd.ui.entry;

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
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.CategoryBreedVO;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.data.PenRepository;
import com.zhimu.slmp.herd.model.request.BatchCreateRequest;
import com.zhimu.slmp.herd.model.request.EntryUpdateRequest;
import com.zhimu.slmp.herd.model.vo.BatchDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchEntryResultVO;
import com.zhimu.slmp.herd.model.vo.BatchEventDetailVO;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;
import com.zhimu.slmp.herd.model.vo.PenOptionVO;
import com.zhimu.slmp.herd.ui.common.FormSupport;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.herd.util.CapacityCalculator;
import com.zhimu.slmp.network.GsonFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * L08 批次入栏（含修改入栏记录）：栏舍、品种（带出品类与默认用途）、来源、日期、日龄、数量、体重、单价；
 * 本地容量提示（CapacityCalculator）超出时必须勾选确认；服务端 42206 时按 data 重写横幅、清除勾选，勾选后换新幂等键重新提交
 * （capacity_confirmed = true）；提交超时调 API-L26 确认是否已生成批次。
 */
public class BatchEntryViewModel extends BaseViewModel {

    public static final String F_PEN = "pen_id";
    public static final String F_BREED = "breed_id";
    public static final String F_DATE = "entry_date";
    public static final String F_AGE = "entry_age_days";
    public static final String F_COUNT = "entry_count";
    public static final String F_WEIGHT = "entry_avg_weight";
    public static final String F_PRICE = "intro_unit_price";

    public static final String PURPOSE_COMMERCIAL = BatchListItemVO.PURPOSE_COMMERCIAL;
    public static final String PURPOSE_BREEDING = BatchListItemVO.PURPOSE_BREEDING;
    public static final String SOURCE_PURCHASED = BatchDetailVO.SOURCE_PURCHASED;
    public static final String SOURCE_SELF_BRED = BatchDetailVO.SOURCE_SELF_BRED;

    private final BatchRepository batchRepository;
    private final PenRepository penRepository;
    private final BaseDataRepository baseData;
    private final FarmContext farmContext;
    private final Gson gson = GsonFactory.get();

    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    private final MutableLiveData<List<CategoryVO>> breedTree = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<PenOptionVO>> penOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> penId = new MutableLiveData<>();
    private final MutableLiveData<Long> breedId = new MutableLiveData<>();
    private final MutableLiveData<String> purpose = new MutableLiveData<>(PURPOSE_COMMERCIAL);
    private final MutableLiveData<String> source = new MutableLiveData<>(SOURCE_PURCHASED);
    private final MutableLiveData<String> entryDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<String> entryAgeText = new MutableLiveData<>("");
    private final MutableLiveData<String> entryCountText = new MutableLiveData<>("");
    private final MutableLiveData<String> avgWeightText = new MutableLiveData<>("");
    private final MutableLiveData<String> unitPriceText = new MutableLiveData<>("");
    private final MediatorLiveData<String> categoryHelper = new MediatorLiveData<>();
    private final MediatorLiveData<String> unitText = new MediatorLiveData<>();
    private final MediatorLiveData<String> penHelper = new MediatorLiveData<>();
    private final MediatorLiveData<String> capacityBanner = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> capacityChecked = new MutableLiveData<>(false);
    private final MutableLiveData<String> bizBanner = new MutableLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final SingleLiveEvent<BatchEntryResultVO> successEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> finishEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> changeRequestEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> discardEvent = new SingleLiveEvent<>();
    /** 42206 后重算横幅的触发器 */
    private final MutableLiveData<Integer> serverTick = new MutableLiveData<>(0);

    private final FormSupport.IdemKeeper idem = new FormSupport.IdemKeeper();
    @Nullable
    private Long batchId;
    @Nullable
    private Long entryEventId;
    private int batchVersion;
    @Nullable
    private Long originalPenId;
    private int originalQty;
    @Nullable
    private String initialSnapshot;
    /** 42206 返回的容量数据（栏舍或数量改动后失效） */
    @Nullable
    private CapacityCalculator.Result serverCapacity;
    @Nullable
    private String serverCountText;
    @Nullable
    private Long serverPenId;

    /** 初始化数据 */
    private static final class Init {
        final List<CategoryVO> categories;
        final List<PenOptionVO> pens;
        @Nullable
        final BatchEventDetailVO event;

        Init(List<CategoryVO> categories, List<PenOptionVO> pens, @Nullable BatchEventDetailVO event) {
            this.categories = categories;
            this.pens = pens;
            this.event = event;
        }
    }

    @Inject
    public BatchEntryViewModel(@NonNull BatchRepository batchRepository, @NonNull PenRepository penRepository,
                               @NonNull BaseDataRepository baseData, @NonNull FarmContext farmContext) {
        this.batchRepository = batchRepository;
        this.penRepository = penRepository;
        this.baseData = baseData;
        this.farmContext = farmContext;
        categoryHelper.addSource(breedId, v -> categoryHelper.setValue(computeCategoryHelper()));
        categoryHelper.addSource(breedTree, v -> categoryHelper.setValue(computeCategoryHelper()));
        unitText.addSource(breedId, v -> unitText.setValue(currentUnit()));
        unitText.addSource(breedTree, v -> unitText.setValue(currentUnit()));
        penHelper.addSource(penId, v -> penHelper.setValue(computePenHelper()));
        penHelper.addSource(penOptions, v -> penHelper.setValue(computePenHelper()));
        penHelper.addSource(unitText, v -> penHelper.setValue(computePenHelper()));
        capacityBanner.addSource(penId, v -> updateBanner());
        capacityBanner.addSource(penOptions, v -> updateBanner());
        capacityBanner.addSource(entryCountText, v -> updateBanner());
        capacityBanner.addSource(serverTick, v -> updateBanner());
        canSubmit.addSource(capacityBanner, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(capacityChecked, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(submitting, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(penId, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(breedId, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(source, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(entryDate, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(entryAgeText, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(entryCountText, v -> canSubmit.setValue(isSubmittable()));
        canSubmit.addSource(unitPriceText, v -> canSubmit.setValue(isSubmittable()));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        init(asLong(handle.get(RouterPath.EXTRA_BATCH_ID)), asLong(handle.get(RouterPath.EXTRA_ENTRY_EVENT_ID)));
    }

    @Nullable
    private static Long asLong(@Nullable Object o) {
        return o instanceof Number ? ((Number) o).longValue() : null;
    }

    // ===== 对外 =====

    public LiveData<Boolean> getEditMode() {
        return editMode;
    }

    public LiveData<List<CategoryVO>> getBreedTree() {
        return breedTree;
    }

    public LiveData<List<PenOptionVO>> getPenOptions() {
        return penOptions;
    }

    public MutableLiveData<Long> getPenId() {
        return penId;
    }

    public MutableLiveData<Long> getBreedId() {
        return breedId;
    }

    public MutableLiveData<String> getPurpose() {
        return purpose;
    }

    public MutableLiveData<String> getSource() {
        return source;
    }

    public MutableLiveData<String> getEntryDate() {
        return entryDate;
    }

    public MutableLiveData<String> getEntryAgeText() {
        return entryAgeText;
    }

    public MutableLiveData<String> getEntryCountText() {
        return entryCountText;
    }

    public MutableLiveData<String> getAvgWeightText() {
        return avgWeightText;
    }

    public MutableLiveData<String> getUnitPriceText() {
        return unitPriceText;
    }

    public LiveData<String> getCategoryHelper() {
        return categoryHelper;
    }

    public LiveData<String> getUnitText() {
        return unitText;
    }

    public LiveData<String> getPenHelper() {
        return penHelper;
    }

    public LiveData<String> getCapacityBanner() {
        return capacityBanner;
    }

    public MutableLiveData<Boolean> getCapacityChecked() {
        return capacityChecked;
    }

    public LiveData<String> getBizBanner() {
        return bizBanner;
    }

    public LiveData<Map<String, String>> getFieldErrors() {
        return fieldErrors;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public SingleLiveEvent<BatchEntryResultVO> getSuccessEvent() {
        return successEvent;
    }

    public SingleLiveEvent<Void> getFinishEvent() {
        return finishEvent;
    }

    public SingleLiveEvent<Long> getChangeRequestEvent() {
        return changeRequestEvent;
    }

    public SingleLiveEvent<Void> getDiscardEvent() {
        return discardEvent;
    }

    // ===== 初始化 =====

    /** API-U09 品类品种、API-L07 栏舍下拉；编辑时 API-L19 入栏记录带入原值 */
    public void init(@Nullable Long batchId, @Nullable Long entryEventId) {
        this.batchId = batchId;
        this.entryEventId = entryEventId;
        boolean edit = batchId != null && entryEventId != null;
        editMode.setValue(edit);
        Single<List<CategoryVO>> categories = baseData.getCategories().map(this::filterBySpecies);
        Single<Init> source;
        if (!edit) {
            source = Single.zip(categories, penRepository.getPenOptions(null, null), (c, p) -> new Init(c, p, null));
        } else {
            source = Single.zip(categories, batchRepository.getEventDetail(entryEventId),
                    (c, e) -> new Init(c, Collections.emptyList(), e))
                    .flatMap(init -> penRepository.getPenOptions(categoryIdOfBreed(init.categories,
                                    init.event == null || init.event.getEntry() == null ? null
                                            : init.event.getEntry().getBreedId()), null)
                            .map(p -> new Init(init.categories, p, init.event)));
        }
        load(source, this::applyInit);
    }

    private void applyInit(@NonNull Init init) {
        breedTree.setValue(init.categories);
        penOptions.setValue(init.pens);
        BatchEventDetailVO e = init.event;
        if (e != null) {
            BatchEventDetailVO.EntryInfo info = e.getEntry();
            if (e.getPen() != null) {
                penId.setValue(e.getPen().getPenId());
                originalPenId = e.getPen().getPenId();
            }
            originalQty = e.getQuantity();
            entryDate.setValue(e.getEventDate());
            entryCountText.setValue(String.valueOf(e.getQuantity()));
            if (info != null) {
                breedId.setValue(info.getBreedId());
                purpose.setValue(info.getPurpose() != null ? info.getPurpose() : PURPOSE_COMMERCIAL);
                source.setValue(info.getSource() != null ? info.getSource() : SOURCE_PURCHASED);
                entryAgeText.setValue(String.valueOf(info.getEntryAgeDays()));
                avgWeightText.setValue(plain(info.getEntryAvgWeight()));
                unitPriceText.setValue(plain(info.getIntroUnitPrice()));
                batchVersion = info.getBatchVersion();
            }
        }
        initialSnapshot = snapshot();
    }

    @Override
    public void retry() {
        init(batchId, entryEventId);
    }

    /** 只保留与当前养殖场同畜种的品类（FarmContextVO.species_text 为空时不过滤） */
    @NonNull
    private List<CategoryVO> filterBySpecies(@NonNull List<CategoryVO> all) {
        FarmContextVO farm = farmContext.current();
        String species = farm == null ? null : farm.getSpeciesText();
        if (species == null || species.isEmpty()) {
            return all;
        }
        List<CategoryVO> out = new ArrayList<>();
        for (CategoryVO c : all) {
            if (species.equals(c.getSpeciesText())) {
                out.add(c);
            }
        }
        return out.isEmpty() ? all : out;
    }

    // ===== 选择 =====

    /** 选品种：带出品类，按品类设置重新选中默认用途，带 category_id 重取 API-L07 */
    public void onBreedSelected(long id) {
        breedId.setValue(id);
        clearFieldError(F_BREED);
        CategoryVO c = categoryOf(id);
        purpose.setValue(c != null && c.isBreeding() ? PURPOSE_BREEDING : PURPOSE_COMMERCIAL);
        Long categoryId = c == null ? null : c.getId();
        launch(penRepository.getPenOptions(categoryId, null), list -> {
            penOptions.setValue(list);
            PenOptionVO selected = selectedPen();
            if (selected != null && selected.isDisabled()) {
                penId.setValue(null);
            }
        });
    }

    public void onPenSelected(long id) {
        penId.setValue(id);
        bizBanner.setValue(null);
        clearFieldError(F_PEN);
    }

    /** 来源：选“自繁”时清空并隐藏引种单价 */
    public void onSourceChanged(@NonNull String value) {
        source.setValue(value);
        if (SOURCE_SELF_BRED.equals(value)) {
            unitPriceText.setValue("");
            clearFieldError(F_PRICE);
        }
    }

    public void onPurposeChanged(@NonNull String value) {
        purpose.setValue(value);
    }

    public void onDateSelected(@Nullable String date) {
        if (date != null) {
            entryDate.setValue(date);
            clearFieldError(F_DATE);
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

    // ===== 派生 =====

    @Nullable
    private CategoryVO categoryOf(@Nullable Long breed) {
        return breed == null ? null : findCategory(breedTree.getValue(), breed);
    }

    @Nullable
    private static CategoryVO findCategory(@Nullable List<CategoryVO> tree, long breed) {
        if (tree == null) {
            return null;
        }
        for (CategoryVO c : tree) {
            if (c.getBreeds() == null) {
                continue;
            }
            for (CategoryBreedVO b : c.getBreeds()) {
                if (b.getId() == breed) {
                    return c;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Long categoryIdOfBreed(@NonNull List<CategoryVO> tree, @Nullable Long breed) {
        if (breed == null) {
            return null;
        }
        CategoryVO c = findCategory(tree, breed);
        return c == null ? null : c.getId();
    }

    @Nullable
    private String computeCategoryHelper() {
        CategoryVO c = categoryOf(breedId.getValue());
        return c == null ? null : "品类：" + c.getName();
    }

    @NonNull
    String currentUnit() {
        CategoryVO c = categoryOf(breedId.getValue());
        if (c != null) {
            return c.getCountUnitText() != null ? c.getCountUnitText() : HerdFormat.unitOfCode(c.getCountUnit());
        }
        FarmContextVO farm = farmContext.current();
        return HerdFormat.unitOfCode(farm == null ? null : farm.getCountUnit());
    }

    @Nullable
    PenOptionVO selectedPen() {
        Long id = penId.getValue();
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

    @Nullable
    private String computePenHelper() {
        PenOptionVO p = selectedPen();
        return p == null ? null : HerdFormat.stockOfCapacity(p.getStockCount(), p.getCapacity(), currentUnit());
    }

    /** 超容量横幅正文（null 为未超出）：42206 数据优先，否则本地计算 */
    @Nullable
    String computeBanner() {
        PenOptionVO pen = selectedPen();
        if (pen == null) {
            return null;
        }
        String unit = currentUnit();
        if (serverCapacity != null && Objects.equals(serverPenId, pen.getPenId())
                && Objects.equals(serverCountText, entryCountText.getValue())) {
            return CapacityCalculator.bannerText(pen.getPenName(), serverCapacity, unit, "入栏", null);
        }
        Integer qty = FormSupport.parseInt(entryCountText.getValue());
        if (qty == null || qty <= 0) {
            return null;
        }
        int original = Objects.equals(originalPenId, pen.getPenId()) ? originalQty : 0;
        CapacityCalculator.Result r = CapacityCalculator.calc(pen.getStockCount(), pen.getCapacity(), qty, original);
        return CapacityCalculator.bannerText(pen.getPenName(), r, unit, "入栏", null);
    }

    private void updateBanner() {
        String old = capacityBanner.getValue();
        String now = computeBanner();
        capacityBanner.setValue(now);
        if (now == null && old != null) {
            // 改为不超出后横幅收起，勾选状态清除；再次超出需重新勾选
            capacityChecked.setValue(false);
        }
    }

    /** 必填项都有值，且超容量时已勾选确认 */
    boolean isSubmittable() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return false;
        }
        if (penId.getValue() == null || breedId.getValue() == null || source.getValue() == null
                || HerdFormat.isEmpty(entryDate.getValue()) || HerdFormat.isEmpty(entryAgeText.getValue())
                || HerdFormat.isEmpty(entryCountText.getValue())) {
            return false;
        }
        if (SOURCE_PURCHASED.equals(source.getValue()) && HerdFormat.isEmpty(unitPriceText.getValue())) {
            return false;
        }
        return computeBanner() == null || Boolean.TRUE.equals(capacityChecked.getValue());
    }

    // ===== 校验与提交 =====

    @NonNull
    Map<String, String> validate() {
        Map<String, String> e = new LinkedHashMap<>();
        if (penId.getValue() == null) {
            e.put(F_PEN, "请选择栏舍");
        }
        if (breedId.getValue() == null) {
            e.put(F_BREED, "请选择品种");
        }
        LocalDate date = DateFormatter.parseDate(entryDate.getValue());
        if (date == null || date.isAfter(DateFormatter.today())) {
            e.put(F_DATE, "入栏日期不能晚于今天");
        }
        String age = trim(entryAgeText.getValue());
        Integer ageV = FormSupport.parseInt(age);
        if (age.isEmpty()) {
            e.put(F_AGE, "请填写入栏日龄");
        } else if (ageV == null || ageV < 0 || ageV > 3650) {
            e.put(F_AGE, "入栏日龄需为 0 或正整数");
        }
        String count = trim(entryCountText.getValue());
        Integer countV = FormSupport.parseInt(count);
        if (count.isEmpty()) {
            e.put(F_COUNT, "请填写数量");
        } else if (countV == null || countV < 1 || countV > 999_999) {
            e.put(F_COUNT, "数量需为大于 0 的整数");
        }
        String weight = trim(avgWeightText.getValue());
        if (!weight.isEmpty()) {
            BigDecimal w = FormSupport.parseDecimal(weight);
            if (w == null || w.signum() <= 0 || w.compareTo(new BigDecimal("9999.99")) > 0
                    || w.stripTrailingZeros().scale() > 2) {
                e.put(F_WEIGHT, "请输入大于 0 的数字，最多保留 2 位小数");
            }
        }
        if (SOURCE_PURCHASED.equals(source.getValue())) {
            String price = trim(unitPriceText.getValue());
            BigDecimal p = FormSupport.parseDecimal(price);
            if (price.isEmpty()) {
                e.put(F_PRICE, "请填写引种单价");
            } else if (p == null || p.signum() < 0 || p.compareTo(new BigDecimal("999999.99")) > 0
                    || p.stripTrailingZeros().scale() > 2) {
                e.put(F_PRICE, "引种单价最多保留 2 位小数");
            }
        }
        return e;
    }

    /** API-L18 新增（Idempotency-Key）/ API-L20 修改入栏记录 */
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
        boolean edit = Boolean.TRUE.equals(editMode.getValue()) && batchId != null;
        BatchCreateRequest req = edit ? new EntryUpdateRequest() : new BatchCreateRequest();
        req.penId = Objects.requireNonNull(penId.getValue());
        req.breedId = Objects.requireNonNull(breedId.getValue());
        req.purpose = purpose.getValue() == null ? PURPOSE_COMMERCIAL : purpose.getValue();
        req.source = source.getValue();
        req.entryDate = entryDate.getValue();
        req.entryAgeDays = Objects.requireNonNull(FormSupport.parseInt(entryAgeText.getValue()));
        req.entryCount = Objects.requireNonNull(FormSupport.parseInt(entryCountText.getValue()));
        req.entryAvgWeight = FormSupport.parseDecimal(avgWeightText.getValue());
        req.introUnitPrice = SOURCE_PURCHASED.equals(req.source) ? FormSupport.parseDecimal(unitPriceText.getValue())
                : null;
        req.capacityConfirmed = banner != null && confirmed ? Boolean.TRUE : null;
        bizBanner.setValue(null);
        submitting.setValue(true);
        if (edit) {
            ((EntryUpdateRequest) req).version = batchVersion;
            launch(batchRepository.updateEntry(batchId, (EntryUpdateRequest) req), vo -> {
                submitting.setValue(false);
                notifyTodo(vo.getBatchId());
                finishEvent.call();
            }, this::onSubmitError);
        } else {
            launch(batchRepository.createBatch(req, idem.keyFor(req)), this::onCreated, this::onSubmitError);
        }
    }

    private void onCreated(@NonNull BatchEntryResultVO result) {
        submitting.setValue(false);
        notifyTodo(result.getBatchId());
        successEvent.setValue(result);
    }

    private void onSubmitError(@NonNull ApiException e) {
        int code = e.getCode();
        if (code == ErrorCodes.CAPACITY_CONFIRM) {
            // 42206：用 data 的 capacity、current、after、over 重写横幅并清除勾选，勾选后换新键重提
            submitting.setValue(false);
            serverCapacity = new CapacityCalculator.Result(dataInt(e, "capacity"), dataInt(e, "current"),
                    dataInt(e, "after"), dataInt(e, "over"));
            serverPenId = penId.getValue();
            serverCountText = entryCountText.getValue();
            capacityChecked.setValue(false);
            Integer tick = serverTick.getValue();
            serverTick.setValue(tick == null ? 1 : tick + 1);
            return;
        }
        if (code == ErrorCodes.PEN_DISABLED || code == ErrorCodes.PEN_CATEGORY_CONFLICT) {
            submitting.setValue(false);
            bizBanner.setValue(e.getMessage());
            return;
        }
        if (code == ErrorCodes.CHANGE_REQUEST_REQUIRED) {
            submitting.setValue(false);
            bizBanner.setValue(e.getMessage());
            changeRequestEvent.setValue(entryEventId);
            return;
        }
        if (!Boolean.TRUE.equals(editMode.getValue()) && FormSupport.isTimeout(e)) {
            // 超时：按钮保持加载，先确认批次是否已生成
            checkByRequest();
            return;
        }
        submitting.setValue(false);
        if (e.isNetwork()) {
            showMessage(e.keptMessage(FormSupport.MSG_NETWORK_KEEP));
            return;
        }
        handleError(e);
    }

    /** API-L26：exists 为真按成功处理（解析为 BatchEntryResultVO 进入 L09），否则“提交没有成功，请重试” */
    public void checkByRequest() {
        String key = idem.current();
        if (key == null) {
            submitting.setValue(false);
            return;
        }
        submitting.setValue(true);
        launch(batchRepository.checkByRequest(key), vo -> {
            if (vo.isExists() && vo.getResult() != null) {
                onCreated(gson.fromJson(vo.getResult(), BatchEntryResultVO.class));
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

    private void notifyTodo(long id) {
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.notifyTodoChanged(new TodoChange(null, HerdExtras.BIZ_BATCH_ENTRY, id, farmContext.farmIdOrZero()));
        }
    }

    /** 有改动时返回：“放弃这次登记？”；返回 true 表示已拦截 */
    public boolean onBackPressed() {
        if (initialSnapshot != null && !initialSnapshot.equals(snapshot())) {
            discardEvent.call();
            return true;
        }
        return false;
    }

    @NonNull
    private String snapshot() {
        return penId.getValue() + "|" + breedId.getValue() + "|" + purpose.getValue() + "|" + source.getValue() + "|"
                + entryDate.getValue() + "|" + trim(entryAgeText.getValue()) + "|" + trim(entryCountText.getValue())
                + "|" + trim(avgWeightText.getValue()) + "|" + trim(unitPriceText.getValue());
    }

    private static int dataInt(@NonNull ApiException e, @NonNull String key) {
        Integer v = FormSupport.parseInt(e.dataString(key));
        return v == null ? 0 : v;
    }

    @NonNull
    private static String plain(@Nullable BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    @NonNull
    private static String trim(@Nullable String s) {
        return s == null ? "" : s.trim();
    }
}
