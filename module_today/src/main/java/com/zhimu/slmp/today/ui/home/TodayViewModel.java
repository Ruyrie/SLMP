package com.zhimu.slmp.today.ui.home;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.PagingData;
import androidx.paging.PagingDataTransforms;
import androidx.paging.rxjava3.PagingRx;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.FarmBriefVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.common.service.TabRegistry;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.common.session.AppSettings;
import com.zhimu.slmp.today.data.HomeRepository;
import com.zhimu.slmp.today.data.TodoRepository;
import com.zhimu.slmp.today.model.vo.HomeVO;
import com.zhimu.slmp.today.model.vo.MyPenVO;
import com.zhimu.slmp.today.model.vo.OwnerMetricsVO;
import com.zhimu.slmp.today.model.vo.StaffMetricsVO;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.service.TodayServiceImpl;
import com.zhimu.slmp.today.util.CalendarMode;
import com.zhimu.slmp.today.util.CalendarRange;
import com.zhimu.slmp.today.util.LinkNavigator;
import com.zhimu.slmp.today.util.NavTarget;
import com.zhimu.slmp.today.util.Texts;
import com.zhimu.slmp.today.util.TodoTypeStyle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * T01 今日首页（TodayFragment、QuickRegisterSheet），技术文档 2.4.1。
 * 首页与“今天和已逾期”并行请求；HomeVO 按 farm_id 写内存缓存，10 秒未返回时有缓存显示缓存并提示数据时刻。
 * 登记回流：对应项淡出后从列表移除（本地移除集合），指标减 1 并静默刷新。
 */
public class TodayViewModel extends BaseViewModel {

    private static final String TAG = "TodayViewModel";

    public static final String CARD_STOCK = "stock";
    public static final String CARD_TODAY = "today";
    public static final String CARD_WARNING = "warning";
    public static final String CARD_PENS = "pens";
    public static final String CARD_OVERDUE = "overdue";

    /** K01 推荐问题参数（RouterPath 中暂无，见交付说明） */
    public static final String EXTRA_QUESTION = "question";

    static final String MSG_REFRESH_FAILED = "刷新失败，请检查网络";
    static final long HOME_TIMEOUT_SECONDS = 10;

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final HomeRepository homeRepository;
    private final TodoRepository todoRepository;
    private final TodayServiceImpl todoService;
    private final LinkNavigator linkNavigator;
    private final AppSettings appSettings;
    private final IPushService pushService;

    private final MutableLiveData<HomeVO> home = new MutableLiveData<>();
    private final MutableLiveData<String> greetingText = new MutableLiveData<>("");
    private final MutableLiveData<String> roleLine = new MutableLiveData<>("");
    private final MutableLiveData<String> avatarText = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> managerView = new MutableLiveData<>(false);
    private final MutableLiveData<OwnerMetricsVO> ownerMetrics = new MutableLiveData<>();
    private final MutableLiveData<StaffMetricsVO> staffMetrics = new MutableLiveData<>();
    private final MutableLiveData<String> aiHint = new MutableLiveData<>();
    private final MutableLiveData<List<MyPenVO>> myPens = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> farmBlocked = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasPen = new MutableLiveData<>(true);
    private final MutableLiveData<PagingData<TodoItemVO>> todoPaging = new MutableLiveData<>();
    private final MutableLiveData<Integer> todoTotal = new MutableLiveData<>(0);
    private final MutableLiveData<FarmBriefVO> currentFarm = new MutableLiveData<>();
    private final MutableLiveData<List<QuickRegisterItem>> quickItems =
            new MutableLiveData<>(QuickRegisterItem.forRole(false));

    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> leavingTodoId = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> openQuickRegister = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> switchTab = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> askNotifyPermission = new SingleLiveEvent<>();

    /** 登记回流后在本地移除的待办 */
    private final BehaviorProcessor<Set<Long>> removedIds = BehaviorProcessor.createDefault(Collections.emptySet());
    /** 已加载的待办（登记回流时判断指标减哪一项） */
    private final Map<Long, TodoItemVO> loadedItems = new ConcurrentHashMap<>();
    @Nullable
    private Disposable pagingDisposable;
    private long loadedFarmId = -1;
    private boolean notifyGuideChecked;
    /** 当前养殖场的首页已显示过（之后的加载失败只提示） */
    private boolean contentShown;

