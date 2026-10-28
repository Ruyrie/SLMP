package com.zhimu.slmp.today.ui.calendar;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.databinding.TodayActivityCalendarBinding;
import com.zhimu.slmp.today.databinding.TodayItemCalendarHeaderBinding;
import com.zhimu.slmp.today.di.TodayInjector;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.ui.common.ListStateAdapter;
import com.zhimu.slmp.today.ui.common.SectionAdapter;
import com.zhimu.slmp.today.ui.common.SingleViewAdapter;
import com.zhimu.slmp.today.ui.common.TodoListAdapter;
import com.zhimu.slmp.today.ui.common.TodoPagingAdapter;
import com.zhimu.slmp.today.ui.detail.TodoDetailActivity;
import com.zhimu.slmp.today.util.CalendarMode;
import com.zhimu.slmp.today.util.NavTarget;
import com.zhimu.slmp.today.util.TodayNavigator;

import java.time.LocalDate;

import kotlin.Unit;

/**
 * T03 养殖日历：月 / 周 / 日三种视图（技术文档 2.6.3），头部（按钮组、周期、MonthCalendarView、图例）与列表由 ConcatAdapter 拼接，
 * 切换视图时换成对应的适配器组合并交叉淡入 250 毫秒；长按待办、点已完成或已忽略的待办以 Activity Result 打开 T04。
 * 当天列表分两段：“待处理 N 项”分页在上，“已完成 / 已忽略 · M 项”折叠头在下（默认收起，展开后分页加载）；
 * 两段末尾各有加载更多 / 失败重试行。
 */
public class CalendarActivity extends BaseActivity implements TodoPagingAdapter.Listener {

    private CalendarViewModel vm;
    private TodayActivityCalendarBinding binding;
    private TodayItemCalendarHeaderBinding header;

    private SingleViewAdapter headerAdapter;
    private final SectionAdapter daySection = new SectionAdapter();
    private final SectionAdapter overdueSection = new SectionAdapter();
    private final SectionAdapter overdueMore = new SectionAdapter();
    private final ListStateAdapter listStateAdapter = new ListStateAdapter();
    /** “待处理”段加载更多 / 失败重试 */
    private final ListStateAdapter openFooter = new ListStateAdapter();
    /** “已完成 / 已忽略”折叠头 */
    private final SectionAdapter closedSection = new SectionAdapter();
    /** “已完成 / 已忽略”段加载中 / 失败重试 */
    private final ListStateAdapter closedFooter = new ListStateAdapter();
    private TodoPagingAdapter dayAdapter;
    private DayTimelineAdapter timelineAdapter;
    private TodoPagingAdapter closedDayAdapter;
    private DayTimelineAdapter closedTimelineAdapter;
    private WeekGroupAdapter weekAdapter;
    private TodoListAdapter overdueAdapter;
    private ConcatAdapter concat;
    @Nullable
    private CalendarMode shownMode;

