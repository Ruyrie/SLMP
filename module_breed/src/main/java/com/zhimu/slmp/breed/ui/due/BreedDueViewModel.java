package com.zhimu.slmp.breed.ui.due;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingDataTransforms;
import androidx.paging.PagingLiveData;
import androidx.paging.PagingSource;

import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.data.BreedRepository;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.request.BreedingDueQuery;
import com.zhimu.slmp.breed.model.vo.BreedingOverviewVO;
import com.zhimu.slmp.breed.model.vo.FarrowDueItemVO;
import com.zhimu.slmp.breed.model.vo.HatchDueItemVO;
import com.zhimu.slmp.breed.util.BreedTextFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.network.model.PageVO;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * B02 待产与待出雏（技术文档 2.4.2）：分段“待产 / 待出雏”（API-B22、B23 分页，分组标题“今天 · 1 头”）、
 * 提醒规则说明（按角色）、跨养殖场横幅（API-B01 switch_farms，单个候选直接切换，多个打开 F01）、4 种空状态。
 * 员工只读版：只含负责栏舍，没有登记按钮，列表项不可点。B01 的“待产”分段复用本 ViewModel 的待产列表（不调 start）。
 */
public class BreedDueViewModel extends BaseViewModel {

    private final BreedRepository repository;
    private final FarmContext farmContext;

    private final MutableLiveData<String> tab = new MutableLiveData<>();
    private final MutableLiveData<BreedingOverviewVO> overview = new MutableLiveData<>();
    private final LiveData<Boolean> isStaff;
    private final LiveData<String> role;
    private final MutableLiveData<BreedingDueQuery> query = new MutableLiveData<>(new BreedingDueQuery(null, 0));
    private final LiveData<PagingData<DueRow>> farrowDue;
    private final LiveData<PagingData<DueRow>> hatchDue;
    private final MutableLiveData<Map<String, Integer>> farrowCounts = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Map<String, Integer>> hatchCounts = new MutableLiveData<>(Collections.emptyMap());
    private final MediatorLiveData<String> ruleText = new MediatorLiveData<>();
    private final MediatorLiveData<String> switchBanner = new MediatorLiveData<>();
    private final MutableLiveData<String> keyword = new MutableLiveData<>("");
    private final LiveData<Long> currentFarmId;

    private final SingleLiveEvent<Long> openFarrowing = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openArchive = new SingleLiveEvent<>();
    /** 值为 0 时新建入孵 */
    private final SingleLiveEvent<Long> openIncubation = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openFarmPicker = new SingleLiveEvent<>();

    private final PublishSubject<String> searchInput = PublishSubject.create();
    @Nullable
    private PagingSource<Integer, FarrowDueItemVO> farrowSource;
    @Nullable
    private PagingSource<Integer, HatchDueItemVO> hatchSource;
    @Nullable
    private String presetTab;
    private boolean started;
    private long reloadToken;