    @Inject
    public TodayViewModel(@NonNull HomeRepository homeRepository, @NonNull TodoRepository todoRepository,
                          @NonNull TodayServiceImpl todoService, @NonNull LinkNavigator linkNavigator,
                          @NonNull AppSettings appSettings, @NonNull IPushService pushService) {
        this.homeRepository = homeRepository;
        this.todoRepository = todoRepository;
        this.todoService = todoService;
        this.linkNavigator = linkNavigator;
        this.appSettings = appSettings;
        this.pushService = pushService;
        disposables.add(todoService.todoChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onTodoChanged, t -> Log.w(TAG, "待办回流订阅失败", t)));
    }

    // ===================== 对外 LiveData =====================

    public LiveData<HomeVO> getHome() {
        return home;
    }

    public LiveData<String> getGreetingText() {
        return greetingText;
    }

    public LiveData<String> getRoleLine() {
        return roleLine;
    }

    public LiveData<String> getAvatarText() {
        return avatarText;
    }

    public LiveData<Boolean> getManagerView() {
        return managerView;
    }

    public LiveData<OwnerMetricsVO> getOwnerMetrics() {
        return ownerMetrics;
    }

    public LiveData<StaffMetricsVO> getStaffMetrics() {
        return staffMetrics;
    }

    public LiveData<String> getAiHint() {
        return aiHint;
    }

    public LiveData<List<MyPenVO>> getMyPens() {
        return myPens;
    }

    public LiveData<Boolean> getFarmBlocked() {
        return farmBlocked;
    }

    public LiveData<Boolean> getHasPen() {
        return hasPen;
    }

    /** 分页数据（首次读取时开始分页） */
    public LiveData<PagingData<TodoItemVO>> getTodoPaging() {
        startPaging();
        return todoPaging;
    }

    public LiveData<Integer> getTodoTotal() {
        return todoTotal;
    }

    /** 全应用唯一的未读数（ITodoService） */
    public LiveData<Integer> getUnreadCount() {
        return todoService.unreadCount();
    }

    public LiveData<FarmBriefVO> getCurrentFarm() {
        return currentFarm;
    }

    public LiveData<List<QuickRegisterItem>> getQuickItems() {
        return quickItems;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    public SingleLiveEvent<Long> getLeavingTodoId() {
        return leavingTodoId;
    }

    public SingleLiveEvent<Void> getOpenQuickRegister() {
        return openQuickRegister;
    }

    public SingleLiveEvent<String> getSwitchTab() {
        return switchTab;
    }

    public SingleLiveEvent<Void> getAskNotifyPermission() {
        return askNotifyPermission;
    }

    // ===================== 加载 =====================

    /** 首次加载（骨架屏）；已有内容时为刷新 */
    public void load() {
        Single<HomeVO> source = homeRepository.getHome()
                .timeout(HOME_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .onErrorResumeNext(this::fallbackToCache);
        load(source, this::applyHome);
    }

    /** 下拉刷新：首页与列表一起重新获取，失败保留旧内容 */
    public void refresh() {
        removedIds.onNext(Collections.emptySet());
        load();
        todoRepository.invalidateToday();
    }

    @Override
    public void retry() {
        load();
        todoRepository.invalidateToday();
    }

    /** 当前养殖场变化（F01 切换、跨场跳转）：显示骨架屏并重新加载全部数据 */
    public void onFarmSwitched(@Nullable FarmContextVO farm) {
        long id = farm == null ? 0 : farm.getFarmId();
        if (id == loadedFarmId) {
            return;
        }
        boolean first = loadedFarmId < 0;
        loadedFarmId = id;
        if (first) {
            load();
            return;
        }
        resetContentLoaded();
        contentShown = false;
        removedIds.onNext(Collections.emptySet());
        loadedItems.clear();
        load();
        todoRepository.invalidateToday();
    }

    /** 应用回到前台：刷新未读数与首页 */
    public void onAppForeground() {
        todoService.refreshUnread();
        silentRefresh(false);
    }

    /**
     * 网络失败或 10 秒超时：已有内容时保留并提示“刷新失败，请检查网络”；没有内容但有缓存时显示缓存并提示数据时刻；
     * 其余显示“今日待办加载失败”。
     */
    @NonNull
    private Single<HomeVO> fallbackToCache(@NonNull Throwable t) {
        boolean timeout = t instanceof java.util.concurrent.TimeoutException;
        ApiException e = ApiException.from(t);
        if (!timeout && !e.isNetwork()) {
            return Single.error(t);
        }
        if (contentShown) {
            return Single.error(new ApiException(ErrorCodes.UNKNOWN, MSG_REFRESH_FAILED));
        }
        HomeRepository.Cached cached = homeRepository.getCached(loadedFarmId < 0 ? 0 : loadedFarmId);
        if (cached != null) {
            String hm = java.time.Instant.ofEpochMilli(cached.fetchedAt).atZone(DateFormatter.ZONE).format(HM);
            showMessage("网络连接不稳定，显示的是 " + hm + " 的数据");
            return Single.just(cached.home);
        }
        return Single.error(new ApiException(ErrorCodes.NETWORK, ApiException.MSG_NETWORK));
    }

    @VisibleForTesting
    void applyHome(@NonNull HomeVO vo) {
        contentShown = true;
        home.setValue(vo);
        greetingText.setValue(Texts.orEmpty(vo.getGreeting()) + "，" + Texts.orEmpty(vo.getUserName()));
        LocalDate date = DateFormatter.parseDate(vo.getServerTime());
        String datePart = date == null ? "" : CalendarRange.dayText(date);
        roleLine.setValue(Texts.join(" · ", java.util.Arrays.asList(datePart, vo.getRoleText())));
        avatarText.setValue(Texts.orEmpty(vo.getAvatarText()));
        boolean manager = vo.getOwnerMetrics() != null;
        managerView.setValue(manager);
        ownerMetrics.setValue(vo.getOwnerMetrics());
        staffMetrics.setValue(vo.getStaffMetrics());
        aiHint.setValue(vo.getAiHint());
        myPens.setValue(vo.getMyPens());
        farmBlocked.setValue(vo.isFarmBlocked());
        hasPen.setValue(vo.isHasPen());
        currentFarm.setValue(vo.getFarm());
        quickItems.setValue(QuickRegisterItem.forRole(manager));
        todoService.setUnread(vo.getUnreadCount());
        if (vo.getFarm() != null && loadedFarmId <= 0) {
            loadedFarmId = vo.getFarm().getFarmId();
        }
    }

    /** 40306 养殖场已冻结也用 U03 无权查看版式 */
    @Override
    protected void applyErrorState(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.FARM_FROZEN) {
            setUiState(UiState.noPermission(e.getMessage(), e.getCode()));
            return;
        }
        super.applyErrorState(e);
    }

    /** 静默刷新首页（不显示骨架与刷新指示），listToo 为真时同时刷新列表 */
    private void silentRefresh(boolean listToo) {
        if (home.getValue() == null) {
            return;
        }
        launch(homeRepository.getHome(), this::applyHome, e -> Log.i(TAG, "静默刷新失败：" + e.getMessage()));
        if (listToo) {
            todoRepository.invalidateToday();
        }
    }

    private void startPaging() {
        if (pagingDisposable != null) {
            return;
        }
        Flowable<PagingData<TodoItemVO>> cached = PagingRx.cachedIn(
                todoRepository.todayPager(TodoRepository.FILTER_ALL, vo -> todoTotal.postValue(vo.getTotal())),
                ViewModelKt.getViewModelScope(this));
        Executor executor = command -> Schedulers.computation().scheduleDirect(command);
        pagingDisposable = Flowable.combineLatest(cached, removedIds,
                        (data, removed) -> PagingDataTransforms.filter(data, executor, item -> {
                            loadedItems.put(item.getTodoId(), item);
                            return !removed.contains(item.getTodoId());
                        }))
                .subscribe(todoPaging::postValue, t -> Log.w(TAG, "待办分页失败", t));
        disposables.add(pagingDisposable);
    }

    // ===================== 点击 =====================

    public void onTodoClick(@NonNull TodoItemVO item) {
        launch(linkNavigator.forTodo(item), navEvent::setValue);
    }

    public void onTodoLongClick(@NonNull TodoItemVO item) {
        navEvent.setValue(NavTarget.todoDetail(item.getTodoId()));
    }

    /** 指标卡：存栏进 L03；负责栏舍切到“我的栏舍”；今日待办、预警、已逾期进 T03 日视图并选中今天 */
    public void onMetricClick(@NonNull String card) {
        switch (card) {
            case CARD_STOCK:
                navEvent.setValue(NavTarget.of("L03"));
                break;
            case CARD_PENS:
                switchTab.setValue(TabRegistry.MY_PENS);
                break;
            default:
                Map<String, String> p = new HashMap<>();
                p.put(RouterPath.EXTRA_MODE, CalendarMode.DAY.name());
                LocalDate today = serverToday();
                if (today != null) {
                    p.put(RouterPath.EXTRA_DATE, DateFormatter.toDateString(today));
                }
                navEvent.setValue(new NavTarget("T03", p, null));
                break;
        }
    }

    public void onCalendarClick() {
        Map<String, String> p = new HashMap<>();
        LocalDate today = serverToday();
        if (today != null) {
            p.put(RouterPath.EXTRA_DATE, DateFormatter.toDateString(today));
        }
        navEvent.setValue(new NavTarget("T03", p, null));
    }

    /** AI 横幅：进入 K01，推荐问题填入输入框，不自动发送 */
    public void onAiBannerClick() {
        navEvent.setValue(NavTarget.of("K01", EXTRA_QUESTION, aiHint.getValue()));
    }

    public void onAvatarClick() {
        switchTab.setValue(TabRegistry.MINE);
    }

    /** 员工栏舍：有在栏批次进 L06（link），没有时切到“我的栏舍” */
    public void onPenClick(@NonNull MyPenVO pen) {
        if (!pen.getBatches().isEmpty() && pen.getLink() != null && pen.getLink().getRoute() != null) {
            navEvent.setValue(new NavTarget(pen.getLink().getRoute(), pen.getLink().getParams(), null));
            return;
        }
        switchTab.setValue(TabRegistry.MY_PENS);
    }

    public void onFabClick() {
        openQuickRegister.call();
    }

    public void onQuickItemClick(@NonNull QuickRegisterItem item) {
        if (QuickRegisterItem.ROUTE_STOCK_IN.equals(item.getRoute())) {
            return;
        }
        navEvent.setValue(NavTarget.of(item.getRoute()));
    }

    public void onAddPenClick() {
        navEvent.setValue(NavTarget.of("L05"));
    }

    public void onViewAuditClick() {
        navEvent.setValue(NavTarget.of("F03"));
    }

    // ===================== 登记回流 =====================

    /** 登记页成功后经 ITodoService 回流：对应项淡出、指标减 1、静默刷新 */
    public void onTodoChanged(@NonNull TodoChange change) {
        Long todoId = change.getTodoId();
        if (todoId == null) {
            silentRefresh(true);
            return;
        }
        if (removedIds.getValue() != null && removedIds.getValue().contains(todoId)) {
            return;
        }
        TodoItemVO item = loadedItems.get(todoId);
        boolean overdue = item != null && TodoTypeStyle.STATUS_OVERDUE.equals(item.getDisplayStatus());
        boolean warning = item != null && TodoTypeStyle.CATEGORY_WARNING.equals(item.getCategory());
        OwnerMetricsVO om = ownerMetrics.getValue();
        if (om != null) {
            ownerMetrics.setValue(om.minusTodo(overdue, warning));
        }
        StaffMetricsVO sm = staffMetrics.getValue();
        if (sm != null) {
            staffMetrics.setValue(sm.minusTodo(overdue));
        }
        Integer total = todoTotal.getValue();
        if (total != null && total > 0) {
            todoTotal.setValue(total - 1);
        }
        leavingTodoId.setValue(todoId);
    }

    /** 淡出动画结束后由页面调用：从列表移除并静默刷新 */
    public void removeTodoLocally(long todoId) {
        Set<Long> set = new HashSet<>(removedIds.getValue() == null ? Collections.emptySet() : removedIds.getValue());
        set.add(todoId);
        removedIds.onNext(Collections.unmodifiableSet(set));
        silentRefresh(false);
    }

    // ===================== 通知权限 =====================

    /**
     * 首次进入后由页面调用（Android 13 及以上）：未授权且没有展示过说明时，发出 askNotifyPermission（先说明再申请）。
     */
    public void checkNotifyGuide(boolean needAsk) {
        if (notifyGuideChecked) {
            return;
        }
        notifyGuideChecked = true;
        if (needAsk && !appSettings.isNotifyGuideShown()) {
            appSettings.setNotifyGuideShown(true);
            askNotifyPermission.call();
        }
    }

    /** 权限申请结果：重新上报设备（API-T20） */
    public void onNotifyPermissionResult(boolean granted) {
        pushService.syncDevice();
    }

    // ===================== 工具 =====================

    /** 服务端“今天”（server_time 的日期） */
    @Nullable
    public LocalDate serverToday() {
        HomeVO vo = home.getValue();
        return vo == null ? null : DateFormatter.parseDate(vo.getServerTime());
    }

    @VisibleForTesting
    Set<Long> removedIdsSnapshot() {
        Set<Long> s = removedIds.getValue();
        return s == null ? Collections.emptySet() : s;
    }

    /** 首页提示（页面在缓存提示之外的场景使用） */
    void postMessage(@NonNull String text) {
        showMessage(UiMessage.of(text));
    }
}
