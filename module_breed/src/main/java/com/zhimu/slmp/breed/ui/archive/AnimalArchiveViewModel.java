package com.zhimu.slmp.breed.ui.archive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingLiveData;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.data.BreedRepository;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.request.AnimalExitRequest;
import com.zhimu.slmp.breed.model.request.PregnancyCheckRequest;
import com.zhimu.slmp.breed.model.request.PregnancyCheckUpdateRequest;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalDetailVO;
import com.zhimu.slmp.breed.model.vo.BreedingRecordVO;
import com.zhimu.slmp.breed.model.vo.RegisterResultVO;
import com.zhimu.slmp.breed.ui.common.BreedFormViewModel;
import com.zhimu.slmp.breed.ui.common.BreedNav;
import com.zhimu.slmp.breed.ui.common.NavRequest;
import com.zhimu.slmp.breed.ui.common.PregCheckHost;
import com.zhimu.slmp.breed.util.BreedFormRules;
import com.zhimu.slmp.breed.util.BreedTextFormatter;
import com.zhimu.slmp.breed.util.SubmitSupport;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.network.GsonFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * B06 种畜档案（技术文档 2.4.6）：档案（API-B05）、繁育记录（API-B06 分页）、头部登记按钮、更多菜单
 * （编辑档案、查看修改记录、登记死亡 / 淘汰 API-B09）、记录菜单（编辑、查看批次、提交修改申请、查看修改记录）、
 * 头部与妊检记录“编辑”的妊检面板（API-B15 / B16）。
 */
public class AnimalArchiveViewModel extends BreedFormViewModel implements PregCheckHost {

    public static final String MENU_EDIT = "EDIT";
    public static final String MENU_LOG = "LOG";
    public static final String MENU_EXIT = "EXIT";

    private final FarmContext farmContext;

    private final MutableLiveData<BreedingAnimalDetailVO> detail = new MutableLiveData<>();
    private final MutableLiveData<String> headerAction = new MutableLiveData<>();
    private final MutableLiveData<List<String>> moreMenu = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> recordsKey = new MutableLiveData<>();
    private final LiveData<PagingData<BreedingRecordVO>> records;
    @Nullable
    private PagingSource<Integer, BreedingRecordVO> recordSource;

    // 死亡淘汰面板
    private final MutableLiveData<String> exitType = new MutableLiveData<>(BreedCodes.EXIT_CULL);
    private final MutableLiveData<String> exitDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<String> exitReason = new MutableLiveData<>("");
    private final MutableLiveData<String> exitSubtitle = new MutableLiveData<>("");

    // 妊检面板
    private final MutableLiveData<String> checkSubtitle = new MutableLiveData<>("");
    private final MutableLiveData<String> checkDate = new MutableLiveData<>(DateFormatter.todayString());
    private final MutableLiveData<String> checkResult = new MutableLiveData<>();
    private final MutableLiveData<String> positiveHint = new MutableLiveData<>("");
    private final MutableLiveData<Map<String, String>> checkErrors = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Boolean> checkSubmitting = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> checkEditMode = new MutableLiveData<>(false);
    @Nullable
    private BreedingRecordVO checkRecord;
    @Nullable
    private String checkFirstMating;

    private final SingleLiveEvent<List<String>> showMoreMenu = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showExitSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> showCheckSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> dismissCheckSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> dismissExitSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<BreedingRecordVO> showRecordMenu = new SingleLiveEvent<>();
    private final SingleLiveEvent<NavRequest> navigate = new SingleLiveEvent<>();
    private final SingleLiveEvent<String[]> openChangeLog = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> finishToList = new SingleLiveEvent<>();

    private long animalId;
    @Nullable
    private SubmitSupport.IdemKeeper exitIdem;
    @Nullable
    private SubmitSupport.IdemKeeper checkIdem;

    @Inject
    public AnimalArchiveViewModel(@NonNull BreedRepository repository, @NonNull FarmContext farmContext) {
        super(repository);
        this.farmContext = farmContext;
        records = androidx.lifecycle.Transformations.switchMap(recordsKey, id -> {
            Pager<Integer, BreedingRecordVO> pager = new Pager<>(
                    new PagingConfig(BaseListViewModel.PAGE_SIZE, BaseListViewModel.PREFETCH_DISTANCE, false,
                            BaseListViewModel.PAGE_SIZE), () -> {
                recordSource = repository.recordSource(id);
                return recordSource;
            });
            return PagingLiveData.cachedIn(PagingLiveData.getLiveData(pager), ViewModelKt.getViewModelScope(this));
        });
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        exitIdem = new SubmitSupport.IdemKeeper(handle, "exit");
        checkIdem = new SubmitSupport.IdemKeeper(handle, "archive_check");
        Object id = handle.get(BreedExtras.EXTRA_ANIMAL_ID);
        if (id instanceof Long) {
            load((Long) id);
        } else {
            setUiState(UiState.error(SubmitSupport.MSG_NOT_FOUND, null, ErrorCodes.NOT_FOUND));
        }
    }

