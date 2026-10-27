package com.zhimu.slmp.breed.ui.farrowing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
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
import com.zhimu.slmp.breed.model.request.BreedingOptionQuery;
import com.zhimu.slmp.breed.model.request.FarrowingRequest;
import com.zhimu.slmp.breed.model.request.FarrowingUpdateRequest;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalOptionVO;
import com.zhimu.slmp.breed.model.vo.CapacityConfirmVO;
import com.zhimu.slmp.breed.model.vo.FarrowingDetailVO;
import com.zhimu.slmp.breed.model.vo.PenOptionVO;
import com.zhimu.slmp.breed.model.vo.RegisterContextVO;
import com.zhimu.slmp.breed.model.vo.RegisterResultVO;
import com.zhimu.slmp.breed.ui.common.BreedFormViewModel;
import com.zhimu.slmp.breed.ui.common.BreedLookup;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.ui.common.FinishEvent;
import com.zhimu.slmp.breed.util.BreedFormRules;
import com.zhimu.slmp.breed.util.BreedTextFormatter;
import com.zhimu.slmp.breed.util.SubmitSupport;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.network.GsonFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * B04 登记产仔（技术文档 2.4.4）：母猪卡（B02、T04、B06 进入时已带入）或妊娠母猪下拉（B01 FAB）；
 * 产仔日期、总产 / 活仔 / 死胎及和校验；“活仔一键转为新批次”与入栏明细、超容量确认（本地预判与 42206）；
 * 提交 API-B17，修改未转批次的产仔 API-B18（初值 API-B19）。
 */
public class FarrowingRegisterViewModel extends BreedFormViewModel {

    public static final String ACTION_OPEN_ARCHIVE = "breed_open_archive";
    public static final String ACTION_CHANGE_REQUEST = "breed_change_request";
    public static final String MSG_ALREADY_FARROWED = "本胎已登记产仔";
    private static final String KEY_DATE = "farrow_date";
    private static final String KEY_TOTAL = "total_born";
    private static final String KEY_LIVE = "live_born";
    private static final String KEY_STILL = "stillborn";
    private static final String KEY_TRANSFER = "transfer_to_batch";
    private static final String KEY_CONFIRMED = "capacity_confirmed";

    private final FarmContext farmContext;
    private final BaseDataRepository baseData;

    private final MutableLiveData<RegisterContextVO> context = new MutableLiveData<>();
    private final MutableLiveData<List<BreedingAnimalOptionVO>> animalOptions =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> optionTotal = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> needPick = new MutableLiveData<>(false);
    private MutableLiveData<String> farrowDate = new MutableLiveData<>(DateFormatter.todayString());
    private MutableLiveData<String> totalBorn = new MutableLiveData<>("");
    private MutableLiveData<String> liveBorn = new MutableLiveData<>("");
    private MutableLiveData<String> stillborn = new MutableLiveData<>("");
    private MutableLiveData<Boolean> transferToBatch = new MutableLiveData<>(true);
    private MutableLiveData<Boolean> capacityConfirmed = new MutableLiveData<>(false);
    private final MutableLiveData<RegisterContextVO.DefaultPen> targetPen = new MutableLiveData<>();
    private final MediatorLiveData<String> entryText = new MediatorLiveData<>();
    private final MediatorLiveData<String> sumError = new MediatorLiveData<>();
    private final MediatorLiveData<CapacityConfirmVO> capacity = new MediatorLiveData<>();
    private final MediatorLiveData<String> capacityText = new MediatorLiveData<>();
    private final MediatorLiveData<String> statusHint = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    private final MutableLiveData<FarrowingDetailVO> editDetail = new MutableLiveData<>();
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final MutableLiveData<List<PenOptionVO>> penOptions = new MutableLiveData<>();

    private final SingleLiveEvent<FinishEvent> finishWithMessage = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openArchive = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openChangeRequest = new SingleLiveEvent<>();
    private final SingleLiveEvent<List<PenOptionVO>> showPenPicker = new SingleLiveEvent<>();

