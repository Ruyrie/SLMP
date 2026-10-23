package com.zhimu.slmp.breed.ui.weaning;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.data.BreedRepository;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.request.BreedingOptionQuery;
import com.zhimu.slmp.breed.model.request.WeaningRequest;
import com.zhimu.slmp.breed.model.request.WeaningUpdateRequest;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalDetailVO;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalOptionVO;
import com.zhimu.slmp.breed.model.vo.BreedingRecordVO;
import com.zhimu.slmp.breed.model.vo.RegisterContextVO;
import com.zhimu.slmp.breed.model.vo.RegisterResultVO;
import com.zhimu.slmp.breed.ui.common.BreedFormViewModel;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.util.BreedFormRules;
import com.zhimu.slmp.breed.util.BreedTextFormatter;
import com.zhimu.slmp.breed.util.SubmitSupport;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.network.GsonFactory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * B05 登记断奶（技术文档 2.4.5）：哺乳母猪下拉（API-B04 WEANING）、产仔信息卡（API-B11 WEANING）、断奶日期、
 * 断奶仔猪数（不多于产仔时活仔）、断奶窝重（选填）、状态预览；提交 API-B20，修改 API-B21（初值取 B06 传入的记录）。
 */
public class WeaningRegisterViewModel extends BreedFormViewModel {

    private static final String KEY_DATE = "wean_date";
    private static final String KEY_COUNT = "weaned_count";
    private static final String KEY_WEIGHT = "litter_weight";

    private final FarmContext farmContext;

    private final MutableLiveData<List<BreedingAnimalOptionVO>> animalOptions =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> optionTotal = new MutableLiveData<>(0);
    private final MutableLiveData<BreedingAnimalOptionVO> selectedAnimal = new MutableLiveData<>();
    private final MutableLiveData<RegisterContextVO> context = new MutableLiveData<>();
    private MutableLiveData<String> weanDate = new MutableLiveData<>(DateFormatter.todayString());
    private MutableLiveData<String> weanedCount = new MutableLiveData<>("");
    private MutableLiveData<String> litterWeight = new MutableLiveData<>("");
    private final MediatorLiveData<String> weanedHint = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> weanedOver = new MediatorLiveData<>();
    private final MediatorLiveData<String> statusPreview = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    private final MutableLiveData<String> editAnimalLabel = new MutableLiveData<>();
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final SingleLiveEvent<String> finishWithMessage = new SingleLiveEvent<>();

    @Nullable
    private Long presetAnimalId;
    @Nullable
    private Long recordId;
    @Nullable
    private BreedingRecordVO editRecord;
    @Nullable
    private SubmitSupport.IdemKeeper idem;

    @Inject
    public WeaningRegisterViewModel(@NonNull BreedRepository repository, @NonNull FarmContext farmContext) {
        super(repository);
        this.farmContext = farmContext;
        weanedHint.addSource(context, c -> computeHint());
        weanedOver.addSource(context, c -> computeOver());
        statusPreview.addSource(selectedAnimal, a -> statusPreview.setValue(a == null ? null
                : a.getEarTag() + " 回到空怀，出现在配种选择中，可以再次配种。"));
        canSubmit.addSource(selectedAnimal, a -> computeCanSubmit());
        canSubmit.addSource(context, a -> computeCanSubmit());
        canSubmit.addSource(submitting, a -> computeCanSubmit());
        canSubmit.addSource(getUiState(), a -> computeCanSubmit());
        canSubmit.addSource(editMode, a -> computeCanSubmit());
        weanedOver.addSource(weanedCount, v -> computeOver());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        weanedOver.removeSource(weanedCount);
        weanDate = handle.getLiveData(KEY_DATE, DateFormatter.todayString());
        weanedCount = handle.getLiveData(KEY_COUNT, "");
        litterWeight = handle.getLiveData(KEY_WEIGHT, "");
        weanedOver.addSource(weanedCount, v -> computeOver());
        idem = new SubmitSupport.IdemKeeper(handle, "weaning");
        String json = handle.get(BreedExtras.EXTRA_RECORD_JSON);
        if (json != null) {
            editRecord = GsonFactory.get().fromJson(json, BreedingRecordVO.class);
        }
        Object a = handle.get(RouterPath.EXTRA_ANIMAL_ID);
        Object r = handle.get(RouterPath.EXTRA_RECORD_ID);
        init(a instanceof Long ? (Long) a : null, r instanceof Long ? (Long) r : null);
    }

