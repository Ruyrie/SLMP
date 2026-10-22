package com.zhimu.slmp.today.ui.calendar;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.PagingData;
import androidx.paging.rxjava3.PagingRx;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.today.data.HomeRepository;
import com.zhimu.slmp.today.data.TodayPrefs;
import com.zhimu.slmp.today.data.TodoRepository;
import com.zhimu.slmp.today.model.vo.CalendarDayVO;
import com.zhimu.slmp.today.model.vo.HomeVO;
import com.zhimu.slmp.today.model.vo.TodoCalendarResult;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.model.vo.TodoWeeklyResult;
import com.zhimu.slmp.today.model.vo.WeeklyDayVO;
import com.zhimu.slmp.today.service.TodayServiceImpl;
import com.zhimu.slmp.today.util.CalendarMode;
import com.zhimu.slmp.today.util.CalendarRange;
import com.zhimu.slmp.today.util.LinkNavigator;
import com.zhimu.slmp.today.util.NavTarget;
import com.zhimu.slmp.today.util.Texts;
import com.zhimu.slmp.today.util.TodoTypeStyle;
import com.zhimu.slmp.today.util.WeekGroup;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.disposables.SerialDisposable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * T03 养殖日历（CalendarActivity），技术文档 2.4.2、2.6.3。
 * <ul>
 * <li>月：API-T03（含本月的完整周）+ API-T04 选中日期分页；周：API-T03（7 天）+ API-T05 每天前 5 项；
 * 日：API-T03（7 天）+ API-T04 时间轴，查看今天时另加 API-T06 已逾期组；</li>
 * <li>“今天”以 T01 的 server_time 与 API-T03 的 is_today 为准，不用手机时间；</li>
 * <li>上一组请求经 SerialDisposable 取消，避免旧结果覆盖新视图；视图与范围记在 zmt_settings 与 SavedStateHandle。</li>
 * </ul>
 */
public class CalendarViewModel extends BaseViewModel {

    private static final String TAG = "CalendarViewModel";

    public static final String SCOPE_CURRENT = "CURRENT";
    public static final String SCOPE_ALL = "ALL";
    public static final String EXTRA_CATEGORY = "category";
    static final int PER_DAY = 5;
    private static final String STATE_MODE = "today_calendar_mode";
    private static final String STATE_SCOPE = "today_calendar_scope";
    private static final String STATE_DATE = "today_calendar_date";

    private final TodoRepository todoRepository;
    private final HomeRepository homeRepository;
    private final TodayPrefs prefs;
    private final FarmContext farmContext;
    private final LinkNavigator linkNavigator;

    private final MutableLiveData<CalendarMode> mode = new MutableLiveData<>(CalendarMode.MONTH);
    private final MutableLiveData<String> scope = new MutableLiveData<>(SCOPE_CURRENT);
    private final MutableLiveData<LocalDate> selectedDate = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> todayDate = new MutableLiveData<>();
    private final MutableLiveData<YearMonth> month = new MutableLiveData<>();
    private final MutableLiveData<String> periodText = new MutableLiveData<>("");
    private final MutableLiveData<Map<LocalDate, CalendarDayVO>> gridDays = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<String> dayHeader = new MutableLiveData<>("");
    private final MutableLiveData<Integer> dayTotal = new MutableLiveData<>(0);
    private final MutableLiveData<PagingData<TodoItemVO>> dayPaging = new MutableLiveData<>();
    private final MutableLiveData<List<WeekGroup>> weekGroups = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<TodoItemVO>> overdueItems = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> overdueTotal = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> showScope = new MutableLiveData<>(false);
    private final MutableLiveData<String> farmName = new MutableLiveData<>("");
    private final MutableLiveData<String> staffBanner = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showFarmTag = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> gridLoading = new MutableLiveData<>(true);
    private final MutableLiveData<UiState> listState = new MutableLiveData<>(UiState.loading());

    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<LocalDate> scrollToDay = new SingleLiveEvent<>();

