package com.zhimu.slmp.inspect.ui.today;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.inspect.R;
import com.zhimu.slmp.inspect.databinding.InspectActivityTodayBinding;
import com.zhimu.slmp.inspect.databinding.InspectViewListEmptyBinding;
import com.zhimu.slmp.inspect.databinding.InspectViewTodayHeaderBinding;
import com.zhimu.slmp.inspect.di.InspectInjector;
import com.zhimu.slmp.inspect.model.vo.InspectionListItemVO;
import com.zhimu.slmp.inspect.model.vo.InspectionSummaryVO;
import com.zhimu.slmp.inspect.model.vo.RecentIssueVO;
import com.zhimu.slmp.inspect.ui.common.InspectFormat;
import com.zhimu.slmp.inspect.ui.common.InspectNav;
import com.zhimu.slmp.inspect.ui.common.SingleViewAdapter;

import java.time.LocalDate;

/**
 * I01 今日巡检：养殖户版（全场统计、截止时间与“修改”、筛选 Chip、今日清单、前 3 天 · 超时与补录）与
 * 员工版（负责栏舍统计、今天 / 昨天分组、卡片内操作按钮）。入口：T01 巡检待办、L01 生产页。子页返回后刷新统计与清单。
 */
public class InspectTodayActivity extends BaseActivity {

    private static final String REQ_DATE = "inspect_today_date";

    private InspectActivityTodayBinding binding;
    private InspectViewTodayHeaderBinding header;
    private InspectViewListEmptyBinding empty;
    private InspectTodayViewModel vm;
    private InspectTodayAdapter adapter;
    private SectionAdapter section;
    private SingleViewAdapter emptyAdapter;
    private boolean listLoaded;

