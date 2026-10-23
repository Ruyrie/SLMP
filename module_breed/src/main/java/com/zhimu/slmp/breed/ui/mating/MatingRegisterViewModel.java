package com.zhimu.slmp.breed.ui.mating;

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
import com.zhimu.slmp.breed.model.request.MatingRequest;
import com.zhimu.slmp.breed.model.request.MatingUpdateRequest;
import com.zhimu.slmp.breed.model.request.PregnancyCheckRequest;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalDetailVO;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalOptionVO;
import com.zhimu.slmp.breed.model.vo.BreedingRecordVO;
import com.zhimu.slmp.breed.model.vo.PendingCheckVO;
import com.zhimu.slmp.breed.model.vo.RegisterContextVO;
import com.zhimu.slmp.breed.model.vo.RegisterResultVO;
import com.zhimu.slmp.breed.ui.common.BreedFormViewModel;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.ui.common.PregCheckHost;
import com.zhimu.slmp.breed.util.BreedFormRules;
import com.zhimu.slmp.breed.util.BreedTextFormatter;
import com.zhimu.slmp.breed.util.SubmitSupport;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.network.model.PageVO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * B03 登记配种 / 妊检（技术文档 2.4.3）：上半部分登记配种（API-B04 MATING 候选、API-B11 带入、API-B12 / B13），
 * 下半部分待妊检（API-B14），“登记妊检”底部面板（API-B15）。修改模式（EXTRA_RECORD_ID）母猪不可改、不显示推算卡与待妊检。
 */
public class MatingRegisterViewModel extends BreedFormViewModel implements PregCheckHost {

    public static final String MSG_NO_ANIMAL = "没有可以配种的母猪。只有空怀或已配种的母猪可以登记配种。";
    private static final String KEY_DATE = "mating_date";
    private static final String KEY_METHOD = "mating_method";
    private static final String KEY_BOAR = "boar_info";

    private final FarmContext farmContext;

    private final MutableLiveData<List<BreedingAnimalOptionVO>> animalOptions =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<BreedingAnimalOptionVO> selectedAnimal = new MutableLiveData<>();
    private final MutableLiveData<RegisterContextVO> context = new MutableLiveData<>();
    private MutableLiveData<String> matingDate = new MutableLiveData<>(DateFormatter.todayString());
    private MutableLiveData<String> matingMethod = new MutableLiveData<>(BreedCodes.METHOD_ARTIFICIAL);
    private MutableLiveData<String> boarInfo = new MutableLiveData<>("");
    private final MediatorLiveData<String> expectedDueDate = new MediatorLiveData<>();
    private final MediatorLiveData<String> dueNote = new MediatorLiveData<>();
    private final MutableLiveData<List<PendingCheckVO>> pendingChecks =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> pendingTotal = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> editMode = new MutableLiveData<>(false);
    /** 修改模式下母猪框显示的文字与状态（来自 API-B05） */
    private final MutableLiveData<String> editAnimalLabel = new MutableLiveData<>();
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    // 妊检面板
    private final MutableLiveData<PendingCheckVO> checkTarget = new MutableLiveData<>();
    private final MutableLiveData<String> checkSubtitle = new MutableLiveData<>("");
    private final MutableLiveData<String> checkDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<String> checkResult = new MutableLiveData<>();
    private final MutableLiveData<String> positiveHint = new MutableLiveData<>("");
    private final MutableLiveData<Map<String, String>> checkErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> checkSubmitting = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> checkEditMode = new MutableLiveData<>(false);

    private final SingleLiveEvent<String> finishWithMessage = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showCheckSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> dismissCheckSheet = new SingleLiveEvent<>();

    @Nullable
    private Long presetAnimalId;
    @Nullable
    private Long recordId;
    @Nullable
    private BreedingRecordVO editRecord;
    @Nullable
    private Integer editAnimalVersion;
    @Nullable
    private SubmitSupport.IdemKeeper idem;
    @Nullable
    private SubmitSupport.IdemKeeper checkIdem;