    private final ActivityResultLauncher<Intent> detailLauncher = Router.registerForResult(this, result -> {
        boolean ignored = result.getResultCode() == Activity.RESULT_OK && result.getData() != null
                && result.getData().getBooleanExtra(TodoDetailActivity.RESULT_IGNORED, false);
        vm.onDetailResult(ignored);
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(TodayInjector.factory(), CalendarViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.today_activity_calendar);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);

        TopAppBarHelper.setupBack(binding.topBar.baseToolbar, getString(R.string.today_calendar_title));
        TopAppBarHelper.inflateMenu(binding.topBar.baseToolbar, R.menu.today_menu_calendar);
        binding.topBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.today_menu_today) {
                vm.goToday();
                return true;
            }
            if (item.getItemId() == R.id.today_menu_settings) {
                vm.onSettingsClick();
                return true;
            }
            return false;
        });
        TopAppBarHelper.bindLiftOnScroll(binding.topBar.baseAppBar, binding.list);

        // 头部以 RecyclerView 为父布局生成 LayoutParams，必须先设置 LayoutManager
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        header = TodayItemCalendarHeaderBinding.inflate(getLayoutInflater(), binding.list, false);
        header.setLifecycleOwner(this);
        header.setVm(vm);
        bindHeader();

        headerAdapter = new SingleViewAdapter(header.getRoot());
        dayAdapter = new TodoPagingAdapter(this, true);
        timelineAdapter = new DayTimelineAdapter(this);
        closedDayAdapter = new TodoPagingAdapter(this, true);
        closedTimelineAdapter = new DayTimelineAdapter(this);
        weekAdapter = new WeekGroupAdapter(this, vm::expandDay, vm::toggleClosedDay);
        overdueAdapter = new TodoListAdapter(this, false);
        overdueSection.setOnClickListener(v -> vm.toggleOverdue());
        overdueMore.set(getString(R.string.today_view_all), "›");
        overdueMore.setVisible(false);
        overdueMore.setOnClickListener(v -> vm.onAllOverdueClick());
        listStateAdapter.setOnActionClickListener(v -> vm.retry());
        closedSection.setVisible(false);
        closedSection.setOnClickListener(v -> vm.toggleClosed());
        openFooter.setOnActionClickListener(v -> {
            TodoPagingAdapterRetry a = activeOpen();
            if (a != null) a.retry();
        });
        closedFooter.setOnActionClickListener(v -> {
            TodoPagingAdapterRetry a = activeClosed();
            if (a != null) a.retry();
        });
        concat = new ConcatAdapter(headerAdapter);
        binding.list.setItemAnimator(null);
        binding.list.setAdapter(concat);

        dayAdapter.addLoadStateListener(states -> onOpenLoadStates(states, CalendarMode.MONTH));
        timelineAdapter.addLoadStateListener(states -> onOpenLoadStates(states, CalendarMode.DAY));
        closedDayAdapter.addLoadStateListener(states -> onClosedLoadStates(states, closedDayAdapter.getItemCount(),
                CalendarMode.MONTH));
        closedTimelineAdapter.addLoadStateListener(states -> onClosedLoadStates(states,
                closedTimelineAdapter.getItemCount(), CalendarMode.DAY));
        observe();
    }

    @Override protected void onResume() {
        super.onResume();
        if (vm != null) vm.onPageResumed();
    }

    private void bindHeader() {
        header.scopeGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) {
                vm.switchScope(id == R.id.scope_all ? CalendarViewModel.SCOPE_ALL : CalendarViewModel.SCOPE_CURRENT);
            }
        });
        header.modeGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) {
                return;
            }
            if (id == R.id.mode_day) {
                vm.switchMode(CalendarMode.DAY);
            } else if (id == R.id.mode_week) {
                vm.switchMode(CalendarMode.WEEK);
            } else {
                vm.switchMode(CalendarMode.MONTH);
            }
        });
        header.calendar.setListener(new MonthCalendarView.Listener() {
            @Override
            public void onDateClick(@NonNull LocalDate date) {
                vm.selectDate(date);
            }

            @Override
            public void onPageChange(int direction) {
                if (direction > 0) {
                    vm.next();
                } else {
                    vm.prev();
                }
            }
        });
    }

    private void observe() {
        vm.getMode().observe(this, this::applyMode);
        vm.getScope().observe(this, scope -> header.scopeGroup.check(
                CalendarViewModel.SCOPE_ALL.equals(scope) ? R.id.scope_all : R.id.scope_current));
        vm.getMonth().observe(this, ym -> {
            if (ym != null) {
                header.calendar.setMonth(ym);
            }
        });
        vm.getTodayDate().observe(this, header.calendar::setToday);
        vm.getDayHeader().observe(this, text -> daySection.set(text,
                CalendarTexts.pendingCount(this, valueOr(vm.getDayTotal().getValue()))));
        vm.getDayTotal().observe(this, total -> daySection.set(vm.getDayHeader().getValue(),
                CalendarTexts.pendingCount(this, valueOr(total))));
        vm.getClosedTotal().observe(this, total -> renderClosed());
        vm.getClosedExpanded().observe(this, expanded -> renderClosed());
        vm.getOverdueTotal().observe(this, total -> renderOverdue());
        vm.getOverdueItems().observe(this, items -> renderOverdue());
        vm.getOverdueExpanded().observe(this, expanded -> renderOverdue());
        vm.getOverdueError().observe(this, error -> renderOverdue());
        vm.getWeekGroups().observe(this, weekAdapter::submitGroups);
        vm.getShowFarmTag().observe(this, show -> {
            boolean s = Boolean.TRUE.equals(show);
            dayAdapter.setShowFarmTag(s);
            closedDayAdapter.setShowFarmTag(s);
            weekAdapter.setShowFarmTag(s);
        });
        vm.getDayPaging().observe(this, data -> {
            dayAdapter.submitData(getLifecycle(), data);
            timelineAdapter.submitData(getLifecycle(), data);
        });
        vm.getClosedPaging().observe(this, data -> {
            closedDayAdapter.submitData(getLifecycle(), data);
            closedTimelineAdapter.submitData(getLifecycle(), data);
        });
        vm.getListState().observe(this, this::renderListState);
        vm.getNavEvent().observe(this, this::open);
        vm.getScrollToDay().observe(this, this::scrollToDay);
    }

    private void renderOverdue() {
        int total = valueOr(vm.getOverdueTotal().getValue());
        boolean expanded = Boolean.TRUE.equals(vm.getOverdueExpanded().getValue());
        String error = vm.getOverdueError().getValue();
        overdueSection.set(error == null ? getString(R.string.today_calendar_overdue_title, total) : error,
                getString(expanded ? R.string.today_collapse : R.string.today_expand));
        overdueSection.setVisible(total > 0 || error != null);
        overdueAdapter.submitList(expanded ? vm.getOverdueItems().getValue() : java.util.Collections.emptyList());
        overdueAdapter.setShowFarmTag(CalendarViewModel.SCOPE_ALL.equals(vm.getScope().getValue()));
        overdueMore.setVisible(expanded && total > 5);
    }

    /** “已完成 / 已忽略 · M 项”折叠头：M 为 0 时不显示 */
    private void renderClosed() {
        int total = valueOr(vm.getClosedTotal().getValue());
        boolean expanded = Boolean.TRUE.equals(vm.getClosedExpanded().getValue());
        closedSection.set(getString(R.string.today_calendar_closed_title, total),
                getString(expanded ? R.string.today_collapse : R.string.today_expand));
        closedSection.setVisible(total > 0);
        if (!expanded || total == 0) {
            closedFooter.setState(ListStateAdapter.State.none());
        }
    }

    private static int valueOr(@Nullable Integer v) {
        return v == null ? 0 : v;
    }

    /** 视图切换：换成对应的适配器组合，交叉淡入 250 毫秒 */
    private void applyMode(@Nullable CalendarMode mode) {
        if (mode == null || mode == shownMode) {
            return;
        }
        boolean first = shownMode == null;
        shownMode = mode;
        int checked = mode == CalendarMode.DAY ? R.id.mode_day : mode == CalendarMode.WEEK ? R.id.mode_week : R.id.mode_month;
        header.modeGroup.check(checked);
        header.calendar.setWeekMode(mode != CalendarMode.MONTH);
        for (RecyclerView.Adapter<? extends RecyclerView.ViewHolder> a : concat.getAdapters()) {
            if (a != headerAdapter) {
                concat.removeAdapter(a);
            }
        }
        switch (mode) {
            case WEEK:
                concat.addAdapter(listStateAdapter);
                concat.addAdapter(weekAdapter);
                break;
            case DAY:
                concat.addAdapter(daySection);
                concat.addAdapter(listStateAdapter);
                concat.addAdapter(timelineAdapter);
                concat.addAdapter(openFooter);
                concat.addAdapter(overdueSection);
                concat.addAdapter(overdueAdapter);
                concat.addAdapter(overdueMore);
                concat.addAdapter(closedSection);
                concat.addAdapter(closedTimelineAdapter);
                concat.addAdapter(closedFooter);
                break;
            case MONTH:
            default:
                concat.addAdapter(daySection);
                concat.addAdapter(listStateAdapter);
                concat.addAdapter(dayAdapter);
                concat.addAdapter(openFooter);
                concat.addAdapter(closedSection);
                concat.addAdapter(closedDayAdapter);
                concat.addAdapter(closedFooter);
                break;
        }
        openFooter.setState(ListStateAdapter.State.none());
        closedFooter.setState(ListStateAdapter.State.none());
        if (!first) {
            binding.list.setAlpha(0f);
            binding.list.animate().alpha(1f).setDuration(250).start();
        }
    }

    /** 当前视图使用的“待处理”段适配器（周视图没有分页） */
    @Nullable
    private TodoPagingAdapterRetry activeOpen() {
        if (shownMode == CalendarMode.WEEK || shownMode == null) {
            return null;
        }
        return shownMode == CalendarMode.DAY ? timelineAdapter::retry : dayAdapter::retry;
    }

    @Nullable
    private TodoPagingAdapterRetry activeClosed() {
        if (shownMode == CalendarMode.WEEK || shownMode == null) {
            return null;
        }
        return shownMode == CalendarMode.DAY ? closedTimelineAdapter::retry : closedDayAdapter::retry;
    }

    /** 分页适配器的 retry（PagingDataAdapter 两个子类共用） */
    private interface TodoPagingAdapterRetry {
        void retry();
    }

    /** “待处理”段：首屏失败交给列表状态；翻页中显示加载圈，翻页失败显示“没能加载更多待办 / 重试” */
    private Unit onOpenLoadStates(@NonNull CombinedLoadStates states, @NonNull CalendarMode mode) {
        if (!isActive(mode)) {
            return Unit.INSTANCE;
        }
        LoadState refresh = states.getRefresh();
        if (refresh instanceof LoadState.Error) {
            vm.onListError(ApiException.from(((LoadState.Error) refresh).getError()));
            openFooter.setState(ListStateAdapter.State.none());
            return Unit.INSTANCE;
        }
        LoadState append = states.getAppend();
        if (refresh instanceof LoadState.NotLoading && append instanceof LoadState.Loading) {
            openFooter.setState(ListStateAdapter.State.loading());
        } else if (refresh instanceof LoadState.NotLoading && append instanceof LoadState.Error) {
            openFooter.setState(ListStateAdapter.State.retry(getString(R.string.today_calendar_more_failed),
                    getString(com.zhimu.slmp.base.R.string.base_action_retry)));
        } else {
            openFooter.setState(ListStateAdapter.State.none());
        }
        return Unit.INSTANCE;
    }

    /** “已完成 / 已忽略”段：展开后首屏与翻页都在段尾显示加载圈或失败重试 */
    private Unit onClosedLoadStates(@NonNull CombinedLoadStates states, int count, @NonNull CalendarMode mode) {
        if (!isActive(mode)) {
            return Unit.INSTANCE;
        }
        if (!Boolean.TRUE.equals(vm.getClosedExpanded().getValue())) {
            closedFooter.setState(ListStateAdapter.State.none());
            return Unit.INSTANCE;
        }
        LoadState refresh = states.getRefresh();
        LoadState append = states.getAppend();
        if (refresh instanceof LoadState.Error) {
            closedFooter.setState(ListStateAdapter.State.retry(getString(R.string.today_calendar_closed_failed),
                    getString(com.zhimu.slmp.base.R.string.base_action_retry)));
        } else if (append instanceof LoadState.Error) {
            closedFooter.setState(ListStateAdapter.State.retry(getString(R.string.today_calendar_more_failed),
                    getString(com.zhimu.slmp.base.R.string.base_action_retry)));
        } else if ((refresh instanceof LoadState.Loading && count == 0) || append instanceof LoadState.Loading) {
            closedFooter.setState(ListStateAdapter.State.loading());
        } else {
            closedFooter.setState(ListStateAdapter.State.none());
        }
        return Unit.INSTANCE;
    }

    /** 月视图用列表适配器，日视图用时间轴适配器；只处理当前视图的那一组 */
    private boolean isActive(@NonNull CalendarMode mode) {
        return (shownMode == null ? CalendarMode.MONTH : shownMode) == mode;
    }

    private void renderListState(@Nullable UiState state) {
        if (state == null) {
            return;
        }
        switch (state.getStatus()) {
            case LOADING:
                listStateAdapter.setState(ListStateAdapter.State.skeleton());
                break;
            case EMPTY:
                // 待处理为空但有已完成 / 已忽略：标题由 ViewModel 给出“今天的待办都处理完了”
                listStateAdapter.setState(ListStateAdapter.State.empty(0, null,
                        state.getTitle() != null ? state.getTitle()
                                : getString(vm.isStaff() ? R.string.today_calendar_day_empty_staff
                                : R.string.today_calendar_day_empty), null));
                break;
            case ERROR: {
                LocalDate d = vm.getSelectedDate().getValue();
                String desc = state.getMessage() != null ? state.getMessage()
                        : getString(R.string.today_calendar_error_desc, d == null ? 0 : d.getMonthValue());
                listStateAdapter.setState(ListStateAdapter.State.error(getString(R.string.today_calendar_error_title),
                        desc, getString(com.zhimu.slmp.base.R.string.base_action_retry)));
                break;
            }
            case NO_PERMISSION:
                listStateAdapter.setState(ListStateAdapter.State.empty(com.zhimu.slmp.base.R.drawable.ic_lock,
                        state.getMessage(), null, null));
                break;
            case CONTENT:
            default:
                listStateAdapter.setState(ListStateAdapter.State.none());
                break;
        }
        if (state.getErrorCode() == ErrorCodes.FARM_STATUS_FORBIDDEN && state.getMessage() != null) {
            showMessage(state.getMessage());
        }
    }

    private void scrollToDay(@Nullable LocalDate date) {
        if (date == null) {
            return;
        }
        int pos = weekAdapter.positionOf(date);
        if (pos < 0) {
            return;
        }
        int offset = headerAdapter.getItemCount() + listStateAdapter.getItemCount();
        RecyclerView.LayoutManager lm = binding.list.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos + offset, 0);
        }
    }

    private void open(@Nullable NavTarget target) {
        if (target == null) {
            return;
        }
        TodayNavigator.open(this, target, "T04".equals(target.getRoute()) ? detailLauncher : null);
    }

    @Override
    public void onTodoClick(@NonNull TodoItemVO item) {
        vm.onTodoClick(item);
    }

    @Override
    public void onTodoLongClick(@NonNull TodoItemVO item) {
        vm.onTodoLongClick(item);
    }
}