    public void init(@Nullable Long animalId, @Nullable Long editRecordId) {
        presetAnimalId = animalId;
        recordId = editRecordId;
        if (farmContext.isStaff()) {
            setUiState(UiState.noPermission(null, ErrorCodes.ROLE_FORBIDDEN));
            return;
        }
        if (editRecordId != null) {
            editMode.setValue(true);
            if (editRecord != null && !dirty) {
                weanDate.setValue(editRecord.getRecordDate());
                weanedCount.setValue(editRecord.getWeanedCount() == null ? ""
                        : String.valueOf(editRecord.getWeanedCount()));
                litterWeight.setValue(editRecord.getLitterWeight() == null ? ""
                        : editRecord.getLitterWeight().toPlainString());
            }
            if (animalId == null) {
                setUiState(UiState.error(SubmitSupport.MSG_NOT_FOUND, null, ErrorCodes.NOT_FOUND));
                return;
            }
            load(repository.getAnimal(animalId), this::applyEditAnimal);
            return;
        }
        load(repository.getOptions(new BreedingOptionQuery(BreedCodes.TYPE_WEANING, null)), page -> {
            animalOptions.setValue(page.getList());
            optionTotal.setValue(page.getTotal());
            if (presetAnimalId != null && selectedAnimal.getValue() == null) {
                selectAnimalInternal(presetAnimalId);
            }
        });
    }

    @Override
    protected boolean isEmptyData(@Nullable Object data) {
        return false;
    }

    @Override
    public void retry() {
        resetContentLoaded();
        init(presetAnimalId, recordId);
    }

    private void applyEditAnimal(@NonNull BreedingAnimalDetailVO d) {
        editAnimalLabel.setValue(BreedTextFormatter.animalLabel(d.getEarTag(), d.getBreedName(), d.getParity()));
    }

    /** 下拉切换母猪：信息卡、上限与预览刷新，已填的断奶仔猪数保留并重新校验 */
    public void selectAnimal(long animalId) {
        BreedingAnimalOptionVO old = selectedAnimal.getValue();
        if (old != null && old.getAnimalId() == animalId) {
            return;
        }
        dirty = true;
        clearFieldError(BreedFormRules.F_ANIMAL, BreedFormRules.F_WEAN_DATE);
        selectAnimalInternal(animalId);
    }

    private void selectAnimalInternal(long animalId) {
        List<BreedingAnimalOptionVO> list = animalOptions.getValue();
        if (list != null) {
            for (BreedingAnimalOptionVO o : list) {
                if (o.getAnimalId() == animalId) {
                    selectedAnimal.setValue(o);
                }
            }
        }
        context.setValue(null);
        launch(repository.getRegisterContext(animalId, BreedCodes.TYPE_WEANING), ctx -> {
            context.setValue(ctx);
            if (ctx.getAnimal() != null) {
                selectedAnimal.setValue(ctx.getAnimal());
            }
            revalidateWeaned();
        }, e -> {
            if (e.getCode() == ErrorCodes.STATE_CHANGED) {
                selectedAnimal.setValue(null);
                showMessage(UiMessage.withAction(e.getMessage(), SubmitSupport.MSG_REFRESH, ACTION_REFRESH));
            } else {
                handleError(e);
            }
        });
    }

    private void revalidateWeaned() {
        if (SubmitSupport.isBlank(weanedCount.getValue())) {
            return;
        }
        putFieldError(BreedFormRules.F_WEANED, BreedFormRules.weanedError(weanedCount.getValue(), liveBornLimit()));
    }

    @Nullable
    private Integer liveBornLimit() {
        RegisterContextVO c = context.getValue();
        if (c != null && c.getLiveBorn() != null) {
            return c.getLiveBorn();
        }
        BreedingAnimalOptionVO a = selectedAnimal.getValue();
        return a == null ? null : a.getLiveBorn();
    }

    @Nullable
    private String farrowDate() {
        RegisterContextVO c = context.getValue();
        if (c != null && c.getFarrowDate() != null) {
            return c.getFarrowDate();
        }
        BreedingAnimalOptionVO a = selectedAnimal.getValue();
        return a == null ? null : a.getLastFarrowDate();
    }

    @Nullable
    public java.time.LocalDate minWeanDate() {
        return DateFormatter.parseDate(farrowDate());
    }

    private void computeHint() {
        Integer l = liveBornLimit();
        weanedHint.setValue(l == null ? null : BreedFormRules.weanedLimit(l));
    }

    private void computeOver() {
        Integer l = liveBornLimit();
        Integer w = SubmitSupport.parseInt(weanedCount.getValue());
        weanedOver.setValue(l != null && w != null && w > l);
    }