    @Inject
    public MatingRegisterViewModel(@NonNull BreedRepository repository, @NonNull FarmContext farmContext) {
        super(repository);
        this.farmContext = farmContext;
        expectedDueDate.addSource(selectedAnimal, a -> computeDue());
        expectedDueDate.addSource(context, c -> computeDue());
        dueNote.addSource(selectedAnimal, a -> computeNote());
        dueNote.addSource(context, c -> computeNote());
        canSubmit.addSource(selectedAnimal, a -> computeCanSubmit());
        canSubmit.addSource(submitting, s -> computeCanSubmit());
        canSubmit.addSource(editMode, s -> computeCanSubmit());
        canSubmit.addSource(getUiState(), s -> computeCanSubmit());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        matingDate = handle.getLiveData(KEY_DATE, DateFormatter.todayString());
        matingMethod = handle.getLiveData(KEY_METHOD, BreedCodes.METHOD_ARTIFICIAL);
        boarInfo = handle.getLiveData(KEY_BOAR, "");
        expectedDueDate.addSource(matingDate, d -> computeDue());
        canSubmit.addSource(matingDate, d -> computeCanSubmit());
        idem = new SubmitSupport.IdemKeeper(handle, "mating");
        checkIdem = new SubmitSupport.IdemKeeper(handle, "preg_check");
        presetAnimalId = readLong(handle.get(RouterPath.EXTRA_ANIMAL_ID));
        recordId = readLong(handle.get(RouterPath.EXTRA_RECORD_ID));
        String json = handle.get(BreedExtras.EXTRA_RECORD_JSON);
        if (json != null) {
            editRecord = GsonFactory.get().fromJson(json, BreedingRecordVO.class);
        }
        init(presetAnimalId, recordId);
    }

