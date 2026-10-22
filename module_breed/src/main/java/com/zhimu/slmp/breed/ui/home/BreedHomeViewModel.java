package com.zhimu.slmp.breed.ui.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.data.BreedRepository;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.request.BreedingAnimalQuery;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalItemVO;
import com.zhimu.slmp.breed.model.vo.BreedingOverviewVO;
import com.zhimu.slmp.breed.util.BreedTextFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * B01 种畜与待产（技术文档 2.4.1）：分段“母猪档案 24 / 待产 11”、临产横幅、状态 Chip 与耳号搜索（API-B02 分页）、
 * FAB“登记”面板；禽类养殖场转到 B02 待出雏；员工直接显示无权查看。EXTRA_MODE = EXITED 时为“已淘汰和死亡的母猪”只读列表。
 */
public class BreedHomeViewModel extends BaseListViewModel<BreedingAnimalQuery, BreedingAnimalItemVO> {

    public static final String SEGMENT_ARCHIVE = "ARCHIVE";
    public static final String SEGMENT_DUE = "DUE";
    public static final String PAGE_NEW_ANIMAL = "NEW_ANIMAL";
    private static final String KEY_SEGMENT = "segment";

    private final BreedRepository repository;
    private final FarmContext farmContext;

    private final MutableLiveData<BreedingOverviewVO> overview = new MutableLiveData<>();
    private final LiveData<String> dueBanner;
    private MutableLiveData<String> segment = new MutableLiveData<>(SEGMENT_ARCHIVE);
    private final LiveData<String> filterStatus;
    private final MutableLiveData<String> keyword = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> exitedMode = new MutableLiveData<>(false);
    private final LiveData<Long> currentFarmId;

    private final SingleLiveEvent<Void> redirectToHatch = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openArchive = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openRegisterSheet = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> openPage = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> scrollToToday = new SingleLiveEvent<>();

    private final PublishSubject<String> searchInput = PublishSubject.create();