    @Nullable
    private Long animalId;
    @Nullable
    private Long recordId;
    @Nullable
    private Long todoId;
    @Nullable
    private SubmitSupport.IdemKeeper idem;
    /** 42206 返回的容量数据：数量、栏舍或开关改动后改用本地预判 */
    @Nullable
    private CapacityConfirmVO serverCapacity;

    @Inject
    public FarrowingRegisterViewModel(@NonNull BreedRepository repository, @NonNull FarmContext farmContext,
                                      @NonNull BaseDataRepository baseData) {
        super(repository);
        this.farmContext = farmContext;
        this.baseData = baseData;
        statusHint.addSource(context, c -> computeStatusHint());
        capacity.addSource(targetPen, p -> computeCapacity());
        capacityText.addSource(capacity, c -> capacityText.setValue(capacityTextOf(c)));
        canSubmit.addSource(submitting, s -> computeCanSubmit());
        canSubmit.addSource(getUiState(), s -> computeCanSubmit());
        canSubmit.addSource(context, s -> computeCanSubmit());
        canSubmit.addSource(editMode, s -> computeCanSubmit());
        bindFields();
    }

    private void bindFields() {
        statusHint.addSource(liveBorn, v -> computeStatusHint());
        entryText.addSource(liveBorn, v -> computeEntry());
        sumError.addSource(totalBorn, v -> computeSum());
        sumError.addSource(liveBorn, v -> computeSum());
        sumError.addSource(stillborn, v -> computeSum());
        capacity.addSource(liveBorn, v -> computeCapacity());
        capacity.addSource(transferToBatch, v -> computeCapacity());
    }

    private void unbindFields() {
        statusHint.removeSource(liveBorn);
        entryText.removeSource(liveBorn);
        sumError.removeSource(totalBorn);
        sumError.removeSource(liveBorn);
        sumError.removeSource(stillborn);
        capacity.removeSource(liveBorn);
        capacity.removeSource(transferToBatch);
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        unbindFields();
        farrowDate = handle.getLiveData(KEY_DATE, DateFormatter.todayString());
        totalBorn = handle.getLiveData(KEY_TOTAL, "");
        liveBorn = handle.getLiveData(KEY_LIVE, "");
        stillborn = handle.getLiveData(KEY_STILL, "");
        transferToBatch = handle.getLiveData(KEY_TRANSFER, true);
        capacityConfirmed = handle.getLiveData(KEY_CONFIRMED, false);
        bindFields();
        idem = new SubmitSupport.IdemKeeper(handle, "farrowing");
        Long a = readLong(handle.get(RouterPath.EXTRA_ANIMAL_ID));
        if (a == null) {
            a = readLong(handle.get(RouterPath.EXTRA_SOW_ID));
        }
        if (a == null) {
            a = readLong(handle.get(com.zhimu.slmp.common.router.RouterPath.EXTRA_BIZ_ID));
        }
        todoId = readLong(handle.get(com.zhimu.slmp.common.router.RouterPath.EXTRA_TODO_ID));
        init(a, readLong(handle.get(RouterPath.EXTRA_RECORD_ID)));
    }

    /** 带入母猪（B11）或加载妊娠母猪候选（B04）；修改模式加载产仔记录（B19） */
    public void init(@Nullable Long animalId, @Nullable Long recordId) {
        this.animalId = animalId;
        this.recordId = recordId;
        if (idem == null) {
            idem = new SubmitSupport.IdemKeeper(getSavedState(), "farrowing");
        }
        if (farmContext.isStaff()) {
            setUiState(UiState.noPermission(null, ErrorCodes.ROLE_FORBIDDEN));
            return;
        }
        if (recordId != null) {
            editMode.setValue(true);
            load(repository.getFarrowing(recordId), this::applyEdit);
            return;
        }
        if (animalId != null) {
            needPick.setValue(false);
            load(repository.getRegisterContext(animalId, BreedCodes.TYPE_FARROWING), this::applyContext);
        } else {
            needPick.setValue(true);
            load(repository.getOptions(new BreedingOptionQuery(BreedCodes.TYPE_FARROWING, null)), page -> {
                animalOptions.setValue(page.getList());
                optionTotal.setValue(page.getTotal());
            });
        }
    }

    @Override
    public void retry() {
        resetContentLoaded();
        init(animalId, recordId);
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return false;
    }