    @Inject
    public BreedDueViewModel(@NonNull BreedRepository repository, @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.farmContext = farmContext;
        LiveData<FarmContextVO> ctx = farmContext.getContext();
        isStaff = Transformations.map(ctx, c -> c != null && FarmContextVO.ROLE_STAFF.equals(c.getRole()));
        role = Transformations.map(ctx, c -> c == null ? null : c.getRole());
        currentFarmId = Transformations.distinctUntilChanged(Transformations.map(ctx,
                c -> c == null ? null : c.getFarmId()));
        ruleText.addSource(role, r -> ruleText.setValue(BreedTextFormatter.ruleText(r, tab.getValue())));
        ruleText.addSource(tab, t -> ruleText.setValue(BreedTextFormatter.ruleText(role.getValue(), t)));
        switchBanner.addSource(overview, o -> switchBanner.setValue(BreedTextFormatter.switchBanner(farmName(), o)));

        Executor executor = command -> Schedulers.computation().scheduleDirect(command);
        PagingConfig config = new PagingConfig(BaseListViewModel.PAGE_SIZE, BaseListViewModel.PREFETCH_DISTANCE,
                false, BaseListViewModel.PAGE_SIZE);
        farrowDue = Transformations.switchMap(query, q -> {
            Pager<Integer, FarrowDueItemVO> pager = new Pager<>(config, () -> {
                farrowSource = repository.farrowDueSource(q, page -> farrowCounts.postValue(countsOf(page)));
                return farrowSource;
            });
            LiveData<PagingData<DueRow>> rows = Transformations.map(PagingLiveData.getLiveData(pager),
                    data -> withHeaders(PagingDataTransforms.map(data, executor, item -> DueRow.of(item)), executor));
            return PagingLiveData.cachedIn(rows, ViewModelKt.getViewModelScope(this));
        });
        hatchDue = Transformations.switchMap(query, q -> {
            Pager<Integer, HatchDueItemVO> pager = new Pager<>(config, () -> {
                hatchSource = repository.hatchDueSource(q, page -> hatchCounts.postValue(countsOf(page)));
                return hatchSource;
            });
            LiveData<PagingData<DueRow>> rows = Transformations.map(PagingLiveData.getLiveData(pager),
                    data -> withHeaders(PagingDataTransforms.map(data, executor, item -> DueRow.of(item)), executor));
            return PagingLiveData.cachedIn(rows, ViewModelKt.getViewModelScope(this));
        });
        disposables.add(searchInput.debounce(300, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(k -> query.setValue(new BreedingDueQuery(k, reloadToken))));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        presetTab = handle.get(BreedExtras.EXTRA_TAB);
        if (presetTab != null) {
            tab.setValue(presetTab);
        }
    }

    @NonNull
    private static <T> Map<String, Integer> countsOf(@NonNull PageVO<T> page) {
        return page.getTabCounts() == null ? Collections.emptyMap() : page.getTabCounts();
    }

    /** group 变化处插入分组标题（跨页不重复） */
    @NonNull
    static PagingData<DueRow> withHeaders(@NonNull PagingData<DueRow> rows, @NonNull Executor executor) {
        return PagingDataTransforms.insertSeparators(rows, executor, (before, after) -> {
            if (after == null || after.isHeader()) {
                return null;
            }
            if (before == null || !Objects.equals(before.getGroup(), after.getGroup())) {
                return DueRow.header(after.getGroup(), after.getGroupText());
            }
            return null;
        });
    }

    /** B02 打开时调用：取 API-B01（分段数量、默认分段、跨场横幅） */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        loadOverview();
    }

    public void loadOverview() {
        load(repository.getOverview(), o -> {
            overview.setValue(o);
            if (tab.getValue() == null) {
                tab.setValue(BreedCodes.KIND_POULTRY.equals(o.getFarmKind()) ? BreedExtras.TAB_HATCH
                        : BreedExtras.TAB_FARROW);
            }
        });
    }

    @Override
    public void retry() {
        resetContentLoaded();
        loadOverview();
        refreshLists();
    }

    /** 下拉刷新：分段数量、横幅与当前列表 */
    public void refresh() {
        if (started) {
            loadOverview();
        }
        refreshLists();
    }

    private void refreshLists() {
        if (farrowSource != null) {
            farrowSource.invalidate();
        }
        if (hatchSource != null) {
            hatchSource.invalidate();
        }
    }

    /** 切换养殖场后整页重载并重建分页 */
    public void onFarmChanged() {
        reloadToken++;
        query.setValue(new BreedingDueQuery(keyword.getValue(), reloadToken));
        if (started) {
            resetContentLoaded();
            loadOverview();
        }
    }

    public void switchTab(@NonNull String t) {
        if (!t.equals(tab.getValue())) {
            tab.setValue(t);
        }
    }

    /** 搜索栏输入（300 毫秒防抖） */
    public void search(@Nullable String text) {
        keyword.setValue(text == null ? "" : text);
        searchInput.onNext(text == null ? "" : text.trim());
    }

    public void onRegisterFarrow(@NonNull FarrowDueItemVO item) {
        if (item.isCanRegister() && !isStaffNow()) {
            openFarrowing.setValue(item.getAnimalId());
        }
    }

    /** 员工版不设监听；这里再按标记兜底 */
    public void onItemClick(@NonNull DueRow row) {
        if (isStaffNow()) {
            return;
        }
        if (row.getFarrow() != null && row.getFarrow().isCanOpenArchive()) {
            openArchive.setValue(row.getFarrow().getAnimalId());
        } else if (row.getHatch() != null && row.getHatch().isCanOpen()) {
            openIncubation.setValue(row.getHatch().getIncubationId());
        }
    }