    @Inject
    public BreedHomeViewModel(@NonNull BreedRepository repository, @NonNull FarmContext farmContext) {
        super(new BreedingAnimalQuery(BreedCodes.FILTER_ALL, null));
        this.repository = repository;
        this.farmContext = farmContext;
        dueBanner = Transformations.map(overview, BreedTextFormatter::dueBanner);
        filterStatus = Transformations.map(getFilter(), f -> f == null ? BreedCodes.FILTER_ALL : f.getStatus());
        currentFarmId = Transformations.distinctUntilChanged(Transformations.map(farmContext.getContext(),
                c -> c == null ? null : c.getFarmId()));
        disposables.add(searchInput.debounce(300, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(k -> {
                    BreedingAnimalQuery q = currentFilter();
                    setFilter(q == null ? new BreedingAnimalQuery(null, k) : q.withKeyword(k));
                }));
    }

    @NonNull
    @Override
    protected PagingSource<Integer, BreedingAnimalItemVO> createPagingSource(@Nullable BreedingAnimalQuery filter) {
        return repository.animalSource(filter == null ? new BreedingAnimalQuery(null, null) : filter, null);
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        segment = handle.getLiveData(KEY_SEGMENT, SEGMENT_ARCHIVE);
        boolean exited = BreedExtras.MODE_EXITED.equals(handle.get(RouterPath.EXTRA_MODE));
        exitedMode.setValue(exited);
        if (exited) {
            setFilter(new BreedingAnimalQuery(BreedCodes.FILTER_EXITED, null));
        }
        if (farmContext.isStaff()) {
            setUiState(UiState.noPermission(null, ErrorCodes.ROLE_FORBIDDEN));
            return;
        }
        if (exited) {
            setUiState(UiState.content());
        } else {
            loadOverview();
        }
    }

    /** API-B01：分段数量与临产横幅；禽类养殖场转到 B02（待出雏） */
    public void loadOverview() {
        load(repository.getOverview(), o -> {
            overview.setValue(o);
            if (BreedCodes.KIND_POULTRY.equals(o.getFarmKind()) && !Boolean.TRUE.equals(exitedMode.getValue())) {
                redirectToHatch.call();
            }
        });
    }

    @Override
    public void retry() {
        resetContentLoaded();
        if (Boolean.TRUE.equals(exitedMode.getValue())) {
            refresh();
        } else {
            loadOverview();
            refresh();
        }
    }

    /** 下拉刷新：分段数量、横幅与列表 */
    public void refreshAll() {
        if (!Boolean.TRUE.equals(exitedMode.getValue())) {
            loadOverview();
        }
        refresh();
    }

    /** 切换养殖场后整页重载 */
    public void onFarmChanged() {
        if (farmContext.isStaff()) {
            setUiState(UiState.noPermission(null, ErrorCodes.ROLE_FORBIDDEN));
            return;
        }
        resetContentLoaded();
        retry();
    }

    /** 状态 Chip（ANIMAL_FILTER，不含 EXITED） */
    public void setStatusFilter(@NonNull String status) {
        BreedingAnimalQuery q = currentFilter();
        setFilter(q == null ? new BreedingAnimalQuery(status, null) : q.withStatus(status));
    }

    /** 耳号搜索（300 毫秒防抖） */
    public void search(@Nullable String text) {
        keyword.setValue(text == null ? "" : text);
        searchInput.onNext(text == null ? "" : text.trim());
    }

    public void switchSegment(@NonNull String s) {
        if (!s.equals(segment.getValue())) {
            segment.setValue(s);
        }
    }

    public void onAnimalClick(long animalId) {
        openArchive.setValue(animalId);
    }

    /** 横幅“查看待产”：切到待产分段并滚动到“今天” */
    public void onDueBannerClick() {
        segment.setValue(SEGMENT_DUE);
        scrollToToday.call();
    }

    public void onFabClick() {
        openRegisterSheet.call();
    }

    /** 面板选择：MATING / FARROWING / WEANING / NEW_ANIMAL */
    public void onRegisterPicked(@Nullable String type) {
        if (type != null) {
            openPage.setValue(type);
        }
    }

    /** 子页面返回：提示并只刷新受影响数据 */
    public void onChildResult(@Nullable UiMessage message) {
        if (message != null) {
            showMessage(message);
        }
        refreshAll();
    }

    /** 列表为空时的文案：没有母猪 / 筛选为空 / 搜索为空 / 已淘汰列表为空 */
    @NonNull
    public String emptyText() {
        BreedingAnimalQuery q = currentFilter();
        if (Boolean.TRUE.equals(exitedMode.getValue())) {
            return "没有已淘汰或死亡的母猪。";
        }
        if (q != null && q.getKeyword() != null) {
            return "没有找到耳号为 " + q.getKeyword() + " 的母猪";
        }
        if (q != null && !BreedCodes.FILTER_ALL.equals(q.getStatus())) {
            return "没有" + statusName(q.getStatus()) + "的" + animalLabel() + "。切换到“全部”查看其他" + animalLabel() + "。";
        }
        return "还没有" + animalLabel() + "档案。先新增" + animalLabel() + "，再登记配种。";
    }

    /** 没有任何母猪时显示“新增母猪” */
    public boolean showNewAnimalOnEmpty() {
        BreedingAnimalQuery q = currentFilter();
        return !Boolean.TRUE.equals(exitedMode.getValue()) && (q == null
                || (q.getKeyword() == null && BreedCodes.FILTER_ALL.equals(q.getStatus())));
    }

    @NonNull
    public String animalLabel() {
        BreedingOverviewVO o = overview.getValue();
        return o == null || o.getAnimalLabel() == null ? "母猪" : o.getAnimalLabel();
    }

    @NonNull
    static String statusName(@Nullable String status) {
        if (BreedCodes.EMPTY.equals(status)) {
            return "空怀";
        }
        if (BreedCodes.MATED.equals(status)) {
            return "已配种";
        }
        if (BreedCodes.PREGNANT.equals(status)) {
            return "妊娠";
        }
        if (BreedCodes.LACTATING.equals(status)) {
            return "哺乳";
        }
        return "";
    }

    @Nullable
    public String farmName() {
        FarmContextVO c = farmContext.current();
        return c == null ? null : c.getName();
    }

    // ===== 对外 LiveData =====

    public LiveData<BreedingOverviewVO> getOverview() {
        return overview;
    }

    public LiveData<String> getDueBanner() {
        return dueBanner;
    }

    public LiveData<String> getSegment() {
        return segment;
    }

    public LiveData<String> getFilterStatus() {
        return filterStatus;
    }

    public MutableLiveData<String> getKeyword() {
        return keyword;
    }

    public LiveData<Boolean> getExitedMode() {
        return exitedMode;
    }

    public LiveData<Long> getCurrentFarmId() {
        return currentFarmId;
    }

    public LiveData<Void> getRedirectToHatch() {
        return redirectToHatch;
    }

    public LiveData<Long> getOpenArchive() {
        return openArchive;
    }

    public LiveData<Void> getOpenRegisterSheet() {
        return openRegisterSheet;
    }

    public LiveData<String> getOpenPage() {
        return openPage;
    }

    public LiveData<Void> getScrollToToday() {
        return scrollToToday;
    }
}
