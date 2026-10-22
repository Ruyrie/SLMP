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
 */
public class CalendarActivity extends BaseActivity implements TodoPagingAdapter.Listener {

    private CalendarViewModel vm;
    private TodayActivityCalendarBinding binding;
    private TodayItemCalendarHeaderBinding header;

    private SingleViewAdapter headerAdapter;
    private final SectionAdapter daySection = new SectionAdapter();
    private final SectionAdapter overdueSection = new SectionAdapter();
    private final ListStateAdapter listStateAdapter = new ListStateAdapter();
    private TodoPagingAdapter dayAdapter;
    private DayTimelineAdapter timelineAdapter;
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
        weekAdapter = new WeekGroupAdapter(this, vm::expandDay);
        overdueAdapter = new TodoListAdapter(this, false);
        listStateAdapter.setOnActionClickListener(v -> vm.retry());
        concat = new ConcatAdapter(headerAdapter);
        binding.list.setItemAnimator(null);
        binding.list.setAdapter(concat);

        dayAdapter.addLoadStateListener(states -> onLoadStates(states, dayAdapter.getItemCount()));
        timelineAdapter.addLoadStateListener(states -> onLoadStates(states, timelineAdapter.getItemCount()));
        observe();
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
                getString(R.string.today_calendar_count, valueOr(vm.getDayTotal().getValue()))));
        vm.getDayTotal().observe(this, total -> daySection.set(vm.getDayHeader().getValue(),
                getString(R.string.today_calendar_count, valueOr(total))));
        vm.getOverdueTotal().observe(this, total -> {
            overdueSection.set(getString(R.string.today_calendar_overdue_title, valueOr(total)), null);
            overdueSection.setVisible(valueOr(total) > 0);
        });
        vm.getOverdueItems().observe(this, overdueAdapter::submitList);
        vm.getWeekGroups().observe(this, weekAdapter::submitGroups);
        vm.getShowFarmTag().observe(this, show -> {
            boolean s = Boolean.TRUE.equals(show);
            dayAdapter.setShowFarmTag(s);
            weekAdapter.setShowFarmTag(s);
        });
        vm.getDayPaging().observe(this, data -> {
            dayAdapter.submitData(getLifecycle(), data);
            timelineAdapter.submitData(getLifecycle(), data);
        });
        vm.getListState().observe(this, this::renderListState);
        vm.getNavEvent().observe(this, this::open);
        vm.getScrollToDay().observe(this, this::scrollToDay);
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
                concat.addAdapter(overdueSection);
                concat.addAdapter(overdueAdapter);
                concat.addAdapter(daySection);
                concat.addAdapter(listStateAdapter);
                concat.addAdapter(timelineAdapter);
                break;
            case MONTH:
            default:
                concat.addAdapter(daySection);
                concat.addAdapter(listStateAdapter);
                concat.addAdapter(dayAdapter);
                break;
        }
        if (!first) {
            binding.list.setAlpha(0f);
            binding.list.animate().alpha(1f).setDuration(250).start();
        }
    }

    private Unit onLoadStates(@NonNull CombinedLoadStates states, int count) {
        LoadState refresh = states.getRefresh();
        if (refresh instanceof LoadState.Error) {
            vm.onListError(ApiException.from(((LoadState.Error) refresh).getError()));
        }
        return Unit.INSTANCE;
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
                listStateAdapter.setState(ListStateAdapter.State.empty(0, null,
                        getString(vm.isStaff() ? R.string.today_calendar_day_empty_staff
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