    private void applyContext(@NonNull RegisterContextVO ctx) {
        if (animalId != null && ctx.getAnimal() != null && ctx.getAnimal().getAnimalId() != animalId) return;
        context.setValue(ctx);
        if (targetPen.getValue() == null) {
            targetPen.setValue(ctx.getDefaultPen());
        }
        if (ctx.getAnimal() != null) {
            animalId = ctx.getAnimal().getAnimalId();
        }
    }

    private void applyEdit(@NonNull FarrowingDetailVO d) {
        editDetail.setValue(d);
        animalId = d.getAnimalId();
        transferToBatch.setValue(false);
        if (!dirty) {
            farrowDate.setValue(d.getFarrowDate());
            totalBorn.setValue(String.valueOf(d.getTotalBorn()));
            liveBorn.setValue(String.valueOf(d.getLiveBorn()));
            stillborn.setValue(String.valueOf(d.getStillborn()));
        }
    }

    /** 下拉选择妊娠母猪（B01 FAB 进入） */
    public void selectAnimal(long id) {
        if (animalId != null && animalId == id && context.getValue() != null) {
            return;
        }
        dirty = true;
        animalId = id;
        targetPen.setValue(null);
        context.setValue(null);
        penOptions.setValue(java.util.Collections.emptyList());
        clearFieldError(BreedFormRules.F_ANIMAL, BreedFormRules.F_FARROW_DATE);
        launch(repository.getRegisterContext(id, BreedCodes.TYPE_FARROWING), ctx -> {
            if (animalId == null || animalId != id) return;
            context.setValue(ctx);
            targetPen.setValue(ctx.getDefaultPen());
        }, e -> {
            if (e.getCode() == ErrorCodes.STATE_CHANGED) {
                showMessage(UiMessage.withAction(e.getMessage(), SubmitSupport.MSG_REFRESH, ACTION_REFRESH));
            } else {
                handleError(e);
            }
        });
    }

    // ===== 字段 =====

    public void onDatePicked(@Nullable String date) {
        if (date != null) {
            dirty = true;
            farrowDate.setValue(date);
            clearFieldError(BreedFormRules.F_FARROW_DATE);
        }
    }

    /** 数量输入（用户编辑时调用） */
    public void onCountEdited(@NonNull String field) {
        dirty = true;
        clearFieldError(field, BreedFormRules.F_LIVE, BreedFormRules.F_STILL, BreedFormRules.F_TRANSFER);
        if (!BreedFormRules.F_TOTAL.equals(field)) {
            clearFieldError(BreedFormRules.F_CAPACITY);
        }
    }

    public void onTransferChanged(boolean on) {
        if (!Boolean.valueOf(on).equals(transferToBatch.getValue())) {
            dirty = true;
            transferToBatch.setValue(on);
            clearFieldError(BreedFormRules.F_TRANSFER, BreedFormRules.F_CAPACITY);
        }
    }

    public void onCapacityChecked(boolean checked) {
        if (!Boolean.valueOf(checked).equals(capacityConfirmed.getValue())) {
            capacityConfirmed.setValue(checked);
            clearFieldError(BreedFormRules.F_CAPACITY);
        }
    }

    /** 点“入栏栏舍”：取 API-L07 栏舍下拉（按后代品种所属品类标出冲突） */
    public void onPickPen() {
        RegisterContextVO ctx = context.getValue();
        if (ctx == null || ctx.getOffspringBreed() == null) return;
        Long breedId = ctx == null || ctx.getOffspringBreed() == null ? null : ctx.getOffspringBreed().getBreedId();
        Long configuredCategory = ctx == null || ctx.getOffspringBreed() == null ? null : ctx.getOffspringBreed().getCategoryId();
        Single<Long> categoryId = configuredCategory != null && configuredCategory > 0 ? Single.just(configuredCategory) : baseData.getCategories()
                .map(list -> {
                    CategoryVO c = BreedLookup.categoryOfBreed(list, breedId, null);
                    return c == null ? -1L : c.getId();
                })
                .onErrorReturnItem(-1L);
        launch(categoryId.flatMap(id -> id > 0 ? repository.getPenOptions(id)
                : Single.error(new IllegalStateException("后代品类加载失败，请刷新后重试"))), list -> {
            if (ctx != context.getValue()) return;
            penOptions.setValue(list);
            showPenPicker.setValue(list);
        });
    }

