package com.zhimu.slmp.breed.ui.archive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.data.BreedRepository;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.request.BreedingAnimalRequest;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalDetailVO;
import com.zhimu.slmp.breed.model.vo.PenOptionVO;
import com.zhimu.slmp.breed.ui.common.BreedFormViewModel;
import com.zhimu.slmp.breed.ui.common.BreedLookup;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.util.BreedFormRules;
import com.zhimu.slmp.breed.util.SubmitSupport;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.CategoryBreedVO;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * 档案表单（技术文档 2.4.7）：EXTRA_ANIMAL_ID 有值为“编辑档案”（API-B05 初值、API-B08），否则“新增母猪”（API-B07）。
 * 品种候选取品类品种字典（API-U09，待定事项 2）中与本场同畜种、用途为种用且设有妊娠期的品种；栏舍取 API-L07。
 */
public class AnimalFormViewModel extends BreedFormViewModel {

    private static final String KEY_EAR = "ear_tag";
    private static final String KEY_BIRTH = "birth_date";
    private static final String KEY_PARITY = "initial_parity";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_BREED = "breed_id";
    private static final String KEY_PEN = "pen_id";

    private final FarmContext farmContext;
    private final BaseDataRepository baseData;

    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    private final MutableLiveData<BreedingAnimalDetailVO> original = new MutableLiveData<>();
    private final MutableLiveData<List<CategoryBreedVO>> breedOptions = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<PenOptionVO>> penOptions = new MutableLiveData<>(Collections.emptyList());
    private MutableLiveData<String> earTag = new MutableLiveData<>("");
    private MutableLiveData<String> birthDate = new MutableLiveData<>();
    private MutableLiveData<String> initialParity = new MutableLiveData<>("0");
    private MutableLiveData<String> source = new MutableLiveData<>();
    private MutableLiveData<Long> breedId = new MutableLiveData<>();
    private MutableLiveData<Long> penId = new MutableLiveData<>();
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final SingleLiveEvent<String> finishWithMessage = new SingleLiveEvent<>();

    @Nullable
    private Long animalId;
    @Nullable
    private SubmitSupport.IdemKeeper idem;

    @Inject
    public AnimalFormViewModel(@NonNull BreedRepository repository, @NonNull FarmContext farmContext,
                               @NonNull BaseDataRepository baseData) {
        super(repository);
        this.farmContext = farmContext;
        this.baseData = baseData;
        canSubmit.addSource(getUiState(), s -> computeCanSubmit());
        canSubmit.addSource(submitting, s -> computeCanSubmit());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        earTag = handle.getLiveData(KEY_EAR, "");
        birthDate = handle.getLiveData(KEY_BIRTH);
        initialParity = handle.getLiveData(KEY_PARITY, "0");
        source = handle.getLiveData(KEY_SOURCE);
        breedId = handle.getLiveData(KEY_BREED);
        penId = handle.getLiveData(KEY_PEN);
        idem = new SubmitSupport.IdemKeeper(handle, "animal");
        Object id = handle.get(BreedExtras.EXTRA_ANIMAL_ID);
        init(id instanceof Long ? (Long) id : null);
    }