    /** 加载档案与繁育记录 */
    public void load(long id) {
        animalId = id;
        if (farmContext.isStaff()) {
            setUiState(UiState.noPermission(null, ErrorCodes.ROLE_FORBIDDEN));
            return;
        }
        load(repository.getAnimal(id), this::applyDetail);
        if (recordsKey.getValue() == null) {
            recordsKey.setValue(id);
        }
    }

    /** 下拉刷新、子页面返回后刷新 */
    public void refresh() {
        load(repository.getAnimal(animalId), this::applyDetail);
        if (recordSource != null) {
            recordSource.invalidate();
        }
    }

    @Override
    public void retry() {
        resetContentLoaded();
        load(animalId);
    }

    @Override
    protected void onDataChanged(@NonNull ApiException e) {
        refresh();
    }

    private void applyDetail(@NonNull BreedingAnimalDetailVO d) {
        detail.setValue(d);
        boolean exited = BreedCodes.isExited(d.getStatus());
        headerAction.setValue(exited ? null : d.getNextActionText());
        List<String> menu = new ArrayList<>();
        if (d.isCanEdit()) {
            menu.add(MENU_EDIT);
        }
        menu.add(MENU_LOG);
        if (d.isCanExit()) {
            menu.add(MENU_EXIT);
        }
        moreMenu.setValue(menu);
        exitSubtitle.setValue(BreedTextFormatter.animalLabel(d.getEarTag(), d.getBreedName(), d.getParity()));
    }

    // ===== 头部与更多菜单 =====

    /** 头部登记按钮：空怀 → B03；已配种 → 本页妊检面板；妊娠 → B04；哺乳 → B05 */
    public void onHeaderAction() {
        BreedingAnimalDetailVO d = detail.getValue();
        if (d == null || d.getNextAction() == null) {
            return;
        }
        switch (d.getNextAction()) {
            case BreedCodes.TYPE_MATING:
                navigate.setValue(NavRequest.forResult(RouterPath.B03).put(BreedExtras.EXTRA_ANIMAL_ID, animalId));
                break;
            case BreedCodes.TYPE_PREG_CHECK:
                openHeaderCheck(d);
                break;
            case BreedCodes.TYPE_FARROWING:
                navigate.setValue(NavRequest.forResult(RouterPath.B04).put(BreedExtras.EXTRA_ANIMAL_ID, animalId));
                break;
            case BreedCodes.TYPE_WEANING:
                navigate.setValue(NavRequest.forResult(RouterPath.B05).put(BreedExtras.EXTRA_ANIMAL_ID, animalId));
                break;
            default:
                break;
        }
    }

    private void openHeaderCheck(@NonNull BreedingAnimalDetailVO d) {
        BreedingAnimalDetailVO.CurrentCycle c = d.getCurrentCycle();
        if (c == null) {
            return;
        }
        checkRecord = null;
        checkEditMode.setValue(false);
        checkFirstMating = c.getFirstMatingDate();
        checkDate.setValue(DateFormatter.todayString());
        checkResult.setValue(null);
        checkErrors.setValue(Collections.emptyMap());
        String days = c.getDaysSinceMating() == null ? "" : "，已 " + c.getDaysSinceMating() + " 天";
        checkSubtitle.setValue(BreedTextFormatter.animalLabel(d.getEarTag(), d.getBreedName(), d.getParity())
                + BreedTextFormatter.DOT + BreedTextFormatter.md(c.getFirstMatingDate()) + "配种" + days);
        positiveHint.setValue(c.getExpectedDueDate() == null ? "转为妊娠"
                : "转为妊娠，预产期 " + BreedTextFormatter.fullDate(c.getExpectedDueDate()));
        if (checkIdem != null) {
            checkIdem.reset();
        }
        showCheckSheet.call();
    }

    public void onMoreClick() {
        List<String> menu = moreMenu.getValue();
        if (menu != null && !menu.isEmpty()) {
            showMoreMenu.setValue(menu);
        }
    }