    public void pickPen(long penId) {
        List<PenOptionVO> list = penOptions.getValue();
        if (list == null) {
            return;
        }
        for (PenOptionVO p : list) {
            if (p.getPenId() == penId && !p.isDisabled()) {
                dirty = true;
                String unit = targetPen.getValue() != null ? targetPen.getValue().getUnit()
                        : BreedTextFormatter.UNIT_HEAD;
                targetPen.setValue(RegisterContextVO.pen(p.getPenId(), p.getPenName(), p.getCapacity(),
                        p.getStockCount(), unit));
                capacityConfirmed.setValue(false);
                clearFieldError(BreedFormRules.F_CAPACITY);
                return;
            }
        }
    }

    private void computeStatusHint() {
        RegisterContextVO c = context.getValue();
        String status = Integer.valueOf(0).equals(SubmitSupport.parseInt(liveBorn.getValue())) ? "空怀" : "哺乳";
        statusHint.setValue(c == null || c.getAnimal() == null ? null
                : "登记后 " + c.getAnimal().getEarTag() + " 转为" + status + "，预产期待办自动完成。");
    }

    private void computeEntry() {
        Integer l = SubmitSupport.parseInt(liveBorn.getValue());
        entryText.setValue(l == null ? "—" : BreedTextFormatter.qty(l, BreedTextFormatter.UNIT_HEAD) + "（按活仔数）");
    }

    private void computeSum() {
        sumError.setValue(BreedFormRules.sumError(totalBorn.getValue(), liveBorn.getValue(), stillborn.getValue()));
    }

    private void computeCapacity() {
        serverCapacity = null;
        RegisterContextVO.DefaultPen pen = targetPen.getValue();
        Integer l = SubmitSupport.parseInt(liveBorn.getValue());
        CapacityConfirmVO c = null;
        if (Boolean.TRUE.equals(transferToBatch.getValue()) && pen != null && l != null && l > 0
                && pen.getCapacity() > 0 && pen.getCurrentCount() + l > pen.getCapacity()) {
            c = CapacityConfirmVO.local(pen.getPenId(), pen.getPenName(), pen.getUnit(), pen.getCapacity(),
                    pen.getCurrentCount(), l);
        }
        CapacityConfirmVO old = capacity.getValue();
        if ((old == null) != (c == null)) {
            capacityConfirmed.setValue(false);
        }
        capacity.setValue(c);
    }

    private void computeCanSubmit() {
        UiState s = getUiState().getValue();
        boolean loaded = s != null && s.is(UiState.Status.CONTENT);
        // 未选母猪时也可点，点击后在母猪框下提示“请选择母猪”
        canSubmit.setValue(loaded && !Boolean.TRUE.equals(submitting.getValue()));
    }

    @Nullable
    static String capacityTextOf(@Nullable CapacityConfirmVO c) {
        if (c == null) {
            return null;
        }
        String unit = c.getUnit() == null ? BreedTextFormatter.UNIT_HEAD : c.getUnit();
        return "入栏后超过栏舍容量。" + BreedTextFormatter.nz(c.getPenName()) + "容量 "
                + BreedTextFormatter.qty(c.getCapacity(), unit) + "，现有 " + BreedTextFormatter.qty(c.getCurrentCount(), unit)
                + "，入栏后共 " + BreedTextFormatter.qty(c.getAfterCount(), unit) + "，超出 "
                + BreedTextFormatter.qty(c.getOverCount(), unit) + "。";
    }

    // ===== 提交 =====

