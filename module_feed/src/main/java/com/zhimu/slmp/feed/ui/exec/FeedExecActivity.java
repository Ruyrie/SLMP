package com.zhimu.slmp.feed.ui.exec;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.zhimu.slmp.base.dialog.TipPopup;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.GroupedListDecoration;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.feed.R;
import com.zhimu.slmp.feed.databinding.FeedActivityListBinding;
import com.zhimu.slmp.feed.databinding.FeedViewExecHeaderBinding;
import com.zhimu.slmp.feed.di.FeedInjector;
import com.zhimu.slmp.feed.model.request.FeedExecQuery;
import com.zhimu.slmp.feed.model.vo.FeedExecDayVO;
import com.zhimu.slmp.feed.model.vo.FeedExecSummaryVO;
import com.zhimu.slmp.feed.model.vo.FeedPlanBatchOptionVO;
import com.zhimu.slmp.feed.ui.common.FeedNav;
import com.zhimu.slmp.feed.ui.common.FooterLoadStateAdapter;
import com.zhimu.slmp.feed.ui.common.SingleViewAdapter;
import com.zhimu.slmp.feed.util.FeedFormat;
import com.zhimu.slmp.feed.widget.BarChartModel;
import com.zhimu.slmp.feed.widget.FeedDailyBarView;
import com.zhimu.slmp.feed.widget.FeedStatCard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import kotlin.Unit;

/**
 * G04 饲喂执行情况（G01 bar_chart 进入）。点柱显示提示气泡与当天明细，点“需要关注”进入 G02。
 */
public class FeedExecActivity extends BaseActivity {

    private FeedActivityListBinding binding;
    private FeedViewExecHeaderBinding header;
    private FeedExecViewModel vm;
    private FeedAttentionPagingAdapter attention;
    private FeedExecDayAdapter dayAdapter;
    private FooterLoadStateAdapter footer;
    private ConcatAdapter concat;
    private boolean showingDay;
    private boolean syncingChips;
    private float barX;
    private float barY;
    private View tipAnchor;

    private final ActivityResultLauncher<Intent> childLauncher =
            Router.registerForResult(this, r -> vm.onChildResult(r.getResultCode() == Activity.RESULT_OK));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FeedActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(FeedInjector.factory(), FeedExecViewModel.class);
        observeBase(vm);