    public void onMenu(@NonNull String item) {
        BreedingAnimalDetailVO d = detail.getValue();
        switch (item) {
            case MENU_EDIT:
                navigate.setValue(NavRequest.forResult(BreedExtras.ACTION_ANIMAL_FORM)
                        .put(BreedExtras.EXTRA_ANIMAL_ID, animalId));
                break;
            case MENU_LOG:
                openChangeLog.setValue(new String[]{BreedCodes.BIZ_BREEDING_ANIMAL, String.valueOf(animalId),
                        d == null ? "" : d.getEarTag()});
                break;
            case MENU_EXIT:
                exitType.setValue(BreedCodes.EXIT_CULL);
                exitDate.setValue(DateFormatter.todayString());
                exitReason.setValue("");
                setErrors(Collections.emptyMap());
                if (exitIdem != null) {
                    exitIdem.reset();
                }
                showExitSheet.call();
                break;
            default:
                break;
        }
    }

    // ===== 繁育记录 =====

    public void onRecordClick(@NonNull BreedingRecordVO r) {
        showRecordMenu.setValue(r);
    }

    public static boolean canEdit(@NonNull BreedingRecordVO r) {
        return r.getChangeState() != null && r.getChangeState().isCanEdit()
                && !BreedCodes.BIZ_BREEDING_EXIT.equals(r.getRecordType());
    }

    /** 记录“编辑”：配种 → B03，妊检 → 本页面板，产仔 → B04，断奶 → B05（修改模式） */
    public void editRecord(@NonNull BreedingRecordVO r) {
        String json = GsonFactory.get().toJson(r);
        String type = r.getRecordType() == null ? "" : r.getRecordType();
        switch (type) {
            case BreedCodes.BIZ_MATING:
                navigate.setValue(NavRequest.forResult(RouterPath.B03).put(BreedExtras.EXTRA_ANIMAL_ID, animalId)
                        .put(BreedExtras.EXTRA_RECORD_ID, r.getRecordId()).put(BreedExtras.EXTRA_RECORD_JSON, json));
                break;
            case BreedCodes.BIZ_PREG_CHECK:
                openEditCheck(r);
                break;
            case BreedCodes.BIZ_FARROWING:
                navigate.setValue(NavRequest.forResult(RouterPath.B04).put(BreedExtras.EXTRA_RECORD_ID, r.getRecordId()));
                break;
            case BreedCodes.BIZ_WEANING:
                navigate.setValue(NavRequest.forResult(RouterPath.B05).put(BreedExtras.EXTRA_ANIMAL_ID, animalId)
                        .put(BreedExtras.EXTRA_RECORD_ID, r.getRecordId()).put(BreedExtras.EXTRA_RECORD_JSON, json));
                break;
            default:
                break;
        }
    }

    private void openEditCheck(@NonNull BreedingRecordVO r) {
        BreedingAnimalDetailVO d = detail.getValue();
        checkRecord = r;
        checkEditMode.setValue(true);
        BreedingAnimalDetailVO.CurrentCycle c = d == null ? null : d.getCurrentCycle();
        checkFirstMating = c == null ? null : c.getFirstMatingDate();
        checkDate.setValue(r.getRecordDate());
        checkResult.setValue(r.getCheckResult());
        checkErrors.setValue(Collections.emptyMap());
        checkSubtitle.setValue(d == null ? "" : BreedTextFormatter.animalLabel(d.getEarTag(), d.getBreedName(),
                d.getParity()) + BreedTextFormatter.DOT + BreedTextFormatter.md(r.getRecordDate()) + "妊检");
        String due = c == null ? null : c.getExpectedDueDate();
        if (due == null && checkFirstMating != null) {
            LocalDate p = BreedTextFormatter.plusDays(checkFirstMating, 114);
            due = p == null ? null : DateFormatter.toDateString(p);
        }
        positiveHint.setValue(due == null ? "转为妊娠" : "转为妊娠，预产期 " + BreedTextFormatter.fullDate(due));
        showCheckSheet.call();
    }

    public void viewBatch(@NonNull BreedingRecordVO r) {
        if (r.getBatchId() != null) {
            navigate.setValue(NavRequest.of(RouterPath.L06).put(RouterPath.EXTRA_BATCH_ID, r.getBatchId()));
        }
    }

