package com.zhimu.slmp.feed.ui.today;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.NoticeView;
import com.zhimu.slmp.base.widget.StateLayout;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.feed.R;
import com.zhimu.slmp.feed.databinding.FeedActivityTodayBinding;
import com.zhimu.slmp.feed.databinding.FeedViewTodayHeaderBinding;
import com.zhimu.slmp.feed.di.FeedInjector;
import com.zhimu.slmp.feed.model.vo.FeedPlanBatchOptionVO;
import com.zhimu.slmp.feed.model.vo.FeedTaskItemVO;
import com.zhimu.slmp.feed.model.vo.FeedTodaySummaryVO;
import com.zhimu.slmp.feed.model.vo.PlanShortageVO;
import com.zhimu.slmp.feed.ui.common.FeedNav;
import com.zhimu.slmp.feed.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.feed.ui.common.SingleViewAdapter;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.util.List;

import kotlin.Unit;

/**
 * G01 今日饲喂任务（L01“饲喂”、T02“投喂”进入）。点任务行进入 G02；tune 进入 G03（多个在养批次先选批次）；
 * bar_chart 进入 G04；横幅“去入库”进入 M03。子页面返回 RESULT_OK 后刷新。
 */
public class FeedTodayActivity extends BaseActivity {

    private static final String REQ_PEN = "feed_today_pen";
    private static final String REQ_BATCH = "feed_today_batch";

    private FeedActivityTodayBinding binding;
    private FeedViewTodayHeaderBinding header;
    private FeedTodayViewModel vm;
    private FeedTaskPagingAdapter tasks;
    private SingleViewAdapter headerAdapter;
    private boolean listEmpty;