    /** 点“登记产仔”（修改模式为“保存修改”） */
    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        boolean edit = Boolean.TRUE.equals(editMode.getValue());
        boolean transfer = !edit && Boolean.TRUE.equals(transferToBatch.getValue());
        RegisterContextVO ctx = context.getValue();
        Map<String, String> errors = BreedFormRules.farrowing(edit ? recordId : (ctx == null ? null : animalId),
                farrowDate.getValue(), ctx == null ? null : ctx.getFirstMatingDate(), totalBorn.getValue(),
                liveBorn.getValue(), stillborn.getValue(), transfer, capacity.getValue() != null,
                Boolean.TRUE.equals(capacityConfirmed.getValue()));
        if (transfer && (targetPen.getValue() == null || targetPen.getValue().getPenId() <= 0)) {
            errors = new java.util.LinkedHashMap<>(errors);
            errors.put("target_pen_id", "请选择与后代品类匹配的入栏栏舍");
        }
        if (!errors.isEmpty()) {
            setErrors(errors);
            return;
        }
        setErrors(Collections.emptyMap());
        Integer t = SubmitSupport.parseInt(totalBorn.getValue());
        Integer l = SubmitSupport.parseInt(liveBorn.getValue());
        Integer s = SubmitSupport.parseInt(stillborn.getValue());
        beginSubmit();
        if (edit && recordId != null) {
            FarrowingDetailVO d = editDetail.getValue();
            FarrowingUpdateRequest req = new FarrowingUpdateRequest()
                    .setFarrowDate(farrowDate.getValue()).setTotalBorn(t).setLiveBorn(l).setStillborn(s)
                    .setVersion(d == null ? 1 : d.getVersion());
            launch(repository.updateFarrowing(recordId, req), r -> {
                endSubmit();
                dirty = false;
                BreedNav.notifyTodo(null, BreedCodes.BIZ_FARROWING, recordId, farmContext.farmIdOrZero());
                finishWithMessage.setValue(new FinishEvent(SubmitSupport.MSG_SAVED_EDIT, null));
            }, this::onEditError);
            return;
        }
        RegisterContextVO.DefaultPen pen = targetPen.getValue();
        FarrowingRequest req = new FarrowingRequest()
                .setAnimalId(animalId)
                .setAnimalVersion(ctx.getAnimal() == null ? null : ctx.getAnimal().getVersion())
                .setFarrowDate(farrowDate.getValue())
                .setTotalBorn(t).setLiveBorn(l).setStillborn(s)
                .setTransferToBatch(transfer)
                .setTargetPenId(transfer && pen != null ? pen.getPenId() : null)
                .setOffspringBreedId(transfer && ctx.getOffspringBreed() != null ? ctx.getOffspringBreed().getBreedId() : null)
                .setCapacityConfirmed(transfer && capacity.getValue() != null
                        && Boolean.TRUE.equals(capacityConfirmed.getValue()) ? Boolean.TRUE : null);
        String key = idem.keyFor(req);
        launch(repository.createFarrowing(req, key), this::onSaved, this::onCreateError);
    }

    @Override
    protected void retrySubmit() {
        submit();
    }

    private void onSaved(@NonNull RegisterResultVO r) {
        endSubmit();
        dirty = false;
        RegisterContextVO ctx = context.getValue();
        String ear = r.getEarTag() != null ? r.getEarTag()
                : ctx != null && ctx.getAnimal() != null ? ctx.getAnimal().getEarTag() : null;
        BreedNav.notifyTodo(todoId, BreedCodes.BIZ_FARROWING, r.getRecordId(), farmContext.farmIdOrZero());
        finishWithMessage.setValue(new FinishEvent(BreedCodes.EMPTY.equals(r.getAnimalStatus())
                ? "已登记产仔，" + ear + " 转为空怀" : BreedTextFormatter.farrowDone(ear, r.getBatchId() != null),
                r.getBatchId()));
    }

    private void onCreateError(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.CAPACITY_CONFIRM) {
            endSubmit();
            CapacityConfirmVO c = parseCapacity(e);
            if (c != null) {
                serverCapacity = c;
                capacity.setValue(c);
            }
            capacityConfirmed.setValue(false);
            putFieldError(BreedFormRules.F_CAPACITY, BreedFormRules.MSG_CAPACITY);
            scrollToError.setValue(BreedFormRules.F_CAPACITY);
            return;
        }
        if (e.getCode() == ErrorCodes.STATE_CHANGED && e.getMessage() != null
                && e.getMessage().contains(MSG_ALREADY_FARROWED)) {
            endSubmit();
            showMessage(UiMessage.withAction(e.getMessage(), "查看档案", ACTION_OPEN_ARCHIVE));
            return;
        }
        onSubmitError(e, BreedCodes.TYPE_FARROWING, idem, this::onSaved);
    }

    private void onEditError(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.CHANGE_REQUEST_REQUIRED) {
            endSubmit();
            showMessage(UiMessage.withAction(e.getMessage(), "提交修改申请", ACTION_CHANGE_REQUEST));
            return;
        }
        onSubmitError(e, null, null, null);
    }

    @Nullable
    static CapacityConfirmVO parseCapacity(@NonNull ApiException e) {
        if (e.getData() == null || !e.getData().isJsonObject()) {
            return null;
        }
        return GsonFactory.get().fromJson(e.getData(), CapacityConfirmVO.class);
    }

    @Override
    public void onMessageAction(@NonNull String actionId) {
        if (ACTION_OPEN_ARCHIVE.equals(actionId)) {
            if (animalId != null) {
                openArchive.setValue(animalId);
            }
            return;
        }
        if (ACTION_CHANGE_REQUEST.equals(actionId)) {
            if (recordId != null) {
                openChangeRequest.setValue(recordId);
            }
            return;
        }
        super.onMessageAction(actionId);
    }

    @Override
    protected boolean isKnownField(@NonNull String field) {
        return BreedFormRules.F_FARROW_DATE.equals(field) || BreedFormRules.F_TOTAL.equals(field)
                || BreedFormRules.F_LIVE.equals(field) || BreedFormRules.F_STILL.equals(field)
                || BreedFormRules.F_TRANSFER.equals(field) || BreedFormRules.F_ANIMAL.equals(field);
    }

    // ===== 对外 LiveData =====

    public LiveData<RegisterContextVO> getContext() {
        return context;
    }

    public LiveData<List<BreedingAnimalOptionVO>> getAnimalOptions() {
        return animalOptions;
    }

    public LiveData<Integer> getOptionTotal() {
        return optionTotal;
    }

    public LiveData<Boolean> getNeedPick() {
        return needPick;
    }

    public MutableLiveData<String> getFarrowDate() {
        return farrowDate;
    }

    public MutableLiveData<String> getTotalBorn() {
        return totalBorn;
    }

    public MutableLiveData<String> getLiveBorn() {
        return liveBorn;
    }

    public MutableLiveData<String> getStillborn() {
        return stillborn;
    }

    public LiveData<Boolean> getTransferToBatch() {
        return transferToBatch;
    }

    public LiveData<Boolean> getCapacityConfirmed() {
        return capacityConfirmed;
    }

    public LiveData<RegisterContextVO.DefaultPen> getTargetPen() {
        return targetPen;
    }

    public LiveData<String> getEntryText() {
        return entryText;
    }

    public LiveData<String> getSumError() {
        return sumError;
    }

    public LiveData<CapacityConfirmVO> getCapacity() {
        return capacity;
    }

    public LiveData<String> getCapacityText() {
        return capacityText;
    }

    public LiveData<String> getStatusHint() {
        return statusHint;
    }

    public LiveData<Boolean> getEditMode() {
        return editMode;
    }

    public LiveData<FarrowingDetailVO> getEditDetail() {
        return editDetail;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public LiveData<FinishEvent> getFinishWithMessage() {
        return finishWithMessage;
    }

    public LiveData<Long> getOpenArchive() {
        return openArchive;
    }

    public LiveData<Long> getOpenChangeRequest() {
        return openChangeRequest;
    }

    public LiveData<List<PenOptionVO>> getShowPenPicker() {
        return showPenPicker;
    }

    @Nullable
    public Long getAnimalId() {
        return animalId;
    }

    @VisibleForTesting
    @Nullable
    String currentIdemKey() {
        return idem == null ? null : idem.current();
    }

    @Nullable
    static Long readLong(@Nullable Object v) {
        if (v instanceof Long) {
            return (Long) v;
        }
        if (v instanceof Integer) {
            return ((Integer) v).longValue();
        }
        if (v instanceof String) {
            try {
                return Long.parseLong((String) v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