    /** 提交修改申请（需审核）→ U01；审核中时只提示 */
    public void requestChange(@NonNull BreedingRecordVO r) {
        if (r.getChangeState() != null && r.getChangeState().getPendingRequestId() != null) {
            showMessage("这条记录已有修改申请在审核中");
            return;
        }
        navigate.setValue(NavRequest.of(RouterPath.U01).put(RouterPath.EXTRA_BIZ_TYPE, r.getRecordType())
                .put(RouterPath.EXTRA_BIZ_ID, r.getRecordId()));
    }

    public void viewChangeLog(@NonNull BreedingRecordVO r) {
        BreedingAnimalDetailVO d = detail.getValue();
        openChangeLog.setValue(new String[]{r.getRecordType(), String.valueOf(r.getRecordId()),
                d == null ? "" : d.getEarTag()});
    }

    // ===== 妊检面板（PregCheckHost） =====

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
        return DateFormatter.parseDate(checkFirstMating);
    }

    @Override
    public void onCheckDatePicked(@Nullable String date) {
        if (date != null) {
            checkDate.setValue(date);
            removeCheckError(BreedFormRules.F_CHECK_DATE);
        }
    }

    @Override
    public void onCheckResultPicked(@NonNull String result) {
        checkResult.setValue(result);
        removeCheckError(BreedFormRules.F_RESULT);
    }

    private void removeCheckError(@NonNull String field) {
        Map<String, String> m = checkErrors.getValue();
        if (m != null && m.containsKey(field)) {
            Map<String, String> next = new LinkedHashMap<>(m);
            next.remove(field);
            checkErrors.setValue(next);
        }
    }

    @Override
    public void submitCheck() {
        BreedingAnimalDetailVO d = detail.getValue();
        if (d == null || Boolean.TRUE.equals(checkSubmitting.getValue())) {
            return;
        }
        Map<String, String> errors = BreedFormRules.pregCheck(checkDate.getValue(), checkFirstMating,
                checkResult.getValue());
        checkErrors.setValue(errors);
        if (!errors.isEmpty()) {
            return;
        }
        boolean positive = BreedCodes.POSITIVE.equals(checkResult.getValue());
        checkSubmitting.setValue(true);
        BreedingRecordVO rec = checkRecord;
        if (rec != null) {
            PregnancyCheckUpdateRequest req = new PregnancyCheckUpdateRequest().setCheckDate(checkDate.getValue())
                    .setResult(checkResult.getValue()).setCheckMethod(rec.getCheckMethod())
                    .setVersion(rec.getVersion() == null ? 1 : rec.getVersion());
            launch(repository.updatePregCheck(rec.getRecordId(), req), r -> {
                checkSubmitting.setValue(false);
                BreedNav.notifyTodo(null, BreedCodes.BIZ_PREG_CHECK, rec.getRecordId(), farmContext.farmIdOrZero());
                dismissCheckSheet.setValue(SubmitSupport.MSG_SAVED_EDIT);
                refresh();
            }, this::onCheckError);
            return;
        }
        BreedingAnimalDetailVO.CurrentCycle c = d.getCurrentCycle();
        PregnancyCheckRequest req = new PregnancyCheckRequest().setAnimalId(d.getAnimalId())
                .setAnimalVersion(d.getVersion()).setCycleId(c == null ? null : c.getCycleId())
                .setCheckDate(checkDate.getValue()).setResult(checkResult.getValue());
        launch(repository.createPregCheck(req, checkIdem.keyFor(req)), r -> onCheckSaved(d, positive, r),
                this::onCheckError);
    }

    private void onCheckSaved(@NonNull BreedingAnimalDetailVO d, boolean positive, @NonNull RegisterResultVO r) {
        checkSubmitting.setValue(false);
        BreedNav.notifyTodo(null, BreedCodes.BIZ_PREG_CHECK, r.getRecordId(), farmContext.farmIdOrZero());
        dismissCheckSheet.setValue(BreedTextFormatter.checkDone(d.getEarTag(), positive));
        refresh();
    }

    private void onCheckError(@NonNull ApiException e) {
        checkSubmitting.setValue(false);
        if (e.getCode() == ErrorCodes.PARAM_INVALID && e.hasFieldErrors()) {
            checkErrors.setValue(e.getFieldErrors());
            return;
        }
        if (ErrorCodes.isNoPermission(e.getCode())) {
            showMessage(SubmitSupport.MSG_NO_PERMISSION);
            noPermission.call();
            return;
        }
        if (e.isNetwork()) {
            showMessage(SubmitSupport.MSG_NETWORK_KEEP);
            return;
        }
        showMessage(e.getMessage());
        if (ErrorCodes.isDataChanged(e.getCode())) {
            dismissCheckSheet.setValue(null);
            refresh();
        }
    }

    @NonNull
    @Override
    public LiveData<String> getDismissCheckSheet() {
        return dismissCheckSheet;
    }

    // ===== 死亡淘汰面板 =====

    public void onExitTypePicked(@NonNull String type) {
        exitType.setValue(type);
    }

    public void onExitDatePicked(@Nullable String date) {
        if (date != null) {
            exitDate.setValue(date);
            clearFieldError(BreedFormRules.F_EXIT_DATE);
        }
    }

    public void onReasonEdited() {
        clearFieldError(BreedFormRules.F_REASON);
    }

    @Nullable
    public LocalDate minExitDate() {
        BreedingAnimalDetailVO d = detail.getValue();
        return d == null ? null : DateFormatter.parseDate(d.getFilingDate());
    }

    /** 面板“确认登记”（API-B09） */
    public void submitExit() {
        BreedingAnimalDetailVO d = detail.getValue();
        if (d == null || Boolean.TRUE.equals(submitting.getValue())) {
            return;
        }
        Map<String, String> errors = BreedFormRules.exit(exitDate.getValue(), d.getFilingDate(), exitReason.getValue());
        if (!errors.isEmpty()) {
            setErrors(errors);
            return;
        }
        setErrors(Collections.emptyMap());
        boolean death = BreedCodes.EXIT_DEATH.equals(exitType.getValue());
        AnimalExitRequest req = new AnimalExitRequest().setExitType(exitType.getValue()).setExitDate(exitDate.getValue())
                .setReason(exitReason.getValue() == null ? null : exitReason.getValue().trim()).setVersion(d.getVersion());
        beginSubmit();
        launch(repository.exitAnimal(animalId, req, exitIdem.keyFor(req)), r -> {
            endSubmit();
            BreedNav.notifyTodo(null, BreedCodes.BIZ_BREEDING_EXIT, animalId, farmContext.farmIdOrZero());
            dismissExitSheet.call();
            finishToList.setValue(BreedTextFormatter.exitDone(d.getEarTag(), death));
        }, e -> {
            if (ErrorCodes.isDataChanged(e.getCode())) {
                dismissExitSheet.call();
            }
            onSubmitError(e, null, null, null);
        });
    }

    @Override
    protected void retrySubmit() {
        submitExit();
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        if (Boolean.TRUE.equals(submitting.getValue()) || detail.getValue() != null) {
            showMessage(SubmitSupport.MSG_NO_PERMISSION);
            noPermission.call();
        } else {
            super.onNoPermission(e);
        }
    }

    @Override
    public void onMessageAction(@NonNull String actionId) {
        if (ACTION_REFRESH.equals(actionId)) {
            refresh();
            return;
        }
        super.onMessageAction(actionId);
    }

    // ===== 对外 LiveData =====

    public LiveData<BreedingAnimalDetailVO> getDetail() {
        return detail;
    }

    public LiveData<String> getHeaderAction() {
        return headerAction;
    }

    public LiveData<List<String>> getMoreMenu() {
        return moreMenu;
    }

    public LiveData<PagingData<BreedingRecordVO>> getRecords() {
        return records;
    }

    public MutableLiveData<String> getExitType() {
        return exitType;
    }

    public MutableLiveData<String> getExitDate() {
        return exitDate;
    }

    public MutableLiveData<String> getExitReason() {
        return exitReason;
    }

    public LiveData<String> getExitSubtitle() {
        return exitSubtitle;
    }

    public LiveData<List<String>> getShowMoreMenu() {
        return showMoreMenu;
    }

    public LiveData<Void> getShowExitSheet() {
        return showExitSheet;
    }

    public LiveData<Void> getDismissExitSheet() {
        return dismissExitSheet;
    }

    public LiveData<Void> getShowCheckSheet() {
        return showCheckSheet;
    }

    public LiveData<BreedingRecordVO> getShowRecordMenu() {
        return showRecordMenu;
    }

    public LiveData<NavRequest> getNavigate() {
        return navigate;
    }

    /** [biz_type, biz_id, 耳号] */
    public LiveData<String[]> getOpenChangeLog() {
        return openChangeLog;
    }

    public LiveData<String> getFinishToList() {
        return finishToList;
    }

    /** 记录变化后的提示（子页面返回） */
    public void onChildResult(@Nullable UiMessage message) {
        if (message != null) {
            showMessage(message);
        }
        refresh();
    }
}
