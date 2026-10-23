package com.zhimu.slmp.inspect.ui.history;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.ui.recordlog.RecordLogSheet;
import com.zhimu.slmp.inspect.InspectExtras;
import com.zhimu.slmp.inspect.R;
import com.zhimu.slmp.inspect.data.HistoryQuery;
import com.zhimu.slmp.inspect.databinding.InspectActivityHistoryBinding;
import com.zhimu.slmp.inspect.databinding.InspectViewHistoryHeaderBinding;
import com.zhimu.slmp.inspect.di.InspectInjector;
import com.zhimu.slmp.inspect.model.vo.PenInspectionRowVO;
import com.zhimu.slmp.inspect.ui.common.InspectNav;
import com.zhimu.slmp.inspect.ui.common.SingleViewAdapter;

import java.util.List;

/**
 * I04 巡检历史：栏舍标题与近 7 天概况、7 天格、筛选 Chip、规则横幅（所有者、场长）、记录列表（每页 20 条，到底“没有更多记录了”），
 * 每行“更多”菜单按记录日期与角色变化；修改、补录、提交修改申请返回后刷新。入口：I02 / I03 的 history、I01 前 3 天分组、员工版“昨天”。
 */
public class InspectHistoryActivity extends BaseActivity {

    private static final String REQ_DATE = "inspect_history_date";

    private InspectActivityHistoryBinding binding;
    private InspectViewHistoryHeaderBinding header;
    private InspectHistoryViewModel vm;
    private HistoryAdapter adapter;
    private TextView footer;

    private final ActivityResultLauncher<Intent> childLauncher = Router.registerForResult(this, r -> {
        if (r.getResultCode() == Activity.RESULT_OK) {
            vm.onChildDone();
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.inspect_activity_history);
        vm = obtainViewModel(InspectInjector.factory(), InspectHistoryViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);

        TopAppBarHelper.setupBack(binding.inspectTopBar.baseToolbar, getString(R.string.inspect_history_title));
        binding.inspectTopBar.baseToolbar.setNavigationOnClickListener(v -> finish());
        TopAppBarHelper.inflateMenu(binding.inspectTopBar.baseToolbar, R.menu.inspect_menu_today);
        binding.inspectTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.inspect_menu_calendar) {
                DateTimePicker.date(REQ_DATE).selection(DateFormatter.today()).bounds(null, DateFormatter.today())
                        .show(getSupportFragmentManager());
                return true;
            }
            return false;
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this, (key, result) -> {
            String d = DateTimePicker.getDate(result);
            if (d != null) {
                vm.locate(d);
            }
        });