        binding.feedToolbar.setTitle(R.string.feed_exec_title);
        binding.feedToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.feedToolbar.inflateMenu(R.menu.feed_menu_exec);
        TopAppBarHelper.tintMenu(binding.feedToolbar);
        binding.feedToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.feed_menu_range) {
                vm.onRangeMenuClick();
                return true;
            }
            return false;
        });
        binding.feedState.setErrorTitle(getString(R.string.feed_exec_error_title));
        binding.feedState.setNoPermissionTitle(getString(R.string.feed_exec_no_permission_title));
        binding.feedState.setEmpty(com.zhimu.slmp.base.R.drawable.ic_rice_bowl, getString(R.string.feed_exec_empty_title),
                getString(R.string.feed_exec_empty_message), null, 0, 0, null);
        binding.feedState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, this::renderState);
        vm.getRefreshing().observe(this, r -> binding.feedRefresh.setRefreshing(Boolean.TRUE.equals(r)));
        binding.feedRefresh.setOnRefreshListener(() -> vm.refresh());

        setupList();
        setupHeader();

        vm.getBatchChips().observe(this, this::renderChips);
        vm.getSelectedBatchId().observe(this, id -> syncChipSelection());
        vm.getRange().observe(this, this::renderRange);
        vm.getRangeText().observe(this, t -> header.feedExecRangeText.setText(t));
        vm.getStatCards().observe(this, this::renderStats);
        vm.getChartModel().observe(this, this::renderChart);
        vm.getSummary().observe(this, this::renderLegend);
        vm.getEmptyKind().observe(this, k -> header.feedExecChartEmpty.setVisibility(
                FeedExecSummaryVO.EMPTY_NO_TASK.equals(k) ? View.VISIBLE : View.GONE));
        vm.getSummaryLoading().observe(this, l -> header.feedExecStats.setAlpha(Boolean.TRUE.equals(l) ? 0.5f : 1f));
        vm.getPagingData().observe(this, data -> attention.submitData(getLifecycle(), data));
        vm.getSelectedBar().observe(this, i -> {
            header.feedExecChart.setSelected(i == null ? -1 : i);
            if (i == null || i < 0) {
                TipPopup.dismiss();
            }
            renderListMode();
        });
        vm.getDayDetail().observe(this, d -> renderListMode());
        vm.getShowBarTip().observe(this, this::showBarTip);
        vm.getShowRangePicker().observe(this, v -> showRangePicker());
        vm.getOpenRegister().observe(this, args -> FeedNav.open(this, childLauncher, RouterPath.G02, args));
    }

    private void setupList() {
        RecyclerView list = binding.feedList;
        list.setLayoutManager(new LinearLayoutManager(this));
        header = FeedViewExecHeaderBinding.inflate(getLayoutInflater(), list, false);
        attention = new FeedAttentionPagingAdapter(vm::onAttentionClick, vm::isAllBatches);
        dayAdapter = new FeedExecDayAdapter();
        footer = new FooterLoadStateAdapter(attention::retry);
        concat = new ConcatAdapter(new SingleViewAdapter(header.getRoot()), attention, footer);
        list.setAdapter(concat);
        list.addItemDecoration(new GroupedListDecoration(this) {
            @Override
            protected boolean isGroupItem(@NonNull RecyclerView parent, View view, int adapterPosition) {
                int localCount = showingDay ? dayAdapter.getItemCount() : attention.getItemCount();
                return adapterPosition >= 1 && adapterPosition <= localCount;
            }
        });
        attention.addLoadStateListener(this::onLoadStates);
        attention.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (positionStart > 0) {
                    list.post(() -> attention.notifyItemChanged(positionStart - 1));
                }
            }
        });
        TopAppBarHelper.bindLiftOnScroll(binding.feedAppBar, list);
        // 提示气泡的锚点（放在柱顶位置）
        tipAnchor = new View(this);
        ((android.view.ViewGroup) header.feedExecChart.getParent()).addView(tipAnchor,
                new android.widget.FrameLayout.LayoutParams(1, 1));
        tipAnchor.setVisibility(View.INVISIBLE);
    }

    private Unit onLoadStates(@NonNull CombinedLoadStates states) {
        boolean empty = states.getRefresh() instanceof LoadState.NotLoading && attention.getItemCount() == 0;
        header.feedExecListEmpty.setVisibility(!showingDay && empty ? View.VISIBLE : View.GONE);
        return Unit.INSTANCE;
    }

    private void setupHeader() {
        header.feedExecBatchChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (syncingChips || ids.isEmpty()) {
                return;
            }
            Chip chip = group.findViewById(ids.get(0));
            Object tag = chip == null ? null : chip.getTag();
            vm.selectBatch(tag instanceof Long ? (Long) tag : null);
        });
        header.feedExecRangeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            String r = checkedId == R.id.feed_exec_range_30 ? FeedExecQuery.RANGE_LAST_30
                    : checkedId == R.id.feed_exec_range_batch ? FeedExecQuery.RANGE_BATCH : FeedExecQuery.RANGE_LAST_7;
            vm.selectRange(r);
        });
        header.feedExecChart.setOnBarClickListener(new FeedDailyBarView.OnBarClickListener() {
            @Override
            public void onBarClick(int index, float centerX, float topY) {
                barX = centerX;
                barY = topY;
                vm.onBarClick(index);
            }

            @Override
            public void onBlankClick() {
                vm.clearBar();
            }
        });
    }

    private void renderState(@Nullable UiState state) {
        binding.feedState.render(state);
        if (state != null && state.is(UiState.Status.NO_PERMISSION) && state.getErrorCode() == ErrorCodes.ROLE_FORBIDDEN) {
            binding.feedState.showNoPermission(getString(R.string.feed_exec_no_permission_title),
                    getString(R.string.feed_exec_no_permission_message));
        }
        boolean content = state == null || state.is(UiState.Status.CONTENT);
        binding.feedToolbar.getMenu().findItem(R.id.feed_menu_range).setVisible(content);
    }

    /** 批次 Chip：在养批次 + 末尾“全部批次” */
    private void renderChips(@Nullable List<FeedPlanBatchOptionVO> list) {
        syncingChips = true;
        header.feedExecBatchChips.removeAllViews();
        if (list != null) {
            for (FeedPlanBatchOptionVO o : list) {
                header.feedExecBatchChips.addView(newChip(FeedFormat.orEmpty(o.getBatchNo()), o.getBatchId()));
            }
        }
        header.feedExecBatchChips.addView(newChip(getString(R.string.feed_exec_all_batches), null));
        syncingChips = false;
        syncChipSelection();
    }

    @NonNull
    private Chip newChip(@NonNull String text, @Nullable Long batchId) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.feed_item_filter_chip, header.feedExecBatchChips,
                false);
        chip.setId(View.generateViewId());
        chip.setText(text);
        chip.setTag(batchId);
        return chip;
    }

    private void syncChipSelection() {
        Long selected = vm.getSelectedBatchId().getValue();
        syncingChips = true;
        for (int i = 0; i < header.feedExecBatchChips.getChildCount(); i++) {
            Chip chip = (Chip) header.feedExecBatchChips.getChildAt(i);
            Object tag = chip.getTag();
            boolean match = Objects.equals(tag, selected);
            if (match && !chip.isChecked()) {
                header.feedExecBatchChips.check(chip.getId());
            }
        }
        syncingChips = false;
    }

    /** 自定义范围时连接按钮组取消选中 */
    private void renderRange(@Nullable String range) {
        int id = FeedExecQuery.RANGE_LAST_30.equals(range) ? R.id.feed_exec_range_30
                : FeedExecQuery.RANGE_BATCH.equals(range) ? R.id.feed_exec_range_batch
                : FeedExecQuery.RANGE_LAST_7.equals(range) ? R.id.feed_exec_range_7 : View.NO_ID;
        if (id == View.NO_ID) {
            header.feedExecRangeGroup.setSelectionRequired(false);
            header.feedExecRangeGroup.clearChecked();
        } else if (header.feedExecRangeGroup.getCheckedButtonId() != id) {
            header.feedExecRangeGroup.setSelectionRequired(true);
            header.feedExecRangeGroup.check(id);
        } else {
            header.feedExecRangeGroup.setSelectionRequired(true);
        }
    }

    private void renderStats(@Nullable List<FeedStatCard> cards) {
        FeedStatCard[] arr = new FeedStatCard[4];
        if (cards != null) {
            for (int i = 0; i < Math.min(4, cards.size()); i++) {
                arr[i] = cards.get(i);
            }
        }
        header.feedExecStatRate.bind(arr[0]);
        header.feedExecStatDev.bind(arr[1]);
        header.feedExecStatQty.bind(arr[2]);
        header.feedExecStatAbnormal.bind(arr[3]);
    }

    private void renderChart(@Nullable BarChartModel model) {
        header.feedExecChart.setData(model);
    }

    /** 图例：单批次“计划 480 千克”；全部批次（阶梯线）为“计划” */
    private void renderLegend(@Nullable FeedExecSummaryVO s) {
        String text = "计划";
        if (s != null && !FeedExecSummaryVO.LINE_STEP.equals(s.getPlanLineType()) && s.getPlanDailyQty() != null) {
            text = "计划 " + FeedFormat.kg(s.getPlanDailyQty());
        }
        header.feedExecLegendPlan.setText(text);
    }

    /** “需要关注”与当天明细切换 */
    private void renderListMode() {
        Integer bar = vm.getSelectedBar().getValue();
        boolean day = bar != null && bar >= 0;
        FeedExecDayVO detail = vm.getDayDetail().getValue();
        if (day) {
            BarChartModel chart = vm.getChartModel().getValue();
            String date = chart != null && bar < chart.bars.size() ? chart.bars.get(bar).date : null;
            header.feedExecSection.setText(FeedFormat.date(date) + " 各餐明细");
            header.feedExecListEmpty.setVisibility(View.GONE);
        } else {
            header.feedExecSection.setText(R.string.feed_exec_section_attention);
        }
        if (day != showingDay) {
            showingDay = day;
            if (day) {
                concat.removeAdapter(attention);
                concat.removeAdapter(footer);
                concat.addAdapter(dayAdapter);
            } else {
                concat.removeAdapter(dayAdapter);
                concat.addAdapter(attention);
                concat.addAdapter(footer);
            }
        }
        if (day) {
            dayAdapter.submit(detail, vm.isAllBatches());
        }
    }

    private void showBarTip(@NonNull String text) {
        tipAnchor.setTranslationX(barX);
        tipAnchor.setTranslationY(barY);
        tipAnchor.post(() -> {
            if (!isFinishing()) {
                TipPopup.show(tipAnchor, text);
            }
        });
    }

    /** MaterialDatePicker 范围模式：结束日期不能晚于今天 */
    private void showRangePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
                .build();
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.feed_exec_menu_range)
                .setCalendarConstraints(constraints)
                .build();
        picker.addOnPositiveButtonClickListener(sel -> {
            if (sel == null || sel.first == null || sel.second == null) {
                return;
            }
            vm.onRangePicked(DateFormatter.toDateString(toDate(sel.first)), DateFormatter.toDateString(toDate(sel.second)));
        });
        picker.show(getSupportFragmentManager(), "feed_exec_range");
    }

    @NonNull
    private static LocalDate toDate(long utcMillis) {
        return Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate();
    }
}