    private void computeCanSubmit() {
        UiState s = getUiState().getValue();
        boolean loaded = s != null && s.is(UiState.Status.CONTENT);
        // 未选母猪时也可点，点击后在母猪框下提示“请选择母猪”
        canSubmit.setValue(loaded && !Boolean.TRUE.equals(submitting.getValue()));
    }

    public void onDatePicked(@Nullable String date) {
        if (date != null) {
            dirty = true;
            weanDate.setValue(date);
            clearFieldError(BreedFormRules.F_WEAN_DATE);
        }
    }

    public void onFieldEdited(@NonNull String field) {
        dirty = true;
        clearFieldError(field);
    }

    /** 点“登记断奶”（修改模式“保存修改”） */
    public void submit() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        boolean edit = Boolean.TRUE.equals(editMode.getValue());
        BreedingAnimalOptionVO a = selectedAnimal.getValue();
        Long animalId = edit ? presetAnimalId : (a == null ? null : a.getAnimalId());
        Map<String, String> errors = BreedFormRules.weaning(animalId, weanDate.getValue(),
                edit ? null : farrowDate(), weanedCount.getValue(), edit ? null : liveBornLimit(),
                litterWeight.getValue());
        if (!errors.isEmpty()) {
            setErrors(errors);
            return;
        }
        setErrors(Collections.emptyMap());
        Integer count = SubmitSupport.parseInt(weanedCount.getValue());
        BigDecimal weight = SubmitSupport.isBlank(litterWeight.getValue()) ? null
                : SubmitSupport.parseDecimal(litterWeight.getValue());
        beginSubmit();
        if (edit && recordId != null) {
            WeaningUpdateRequest req = new WeaningUpdateRequest().setWeanDate(weanDate.getValue())
                    .setWeanedCount(count).setLitterWeight(weight)
                    .setVersion(editRecord != null && editRecord.getVersion() != null ? editRecord.getVersion() : 1);
            launch(repository.updateWeaning(recordId, req), r -> {
                endSubmit();
                dirty = false;
                BreedNav.notifyTodo(null, BreedCodes.BIZ_WEANING, recordId, farmContext.farmIdOrZero());
                finishWithMessage.setValue(SubmitSupport.MSG_SAVED_EDIT);
            }, e -> onSubmitError(e, null, null, null));
            return;
        }
        WeaningRequest req = new WeaningRequest().setAnimalId(animalId)
                .setAnimalVersion(a == null ? null : a.getVersion())
                .setWeanDate(weanDate.getValue()).setWeanedCount(count).setLitterWeight(weight);
        launch(repository.createWeaning(req, idem.keyFor(req)), this::onSaved,
                e -> onSubmitError(e, BreedCodes.TYPE_WEANING, idem, this::onSaved));
    }

    @Override
    protected void retrySubmit() {
        submit();
    }

    private void onSaved(@NonNull RegisterResultVO r) {
        endSubmit();
        dirty = false;
        BreedingAnimalOptionVO a = selectedAnimal.getValue();
        String ear = a != null ? a.getEarTag() : r.getEarTag();
        BreedNav.notifyTodo(null, BreedCodes.BIZ_WEANING, r.getRecordId(), farmContext.farmIdOrZero());
        finishWithMessage.setValue(BreedTextFormatter.weanDone(ear));
    }

    @Override
    protected boolean isKnownField(@NonNull String field) {
        return BreedFormRules.F_ANIMAL.equals(field) || BreedFormRules.F_WEAN_DATE.equals(field)
                || BreedFormRules.F_WEANED.equals(field) || BreedFormRules.F_WEIGHT.equals(field);
    }

    // ===== 对外 LiveData =====

    public LiveData<List<BreedingAnimalOptionVO>> getAnimalOptions() {
        return animalOptions;
    }

    public LiveData<Integer> getOptionTotal() {
        return optionTotal;
    }

    public LiveData<BreedingAnimalOptionVO> getSelectedAnimal() {
        return selectedAnimal;
    }

    public LiveData<RegisterContextVO> getContext() {
        return context;
    }

    public MutableLiveData<String> getWeanDate() {
        return weanDate;
    }

    public MutableLiveData<String> getWeanedCount() {
        return weanedCount;
    }

    public MutableLiveData<String> getLitterWeight() {
        return litterWeight;
    }

    public LiveData<String> getWeanedHint() {
        return weanedHint;
    }

    public LiveData<Boolean> getWeanedOver() {
        return weanedOver;
    }

    public LiveData<String> getStatusPreview() {
        return statusPreview;
    }

    public LiveData<Boolean> getEditMode() {
        return editMode;
    }

    public LiveData<String> getEditAnimalLabel() {
        return editAnimalLabel;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public LiveData<String> getFinishWithMessage() {
        return finishWithMessage;
    }
}