    private final ActivityResultLauncher<Intent> childLauncher = Router.registerForResult(this, r -> {
        if (r.getResultCode() == Activity.RESULT_OK) {
            vm.onRefresh();
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.inspect_activity_today);
        vm = obtainViewModel(InspectInjector.factory(), InspectTodayViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        TopAppBarHelper.setupBack(binding.inspectTopBar.baseToolbar, getString(R.string.inspect_today_title));
        binding.inspectTopBar.baseToolbar.setNavigationOnClickListener(v -> finish());
        vm.getIsManagerView().observe(this, manager -> {
            binding.inspectTopBar.baseToolbar.getMenu().clear();
            if (Boolean.TRUE.equals(manager)) {
                TopAppBarHelper.inflateMenu(binding.inspectTopBar.baseToolbar, R.menu.inspect_menu_today);
            }
        });
        binding.inspectTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.inspect_menu_calendar) {
                LocalDate cur = DateFormatter.parseDate(vm.currentDate());
                DateTimePicker.date(REQ_DATE).selection(cur).bounds(null, DateFormatter.today())
                        .show(getSupportFragmentManager());
                return true;
            }
            return false;
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> {
                    String d = DateTimePicker.getDate(result);
                    if (d != null) {
                        vm.setDate(d);
                    }
                });

        // 列表：先设 LayoutManager 再以 RecyclerView 为 parent inflate 头部
        binding.inspectList.setLayoutManager(new LinearLayoutManager(this));
        header = InspectViewTodayHeaderBinding.inflate(getLayoutInflater(), binding.inspectList, false);
        header.setLifecycleOwner(this);
        header.setVm(vm);
        empty = InspectViewListEmptyBinding.inflate(getLayoutInflater(), binding.inspectList, false);
        adapter = new InspectTodayAdapter(new InspectTodayAdapter.Callback() {
            @Override
            public void onItemClick(@NonNull InspectionListItemVO item) {
                vm.onItemClick(item);
            }

            @Override
            public void onReportClick(@NonNull InspectionListItemVO item) {
                vm.onReportClick(item);
            }
        });
        section = new SectionAdapter(new SectionAdapter.Callback() {
            @Override
            public void onIssueClick(@NonNull RecentIssueVO issue) {
                vm.onIssueClick(issue);
            }

            @Override
            public void onBackfillClick(@NonNull RecentIssueVO issue) {
                vm.onBackfillClick(issue);
            }

            @Override
            public void onYesterdayClick(@NonNull InspectionListItemVO item) {
                vm.onYesterdayClick(item);
            }
        });
        emptyAdapter = new SingleViewAdapter(empty.getRoot(), false);
        binding.inspectList.setAdapter(new ConcatAdapter(new SingleViewAdapter(header.getRoot(), true), adapter,
                emptyAdapter, section));
        binding.inspectList.addItemDecoration(new GroupedListDecoration(this));

        adapter.addLoadStateListener(states -> {
            onLoadStates(states);
            return kotlin.Unit.INSTANCE;
        });
        vm.getPagingData().observe(this, data -> adapter.submitData(getLifecycle(), data));
        vm.getUiState().observe(this, binding.inspectState::render);
        binding.inspectState.setOnRetryListener(v -> vm.retry());
        binding.inspectRefresh.setOnRefreshListener(() -> vm.onRefresh());
        vm.getRefreshing().observe(this, r -> {
            if (!Boolean.TRUE.equals(r)) {
                binding.inspectRefresh.setRefreshing(false);
            }
        });

        vm.getSummary().observe(this, this::renderSummary);
        vm.getProgress().observe(this, p -> header.inspectProgress.setProgressCompat(p == null ? 0 : p, true));
        vm.getDeadlineTime().observe(this, t -> renderDeadline());
        vm.getDeadlineText().observe(this, t -> renderDeadline());
        vm.getRecentIssues().observe(this, l -> renderSection());
        vm.getRecentVisible().observe(this, v -> renderSection());
        vm.getYesterdayItems().observe(this, l -> renderSection());

        header.inspectFilters.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            int id = ids.get(0);
            String status = id == R.id.inspect_filter_pending ? TodayQuery.PENDING
                    : id == R.id.inspect_filter_abnormal ? TodayQuery.ABNORMAL
                    : id == R.id.inspect_filter_done ? TodayQuery.DONE : TodayQuery.ALL;
            vm.setStatus(status);
        });

        vm.getNavEvent().observe(this, t -> InspectNav.open(this, childLauncher, t));
        vm.getShowDeadlinePanel().observe(this, v -> DeadlineBottomSheet.newInstance(
                vm.getSetting().getValue() == null ? null : vm.getSetting().getValue().getFarmName())
                .showSafely(getSupportFragmentManager()));
        ClickThrottle.setOnClickListener(empty.inspectEmptyAction, v -> vm.onEmptyAction());
    }

    private void onLoadStates(@NonNull CombinedLoadStates states) {
        boolean loading = states.getRefresh() instanceof LoadState.Loading;
        if (!loading) {
            listLoaded = true;
        }
        boolean isEmpty = listLoaded && !loading && adapter.getItemCount() == 0;
        emptyAdapter.setVisible(isEmpty);
        if (isEmpty) {
            renderEmpty();
        }
        InspectionSummaryVO s = vm.getSummary().getValue();
        adapter.setMode(!Boolean.TRUE.equals(vm.getIsManagerView().getValue()), s == null ? null : s.getDeadlineTime());
    }

    private void renderEmpty() {
        int kind = vm.emptyKind();
        String status = vm.currentStatus();
        int text;
        if (kind == InspectTodayViewModel.EMPTY_FILTER) {
            text = TodayQuery.PENDING.equals(status) ? R.string.inspect_today_empty_pending
                    : TodayQuery.ABNORMAL.equals(status) ? R.string.inspect_today_empty_abnormal
                    : R.string.inspect_today_empty_done;
        } else if (kind == InspectTodayViewModel.EMPTY_NO_PENS) {
            text = R.string.inspect_today_empty_staff;
        } else {
            text = R.string.inspect_today_empty_batches;
        }
        empty.inspectEmptyText.setText(text);
        boolean showAction = kind == InspectTodayViewModel.EMPTY_NO_BATCH
                && Boolean.TRUE.equals(vm.getIsManagerView().getValue());
        empty.inspectEmptyAction.setText(R.string.inspect_today_batch_entry);
        empty.inspectEmptyAction.setVisibility(showAction ? View.VISIBLE : View.GONE);
    }

    private void renderSummary(@Nullable InspectionSummaryVO s) {
        if (s == null) {
            return;
        }
        setCount(header.inspectStatShould, s.getShouldCount());
        setCount(header.inspectStatDone, s.getDoneCount());
        setCount(header.inspectStatPending, s.getPendingCount());
        setCount(header.inspectStatAbnormal, s.getAbnormalCount());
        adapter.setMode(s.isMyPens(), s.getDeadlineTime());
        renderSection();
    }

    /** “4个”：数字 28 sp 等宽，单位 14 sp on-surface-variant */
    private void setCount(@NonNull TextView view, int value) {
        SpannableStringBuilder sb = new SpannableStringBuilder(String.valueOf(value));
        int start = sb.length();
        sb.append(getString(R.string.inspect_today_unit));
        sb.setSpan(new RelativeSizeSpan(0.5f), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, com.zhimu.slmp.base.R.color.zm_on_surface_variant)),
                start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(sb);
    }

    /** “截止 18:00 · 17:00 提醒负责员工”：时刻加粗，后半句 on-surface-variant */
    private void renderDeadline() {
        String bold = vm.getDeadlineTime().getValue();
        String rest = vm.getDeadlineText().getValue();
        if (bold == null || bold.isEmpty()) {
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder("截止 ");
        int s = sb.length();
        sb.append(bold.replaceFirst("^截止 ", ""));
        sb.setSpan(new StyleSpan(Typeface.BOLD), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int r = sb.length();
        sb.append(rest == null ? "" : rest);
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, com.zhimu.slmp.base.R.color.zm_on_surface_variant)),
                r, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        header.inspectDeadlineText.setText(sb);
    }

    private void renderSection() {
        if (Boolean.TRUE.equals(vm.getIsManagerView().getValue())) {
            if (Boolean.TRUE.equals(vm.getRecentVisible().getValue())) {
                section.showIssues(getString(R.string.inspect_today_recent_title), vm.getRecentIssues().getValue());
            } else {
                section.hide();
            }
        } else {
            LocalDate y = DateFormatter.today().minusDays(1);
            section.showYesterday("昨天" + InspectFormat.DOT + DateFormatter.monthDay(y),
                    vm.getYesterdayItems().getValue());
        }
    }
}