    private final ActivityResultLauncher<Intent> childLauncher =
            Router.registerForResult(this, r -> vm.onChildResult(r.getResultCode() == Activity.RESULT_OK));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FeedActivityTodayBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FeedInjector.factory(), FeedTodayViewModel.class);
        observeBase(vm);

        binding.feedToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.feedToolbar.inflateMenu(R.menu.feed_menu_today);
        TopAppBarHelper.tintMenu(binding.feedToolbar);
        setManageMenuVisible(false);
        binding.feedToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.feed_menu_plan) {
                vm.onPlanEntryClick();
                return true;
            }
            if (item.getItemId() == R.id.feed_menu_exec) {
                vm.onExecEntryClick();
                return true;
            }
            return false;
        });

        setupList();
        setupFilters();

        binding.feedState.setOnRetryListener(v -> vm.retry());
        vm.getEmptyKind().observe(this, this::applyEmptyKind);
        vm.getUiState().observe(this, this::renderState);
        vm.getRefreshing().observe(this, r -> binding.feedRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.feedRefresh.setOnRefreshListener(() -> vm.refresh());

        vm.getPlanCard().observe(this, header.feedStatPlan::bind);
        vm.getDoneCard().observe(this, header.feedStatDone::bind);
        vm.getOverdueCard().observe(this, header.feedStatOverdue::bind);
        vm.getShowManageEntry().observe(this, show -> setManageMenuVisible(Boolean.TRUE.equals(show)));
        vm.getShortageBanners().observe(this, list -> renderBanners());
        vm.getShortageMoreText().observe(this, t -> renderBanners());
        vm.getShowStockIn().observe(this, s -> renderBanners());
        vm.getFeedTasks().observe(this, data -> tasks.submitData(getLifecycle(), data));

        vm.getOpenRegister().observe(this, args -> FeedNav.open(this, childLauncher, RouterPath.G02, args));
        vm.getOpenPlan().observe(this, id -> FeedNav.open(this, childLauncher, RouterPath.G03, FeedNav.batchArgs(id)));
        vm.getOpenExec().observe(this, v -> FeedNav.open(this, childLauncher, RouterPath.G04, null));
        vm.getOpenStockIn().observe(this, id -> FeedNav.open(this, childLauncher, RouterPath.M03,
                FeedNav.stockInArgs(id)));
        vm.getShowPenSheet().observe(this, v -> showPenSheet());
        vm.getShowBatchSheet().observe(this, v -> showBatchSheet());
        getSupportFragmentManager().setFragmentResultListener(REQ_PEN, this, (key, result) -> {
            String value = OptionSheetDialogFragment.getValue(result);
            vm.selectPenById(value == null ? 0L : Long.parseLong(value));
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_BATCH, this, (key, result) -> {
            String value = OptionSheetDialogFragment.getValue(result);
            if (value != null) {
                vm.onPlanBatchPicked(Long.parseLong(value));
            }
        });
    }

    private void setManageMenuVisible(boolean visible) {
        Menu menu = binding.feedToolbar.getMenu();
        menu.findItem(R.id.feed_menu_plan).setVisible(visible);
        menu.findItem(R.id.feed_menu_exec).setVisible(visible);
    }

    private void setupList() {
        RecyclerView list = binding.feedList;
        // 以 RecyclerView 为 parent inflate 头部前先设置 LayoutManager
        list.setLayoutManager(new LinearLayoutManager(this));
        header = FeedViewTodayHeaderBinding.inflate(getLayoutInflater(), list, false);
        headerAdapter = new SingleViewAdapter(header.getRoot());
        tasks = new FeedTaskPagingAdapter(vm::onTaskClick);
        FooterLoadStateAdapter footer = new FooterLoadStateAdapter(tasks::retry);
        ConcatAdapter concat = new ConcatAdapter(headerAdapter, tasks, footer);
        list.setAdapter(concat);
        list.addItemDecoration(new GroupedListDecoration(this) {
            @Override
            protected boolean isGroupItem(@NonNull RecyclerView parent, View view, int adapterPosition) {
                return tasks.isTask(adapterPosition - headerAdapter.getItemCount());
            }
        });
        tasks.addLoadStateListener(this::onLoadStates);
        // 新一页追加时，上一页末项的组内位置可能变化，重新绑定
        tasks.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (positionStart > 0) {
                    list.post(() -> tasks.notifyItemChanged(positionStart - 1));
                }
            }
        });
        TopAppBarHelper.bindLiftOnScroll(binding.feedAppBar, list);
    }

    private Unit onLoadStates(@NonNull CombinedLoadStates states) {
        boolean done = states.getRefresh() instanceof LoadState.NotLoading;
        listEmpty = done && tasks.getItemCount() == 0;
        header.feedTodayFilterEmpty.setText(vm.filterEmptyText());
        header.feedTodayFilterEmpty.setVisibility(listEmpty ? View.VISIBLE : View.GONE);
        return Unit.INSTANCE;
    }

    private void setupFilters() {
        header.feedStatusChips.setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.feed_chip_all : ids.get(0);
            String status = id == R.id.feed_chip_pending ? FeedTaskItemVO.STATUS_PENDING
                    : id == R.id.feed_chip_done ? FeedTaskItemVO.STATUS_DONE
                    : id == R.id.feed_chip_overdue ? FeedTaskItemVO.STATUS_OVERDUE : null;
            vm.selectStatus(status);
        });
        header.feedChipPen.setOnClickListener(v -> vm.onPenChipClick());
        header.feedChipPen.setOnCloseIconClickListener(v -> vm.onPenChipClick());
        vm.getPenFilter().observe(this, pen -> {
            boolean selected = pen != null;
            header.feedChipPen.setText(selected ? pen.getPenName() : getString(R.string.feed_today_chip_pen));
            header.feedChipPen.setCheckable(selected);
            header.feedChipPen.setChecked(selected);
        });
    }

    private void renderState(@Nullable UiState state) {
        binding.feedState.render(state);
    }

    /** 空状态：员工没有负责栏舍 / 养殖户无方案引导设置 / 员工今天没有任务 */
    private void applyEmptyKind(@Nullable FeedTodayViewModel.FeedEmptyKind kind) {
        StateLayout state = binding.feedState;
        boolean manager = vm.isManager();
        if (kind == FeedTodayViewModel.FeedEmptyKind.NO_PEN) {
            state.setEmpty(com.zhimu.slmp.base.R.drawable.ic_fence, getString(R.string.feed_today_empty_no_pen_title),
                    getString(R.string.feed_today_empty_no_pen_message), null, 0, StateLayout.EMPTY_ACTION_TONAL,
                    null);
        } else if (manager) {
            state.setEmpty(com.zhimu.slmp.base.R.drawable.ic_rice_bowl, getString(R.string.feed_today_empty_title),
                    getString(R.string.feed_today_empty_owner_message), getString(R.string.feed_today_empty_action),
                    com.zhimu.slmp.base.R.drawable.ic_tune, StateLayout.EMPTY_ACTION_TONAL,
                    v -> vm.onPlanEntryClick());
        } else {
            state.setEmpty(com.zhimu.slmp.base.R.drawable.ic_rice_bowl, getString(R.string.feed_today_empty_title),
                    getString(R.string.feed_today_empty_staff_message), null, 0, StateLayout.EMPTY_ACTION_TONAL, null);
        }
    }

    /** 库存不足横幅：逐种显示（最多 2 条），其余合并为“另有 n 种饲料库存不足”；员工没有“去入库” */
    private void renderBanners() {
        ViewGroup box = header.feedTodayBanners;
        box.removeAllViews();
        List<PlanShortageVO> list = vm.getShortageBanners().getValue();
        boolean stockIn = Boolean.TRUE.equals(vm.getShowStockIn().getValue());
        int gap = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_2);
        if (list != null) {
            for (PlanShortageVO s : list) {
                NoticeView n = newBanner(gap);
                n.setNotice(FeedFormat.orEmpty(s.getFeedName()) + "库存不足。", FeedTodayViewModel.shortageText(s));
                if (stockIn) {
                    n.setAction(getString(R.string.feed_action_stock_in), v -> vm.onStockInClick(s.getFeedId()));
                }
                box.addView(n);
            }
        }
        String more = vm.getShortageMoreText().getValue();
        if (more != null) {
            NoticeView n = newBanner(gap);
            n.setText(more);
            box.addView(n);
        }
        box.setVisibility(box.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) box.getLayoutParams();
        lp.topMargin = box.getChildCount() > 0 ? getResources().getDimensionPixelSize(
                com.zhimu.slmp.base.R.dimen.base_space_1) : 0;
        box.setLayoutParams(lp);
    }

    @NonNull
    private NoticeView newBanner(int topMargin) {
        NoticeView n = new NoticeView(this);
        n.setLevel(NoticeView.LEVEL_WARNING);
        n.setIcon(com.zhimu.slmp.base.R.drawable.ic_inventory_2);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        n.setLayoutParams(lp);
        return n;
    }

    /** “按栏舍查看”：点选立即生效并关闭，不设确认按钮 */
    private void showPenSheet() {
        FeedTodaySummaryVO s = vm.getSummary().getValue();
        if (s == null) {
            return;
        }
        FeedTodaySummaryVO.FeedPenOptionVO current = vm.getPenFilter().getValue();
        OptionSheetDialogFragment.Builder b = OptionSheetDialogFragment.builder(REQ_PEN)
                .title(getString(R.string.feed_today_pen_sheet_title))
                .option("0", getString(R.string.feed_today_pen_all), null);
        for (FeedTodaySummaryVO.FeedPenOptionVO p : s.getPenOptions()) {
            b.option(String.valueOf(p.getPenId()), FeedFormat.orEmpty(p.getPenName()), p.getMealCount() + " 餐");
        }
        b.selected(current == null ? "0" : String.valueOf(current.getPenId()));
        b.build().showSafely(getSupportFragmentManager());
    }

    /** “选择批次”：没有饲喂方案的批次行尾为“未设置” */
    private void showBatchSheet() {
        List<FeedPlanBatchOptionVO> list = vm.getPlanBatches().getValue();
        if (list == null || list.isEmpty()) {
            return;
        }
        OptionSheetDialogFragment.Builder b = OptionSheetDialogFragment.builder(REQ_BATCH)
                .title(getString(R.string.feed_batch_sheet_title));
        for (FeedPlanBatchOptionVO o : list) {
            String hint = FeedFormat.join(o.getPenNames(), o.getBreedName(),
                    o.getDayAge() == null ? null : o.getDayAge() + " 日龄");
            OptionSheetDialogFragment.Option option = new OptionSheetDialogFragment.Option(
                    String.valueOf(o.getBatchId()), FeedFormat.orEmpty(o.getBatchNo()), hint);
            if (!o.isHasPlan()) {
                option.tag(getString(R.string.feed_plan_not_set), StatusTagView.NEUTRAL);
            }
            b.option(option);
        }
        b.build().showSafely(getSupportFragmentManager());
    }
}