    /** 加载候选、待妊检与预选母猪；修改模式加载档案 */
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
                matingDate.setValue(editRecord.getRecordDate());
                if (editRecord.getMatingMethod() != null) {
                    matingMethod.setValue(editRecord.getMatingMethod());
                }
                boarInfo.setValue(editRecord.getBoarInfo() == null ? "" : editRecord.getBoarInfo());
            }
            if (animalId == null) {
                setUiState(UiState.error(SubmitSupport.MSG_NOT_FOUND, null, ErrorCodes.NOT_FOUND));
                return;
            }
            load(repository.getAnimal(animalId), this::applyEditAnimal);
            return;
        }
        Single<PageVO<BreedingAnimalOptionVO>> options = repository.getOptions(
                new BreedingOptionQuery(BreedCodes.TYPE_MATING, null));
        Single<PageVO<PendingCheckVO>> checks = repository.getPendingChecks();
        load(Single.zip(options, checks, Pair::new), pair -> {
            animalOptions.setValue(pair.options.getList());
            pendingChecks.setValue(pair.checks.getList());
            pendingTotal.setValue(pair.checks.getTotal());
            if (presetAnimalId != null && selectedAnimal.getValue() == null) {
                BreedingAnimalOptionVO preset = findOption(presetAnimalId);
                if (preset != null) {
                    selectedAnimal.setValue(preset);
                }
                loadContext(presetAnimalId);
            }
        });
    }

    @Override
    public void retry() {
        resetContentLoaded();
        init(presetAnimalId, recordId);
    }

    private void applyEditAnimal(@NonNull BreedingAnimalDetailVO d) {
        editAnimalVersion = d.getVersion();
        editAnimalLabel.setValue(BreedTextFormatter.animalLabel(d.getEarTag(), d.getBreedName(), d.getParity()));
    }

    private void loadContext(long animalId) {
        launch(repository.getRegisterContext(animalId, BreedCodes.TYPE_MATING), ctx -> {
            context.setValue(ctx);
            if (ctx.getAnimal() != null) {
                selectedAnimal.setValue(ctx.getAnimal());
            }
        }, e -> {
            if (e.getCode() == ErrorCodes.STATE_CHANGED) {
                selectedAnimal.setValue(null);
                context.setValue(null);
                showMessage(UiMessage.withAction(e.getMessage(), SubmitSupport.MSG_REFRESH, ACTION_REFRESH));
            } else {
                handleError(e);
            }
        });
    }

    @Nullable
    private BreedingAnimalOptionVO findOption(long animalId) {
        List<BreedingAnimalOptionVO> list = animalOptions.getValue();
        if (list != null) {
            for (BreedingAnimalOptionVO o : list) {
                if (o.getAnimalId() == animalId) {
                    return o;
                }
            }
        }
        return null;
    }

    // ===== 配种表单 =====

    /** 下拉选择母猪：带入状态标签与推算卡，再取 API-B11 的日期下限 */
    public void selectAnimal(long animalId) {
        BreedingAnimalOptionVO o = findOption(animalId);
        if (o == null) {
            return;
        }
        BreedingAnimalOptionVO old = selectedAnimal.getValue();
        if (old != null && old.getAnimalId() == animalId) {
            return;
        }
        dirty = true;
        selectedAnimal.setValue(o);
        context.setValue(null);
        clearFieldError(BreedFormRules.F_ANIMAL, BreedFormRules.F_MATING_DATE);
        loadContext(animalId);
    }

    /** 按耳号过滤候选（API-B04 keyword） */
    public void filterOptions(@Nullable String keyword) {
        launch(repository.getOptions(new BreedingOptionQuery(BreedCodes.TYPE_MATING, keyword)),
                page -> animalOptions.setValue(page.getList()));
    }

    public void onMatingDatePicked(@Nullable String date) {
        if (date == null) {
            return;
        }
        dirty = true;
        matingDate.setValue(date);
        clearFieldError(BreedFormRules.F_MATING_DATE);
    }

    public void onMethodPicked(@NonNull String method) {
        if (!method.equals(matingMethod.getValue())) {
            dirty = true;
            matingMethod.setValue(method);
        }
    }

    public void onBoarEdited() {
        dirty = true;
        clearFieldError(BreedFormRules.F_BOAR);
    }

    /** 日期选择器下限：min_mating_date（已配种为首次配种日期） */
    @Nullable
    public LocalDate minMatingDate() {
        if (Boolean.TRUE.equals(editMode.getValue())) {
            return null;
        }
        return DateFormatter.parseDate(lowerBound());
    }

    @Nullable
    private String lowerBound() {
        RegisterContextVO ctx = context.getValue();
        if (ctx != null && ctx.getMinMatingDate() != null) {
            return ctx.getMinMatingDate();
        }
        BreedingAnimalOptionVO a = selectedAnimal.getValue();
        if (a == null) {
            return null;
        }
        return a.getFirstMatingDate() != null ? a.getFirstMatingDate() : a.getLastWeanDate();
    }

    private boolean lowerIsWean() {
        BreedingAnimalOptionVO a = selectedAnimal.getValue();
        String min = lowerBound();
        return a != null && min != null && min.equals(a.getLastWeanDate());
    }

    @Nullable
    private String firstMatingDate() {
        BreedingAnimalOptionVO a = selectedAnimal.getValue();
        if (a == null || !BreedCodes.MATED.equals(a.getStatus())) {
            return null;
        }
        RegisterContextVO ctx = context.getValue();
        if (ctx != null && ctx.getFirstMatingDate() != null) {
            return ctx.getFirstMatingDate();
        }
        return a.getFirstMatingDate();
    }

    @Nullable
    private Integer gestationDays() {
        RegisterContextVO ctx = context.getValue();
        if (ctx != null && ctx.getGestationDays() != null) {
            return ctx.getGestationDays();
        }
        BreedingAnimalOptionVO a = selectedAnimal.getValue();
        return a == null ? null : a.getGestationDays();
    }

    @Nullable
    private String breedName() {
        RegisterContextVO ctx = context.getValue();
        if (ctx != null && ctx.getBreedName() != null) {
            return ctx.getBreedName();
        }
        BreedingAnimalOptionVO a = selectedAnimal.getValue();
        return a == null ? null : a.getBreedName();
    }

    private void computeDue() {
        if (selectedAnimal.getValue() == null) {
            expectedDueDate.setValue(null);
            return;
        }
        LocalDate due = BreedFormRules.expectedDueDate(firstMatingDate(), matingDate.getValue(), gestationDays());
        expectedDueDate.setValue(due == null ? null : DateFormatter.toDateString(due));
    }

    private void computeNote() {
        Integer days = gestationDays();
        dueNote.setValue(days == null ? null
                : "按" + BreedTextFormatter.nz(breedName()) + "妊娠期 " + days + " 天推算；同一情期再次配种时，仍按首次配种日期计算");
    }

    private void computeCanSubmit() {
        UiState s = getUiState().getValue();
        boolean loaded = s != null && s.is(UiState.Status.CONTENT);
        // 未选母猪时也可点，点击后在母猪框下提示“请选择母猪”
        canSubmit.setValue(loaded && !Boolean.TRUE.equals(submitting.getValue()));
    }

    /** 点“登记配种”（修改模式为“保存修改”） */
    public void submitMating() {
        if (Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        boolean edit = Boolean.TRUE.equals(editMode.getValue());
        BreedingAnimalOptionVO a = selectedAnimal.getValue();
        Long animalId = edit ? presetAnimalId : (a == null ? null : a.getAnimalId());
        String boar = boarInfo.getValue() == null ? null : boarInfo.getValue().trim();
        Map<String, String> errors = BreedFormRules.mating(animalId, matingDate.getValue(),
                edit ? null : lowerBound(), !edit && lowerIsWean(), edit ? null : firstMatingDate(),
                matingMethod.getValue(), boar);
        if (!errors.isEmpty()) {
            setErrors(errors);
            return;
        }
        setErrors(Collections.emptyMap());
        beginSubmit();
        if (edit && recordId != null) {
            MatingUpdateRequest req = new MatingUpdateRequest()
                    .setMatingDate(matingDate.getValue())
                    .setMatingMethod(matingMethod.getValue())
                    .setBoarInfo(boar == null || boar.isEmpty() ? null : boar)
                    .setVersion(editRecord != null && editRecord.getVersion() != null ? editRecord.getVersion() : 1);
            launch(repository.updateMating(recordId, req), r -> {
                endSubmit();
                dirty = false;
                BreedNav.notifyTodo(null, BreedCodes.BIZ_MATING, recordId, farmContext.farmIdOrZero());
                finishWithMessage.setValue(SubmitSupport.MSG_SAVED_EDIT);
            }, e -> onSubmitError(e, null, null, null));
            return;
        }
        MatingRequest req = new MatingRequest()
                .setAnimalId(animalId)
                .setAnimalVersion(a == null ? null : a.getVersion())
                .setMatingDate(matingDate.getValue())
                .setMatingMethod(matingMethod.getValue())
                .setBoarInfo(boar == null || boar.isEmpty() ? null : boar);
        String key = idem.keyFor(req);
        launch(repository.createMating(req, key), this::onMatingSaved,
                e -> onSubmitError(e, BreedCodes.TYPE_MATING, idem, this::onMatingSaved));
    }

    @Override
    protected void retrySubmit() {
        submitMating();
    }

    private void onMatingSaved(@NonNull RegisterResultVO r) {
        endSubmit();
        dirty = false;
        String due = r.getExpectedDueDate() != null ? r.getExpectedDueDate() : expectedDueDate.getValue();
        BreedNav.notifyTodo(null, BreedCodes.BIZ_MATING, r.getRecordId(), farmContext.farmIdOrZero());
        finishWithMessage.setValue(BreedTextFormatter.matingDone(due));
    }

    @Override
    protected boolean isKnownField(@NonNull String field) {
        return BreedFormRules.F_ANIMAL.equals(field) || BreedFormRules.F_MATING_DATE.equals(field)
                || BreedFormRules.F_MATING_METHOD.equals(field) || BreedFormRules.F_BOAR.equals(field);
    }

    // ===== 待妊检与妊检面板 =====

    /** 点待妊检项的“登记妊检” */
    public void openCheck(@NonNull PendingCheckVO item) {
        checkTarget.setValue(item);
        checkDate.setValue(DateFormatter.todayString());
        checkResult.setValue(null);
        checkErrors.setValue(Collections.emptyMap());
        checkSubtitle.setValue(BreedTextFormatter.animalLabel(item.getEarTag(), item.getBreedName(), item.getParity())
                + BreedTextFormatter.DOT + BreedTextFormatter.md(item.getFirstMatingDate()) + "配种，已 "
                + item.getDaysSinceMating() + " 天");
        positiveHint.setValue("转为妊娠，预产期 " + BreedTextFormatter.fullDate(item.getExpectedDueDate()));
        if (checkIdem != null) {
            checkIdem.reset();
        }
        showCheckSheet.call();
    }

    @NonNull
    @Override
    public LiveData<String> getCheckSubtitle() {
        return checkSubtitle;
    }

    @NonNull
    @Override
    public MutableLiveData<String> getCheckDate() {
        return checkDate;
    }

    @NonNull
    @Override
    public MutableLiveData<String> getCheckResult() {
        return checkResult;
    }

    @NonNull
    @Override
    public LiveData<String> getPositiveHint() {
        return positiveHint;
    }

    @NonNull
    @Override
    public LiveData<Map<String, String>> getCheckErrors() {
        return checkErrors;
    }

    @NonNull
    @Override
    public LiveData<Boolean> getCheckSubmitting() {
        return checkSubmitting;
    }

    @NonNull
    @Override
    public LiveData<Boolean> getCheckEditMode() {
        return checkEditMode;
    }

    @Nullable
    @Override
    public LocalDate checkMinDate() {
        PendingCheckVO t = checkTarget.getValue();
        return t == null ? null : DateFormatter.parseDate(t.getFirstMatingDate());
    }

    @Override
    public void onCheckDatePicked(@Nullable String date) {
        if (date != null) {
            checkDate.setValue(date);
            clearCheckError(BreedFormRules.F_CHECK_DATE);
        }
    }

    @Override
    public void onCheckResultPicked(@NonNull String result) {
        checkResult.setValue(result);
        clearCheckError(BreedFormRules.F_RESULT);
    }

    private void clearCheckError(@NonNull String field) {
        Map<String, String> m = checkErrors.getValue();
        if (m != null && m.containsKey(field)) {
            Map<String, String> next = new java.util.LinkedHashMap<>(m);
            next.remove(field);
            checkErrors.setValue(next);
        }
    }

    /** 面板“确认结果”（API-B15） */
    @Override
    public void submitCheck() {
        PendingCheckVO t = checkTarget.getValue();
        if (t == null || Boolean.TRUE.equals(checkSubmitting.getValue())) {
            return;
        }
        Map<String, String> errors = BreedFormRules.pregCheck(checkDate.getValue(), t.getFirstMatingDate(),
                checkResult.getValue());
        checkErrors.setValue(errors);
        if (!errors.isEmpty()) {
            return;
        }
        PregnancyCheckRequest req = new PregnancyCheckRequest()
                .setAnimalId(t.getAnimalId())
                .setAnimalVersion(t.getAnimalVersion())
                .setCycleId(t.getCycleId())
                .setCheckDate(checkDate.getValue())
                .setResult(checkResult.getValue());
        checkSubmitting.setValue(true);
        launch(repository.createPregCheck(req, checkIdem.keyFor(req)), r -> onCheckSaved(t, r),
                e -> onCheckError(t, e));
    }

    private void onCheckSaved(@NonNull PendingCheckVO t, @NonNull RegisterResultVO r) {
        checkSubmitting.setValue(false);
        List<PendingCheckVO> list = new ArrayList<>(pendingChecks.getValue() == null ? Collections.emptyList()
                : pendingChecks.getValue());
        list.remove(t);
        pendingChecks.setValue(list);
        Integer total = pendingTotal.getValue();
        pendingTotal.setValue(Math.max(0, (total == null ? 0 : total) - 1));
        boolean positive = BreedCodes.POSITIVE.equals(checkResult.getValue());
        BreedNav.notifyTodo(null, BreedCodes.BIZ_PREG_CHECK, r.getRecordId(), farmContext.farmIdOrZero());
        dismissCheckSheet.setValue(BreedTextFormatter.checkDone(t.getEarTag(), positive));
        // 妊检阴性的母猪回到空怀，重新出现在配种候选中
        filterOptions(null);
    }

    private void onCheckError(@NonNull PendingCheckVO t, @NonNull ApiException e) {
        if (SubmitSupport.isTimeout(e) && checkIdem.current() != null) {
            launch(repository.getSubmission(checkIdem.current(), BreedCodes.TYPE_PREG_CHECK), s -> {
                if (s.isSaved() && s.getResult() != null) {
                    onCheckSaved(t, s.getResult());
                } else {
                    checkSubmitting.setValue(false);
                    showMessage(SubmitSupport.MSG_NOT_SAVED);
                }
            }, err -> {
                checkSubmitting.setValue(false);
                showMessage(SubmitSupport.MSG_NETWORK_KEEP);
            });
            return;
        }
        checkSubmitting.setValue(false);
        if (e.getCode() == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()) {
            checkErrors.setValue(e.getFieldErrors());
            return;
        }
        if (e.isNetwork()) {
            showMessage(SubmitSupport.MSG_NETWORK_KEEP);
            return;
        }
        if (ErrorCodes.isNoPermission(e.getCode())) {
            showMessage(SubmitSupport.MSG_NO_PERMISSION);
            noPermission.call();
            return;
        }
        showMessage(e.getMessage());
        if (ErrorCodes.isDataChanged(e.getCode())) {
            dismissCheckSheet.setValue(null);
            launch(repository.getPendingChecks(), p -> {
                pendingChecks.setValue(p.getList());
                pendingTotal.setValue(p.getTotal());
            });
        }
    }

    // ===== 对外 LiveData =====

    public LiveData<List<BreedingAnimalOptionVO>> getAnimalOptions() {
        return animalOptions;
    }

    public LiveData<BreedingAnimalOptionVO> getSelectedAnimal() {
        return selectedAnimal;
    }

    public LiveData<RegisterContextVO> getContext() {
        return context;
    }

    public MutableLiveData<String> getMatingDate() {
        return matingDate;
    }

    public MutableLiveData<String> getMatingMethod() {
        return matingMethod;
    }

    public MutableLiveData<String> getBoarInfo() {
        return boarInfo;
    }

    public LiveData<String> getExpectedDueDate() {
        return expectedDueDate;
    }

    public LiveData<String> getDueNote() {
        return dueNote;
    }

    public LiveData<List<PendingCheckVO>> getPendingChecks() {
        return pendingChecks;
    }

    public LiveData<Integer> getPendingTotal() {
        return pendingTotal;
    }

    public LiveData<PendingCheckVO> getCheckTarget() {
        return checkTarget;
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

    public LiveData<Void> getShowCheckSheet() {
        return showCheckSheet;
    }

    @NonNull
    @Override
    public LiveData<String> getDismissCheckSheet() {
        return dismissCheckSheet;
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

    private static final class Pair {
        final PageVO<BreedingAnimalOptionVO> options;
        final PageVO<PendingCheckVO> checks;

        Pair(PageVO<BreedingAnimalOptionVO> options, PageVO<PendingCheckVO> checks) {
            this.options = options;
            this.checks = checks;
        }
    }
}