    public void init(@Nullable Long id) {
        animalId = id;
        if (farmContext.isStaff()) {
            setUiState(UiState.noPermission(null, ErrorCodes.ROLE_FORBIDDEN));
            return;
        }
        editMode.setValue(id != null);
        FarmContextVO ctx = farmContext.current();
        Long mainCategory = ctx == null ? null : ctx.getMainCategoryId();
        Single<List<CategoryVO>> categories = baseData.getCategories();
        Single<Form> form = categories.flatMap(list -> {
            List<CategoryBreedVO> breeds = BreedLookup.breedingBreeds(list, mainCategory);
            Long categoryId = null;
            for (CategoryVO c : list) {
                if (c.isBreeding() && !"POULTRY".equals(c.getSpecies())) {
                    categoryId = c.getId();
                    break;
                }
            }
            Single<List<PenOptionVO>> pens = repository.getPenOptions(categoryId);
            Single<BreedingAnimalDetailVO> detail = id == null ? Single.just(new BreedingAnimalDetailVO())
                    : repository.getAnimal(id);
            return Single.zip(pens, detail, (p, d) -> new Form(breeds, p, id == null ? null : d));
        });
        load(form, f -> {
            breedOptions.setValue(f.breeds);
            penOptions.setValue(f.pens);
            if (f.detail != null) {
                original.setValue(f.detail);
                if (!dirty) {
                    applyOriginal(f.detail);
                }
            } else if (breedId.getValue() == null && f.breeds.size() == 1) {
                breedId.setValue(f.breeds.get(0).getId());
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
        init(animalId);
    }

    private void applyOriginal(@NonNull BreedingAnimalDetailVO d) {
        earTag.setValue(d.getEarTag());
        birthDate.setValue(d.getBirthDate());
        initialParity.setValue(String.valueOf(d.getInitialParity()));
        source.setValue(d.getSource());
        breedId.setValue(d.getBreedId());
        penId.setValue(d.getPenId());
    }

    private void computeCanSubmit() {
        UiState s = getUiState().getValue();
        canSubmit.setValue(s != null && s.is(UiState.Status.CONTENT) && !Boolean.TRUE.equals(submitting.getValue()));
    }

    public void onBreedPicked(long id) {
        dirty = true;
        breedId.setValue(id);
        clearFieldError(BreedFormRules.F_BREED);
    }

    public void onPenPicked(long id) {
        dirty = true;
        penId.setValue(id);
        clearFieldError(BreedFormRules.F_PEN);
    }

    public void onSourcePicked(@NonNull String value) {
        dirty = true;
        source.setValue(value);
        clearFieldError(BreedFormRules.F_SOURCE);
    }

    public void onBirthPicked(@Nullable String date) {
        dirty = true;
        birthDate.setValue(date);
        clearFieldError(BreedFormRules.F_BIRTH);
    }

    public void onFieldEdited(@NonNull String field) {
        dirty = true;
        clearFieldError(field);
    }

    /** 点“保存档案” */
    public void save() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        String ear = earTag.getValue() == null ? "" : earTag.getValue().trim();
        Map<String, String> errors = BreedFormRules.animal(ear, breedId.getValue(), penId.getValue(),
                birthDate.getValue(), source.getValue(), initialParity.getValue());
        if (!errors.isEmpty()) {
            setErrors(errors);
            return;
        }
        setErrors(Collections.emptyMap());
        Integer parity = SubmitSupport.parseInt(initialParity.getValue());
        BreedingAnimalRequest req = new BreedingAnimalRequest().setEarTag(ear).setBreedId(breedId.getValue())
                .setBirthDate(birthDate.getValue()).setSource(source.getValue()).setPenId(penId.getValue())
                .setInitialParity(parity == null ? 0 : parity);
        beginSubmit();
        if (animalId != null) {
            BreedingAnimalDetailVO d = original.getValue();
            req.setVersion(d == null ? 1 : d.getVersion());
            launch(repository.updateAnimal(animalId, req), r -> onSaved(r.getAnimalId()), this::onError);
        } else {
            launch(repository.createAnimal(req, idem.keyFor(req)), r -> onSaved(r.getAnimalId()), this::onError);
        }
    }

    @Override
    protected void retrySubmit() {
        save();
    }

    private void onSaved(long id) {
        endSubmit();
        dirty = false;
        BreedNav.notifyTodo(null, BreedCodes.BIZ_BREEDING_ANIMAL, id, farmContext.farmIdOrZero());
        finishWithMessage.setValue("已保存档案");
    }

    private void onError(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.NAME_DUPLICATE) {
            endSubmit();
            putFieldError(BreedFormRules.F_EAR_TAG, e.getMessage());
            scrollToError.setValue(BreedFormRules.F_EAR_TAG);
            return;
        }
        if (e.getCode() == ErrorCodes.STATE_CHANGED) {
            // “0161 已淘汰，档案只能查看”并关闭
            endSubmit();
            showMessage(e.getMessage());
            finishWithMessage.setValue(null);
            return;
        }
        onSubmitError(e, null, null, null);
    }

    @Override
    protected boolean isKnownField(@NonNull String field) {
        return BreedFormRules.F_EAR_TAG.equals(field) || BreedFormRules.F_BREED.equals(field)
                || BreedFormRules.F_PEN.equals(field) || BreedFormRules.F_BIRTH.equals(field)
                || BreedFormRules.F_SOURCE.equals(field) || BreedFormRules.F_INITIAL_PARITY.equals(field);
    }

    // ===== 对外 LiveData =====

    public LiveData<Boolean> getEditMode() {
        return editMode;
    }

    public LiveData<BreedingAnimalDetailVO> getOriginal() {
        return original;
    }

    public LiveData<List<CategoryBreedVO>> getBreedOptions() {
        return breedOptions;
    }

    public LiveData<List<PenOptionVO>> getPenOptions() {
        return penOptions;
    }

    public MutableLiveData<String> getEarTag() {
        return earTag;
    }

    public LiveData<String> getBirthDate() {
        return birthDate;
    }

    public MutableLiveData<String> getInitialParity() {
        return initialParity;
    }

    public LiveData<String> getSource() {
        return source;
    }

    public LiveData<Long> getBreedId() {
        return breedId;
    }

    public LiveData<Long> getPenId() {
        return penId;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    /** 值为 null 时直接关闭（已淘汰不能编辑） */
    public LiveData<String> getFinishWithMessage() {
        return finishWithMessage;
    }

    private static final class Form {
        final List<CategoryBreedVO> breeds;
        final List<PenOptionVO> pens;
        @Nullable
        final BreedingAnimalDetailVO detail;

        Form(List<CategoryBreedVO> breeds, List<PenOptionVO> pens, @Nullable BreedingAnimalDetailVO detail) {
            this.breeds = breeds;
            this.pens = pens;
            this.detail = detail;
        }
    }
}