    /** 入口参数（WORK / WARNING / BUSINESS，可空） */
    @Nullable
    private String category;
    private boolean staff;
    private boolean initialized;

    /** 当前日期列表的查询条件（变化时重建分页） */
    private final BehaviorProcessor<DayKey> dayKey = BehaviorProcessor.create();
    @Nullable
    private Disposable pagingDisposable;
    private final SerialDisposable gridRequest = new SerialDisposable();
    private final SerialDisposable listRequest = new SerialDisposable();
    private final SerialDisposable overdueRequest = new SerialDisposable();
    private int nonce;

    @Inject
    public CalendarViewModel(@NonNull TodoRepository todoRepository, @NonNull HomeRepository homeRepository,
                             @NonNull TodayPrefs prefs, @NonNull FarmContext farmContext,
                             @NonNull LinkNavigator linkNavigator, @NonNull TodayServiceImpl todoService) {
        this.todoRepository = todoRepository;
        this.homeRepository = homeRepository;
        this.prefs = prefs;
        this.farmContext = farmContext;
        this.linkNavigator = linkNavigator;
        disposables.add(gridRequest);
        disposables.add(listRequest);
        disposables.add(overdueRequest);
        disposables.add(todoService.todoChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(c -> onTodoChanged(), t -> Log.w(TAG, "待办回流订阅失败", t)));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        String date = handle.contains(STATE_DATE) ? handle.get(STATE_DATE) : handle.get(RouterPath.EXTRA_DATE);
        String m = handle.contains(STATE_MODE) ? handle.get(STATE_MODE) : handle.get(RouterPath.EXTRA_MODE);
        init(date, handle.get(EXTRA_CATEGORY), m);
    }

    // ===================== 对外 LiveData =====================

    public LiveData<CalendarMode> getMode() {
        return mode;
    }

    public LiveData<String> getScope() {
        return scope;
    }

    public LiveData<LocalDate> getSelectedDate() {
        return selectedDate;
    }

    public LiveData<LocalDate> getTodayDate() {
        return todayDate;
    }

    /** 月历显示的月份 */
    public LiveData<YearMonth> getMonth() {
        return month;
    }

    public LiveData<String> getPeriodText() {
        return periodText;
    }

    public LiveData<Map<LocalDate, CalendarDayVO>> getGridDays() {
        return gridDays;
    }

    public LiveData<String> getDayHeader() {
        return dayHeader;
    }

    public LiveData<Integer> getDayTotal() {
        return dayTotal;
    }

    /** 选中日期的分页（首次读取时开始分页） */
    public LiveData<PagingData<TodoItemVO>> getDayPaging() {
        startPaging();
        return dayPaging;
    }

    public LiveData<List<WeekGroup>> getWeekGroups() {
        return weekGroups;
    }

    public LiveData<List<TodoItemVO>> getOverdueItems() {
        return overdueItems;
    }

    public LiveData<Integer> getOverdueTotal() {
        return overdueTotal;
    }

    public LiveData<Boolean> getShowScope() {
        return showScope;
    }

    public LiveData<String> getFarmName() {
        return farmName;
    }

    public LiveData<String> getStaffBanner() {
        return staffBanner;
    }

    public LiveData<Boolean> getShowFarmTag() {
        return showFarmTag;
    }

    public LiveData<Boolean> getGridLoading() {
        return gridLoading;
    }