    public void onRegisterHatch(@NonNull HatchDueItemVO item) {
        if (item.isCanRegister() && !isStaffNow()) {
            openIncubation.setValue(item.getIncubationId());
        }
    }

    public void onRegisterIncubation() {
        if (!isStaffNow()) {
            openIncubation.setValue(0L);
        }
    }

    /** 横幅“切换”：单个候选直接切换（API-F03），多个打开 F01 */
    public void onSwitchFarm() {
        BreedingOverviewVO o = overview.getValue();
        if (o == null || o.getSwitchFarms().isEmpty()) {
            return;
        }
        if (o.getSwitchFarms().size() > 1) {
            openFarmPicker.call();
            return;
        }
        IFarmContextService service = ServiceHub.get(IFarmContextService.class);
        if (service == null) {
            showMessage("暂时无法切换养殖场，请从顶部养殖场切换器切换");
            return;
        }
        launch(service.switchTo(o.getSwitchFarms().get(0).getFarmId()), ctx -> {
            tab.setValue(BreedExtras.TAB_HATCH);
            showMessage("已切换到" + ctx.getName());
        });
    }

    private boolean isStaffNow() {
        return farmContext.isStaff();
    }

    @Nullable
    private String farmName() {
        FarmContextVO c = farmContext.current();
        return c == null ? null : c.getName();
    }

    /** 列表为空时的文案（UI 2.5 节 4 种空状态） */
    @NonNull
    public String emptyText(@Nullable String t) {
        boolean staff = isStaffNow();
        if (BreedExtras.TAB_HATCH.equals(t)) {
            if (staff) {
                return "你负责的栏舍里没有待出雏的孵化记录。";
            }
            BreedingOverviewVO o = overview.getValue();
            if (o != null && BreedCodes.KIND_POULTRY.equals(o.getFarmKind())) {
                return "还没有入孵记录。登记入孵后，这里会按预计出雏日提醒你。";
            }
            return BreedTextFormatter.nz(farmName()) + "没有孵化记录。禽类养殖场登记入孵后，会按预计出雏日出现在这里。";
        }
        return staff ? "你负责的栏舍里没有待产的母猪。" : "没有待产的母猪。妊检结果为阳性后，母猪会按预产期出现在这里。";
    }

    /** “登记入孵”按钮：禽类养殖场的所有者、场长 */
    public boolean canRegisterIncubation() {
        BreedingOverviewVO o = overview.getValue();
        return !isStaffNow() && o != null && BreedCodes.KIND_POULTRY.equals(o.getFarmKind());
    }

    // ===== 对外 LiveData =====

    public LiveData<String> getTab() {
        return tab;
    }

    public LiveData<BreedingOverviewVO> getOverview() {
        return overview;
    }

    public LiveData<Boolean> getIsStaff() {
        return isStaff;
    }

    /** 当前角色（提醒规则说明的称呼） */
    public LiveData<String> getRole() {
        return role;
    }

    public LiveData<PagingData<DueRow>> getFarrowDue() {
        return farrowDue;
    }

    public LiveData<PagingData<DueRow>> getHatchDue() {
        return hatchDue;
    }

    public LiveData<Map<String, Integer>> getFarrowCounts() {
        return farrowCounts;
    }

    public LiveData<Map<String, Integer>> getHatchCounts() {
        return hatchCounts;
    }

    public LiveData<String> getRuleText() {
        return ruleText;
    }

    public LiveData<String> getSwitchBanner() {
        return switchBanner;
    }

    public MutableLiveData<String> getKeyword() {
        return keyword;
    }

    public LiveData<Long> getCurrentFarmId() {
        return currentFarmId;
    }

    public LiveData<Long> getOpenFarrowing() {
        return openFarrowing;
    }

    public LiveData<Long> getOpenArchive() {
        return openArchive;
    }

    public LiveData<Long> getOpenIncubation() {
        return openIncubation;
    }

    public LiveData<Void> getOpenFarmPicker() {
        return openFarmPicker;
    }

    /** 本地筛选用的分组计数（第 1 页 tab_counts） */
    @NonNull
    public static String groupTitle(@NonNull DueRow header, @Nullable Map<String, Integer> counts, boolean hatch) {
        Integer n = counts == null ? null : counts.get(header.getGroup());
        return BreedTextFormatter.groupTitle(header.getGroupText(), n, hatch ? "批" : "头");
    }
}