        binding.inspectList.setLayoutManager(new LinearLayoutManager(this));
        header = InspectViewHistoryHeaderBinding.inflate(getLayoutInflater(), binding.inspectList, false);
        header.setLifecycleOwner(this);
        header.setVm(vm);
        footer = (TextView) getLayoutInflater().inflate(R.layout.inspect_item_list_footer, binding.inspectList, false);
        adapter = new HistoryAdapter(new HistoryAdapter.Callback() {
            @Override
            public void onRowClick(@NonNull PenInspectionRowVO row) {
                vm.onRowClick(row);
            }

            @Override
            public void onMoreClick(@NonNull View anchor, @NonNull PenInspectionRowVO row) {
                showMenu(anchor, row);
            }

            @Override
            public void onBackfillClick(@NonNull PenInspectionRowVO row) {
                vm.onBackfill(row);
            }

            @Override
            public boolean hasMenu(@NonNull PenInspectionRowVO row) {
                return !vm.menuFor(row).isEmpty();
            }

            @Override
            public boolean canBackfill() {
                return Boolean.TRUE.equals(vm.getIsManager().getValue());
            }
        });
        SingleViewAdapter footerAdapter = new SingleViewAdapter(footer, false);
        binding.inspectList.setAdapter(new ConcatAdapter(
                new SingleViewAdapter(header.getRoot(), true), adapter,
                footerAdapter));
        binding.inspectList.addItemDecoration(new GroupedListDecoration(this));
        adapter.addLoadStateListener(states -> {
            boolean end = states.getAppend().getEndOfPaginationReached()
                    && !(states.getRefresh() instanceof LoadState.Loading);
            footerAdapter.setVisible(end);
            footer.setText(adapter.getItemCount() == 0 ? emptyText() : getString(R.string.inspect_history_no_more));
            return kotlin.Unit.INSTANCE;
        });
        vm.getPagingData().observe(this, data -> adapter.submitData(getLifecycle(), data));
        vm.getUiState().observe(this, binding.inspectState::render);
        binding.inspectState.setOnRetryListener(v -> vm.retry());
        vm.getOverview().observe(this, o -> header.inspectDays.setDays(o == null ? null : o.getDays()));
        header.inspectDays.setOnDayClick(vm::locate);
        vm.getHighlightDate().observe(this, d -> {
            adapter.setHighlight(d);
            scrollToDate(d);
        });
        header.inspectHistoryFilters.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            int id = ids.get(0);
            vm.setFilterType(id == R.id.inspect_history_abnormal ? HistoryQuery.ABNORMAL
                    : id == R.id.inspect_history_overdue ? HistoryQuery.OVERDUE
                    : id == R.id.inspect_history_backfill ? HistoryQuery.BACKFILL
                    : id == R.id.inspect_history_off_site ? HistoryQuery.OFF_SITE : HistoryQuery.ALL);
        });
        vm.getNavEvent().observe(this, t -> InspectNav.open(this, childLauncher, t));
        vm.getShowRecordLogs().observe(this, id -> RecordLogSheet.show(getSupportFragmentManager(),
                InspectExtras.BIZ_INSPECTION, id, null));
    }

    @NonNull
    private String emptyText() {
        switch (vm.currentFilterType()) {
            case HistoryQuery.ABNORMAL:
                return "没有异常记录";
            case HistoryQuery.OVERDUE:
                return "没有超时记录";
            case HistoryQuery.BACKFILL:
                return "没有补录记录";
            case HistoryQuery.OFF_SITE:
                return "没有非现场记录";
            default:
                return getString(R.string.inspect_history_empty);
        }
    }

    private void scrollToDate(@Nullable String date) {
        if (date == null) {
            return;
        }
        binding.inspectList.postDelayed(() -> {
            List<PenInspectionRowVO> items = adapter.snapshot().getItems();
            for (int i = 0; i < items.size(); i++) {
                if (date.equals(items.get(i).getInspectDate())) {
                    RecyclerView.LayoutManager lm = binding.inspectList.getLayoutManager();
                    if (lm instanceof LinearLayoutManager) {
                        ((LinearLayoutManager) lm).scrollToPositionWithOffset(i + 1, 0);
                    }
                    return;
                }
            }
        }, 400);
    }

    private void showMenu(@NonNull View anchor, @NonNull PenInspectionRowVO row) {
        MoreMenuHelper.Builder menu = MoreMenuHelper.with(anchor);
        for (int action : vm.menuFor(row)) {
            switch (action) {
                case InspectHistoryViewModel.MENU_DETAIL:
                    menu.add(action, getString(R.string.inspect_history_menu_detail), R.drawable.inspect_ic_visibility);
                    break;
                case InspectHistoryViewModel.MENU_MODIFY:
                    menu.add(action, getString(R.string.inspect_history_menu_modify),
                            com.zhimu.slmp.base.R.drawable.ic_edit_note);
                    break;
                case InspectHistoryViewModel.MENU_CHANGE:
                    menu.addReview(action, getString(R.string.inspect_history_menu_change),
                            com.zhimu.slmp.base.R.drawable.ic_edit_note);
                    break;
                case InspectHistoryViewModel.MENU_VIEW_CHANGE:
                    menu.add(action, getString(R.string.inspect_history_menu_view_change),
                            com.zhimu.slmp.base.R.drawable.ic_edit_note);
                    break;
                case InspectHistoryViewModel.MENU_LOGS:
                    menu.add(action, getString(R.string.inspect_history_menu_logs),
                            com.zhimu.slmp.base.R.drawable.ic_history);
                    break;
                default:
                    break;
            }
        }
        menu.setOnItemClickListener(id -> vm.onMenu(row, id)).show();
    }
}