    public LiveData<UiState> getListState() {
        return listState;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    public SingleLiveEvent<LocalDate> getScrollToDay() {
        return scrollToDay;
    }

    public boolean isStaff() {
        return staff;
    }

    // ===================== 初始化 =====================

    /**
     * @param date     入口日期 yyyy-MM-dd（可空，默认今天）
     * @param category 入口筛选（可空，透传，暂不加提示，见待定事项 4）
     * @param modeName 入口视图（可空，默认上次视图；首次养殖户为月，员工为日）
     */
    public void init(@Nullable String date, @Nullable String category, @Nullable String modeName) {
        if (initialized) {
            return;
        }
        initialized = true;
        this.category = category;
        FarmContextVO ctx = farmContext.current();
        staff = farmContext.isStaff();
        farmName.setValue(ctx == null || ctx.getName() == null ? "" : ctx.getName());
        showScope.setValue(!staff);

        CalendarMode fallback = staff ? CalendarMode.DAY : CalendarMode.MONTH;
        CalendarMode m = CalendarMode.parse(modeName, CalendarMode.parse(prefs.getCalendarMode(), fallback));
        mode.setValue(m);
        showFarmTag.setValue(m != CalendarMode.DAY);
        String s = staff ? SCOPE_CURRENT : (SCOPE_ALL.equals(prefs.getCalendarScope()) ? SCOPE_ALL : SCOPE_CURRENT);
        scope.setValue(s);

        LocalDate today = serverToday();
        todayDate.setValue(today);
        LocalDate selected = DateFormatter.parseDate(date);
        selectedDate.setValue(selected == null ? today : selected);
        if (staff) {
            buildStaffBanner(ctx);
        }
        reload();
    }

    /** “今天”：T01 缓存的 server_time，没有时暂用手机日期，API-T03 返回后按 is_today 校正 */
    @NonNull
    private LocalDate serverToday() {
        HomeVO home = homeRepository.getCachedHome(farmContext.farmIdOrZero());
        LocalDate d = home == null ? null : DateFormatter.parseDate(home.getServerTime());
        return d == null ? DateFormatter.today() : d;
    }

    /** 员工横幅：“东山猪场 · 只显示你负责的 3 号育肥舍、4 号育肥舍的作业待办。……”；超过 3 个写“等 N 个栏舍” */
    private void buildStaffBanner(@Nullable FarmContextVO ctx) {
        HomeVO home = homeRepository.getCachedHome(farmContext.farmIdOrZero());
        if (home != null && home.getStaffMetrics() != null) {
            staffBanner.setValue(staffBannerText(ctx == null ? null : ctx.getName(), home.getStaffMetrics().getPenNames()));
            return;
        }
        launch(homeRepository.getHome(), vo -> {
            if (vo.getStaffMetrics() != null) {
                staffBanner.setValue(staffBannerText(ctx == null ? null : ctx.getName(),
                        vo.getStaffMetrics().getPenNames()));
            }
        }, e -> Log.i(TAG, "负责栏舍读取失败：" + e.getMessage()));
    }

    @NonNull
    static String staffBannerText(@Nullable String farm, @Nullable List<String> pens) {
        List<String> names = pens == null ? Collections.emptyList() : pens;
        String penText;
        if (names.size() > 3) {
            penText = names.get(0) + "等 " + names.size() + " 个栏舍";
        } else {
            penText = Texts.join("、", names);
        }
        return Texts.orEmpty(farm) + " · 只显示你负责的 " + penText + "的作业待办。登记对应记录后，待办自动完成。";
    }

    // ===================== 切换 =====================

    public void switchMode(@NonNull CalendarMode newMode) {
        if (newMode == mode.getValue()) {
            return;
        }
        mode.setValue(newMode);
        showFarmTag.setValue(newMode != CalendarMode.DAY);
        prefs.setCalendarMode(newMode.name());
        getSavedState().set(STATE_MODE, newMode.name());
        reload();
    }

    public void switchScope(@NonNull String newScope) {
        if (staff || newScope.equals(scope.getValue())) {
            return;
        }
        scope.setValue(newScope);
        prefs.setCalendarScope(newScope);
        getSavedState().set(STATE_SCOPE, newScope);
        reload();
    }

    public void prev() {
        shift(-1);
    }

    public void next() {
        shift(1);
    }

    private void shift(int delta) {
        CalendarMode m = currentMode();
        LocalDate selected = currentSelected();
        LocalDate target = CalendarRange.shift(m, selected, delta);
        LocalDate today = currentToday();
        if (m == CalendarMode.MONTH && YearMonth.from(target).equals(YearMonth.from(today))) {
            target = today;
        }
        setSelected(target);
        reload();
    }

    public void goToday() {
        LocalDate today = currentToday();
        if (today.equals(selectedDate.getValue())) {
            return;
        }
        boolean samePeriod = CalendarRange.of(currentMode(), today).getStart()
                .equals(CalendarRange.of(currentMode(), currentSelected()).getStart());
        if (samePeriod && currentMode() != CalendarMode.DAY) {
            selectDate(today);
        } else {
            setSelected(today);
            reload();
        }
    }

    /** 点日期：同一周期内只换列表；跨周期（月视图点非本月日期）翻页并重新加载 */
    public void selectDate(@NonNull LocalDate date) {
        CalendarMode m = currentMode();
        LocalDate old = currentSelected();
        boolean samePeriod = CalendarRange.of(m, date).getStart().equals(CalendarRange.of(m, old).getStart());
        if (!samePeriod) {
            setSelected(date);
            reload();
            return;
        }
        setSelected(date);
        if (m == CalendarMode.WEEK) {
            scrollToDay.setValue(date);
            return;
        }
        loadDayList();
        if (m == CalendarMode.DAY) {
            loadOverdue();
        }
    }

    private void setSelected(@NonNull LocalDate date) {
        selectedDate.setValue(date);
        getSavedState().set(STATE_DATE, DateFormatter.toDateString(date));
        updateTexts();
    }

    private void updateTexts() {
        CalendarMode m = currentMode();
        LocalDate selected = currentSelected();
        month.setValue(YearMonth.from(selected));
        periodText.setValue(CalendarRange.periodText(m, selected));
        dayHeader.setValue(CalendarRange.dayHeader(selected, selected.equals(currentToday())));
    }

    // ===================== 加载 =====================

    /** 按当前视图重新加载圆点与列表（技术文档 2.6.3） */
    public void reload() {
        updateTexts();
        CalendarMode m = currentMode();
        LocalDate selected = currentSelected();
        CalendarRange range = CalendarRange.of(m, selected);
        gridLoading.setValue(true);
        listState.setValue(UiState.loading());
        gridRequest.set(launch(todoRepository.getCalendar(DateFormatter.toDateString(range.getStart()),
                        DateFormatter.toDateString(range.getEnd()), scopeParam(), category),
                this::applyGrid, this::onListError));
        switch (m) {
            case WEEK:
                overdueItems.setValue(Collections.emptyList());
                overdueTotal.setValue(0);
                loadWeek(range.getStart());
                break;
            case DAY:
                weekGroups.setValue(Collections.emptyList());
                loadDayList();
                loadOverdue();
                break;
            case MONTH:
            default:
                weekGroups.setValue(Collections.emptyList());
                overdueItems.setValue(Collections.emptyList());
                overdueTotal.setValue(0);
                loadDayList();
                break;
        }
    }

    @VisibleForTesting
    void applyGrid(@NonNull TodoCalendarResult result) {
        Map<LocalDate, CalendarDayVO> map = new HashMap<>();
        LocalDate serverToday = null;
        List<CalendarDayVO> days = result.getDays() == null ? Collections.emptyList() : result.getDays();
        for (CalendarDayVO d : days) {
            LocalDate date = DateFormatter.parseDate(d.getDate());
            if (date != null) {
                map.put(date, d);
                if (d.isToday()) {
                    serverToday = date;
                }
            }
        }
        gridLoading.setValue(false);
        gridDays.setValue(Collections.unmodifiableMap(map));
        LocalDate oldToday = currentToday();
        if (serverToday != null && !serverToday.equals(oldToday)) {
            // 以服务端 is_today 校正“今天”；原先选中的是手机的今天时改选服务端的今天
            todayDate.setValue(serverToday);
            if (oldToday.equals(selectedDate.getValue())) {
                LocalDate target = serverToday;
                setSelected(target);
                if (!CalendarRange.of(currentMode(), target).getStart()
                        .equals(CalendarRange.of(currentMode(), oldToday).getStart())) {
                    reload();
                    return;
                }
                loadDayList();
                loadOverdue();
            } else {
                updateTexts();
            }
        }
    }

    private void loadDayList() {
        if (currentMode() == CalendarMode.WEEK) {
            return;
        }
        listState.setValue(UiState.loading());
        nonce++;
        dayKey.onNext(new DayKey(DateFormatter.toDateString(currentSelected()), scopeParam(), category, nonce));
    }

    private void loadWeek(@NonNull LocalDate weekStart) {
        listRequest.set(launch(todoRepository.getWeekly(DateFormatter.toDateString(weekStart), scopeParam(),
                category, PER_DAY), this::applyWeek, this::onListError));
    }

    @VisibleForTesting
    void applyWeek(@NonNull TodoWeeklyResult result) {
        List<WeekGroup> groups = new ArrayList<>();
        List<WeeklyDayVO> days = result.getDays() == null ? Collections.emptyList() : result.getDays();
        for (WeeklyDayVO d : days) {
            LocalDate date = DateFormatter.parseDate(d.getDate());
            if (date == null || d.getTotal() == 0) {
                continue;
            }
            List<TodoItemVO> items = d.getItems() == null ? Collections.emptyList() : d.getItems();
            groups.add(new WeekGroup(date, CalendarRange.dayHeader(date, d.isToday() || date.equals(currentToday())),
                    d.getTotal(), items, false, false));
        }
        weekGroups.setValue(Collections.unmodifiableList(groups));
        listState.setValue(groups.isEmpty() ? UiState.empty(null, null) : UiState.content());
    }

    /** 日视图查看今天时显示“已逾期 · N 项”（API-T06） */
    private void loadOverdue() {
        if (currentMode() != CalendarMode.DAY || !currentSelected().equals(currentToday())) {
            overdueRequest.set(Disposable.disposed());
            overdueItems.setValue(Collections.emptyList());
            overdueTotal.setValue(0);
            return;
        }
        overdueRequest.set(launch(todoRepository.getOverdue(scopeParam(), 1, TodoRepository.EXPAND_SIZE), page -> {
            overdueItems.setValue(page.getList());
            overdueTotal.setValue(page.getTotal());
        }, e -> Log.i(TAG, "已逾期组加载失败：" + e.getMessage())));
    }

    /** 周视图“展开其余 N 项”（API-T04，size 100）；已展开时收起为前 5 项 */
    public void expandDay(@NonNull LocalDate date) {
        List<WeekGroup> groups = weekGroups.getValue();
        if (groups == null) {
            return;
        }
        int index = indexOf(groups, date);
        if (index < 0) {
            return;
        }
        WeekGroup g = groups.get(index);
        if (g.isExpanded()) {
            List<TodoItemVO> first = g.getItems().subList(0, Math.min(PER_DAY, g.getItems().size()));
            replaceGroup(index, g.withItems(new ArrayList<>(first), false));
            return;
        }
        if (g.isExpanding()) {
            return;
        }
        replaceGroup(index, g.withExpanding(true));
        launch(todoRepository.getDayTodos(DateFormatter.toDateString(date), scopeParam(), category,
                        TodoRepository.EXPAND_SIZE),
                page -> {
                    List<WeekGroup> now = weekGroups.getValue();
                    int i = now == null ? -1 : indexOf(now, date);
                    if (i >= 0) {
                        replaceGroup(i, now.get(i).withItems(page.getList(), true));
                    }
                }, e -> {
                    List<WeekGroup> now = weekGroups.getValue();
                    int i = now == null ? -1 : indexOf(now, date);
                    if (i >= 0) {
                        replaceGroup(i, now.get(i).withExpanding(false));
                    }
                    handleError(e);
                });
    }

    private void replaceGroup(int index, @NonNull WeekGroup g) {
        List<WeekGroup> list = new ArrayList<>(weekGroups.getValue() == null
                ? Collections.emptyList() : weekGroups.getValue());
        list.set(index, g);
        weekGroups.setValue(Collections.unmodifiableList(list));
    }

    private static int indexOf(@NonNull List<WeekGroup> groups, @NonNull LocalDate date) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).getDate().equals(date)) {
                return i;
            }
        }
        return -1;
    }

    private void startPaging() {
        if (pagingDisposable != null) {
            return;
        }
        Flowable<PagingData<TodoItemVO>> flow = dayKey.distinctUntilChanged()
                .switchMap(key -> todoRepository.dayPager(key.date, key.scope, key.category, TodoRepository.STATUS_ALL,
                        page -> {
                            dayTotal.postValue(page.getTotal());
                            listState.postValue(page.getList().isEmpty() ? UiState.empty(null, null) : UiState.content());
                        }));
        pagingDisposable = PagingRx.cachedIn(flow, ViewModelKt.getViewModelScope(this))
                .subscribe(dayPaging::postValue, t -> Log.w(TAG, "当日分页失败", t));
        disposables.add(pagingDisposable);
    }

    /** 列表区加载失败（页面在分页首屏失败时也调用） */
    public void onListError(@NonNull ApiException e) {
        gridLoading.setValue(false);
        if (e.getCode() == com.zhimu.slmp.base.error.ErrorCodes.FARM_STATUS_FORBIDDEN) {
            listState.setValue(UiState.noPermission(e.getMessage(), e.getCode()));
            return;
        }
        listState.setValue(UiState.error(e.isNetwork() ? null : e.getMessage(), e.getTraceId(), e.getCode()));
    }

    @Override
    public void retry() {
        reload();
    }

    // ===================== 点击 =====================

    public void onTodoClick(@NonNull TodoItemVO item) {
        launch(linkNavigator.forTodo(item), navEvent::setValue);
    }

    public void onTodoLongClick(@NonNull TodoItemVO item) {
        navEvent.setValue(NavTarget.todoDetail(item.getTodoId()));
    }

    public void onSettingsClick() {
        navEvent.setValue(NavTarget.of("T05"));
    }

    /** T04 返回：忽略成功时提示并刷新 */
    public void onDetailResult(boolean ignored) {
        if (ignored) {
            showMessage("已忽略这条待办");
        }
        reload();
    }

    /** 登记回流：刷新圆点与列表 */
    public void onTodoChanged() {
        if (initialized) {
            reload();
        }
    }

    // ===================== 工具 =====================

    @NonNull
    private CalendarMode currentMode() {
        CalendarMode m = mode.getValue();
        return m == null ? CalendarMode.MONTH : m;
    }

    @NonNull
    private LocalDate currentSelected() {
        LocalDate d = selectedDate.getValue();
        return d == null ? currentToday() : d;
    }

    @NonNull
    private LocalDate currentToday() {
        LocalDate d = todayDate.getValue();
        return d == null ? DateFormatter.today() : d;
    }

    /** 员工不传 scope */
    @Nullable
    private String scopeParam() {
        return staff ? null : scope.getValue();
    }

    /** 已逾期组中是否显示养殖场标签：与列表一致 */
    public boolean isOverdue(@NonNull TodoItemVO item) {
        return TodoTypeStyle.STATUS_OVERDUE.equals(item.getDisplayStatus());
    }

    /** 当日列表查询条件 */
    static final class DayKey {
        final String date;
        @Nullable
        final String scope;
        @Nullable
        final String category;
        final int nonce;

        DayKey(@NonNull String date, @Nullable String scope, @Nullable String category, int nonce) {
            this.date = date;
            this.scope = scope;
            this.category = category;
            this.nonce = nonce;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DayKey)) {
                return false;
            }
            DayKey k = (DayKey) o;
            return nonce == k.nonce && date.equals(k.date) && java.util.Objects.equals(scope, k.scope)
                    && java.util.Objects.equals(category, k.category);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(date, scope, category, nonce);
        }
    }

    @VisibleForTesting
    @Nullable
    DayKey lastDayKey() {
        return dayKey.getValue();
    }
}
